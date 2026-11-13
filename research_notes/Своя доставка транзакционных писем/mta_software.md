# Self-hosted outbound mail software and reference architecture (state as of 2026-11-06)

Method note: release dates, licences and activity below were read from the GitHub API on 2026-11-06, not from memory. postfix.org refused connections from the research environment (connection reset / HTTP 402), so Postfix facts come from the documentation sources in the `vdukhovni/postfix` repository (`postfix/proto/*`, `RELEASE_NOTES*`), which is a mirror and not the official site. Config blocks in this document were composed from the cited parameter docs and have NOT been run on a live host.

## 1. Which software is realistic? Comparison of candidates

### Takeaway
For few developers, a few hundred to low tens of thousands of messages a day, and no mail admin, the realistic shortlist is Postfix + OpenDKIM (most boring, smallest, no API or webhooks included), then Postal (closest to a self-hosted Mailgun, but requires MariaDB and 4 GB RAM), then Mox and Hyvor Relay (built-in webapi, webhooks and suppression, but both are pre-1.0). KumoMTA's own docs steer this volume elsewhere, and Mailcow, Stalwart, docker-mailserver and similar are mailbox suites where most of the software would sit unused.

### Cited Findings

**Maintenance and licence (verified 2026-11-06)**

| Project | Latest release (date) | Last push | Licence | Sources |
|---|---|---|---|---|
| Postfix | 3.11.7 stable (2026-09-08); other tags 3.10.14, 3.9.15, 3.8.21; snapshot 3.12-20261015 | 2026-10-20 | IPL 1.0 / EPL 2.0 dual, recipient's choice | [tag](https://github.com/vdukhovni/postfix/tree/v3.11.7), [RELEASE_NOTES](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/RELEASE_NOTES) |
| OpenDKIM | No tag newer than 2.11.0-Beta2; `develop` branch has fixes merged July 2026, including a stack overflow fix | 2026-07-23 | GitHub reports NOASSERTION | [repo](https://github.com/trusteddomainproject/OpenDKIM) |
| Rspamd | 4.2.3 (2026-10-21) | 2026-11-06 | GitHub reports NOASSERTION | [releases](https://github.com/rspamd/rspamd/releases) |
| Postal | 3.3.7 (2026-06-03) | 2026-11-04 | MIT | [releases](https://github.com/postalserver/postal/releases) |
| Haraka | 3.4.0 (2026-11-05) | 2026-11-06 | MIT | [releases](https://github.com/haraka/Haraka/releases) |
| KumoMTA | 2026.09.29-b90d8bc1 (2026-09-29) | 2026-11-06 | Apache-2.0 | [releases](https://github.com/KumoCorp/kumomta/releases) |
| Stalwart | 1.0.3 (2026-11-04); 1.0.0 on 2026-10-26 | 2026-11-04 | AGPL-3.0 + proprietary SELv2 dual | [releases](https://github.com/stalwartlabs/stalwart/releases), [README](https://github.com/stalwartlabs/stalwart/blob/HEAD/README.md) |
| Maddy | 0.9.7 (2026-11-05) | 2026-11-05 | GPL-3.0 | [releases](https://github.com/foxcpp/maddy/releases) |
| Mox | 0.0.17 (2026-08-19) | 2026-10-27 | MIT | [releases](https://github.com/mjl-/mox/releases) |
| Hyvor Relay | 0.0.48 (2026-10-21) | 2026-11-03 | AGPL-3.0 | [repo](https://github.com/hyvor/relay) |
| Mailcow | 2026-11 (2026-11-05) | 2026-11-05 | GPL-3.0 | [repo](https://github.com/mailcow/mailcow-dockerized) |
| docker-mailserver | 17.0.0 (2026-11-05) | 2026-11-06 | MIT | [repo](https://github.com/docker-mailserver/docker-mailserver) |
| Mailu | 2024.06.67 (2026-10-23) | 2026-11-06 | GitHub reports NOASSERTION | [repo](https://github.com/Mailu/Mailu) |
| Mail-in-a-Box | v77 (2026-09-26) | 2026-10-22 | CC0-1.0 | [repo](https://github.com/mail-in-a-box/mailinabox) |
| Exim | 4.100.1 (a security release) | GitHub repository archived, last push 2025-12-29 | not checked | [exim.org](https://www.exim.org/), [GitHub](https://github.com/Exim/exim) |
| OpenSMTPD | 7.9.0p0 (2026-09-13) | 2026-11-03 | GitHub reports NOASSERTION | [repo](https://github.com/OpenSMTPD/OpenSMTPD) |
| BillionMail | 4.9 (2025-12-11) | 2026-06-11 | AGPL-3.0 | [repo](https://github.com/Billionmail/BillionMail) |
| bokysan/docker-postfix (Postfix container) | 5.2.1 (2026-11-02) | 2026-11-04 | MIT | [repo](https://github.com/bokysan/docker-postfix) |

**Distribution packages**
- Debian 13 (trixie) ships Postfix 3.10.13, OpenDKIM 2.11.0~beta2 and Rspamd 3.12.1; forky/sid have Postfix 3.11.7 and Rspamd 4.1.5 — [sources.debian.org postfix](https://sources.debian.org/api/src/postfix/), [opendkim](https://sources.debian.org/api/src/opendkim/), [rspamd](https://sources.debian.org/api/src/rspamd/)
- Ubuntu's `resolute` series publishes Postfix 3.10.6-4ubuntu2.2 — [Launchpad API](https://api.launchpad.net/1.0/ubuntu/+archive/primary?ws.op=getPublishedSources&source_name=postfix&exact_match=true&status=Published&ws.size=10)

**Postfix**
- Dedicated send-only pattern is documented as the "null client": "a machine that can only send mail", though the stock example relays through another server rather than delivering directly to MX — [STANDARD_CONFIGURATION_README](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/STANDARD_CONFIGURATION_README.html)
- Postfix is paired with OpenDKIM for signing, connected as a milter; step-by-step setup (keys, TrustedHosts, SigningTable) in a [Habr article](https://habr.com/post/415569/), and a common failure is a wrongly configured milter producing no DKIM header — [Habr Q&A](https://qna.habr.com/q/400552). Those posts are old but the mechanism has not changed
- TLSRPT support since 3.10; MTA-STS only through a policy plugin — [TLSRPT_README](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/TLSRPT_README.html)

**Postal**
- Positions itself as "Sendgrid, Mailgun or Postmark but open source" — [README](https://github.com/postalserver/postal/blob/HEAD/README.md)
- Sends through SMTP or HTTP API, with DKIM signing, built-in suppression list, per-server send limits, IP pools, delivery logs, and webhook request history kept 7 days. The feature page does not mention per-domain limits — [feature list](https://docs.postalserver.io/welcome/feature-list)
- Requires at least 4 GB RAM, 2 cores and 25 GB disk, a dedicated server is "strongly recommended", runs entirely in containers, needs MariaDB 10.6+ and explicitly does not support MySQL — [prerequisites](https://docs.postalserver.io/getting-started/prerequisites)
- Webhook events: MessageSent, MessageDelayed, MessageDeliveryFailed, MessageHeld, MessageBounced, MessageLinkClicked, MessageLoaded, DomainDNSError — [webhooks](https://docs.postalserver.io/developer/webhooks)
- Webhook requests carry `X-Postal-Signature`, `X-Postal-Signature-256` and `X-Postal-Signature-KID` — [http.rb](https://github.com/postalserver/postal/blob/HEAD/lib/postal/http.rb). The webhook docs page itself does not describe signing — [webhooks](https://docs.postalserver.io/developer/webhooks)
- Not literally send-only: the return path domain has an MX pointing to Postal, so it receives bounces — [DNS configuration](https://docs.postalserver.io/getting-started/dns-configuration)
- Prometheus metrics at `/metrics`, worker on 9090 and SMTP on 9091 — [health & metrics](https://github.com/postalserver/docs/blob/HEAD/content/3.features/health-metrics.md)
- Defaults: 18 delivery attempts, suppression entries removed after 30 days — [yaml.yml](https://github.com/postalserver/postal/blob/HEAD/doc/config/yaml.yml)
- Release cadence is uneven: 3.3.4 on 2024-06-20, then nothing until 3.3.5 on 2026-02-01, then 3.3.6 and 3.3.7 in April and June 2026. Commits after the last release include configurable DKIM key size and rotation (2026-10-01), so that is not yet in a tagged release — [releases](https://github.com/postalserver/postal/releases), [commits](https://github.com/postalserver/postal/commits)

**KumoMTA**
- Aimed at "experienced email operations professionals"; senders under one million messages a month are told a general-purpose MTA would serve them better, with Postal named as an example — [who is KumoMTA for](https://docs.kumomta.com/faq/who_is_kumomta_for/)
- Absolute minimum 4 CPU cores, 4 GB RAM, 20 GB storage, same for Docker — [hardware](https://docs.kumomta.com/userguide/installation/hardware/)
- Docs cover Docker, webhooks, DKIM, traffic shaping, bounce classification and Prometheus — [docs home](https://docs.kumomta.com/)

**Stalwart**
- About 85% of features are AGPL; the rest are Enterprise. The README promises that no existing open-source feature moves to Enterprise — [README](https://github.com/stalwartlabs/stalwart/blob/HEAD/README.md)
- Recent licensing-adjacent change: with 1.0, unit, integration, conformance and stress test suites moved to a private repository and new features are developed privately before release. Each release's source stays public under AGPL-3.0 — [v1.0.0 notes](https://github.com/stalwartlabs/stalwart/releases/tag/v1.0.0), [blog](https://stalw.art/blog/protecting-stalwart/)
- 0.16.x cannot be upgraded in place to 1.0; migration tool required, with many schema renames — [v1.0.0 notes](https://github.com/stalwartlabs/stalwart/releases/tag/v1.0.0)
- Community edition includes webhooks, DKIM, MTA-STS, TLS reporting, DMARC reports, throttling and PostgreSQL storage. Metrics are "Limited"; live telemetry and message delivery history are Enterprise only — [compare editions](https://stalw.art/compare/)
- Date conflict: GitHub dates 1.0.0 to 2026-10-26 — [release](https://github.com/stalwartlabs/stalwart/releases/tag/v1.0.0); heise reports 27 October — [heise](https://www.heise.de/en/news/Stalwart-The-world-s-fastest-mail-server-11501616.html)
- GitHub's licence field is empty because licences live in a `LICENSES/` directory (REUSE layout) — [LICENSES](https://github.com/stalwartlabs/stalwart/tree/HEAD/LICENSES)
- Operator anecdotes from before 1.0: one finds it the easiest to set up as a single binary — [HN](https://news.ycombinator.com/item?id=45475846); another points out it has no webmail — [HN](https://news.ycombinator.com/item?id=43263714)

**Mox**
- Webapi and webhooks exist specifically for transactional email "with only HTTP/JSON"; outgoing webhook events are delivered, delayed, failed, suppressed; per-account suppression lists are managed automatically from SMTP codes and DSNs — [features](https://www.xmox.nl/features/)
- Prometheus metrics, structured logging — [README](https://github.com/mjl-/mox/blob/HEAD/README.md)
- MTA-STS and DANE on delivery — [features](https://www.xmox.nl/features/)

**Hyvor Relay**
- Self-hosted email API, Docker compose or swarm deployment, PostgreSQL as the database dependency, with bounce handling, feedback loops, suppressions, webhooks, Prometheus and Grafana, separate transactional and distributional queues, plus a built-in DNS server. PHP/Symfony API with Go workers — [README](https://github.com/hyvor/relay/blob/HEAD/README.md), [hosting docs](https://relay.hyvor.com/hosting)

**Haraka**
- Outbound retry pattern is configurable, e.g. `temp_fail_intervals`, and `none` bounces on first temp-fail. Plugin hooks `deferred`, `bounce`, `delivered` — [Outbound.md](https://github.com/haraka/Haraka/blob/HEAD/docs/Outbound.md)
- DKIM lives in a separate plugin — [haraka-plugin-dkim](https://github.com/haraka/haraka-plugin-dkim)

**Maddy**
- Combines MTA, MX and IMAP in one daemon; IMAP storage is labelled beta but it "still can handle message delivery business" — [README](https://github.com/foxcpp/maddy/blob/HEAD/README.md)
- 0.9.7 fixes vulnerabilities rated up to High, including two DMARC verification bypasses — [release](https://github.com/foxcpp/maddy/releases/tag/v0.9.7)
- Queue has `max_tries`, `initial_retry_time`, `retry_time_scale` and a bounce block — [queue docs](https://github.com/foxcpp/maddy/blob/HEAD/docs/reference/targets/queue.md); remote target has `limits`, MTA-STS and DANE — [remote docs](https://github.com/foxcpp/maddy/blob/HEAD/docs/reference/targets/remote.md); OpenMetrics endpoint — [openmetrics](https://github.com/foxcpp/maddy/blob/HEAD/docs/reference/endpoints/openmetrics.md)

**Mailcow**
- Minimum 6 GiB RAM plus 1 GiB swap, 20 GiB disk without mail — [prerequisites](https://docs.mailcow.email/getstarted/prerequisite-system/)

**docker-mailserver**
- Describes itself as a full-stack server with SMTP, IMAP, LDAP, anti-spam and anti-virus — [README](https://github.com/docker-mailserver/docker-mailserver/blob/HEAD/README.md)

### Inferences

| Option | Send-only fit | API | Webhooks / bounces | Resources (stated) | Ops complexity for this team | Verdict |
|---|---|---|---|---|---|---|
| Postfix + OpenDKIM | Very good | SMTP only | Build it yourself | Not stated; small | Low once set up; config is stable, knowledge is everywhere | **Default choice** |
| Postfix + Rspamd | Good | SMTP only | Build it yourself | Larger | Extra service to run for DKIM only | Only if Rspamd is already wanted |
| Postal | Good | SMTP + HTTP | Yes, signed | 4 GB, 2 cores, dedicated, MariaDB | Medium; a second DB engine next to Postgres, in-app mail DB to manage | **Choice if a UI and API are worth a bigger box** |
| Mox | Medium (full server) | SMTP + webapi | Yes, with suppression | Not found | Low, single binary; pre-1.0 | Interesting; try before committing |
| Hyvor Relay | Good | HTTP | Yes, including FBL | Not checked | Medium; young project, 0.0.x; reuses Postgres | Watch; no operator reports found |
| Haraka | Medium | SMTP; API via plugin | Via plugin code | Not checked | Medium; you write JS plugins in a Java team | No clear gain over Postfix |
| KumoMTA | Technically | SMTP + HTTP | Yes | 4 cores, 4 GB minimum | High; vendor itself says not this volume | Reject |
| Stalwart | Overkill | SMTP | Webhooks in Community | Not stated | Medium; 1.0 is 11 days old, fast breaking changes | Reject for now |
| Maddy | Medium | SMTP | No webhooks found | Not checked | Low; pre-1.0, recent security fixes | Reject |
| Mailcow, docker-mailserver, Mailu, MIAB | Poor (mailbox suites) | SMTP | No | Mailcow 6 GiB | High relative to need | Reject |
| Exim, OpenSMTPD | Good | SMTP only | Build it yourself | Not checked | Same class as Postfix, less fit with existing docs | No advantage |
| BillionMail | Not checked | Not checked | Not checked | Not checked | Last release Dec 2025, last push June 2026 | Not evaluated further |

- Docker: Postal, Mailcow, Hyvor and KumoMTA have Docker paths per their docs; Postfix can run in a container through `bokysan/docker-postfix`, but a Debian package plus systemd has fewer moving parts on a single VPS (journald logs, no Docker NAT in front of port 25). This is judgement, not a sourced benchmark.
- Choice of MTA does not change the hard part: the deliverability sources I found point at IP and domain reputation, not at software (see section 7).
- OpenDKIM is a weak link by maintenance status: distributions ship a 2.11.0 beta, and upstream only recently resumed fixing. It still works and is packaged. Keep it replaceable, because it only sits behind a milter socket.

### Gaps
- OpenDKIM and Rspamd exact licence names were not verified; GitHub reports NOASSERTION for both.
- No independent 2026 operator write-ups of Postal, Hyvor Relay or Mox at this volume were found. Search results for Postal were mainly SEO guides, e.g. [pistack](https://www.pistack.xyz/posts/2026-05-24-self-hosted-email-bounce-management-postal-stalwart-haraka-guide/), and I did not rely on them.
- Stated memory footprints for Postfix, Haraka, Mox, Maddy and Hyvor Relay were not found.
- Whether Mox supports per-destination-domain rate limits was not confirmed.
- Maddy: absence of webhooks is based on docs I read, not an exhaustive check.
- SELv2 terms were not read.

## 2. Minimal correct send-only Postfix configuration on Debian/Ubuntu

### Takeaway
The Postfix defaults are safe for relay but wrong for one-time codes: queue lifetime is 5 days and outbound TLS default depends on version. Set queue lifetime and backoff explicitly, set `smtp_tls_security_level = may` on 3.10, keep `smtpd_relay_restrictions` restrictive, and add dedicated transports for per-domain throttling. Remember that `maximal_queue_lifetime` is global.

### Cited Findings

**DNS and authentication requirements**
- All senders to Gmail need SPF or DKIM, valid forward and reverse DNS (PTR) for sending IP or domain, TLS, spam rate under 0.3%, and RFC 5322 formatting. Over 5,000 messages a day needs both SPF and DKIM, plus DMARC (`p=none` acceptable) and From alignment. DKIM keys at least 1024 bits, 2048 recommended — [Google sender guidelines](https://support.google.com/mail/answer/81126?hl=en)
- An IPv6 authorization error can mean the PTR is not set up for IPv6 — [Google](https://support.google.com/mail/answer/81126?hl=en)
- Mail.ru and Yandex check SPF, DKIM, DMARC and are stricter with new IPs; RSA-2048 suggested, avoid `l=`, one SPF record, start DMARC at `p=none` with `rua`. This is from a self-promoting author (article tagged as PR), and his figures are unverified — [Habr](https://habr.com/ru/articles/1061824/)

**Queue parameters**
- `maximal_queue_lifetime` default 5d; 0 means try once — [postconf.proto](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/postconf.proto)
- `bounce_queue_lifetime` default 5d — same source
- `minimal_backoff_time` 300s, `maximal_backoff_time` 4000s, `queue_run_delay` 300s; `queue_run_delay` should be ≤ `minimal_backoff_time` ≤ `maximal_backoff_time` — same source
- No transport-specific queue lifetime parameter exists in the parameter reference (searched `postconf.proto`). Multiple instances on one host are supported through `postmulti` — [MULTI_INSTANCE_README](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/MULTI_INSTANCE_README.html)

**Rate limits per destination**
- `default_destination_rate_delay` default 0; override is `transport_destination_rate_delay`. With recipient limit > 1 it is the time between deliveries to the same domain. Enforced by the queue manager, and the timer does not survive `postfix reload` — [postconf.proto](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/postconf.proto)
- With a non-zero rate delay, docs say to set `transport_destination_concurrency_failed_cohort_limit` to 10 or more, otherwise a single connection or handshake error defers all mail for that destination — same source
- `smtp_destination_concurrency_limit` applies per destination for the smtp transport — same source
- `transport_maps` assigns recipient domains to a delivery transport — same source
- `smtp_transport_rate_delay` throttles the transport regardless of destination — same source

**TLS**
- `smtp_tls_security_level` default is `may` for Postfix ≥ 3.11, and empty for < 3.11, with the effective level `none` at `compatibility_level` < 3.11. Debian 13's 3.10 is therefore on the older side of this change — [postconf.proto](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/postconf.proto)
- `smtpd_tls_auth_only` default no — same source
- `smtp_tls_connection_reuse` default no (Postfix 3.4+) — same source
- `smtpd_tls_chain_files` is the preferred interface for certificate files — [TLS_README](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/TLS_README.html)

**MTA-STS and TLS-RPT**
- MTA-STS is published by the receiving domain: TXT `_mta-sts` and HTTPS `mta-sts.<domain>/.well-known/mta-sts.txt` — [RFC 8461](https://www.rfc-editor.org/rfc/rfc8461.txt)
- A TLS-RPT policy looks like `_smtp._tls.example.com. IN TXT "v=TLSRPTv1; rua=mailto:smtp-tls-report@example.com"` and may use `https:` — [TLSRPT_README](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/TLSRPT_README.html)
- Outbound MTA-STS enforcement needs a plugin via `smtp_tls_policy_maps`: `postfix-tlspol` (with DANE) or `postfix-mta-sts-resolver` — same source. The resolver is at 1.5.1, 2025-10-10 — [repo](https://github.com/Snawoot/postfix-mta-sts-resolver)
- Sending TLS-RPT reports needs linked libtlsrpt and sys4's reporter, at 0.6.0 (2026-08-31) — [tlsrpt-reporter](https://github.com/sys4/tlsrpt-reporter)
- `smtp_tls_enforce_sts_mx_patterns` default yes, in 3.11 and backported to 3.10.5 — [RELEASE_NOTES-3.11](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/RELEASE_NOTES-3.11)

**Submission authentication**
- SASL has two backends. Docs warn that Cyrus SASL "contains a lot of code" and that Dovecot SASL is an alternative — [SASL_README](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/SASL_README.html)
- `smtpd_relay_restrictions` default is `permit_mynetworks, permit_sasl_authenticated, defer_unauth_destination` — [postconf.proto](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/postconf.proto)

### Inferences

Composed from the parameters cited above. **Not run on a live host; numbers are starting values, not sourced recommendations.**

DNS (mail host `mta.example.com`, sending from `example.com`, bounces at `bounce.example.com`):
```
mta.example.com.                    A    203.0.113.10
203.0.113.10 PTR                    mta.example.com.        ; set at the VPS provider, must match A
example.com.                        TXT  "v=spf1 ip4:203.0.113.10 -all"   ; merge into any existing SPF
bounce.example.com.                 MX   10 mta.example.com.
bounce.example.com.                 TXT  "v=spf1 ip4:203.0.113.10 -all"
s2026._domainkey.example.com.       TXT  "v=DKIM1; k=rsa; p=..."          ; RSA-2048, split at 255 chars
_dmarc.example.com.                 TXT  "v=DMARC1; p=none; rua=mailto:dmarc@example.com"
_smtp._tls.example.com.             TXT  "v=TLSRPTv1; rua=mailto:tlsrpt@example.com"  ; only if example.com receives mail
```
Publishing MTA-STS is only relevant to domains that receive mail. It does not help a send-only domain's outbound mail (RFC 8461 is defined around inbound MX); the bounce subdomain does not need it for this volume. Outbound enforcement of other domains' policies through a plugin is optional hardening; `may` works without it.

`/etc/postfix/main.cf` essentials:
```
compatibility_level = 3.10
myhostname = mta.example.com
mydomain = example.com
myorigin = $mydomain
mydestination =
inet_protocols = ipv4            # drop IPv6 unless PTR, SPF cover it
mynetworks = 127.0.0.0/8
local_transport = error:local delivery disabled

# Relay safety: explicit even though it matches the default
smtpd_relay_restrictions = permit_mynetworks, permit_sasl_authenticated, defer_unauth_destination

# Outbound TLS: explicit, default differs below 3.11
smtp_tls_security_level = may
smtp_tls_loglevel = 1
smtp_tls_note_starttls_offer = yes

# One-time codes
maximal_queue_lifetime = 30m
bounce_queue_lifetime = 30m
queue_run_delay = 60s
minimal_backoff_time = 60s
maximal_backoff_time = 300s

# DKIM: refuse rather than send unsigned if milter is down
smtpd_milters = local:opendkim/opendkim.sock
non_smtpd_milters = $smtpd_milters
milter_default_action = tempfail

# Bounce capture on subdomain only (see section 4)
relay_domains = bounce.example.com
relay_recipient_maps =           # any local part, because the return path is generated per message
transport_maps = hash:/etc/postfix/transport
```

`/etc/postfix/master.cf` additions:
```
# Submission for the backend only; bind to WireGuard address
10.8.0.1:submission inet n - y - - smtpd
  -o syslog_name=postfix/submission
  -o smtpd_tls_security_level=encrypt
  -o smtpd_tls_auth_only=yes
  -o smtpd_sasl_auth_enable=yes
  -o smtpd_relay_restrictions=permit_sasl_authenticated,reject
  -o smtpd_client_event_limit_exceptions=
  -o smtpd_client_message_rate_limit=600
  -o smtpd_client_auth_rate_limit=30
# Throttled transport
slow unix - - y - 2 smtp
# Bounce intake: posts raw DSN to the backend (script is yours to write)
bouncepipe unix - n n - 2 pipe
  flags=Rq user=mailbounce argv=/usr/local/bin/post-dsn ${recipient}
```

`main.cf` throttling for the transport, and `/etc/postfix/transport`:
```
slow_destination_rate_delay = 1s
slow_destination_concurrency_failed_cohort_limit = 10
```
```
bounce.example.com  bouncepipe:
gmail.com        slow:
outlook.com      slow:
hotmail.com      slow:
mail.ru          slow:
yandex.ru        slow:
```
Notes: per the docs, setting a recipient limit of 1 changes the rate delay's meaning from per-domain to per-recipient, so do not set it on this transport. At low volume, leave the `slow:` lines out entirely until a provider answers with a 421/450 rate-limit deferral, then add only the affected domain. Run `postmap /etc/postfix/transport` after edits.

Other points:
- Global `maximal_queue_lifetime` means codes and notifications share one lifetime. Simplest approach: use a short lifetime for everything, and let the Postgres queue re-enqueue important notifications after a final failure. Second `postmulti` instance is the alternative if a second lifetime is truly needed; not worth it at this volume.
- A 30-minute lifetime with 60–300s backoff allows several attempts and generally survives greylisting. A code that expires sooner can still arrive late, so the backend must enforce code TTL regardless.
- Cert: Let's Encrypt for `mta.example.com` via DNS-01 or HTTP-01; use `smtpd_tls_chain_files`. Add a reload hook.
- `smtpd_client_event_limit_exceptions` defaults to `$mynetworks`, so rate limits silently do not apply to trusted clients. That is why it is cleared on the submission service.
- Rate limits are per client IP (`anvil`), kept in memory, and so lost on restart.

### Gaps
- Whether Debian's or Ubuntu's postfix package pre-seeds `smtp_tls_security_level` in the generated `main.cf` was not verified; set it explicitly regardless.
- Whether Debian's Postfix build links libtlsrpt was not verified.
- `milter_default_action` default and the exact OpenDKIM socket path on Debian were written from prior knowledge, not re-read.
- No sourced per-provider rate numbers for Gmail, Outlook, Mail.ru or Yandex were found; providers do not publish them in the sources I read.
- `compatibility_level` should match the installed version's `postconf -d` value, which I did not check on a real host.

## 3. Java/Spring integration

### Takeaway
Use `JavaMailSender` over SMTP submission for this stack, with explicit timeouts and required STARTTLS. Defaults are unsafe: timeouts are infinite, and STARTTLS is off and not required. Connection pooling is unnecessary at this volume. The Postgres task is complete once the MTA returns 250; final delivery status arrives later through a separate channel.

### Cited Findings
- Spring Boot warns that some default timeouts are infinite and suggests setting `mail.smtp.connectiontimeout`, `mail.smtp.timeout`, `mail.smtp.writetimeout` via `spring.mail.properties`; its example values are 5000, 3000, 5000 ms. A `jndi-name` overrides other session settings — [Spring Boot email docs](https://docs.spring.io/spring-boot/reference/io/email.html)
- Angus Mail: `mail.smtp.connectiontimeout`, `timeout`, `writetimeout` all default to infinite. `writetimeout` adds one thread per connection. `starttls.enable` defaults false, and if the server lacks STARTTLS the connection continues in plain text unless `starttls.required=true`. `ssl.checkserveridentity` defaults true. `mail.smtp.from` sets the envelope return address. `quitwait` defaults true — [Angus Mail SMTP provider](https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html)
- Angus Mail latest release is 2.0.5 (2026-02-27) — [releases](https://github.com/eclipse-ee4j/angus-mail/releases)
- `JavaMailSenderImpl.doSend` holds `Transport` in a local variable, connects when needed, and reuses that transport across the messages of one call. There is no pooling between calls — [JavaMailSenderImpl](https://github.com/spring-projects/spring-framework/blob/HEAD/spring-context-support/src/main/java/org/springframework/mail/javamail/JavaMailSenderImpl.java)
- Simple Java Mail 9.3.9 (2026-10-26) offers batching, connection pools, SMTP clusters and DKIM signing through modules — [features](https://www.simplejavamail.org/features.html), [releases](https://github.com/bbottema/simple-java-mail/releases)
- Railway: SMTP is available only on Pro and above, disabled on Free, Trial and Hobby, and Railway recommends HTTPS-based services on all plans — [Railway docs](https://docs.railway.com/reference/outbound-networking)
- HTTP APIs exist for Postal, Mox and Hyvor — see section 1.

### Inferences

Config (not run):
```yaml
spring:
  mail:
    host: 10.8.0.1           # WireGuard/Tailscale address
    port: 587
    username: ${MAIL_USER}
    password: ${MAIL_PASSWORD}
    protocol: smtp
    default-encoding: UTF-8
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.starttls.required: true
      mail.smtp.ssl.checkserveridentity: true
      mail.smtp.ssl.protocols: TLSv1.3 TLSv1.2
      mail.smtp.connectiontimeout: 5000
      mail.smtp.timeout: 10000
      mail.smtp.writetimeout: 10000
```

- **SMTP vs HTTP.** SMTP keeps the MTA swappable, because Postfix, Postal, Mox and any commercial provider all accept submission. That also makes a provider fallback a config change. HTTP gives richer per-message responses but ties code to one vendor's API. Prefer SMTP unless Postal or Mox is chosen for its API.
- **Hosting constraint.** On Railway, SMTP availability depends on plan. Check the actual plan first. If on Hobby, a direct 587 connection will fail; a WireGuard/Tailscale tunnel from a Railway container may avoid that block, since it is not SMTP on the wire, but it likely requires userspace mode, which I did not verify.
- **Secure path.** Prefer a private overlay (WireGuard or Tailscale) with 587 bound only to the overlay address, while still requiring TLS and SASL. If a tunnel is impossible, expose 587 publicly, restricted to known egress IPs if available, with `smtpd_tls_security_level=encrypt`.
- **Pooling.** At up to tens of thousands a day, that is under 1 message/s on average. New connection per send with a few queue workers is fine. Add `Simple Java Mail` pool or batch `send(MimeMessage...)` only if TLS handshake cost becomes measurable.
- **Workers.** Bound concurrency (for example 2–4 workers). Separate fast queue for codes and slower queue for notifications, so a notification burst cannot delay a login code.
- **Idempotency.**
  - Delivery from the task queue to SMTP is at-least-once. If the worker crashes after 250 but before commit, the message is sent twice. SMTP has no idempotency key.
  - Use a unique `idempotency_key` on the outbox row, and generate `Message-ID` deterministically from the row ID so duplicates are at least traceable.
  - A duplicate code is harmless. For notifications, accept the rare duplicate.
- **Retries.**
  - Retry connection errors, timeouts and 4xx from the local MTA with backoff.
  - Do not retry 5xx from submission (config or policy problem; alert instead).
  - Before each attempt on a code, check `expires_at` and drop expired tasks.
  - Stop retrying once 250 has been received: MTA owns delivery from then on.
- **Multipart.** Use `MimeMessageHelper(message, true, "UTF-8")` with `setText(plain, html)`, set `Message-ID`, keep text part meaningful (codes readable in plain text). Set envelope sender per message for bounce correlation (section 4); `mail.smtp.from` is session-level, so for a per-message value use `SMTPMessage#setEnvelopeFrom`. This API detail comes from prior knowledge and should be tested.

### Gaps
- Spring Boot 4 property names were read from the current docs page, but the exact Boot version used by this project was not checked against them.
- Whether Railway allows WireGuard or Tailscale in a container, and in which mode, was not verified.
- No sourced benchmark for connection reuse vs per-send connections was found.

## 4. Bounce and complaint handling

### Takeaway
At low volume the necessary part is small: a per-message return-path on a bounce subdomain, a pipe that posts DSNs to the backend, a suppression table, and manual registration at provider postmaster tools. Automatic ARF/FBL parsing can wait. Never suppress a recipient for sender-side policy blocks; treat those as an incident.

### Cited Findings
- Postfix supports RFC 3464 DSN. By default it notifies the sender only on delay or failure — [DSN_README](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/DSN_README.html)
- VERP: Postfix can encode the recipient in the envelope sender (`owner-listname+user=domain@origin`), so a bounce reveals the recipient without parsing — [VERP_README](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/VERP_README.html)
- Postfix 3.11 changes bounce logging: `<old-queue-id>: sender non-delivery notification: <new-queue-id>` is now always logged and logged earlier, and failure to queue a notification is logged. Log parsers must allow for this — [RELEASE_NOTES-3.11](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/RELEASE_NOTES-3.11)
- Postal reference design: holds messages whose recipient is on the suppression list with a logged reason, adds a recipient after too many hard fails, and removes on success — [outgoing_message_processor.rb](https://github.com/postalserver/postal/blob/HEAD/app/lib/message_dequeuer/outgoing_message_processor.rb); automatic removal after 30 days by default — [yaml.yml](https://github.com/postalserver/postal/blob/HEAD/doc/config/yaml.yml)
- Mox manages per-account suppression from SMTP codes and DSNs — [features](https://www.xmox.nl/features/)
- Google gives all senders a spam-rate threshold under 0.3% in Postmaster Tools — [Google](https://support.google.com/mail/answer/81126?hl=en)
- Gmail gives aggregate complaint data rather than per-message reports — [Suped](https://suped.com/email-deliverability/answers/which-mailbox-providers-report-spam-complaints-back-to-esps) (secondary)
- Microsoft's complaint feed is JMRP, set up through SNDS — [Suped](https://www.suped.com/knowledge/email-deliverability/technical/which-major-email-providers-offer-feedback-loops-fbls) (secondary)
- Mail.ru offered an FBL requiring DKIM, domain verification and a report address on the same domain, but the source is an old post — [Habr/VK](https://habr.com/en/companies/vk/articles/147713)
- Yandex is listed under Validity's universal FBL; an old Q&A says its reports strip the recipient and keep the message ID — [Validity](https://www.validity.com/feedback-loops-all-about-being-in-the-loop/), [Habr Q&A](https://qna.habr.com/q/292712) (old; may be outdated)
- Author's thresholds: hard bounce under 2%, complaints under 0.1%; he also recommends registering at postmaster.mail.ru and postoffice.yandex.ru. He notes SMTP 250 means accepted, not inboxed — [Habr](https://habr.com/ru/articles/1061824/) (self-promotional, unverified)
- SubEtha SMTP is a library to receive SMTP in Java — [subethasmtp](https://github.com/davidmoten/subethasmtp)

### Inferences

**Design for Postfix.**
1. Return path `b+<outbox_id>.<hmac8>@bounce.example.com`, set per message. Simpler than Postfix's `XVERP`, because it identifies the message row and is tamper-evident. It also aligns with `example.com` under relaxed SPF, while DKIM covers `example.com` directly.
2. `bounce.example.com` is the only domain Postfix accepts on port 25 (`relay_domains` plus a transport to a `pipe`, as in the section 2 config). This means port 25 inbound is open, but only for that subdomain, and `defer_unauth_destination` refuses everything else.
3. Failures generated locally by Postfix also arrive at the same address, because the local bounce daemon mails the envelope sender. So one channel carries both synchronous failures (remote refused during our delivery) and asynchronous ones (remote accepted, then bounced). This is the main reason to prefer it over log parsing only.
4. The `pipe` command posts the raw DSN to a backend webhook with an HMAC header. Keep the script tiny, and return temp-fail on HTTP errors so Postfix retries.
5. Backend parses `message/delivery-status` (`Final-Recipient`, `Action`, `Status`, `Diagnostic-Code`). Jakarta/Angus `dsn` classes exist for this, from prior knowledge. Mark unknown formats as `unparsed` and keep the raw message for a while.

Log-only alternative (no port 25 inbound): parse `status=bounced/deferred/sent` from journald, joining by queue ID. Simpler firewall, but misses asynchronous bounces. Acceptable at very first launch.

SubEtha alternative: Postfix relays the bounce domain to a Java SMTP listener. This is one legitimate use of SubEtha, but it needs an inbound path to the backend, which is awkward on a PaaS. A signed HTTP POST is simpler.

**Suppression model (Postgres):**
```sql
CREATE TABLE email_suppression (
  email_normalized text PRIMARY KEY,          -- lower-cased, trimmed
  reason           text NOT NULL,             -- hard_bounce | complaint | manual | soft_bounce_limit
  source           text NOT NULL,             -- dsn | smtp | fbl | admin
  dsn_status       text,                      -- e.g. 5.1.1
  diagnostic       text,
  first_message_id bigint,
  created_at       timestamptz NOT NULL DEFAULT now(),
  expires_at       timestamptz                -- null = permanent
);

CREATE TABLE email_event (
  id           bigserial PRIMARY KEY,
  outbox_id    bigint NOT NULL,
  type         text NOT NULL,                 -- accepted | sent | deferred | bounced | complained
  rcpt_domain  text NOT NULL,
  mta_queue_id text,
  dsn_status   text,
  raw          text,
  occurred_at  timestamptz NOT NULL
);
CREATE INDEX ON email_event (rcpt_domain, occurred_at);
```

**Classification rules:**
- 5.1.1, 5.1.2, 5.1.10-type (no such user or domain): suppress at once.
- 4.x.x final expiry: count; suppress with `expires_at` after, say, 3 in 7 days.
- 5.7.x, or text naming a blocklist, reputation or policy: do **not** suppress the recipient. It is a sender problem and needs an alert. Mass suppression in this case would silently lock users out.
- Complaint: suppress notifications permanently. For login codes, think separately: a user who needs a code explicitly asked for it. Allow codes through a complaint entry, but not through a hard bounce entry.
- Check the table before enqueue and also before send. Surface suppressed status on the login screen ("this address cannot receive mail"), since silently dropping codes is a support problem.

**FBL at low volume:** register the domain and IP with Google Postmaster Tools, Microsoft SNDS/JMRP, Yandex Postoffice and postmaster.mail.ru, with reports going to a mailbox someone reads. Build ARF parsing (RFC 5965 `message/feedback-report`) only if the volume of reports justifies it. Login codes and important notifications should draw very few complaints.

### Gaps
- Current official FBL enrolment terms for Mail.ru and Yandex were not verified; the sources found are old or secondary. Official docs should be checked before planning around them.
- Microsoft SNDS/JMRP current status in 2026 is based on secondary sources only.
- Frequency of asynchronous bounces from major providers (how much log-only mode would miss) was not found.

## 5. Monitoring and alerting

### Takeaway
A minimal set is enough: Hsn723's `postfix_exporter` for queue depth and age, Prometheus/Alertmanager (or existing monitoring) on top, delivery rates per domain from the backend's own event table, `dnsbl_exporter` with your own resolver, and `parsedmarc` writing into the existing Postgres. Use mail-tester sparingly before launch and after changes.

### Cited Findings
- `kumina/postfix_exporter` is stale: last release 0.3.0 in 2021-10, last push 2024-02 — [repo](https://github.com/kumina/postfix_exporter)
- `Hsn723/postfix_exporter` is active: 0.21.2 (2026-10-05), Apache-2.0. Gives histograms of size and age of queued messages via the `showq` socket, and counts events by regex-parsing logs from systemd journal, Docker logs or a file. Default listen port is 9154 — [README](https://github.com/Hsn723/postfix_exporter/blob/HEAD/README.md), [releases](https://github.com/Hsn723/postfix_exporter/releases)
- Custom transport or service labels need to be mapped for the exporter to recognise them, which is relevant to custom transports such as `slow` — [README](https://github.com/Hsn723/postfix_exporter/blob/HEAD/README.md)
- `qshape` analyses queue congestion, bundled with Postfix under `auxiliary` — [QSHAPE_README](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/QSHAPE_README.html)
- Postfix `notify_classes` defaults to `resource, software` — [postconf.proto](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/postconf.proto)
- `parsedmarc` 11.2.1 (2026-11-01), Apache-2.0, maintained by one developer. It parses aggregate and failure DMARC reports (including RFC 9989/9990) and TLS-RPT, reads from IMAP, Microsoft Graph or Gmail API, and writes to Elasticsearch, OpenSearch, Splunk, **PostgreSQL**, Kafka, S3, Loki and webhooks — [README](https://github.com/domainaware/parsedmarc/blob/HEAD/README.md)
- `Luzilla/dnsbl_exporter` 0.12.0 (2026-04-17) checks hosts against DNSBLs for Prometheus — [repo](https://github.com/Luzilla/dnsbl_exporter), [pkg.go.dev](https://pkg.go.dev/github.com/luzilla/dnsbl_exporter)
- Spamhaus blocks queries via public or open resolvers and returns `127.255.255.x`, which must not be read as listed or as clean. Free DQS key is available to those eligible for public mirrors — [Spamhaus return codes](https://www.spamhaus.org/resource-hub/dnsbl/using-our-public-mirrors-check-your-return-codes-now/), [help for public mirror users](https://www.spamhaus.com/product/help-for-spamhaus-public-mirror-users/)
- mail-tester: 3 free tests per 24 hours, results visible 7 days on free tier, or 30 days with an account; paid plans for API — [FAQ](https://www.mail-tester.com/faq)
- Postal's `/metrics` and `/health` are built in — [health & metrics](https://github.com/postalserver/docs/blob/HEAD/content/3.features/health-metrics.md)

### Inferences

**Alerts worth having (thresholds are starting guesses):**

| Signal | Source | Alert |
|---|---|---|
| Active or deferred queue size | exporter | deferred > 50 for 10 min |
| Age of oldest message | exporter | > 10 min (codes are already useless) |
| Bounce rate per domain | backend `email_event` | > 5% over 1h with at least 20 sends |
| Deferral rate per large provider | backend or log counters | > 20% over 30 min |
| 5.7.x responses | backend | any burst, immediately |
| Time from enqueue to 250 at MTA | backend Micrometer timer | p95 > 30s |
| Send volume per hour | backend vs MTA counters | MTA count well above backend count means credential abuse |
| DNSBL listing | dnsbl_exporter | any |
| TLS cert expiry | blackbox or existing tool | < 14 days |
| Disk, Postfix and OpenDKIM process | node exporter, systemd | standard |
| End-to-end canary | scheduled mail to a controlled mailbox at Gmail, Yandex, Mail.ru | no arrival within 5 min |

- The **MTA vs backend count comparison** is the cheapest possible detector for leaked credentials.
- Per-domain rates are easier from the backend, since it knows the recipient per message and has a Postgres table already. Grafana can read Postgres directly.
- `parsedmarc` writing to Postgres avoids introducing Elasticsearch or OpenSearch, which would otherwise be the heaviest component in the stack. Run it on a schedule (cron or a one-shot container) reading one dedicated `dmarc@` mailbox on an existing provider. A separate read of TLS-RPT is only useful if the domain receives mail.
- At this size, a weekly `pflogsumm` mail and DMARC summary review may be all the "reporting" needed.
- Tools at pre-launch: mail-tester (limited free tries) plus a real test at each large provider, checking `Authentication-Results`.

### Gaps
- Exact metric names in `Hsn723/postfix_exporter`, and whether any per-destination label exists, were not confirmed.
- Spamhaus free-tier eligibility for a commercial sender checking its own IP was not read in full; [spamresource](https://www.spamresource.com/2021/11/howto-query-spamhaus-safely.html) gives practical guidance, but terms should be read directly.
- GlockApps or similar paid seed testers were not evaluated.

## 6. Could one write a custom MTA in Java?

### Takeaway
Possible, but not worth it. A JVM service still has to run on a static-IP VPS with PTR and port 25 and be operated, so nothing gets simpler, while queueing, MX fallback, TLS policy, per-destination throttling and bounce handling must be rebuilt. Java libraries in the question are mostly clients or receivers; only Apache James contains a real outbound delivery engine, and it is a full mail server.

### Cited Findings
- Apache James: 3.9.1 is listed as latest stable, tag dated 2026-09-25; main is at 3.10.0-SNAPSHOT; Apache-2.0 — [download](https://james.apache.org/download.cgi), [repo](https://github.com/apache/james-project), [pom](https://github.com/apache/james-project/blob/HEAD/pom.xml). The site's server page still headlines 3.9.0 and the download page offers 3.9.0 app zips next to 3.9.1 sources, so docs are inconsistent — [server page](https://james.apache.org/server/)
- James `RemoteDelivery` mailet delivers to remote SMTP servers, with `delayTime` retry schedule, `maxRetries`, `bounceProcessor` and optional `gateway` — [RemoteDelivery.java](https://github.com/apache/james-project/blob/HEAD/server/mailet/mailets/src/main/java/org/apache/james/transport/mailets/RemoteDelivery.java)
- SubEtha SMTP lets an application **receive** SMTP mail; it does not send. davidmoten fork 7.2.2 (2026-02-26) — [repo](https://github.com/davidmoten/subethasmtp)
- Simple Java Mail 9.3.9 composes, secures and delivers mail with DKIM, S/MIME, pools and clusters. It is an SMTP client library — [features](https://www.simplejavamail.org/features.html)
- dnsjava 3.7.1 (2026-11-01) is available for MX lookups — [releases](https://github.com/dnsjava/dnsjava/releases)
- What a real delivery agent handles, as seen in Postfix: per-destination concurrency and failure cohorts, rate delay, backoff queue, DSN generation, connection cache, DANE, MTA-STS plugin interface, TLSRPT — [postconf.proto](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/postconf.proto), [TLSRPT_README](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/TLSRPT_README.html)
- A practitioner on Habr built a custom engine with own DKIM signing, IP pools, suppression and DSN parsing, and says it is live but has no deliverability figures yet; he is selling the service — [Habr](https://habr.com/ru/articles/1061824/)
- Railway restricts SMTP by plan — [Railway](https://docs.railway.com/reference/outbound-networking)

### Inferences
- **Writing direct-to-MX in the backend does not work on the current hosting.** It needs a stable IP with PTR, clean reputation and outbound port 25. A PaaS container gives none of these reliably, so a VPS is needed anyway. Once there is a VPS, Postfix costs almost nothing to add.
- A naive implementation (MX lookup + Jakarta Mail to MX + DKIM through Simple Java Mail + retries in the Postgres queue) can be working in days, and that is the trap: the first version looks fine until greylisting, multi-MX failure, per-provider throttling, TLS edge cases, IPv6 PTR, DSN generation and malformed responses appear. Each is solved in Postfix.
- Writing an MTA also moves security exposure (SMTP parsing on port 25 for bounces) from a very widely audited daemon into own code.
- The one genuine advantage is having the queue in Postgres, which provides visibility and per-domain logic in one place. That can be had without a custom MTA: keep the outbox in Postgres, with Postfix behind it. Apply per-domain pacing in the backend if ever needed.
- If a JVM stack were mandatory, James `RemoteDelivery` is the starting point rather than a from-scratch sender, but James is a large system, so it would be heavier than Postfix for no benefit here.

### Gaps
- No public account was found of a small team running a custom Java direct-to-MX sender in production, successful or otherwise.
- James memory footprint and suitability as an outbound-only server were not tested.

## 7. Security hardening and failure modes

### Takeaway
Most severe realistic failure is a leaked submission credential turning the box into a spam source, which destroys the reputation of the IP and possibly the domain. Binding 587 to a private overlay removes most of this risk. Backups are small: config, DKIM private keys, runbook. The queue is transient and Postgres is the record of truth. IP change is a planned procedure with warm-up, not an ops detail.

### Cited Findings
- `smtpd_relay_restrictions` default permits only `mynetworks` and SASL, then `defer_unauth_destination`, separating relay control since Postfix 2.10 — [postconf.proto](https://github.com/vdukhovni/postfix/blob/HEAD/postfix/proto/postconf.proto)
- `smtpd_client_message_rate_limit`, `smtpd_client_auth_rate_limit`, `smtpd_client_recipient_rate_limit` all default to 0 (no limit); `anvil_rate_time_unit` is 60s and memory-only; clients in `$mynetworks` are exempt via `smtpd_client_event_limit_exceptions` — same source
- `smtpd_sender_login_maps` ties SASL logins to envelope sender only. Docs explicitly say enforcing From: header match needs an external milter — same source
- `smtpd_tls_auth_only` prevents AUTH on plaintext connections; default no — same source
- Postal lets a credential be set to hold all its messages, a ready-made kill switch — [outgoing_message_processor.rb](https://github.com/postalserver/postal/blob/HEAD/app/lib/message_dequeuer/outgoing_message_processor.rb)
- The hosting provider must not block outbound port 25 — [Postal prerequisites](https://docs.postalserver.io/getting-started/prerequisites)
- Google: increase volume gradually, reduce on bounces or deferrals; on shared IPs check blocklists and reputation — [Google](https://support.google.com/mail/answer/81126?hl=en)
- New IPs are viewed with suspicion by Gmail and Outlook per a 2026 opinion piece — [servers.expert](https://servers.expert/en/news/142); a startup reports a dedicated-IP block from a German provider for sending too little mail — [feedbagel summary](https://feedbagel.com/post/why-self-hosting-email-is-problematic-a-startups-experience-with-email-deliverab). Both are anecdotal.
- Warm-up ladder from one practitioner, 50/day → 500 after 3 healthy days → 5,000 after 14 → 50,000 after 30 — [Habr](https://habr.com/ru/articles/1061824/) (author's own unverified numbers)
- Maddy's recent DMARC bypass fixes, and OpenDKIM's July 2026 stack overflow fix, show even auxiliary components need patch tracking — [Maddy](https://github.com/foxcpp/maddy/releases/tag/v0.9.7), [OpenDKIM](https://github.com/trusteddomainproject/OpenDKIM)

### Inferences

**Open relay avoidance**
- Keep `mynetworks` at loopback only. Do not add the Docker bridge or overlay range to it: that is the classic way a relay opens by accident.
- Port 25 accepts only the bounce subdomain. Test from outside with a real relay attempt (`RCPT TO:<someone@gmail.com>` must be refused) after every config change, and automate that as a canary.
- Containers: beware of Docker's published ports bypassing `ufw` rules, and of NAT hiding client IPs so everything appears to be from a trusted network.

**Credential leak**
- Bind 587 to the overlay; firewall denies it elsewhere. A leaked password is then useless from outside.
- Dedicated credential per environment, stored in the platform's secret store, rotation runbook.
- `smtpd_sender_login_maps` plus `reject_sender_login_mismatch`, so the credential can only use specific senders; note that this does not check the From header.
- Cap per-client messages with `smtpd_client_message_rate_limit`, cleared exception list; pick a value just above expected peak.
- Backend-side limits per recipient and per IP on code requests, which stops email bombing via the login form. This abuse route needs no leaked credentials, and it is more likely than a leak.
- Kill switch documented in advance: stop the submission service, hold queue with `postsuper -h ALL`, delete with care. Commands are from prior knowledge and should be tested on staging first.
- Host access: SSH keys only, unattended security upgrades, minimal firewall (22 restricted, 25 in, overlay UDP in, 80 if ACME HTTP-01).

**Backups**
- Back up: `/etc/postfix`, `/etc/opendkim` (private keys, encrypted), overlay config, TLS renewal setup, firewall rules, and ideally everything as Ansible or a simple script. Rebuild from code is a better restore path than an image.
- Skip: mail queue. Lifetime is minutes, and Postgres retains everything for re-enqueue.
- Keep a reconciliation path: outbox rows without a final event after X hours can be re-enqueued or reported.

**IP change procedure**
1. Before: lower TTLs on A and SPF records a day ahead.
2. Provision new host from code. Check that outbound 25 is open. Check the new IP against blocklists, and ask for a different IP if listed.
3. Set PTR on the new IP and the forward A; verify they match.
4. Add new IP to SPF alongside the old one. DKIM can stay as is, because it is not IP-bound.
5. Register the IP in SNDS and provider tools.
6. Move a small share of traffic, notifications first, codes last. Keep the old host running to drain.
7. Warm up. Expect deferrals for days to weeks, so keep a commercial provider configured as an SMTP fallback for login codes. A failover to a commercial provider also needs its SPF and DKIM set up ahead of time.
8. After drain: remove old IP from SPF, and restore TTLs.

**Failure modes summary**

| Failure | Effect | Mitigation |
|---|---|---|
| IP blocklisted or throttled | Codes delayed or rejected | Fallback relay, alert on 5.7.x and deferred growth |
| VPS down | Sends fail fast with 4xx/timeouts | Outbox retries, failover to fallback relay |
| OpenDKIM down | Mail held or tempfailed if `milter_default_action=tempfail` | Alert; better than sending unsigned |
| Cert expired on 587 | Backend cannot submit | Monitor expiry; reload hook |
| Disk full | Postfix stops accepting | `queue_minfree`, alert |
| Provider blocks port 25 | No delivery at all | Test before committing; check terms |
| VPS provider abuse report | Host suspended | Keep rDNS and abuse contact current; respond quickly |
| Bad deploy loops sends | Bombards a recipient, reputational harm | Per-recipient limits in backend |

### Gaps
- Procedure timing for warm-up per provider has no authoritative source; Google says only "gradually".
- Practitioner incident reports specific to leaked submission credentials on small self-hosted setups were not found.
- Comparisons of specific VPS providers for port 25 and IP reputation were outside this research and not checked.
