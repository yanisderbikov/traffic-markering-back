# Where a self-hosted send-only mail server can run, and what it really costs (observed 2026-11-06)

Context: Russian product; Spring Boot, Postgres and gateway on Railway; recipients mostly on Yandex, Mail.ru, Gmail. The backend currently sends through NotiSend ([NotiSendEmailService.java](file:///Users/user/IdeaProjects/traffic-markering-back/src/main/java/ru/trafficmarkering/service/email/impl/NotiSendEmailService.java)). FX used: USD 81.67 ₽, EUR 91.60 ₽, CBR rates for 2026-11-04 via a secondary source — [Investfuture](https://investfuture.ru/articles/obzor-valyut-2026-11-04). All prices were observed on 2026-11-06 unless marked otherwise.

## Railway: does it block SMTP, and can a mail server run there?

### Takeaway
SMTP is off on Free, Trial and Hobby and on from Pro ($20/month), so Railway can be a client of a mail server on Pro. It cannot itself be a credible mail server: Railway sets no PTR, static IPs may be shared and cannot receive traffic.

### Cited Findings
- "SMTP is only available on the Pro plan and above"; Free, Trial and Hobby are told to use HTTPS email APIs — [Railway docs: Outbound Networking](https://docs.railway.com/reference/outbound-networking).
- A Railway reply dated 2026-09-14: "Outbound SMTP (ports 25, 465, 587, 2525) is disabled on Hobby plans to prevent abuse and is only available on Pro and above" — [Railway Station thread](https://station.railway.com/questions/please-unblock-outbound-smtp-ports-for-o-640ce726). Repeated on 2026-09-20 — [second thread](https://station.railway.com/questions/outbound-smtp-ports-465-587-fully-bloc-263c5391).
- After upgrading, the service must be redeployed. The docs also warn that email providers may block Railway's IPs — [Outbound Networking](https://docs.railway.com/reference/outbound-networking).
- Static outbound IPs are Pro only, IPv4, and tied to the region: "There is no guarantee that the IPv4 addresses assigned to your service are dedicated." They "cannot be used to receive inbound traffic." The HA version assigns three load-balanced IPs — [Railway docs: Static Outbound IPs](https://docs.railway.com/networking/static-outbound-ips).
- A Railway employee, 2025-11-01: "Railway does not configure any PTR records" — [Railway Station thread](https://station.railway.com/questions/ptr-r-dns-record-request-for-smtp-email-v-9e991720).
- Pricing: Hobby $5; Pro $20 per workspace including $20 of usage; about $10/GB-month RAM, about $20/vCPU-month CPU, egress $0.05/GB — [Railway pricing](https://railway.com/pricing).

### Inferences
- A mail server on Railway fails on three independent requirements: controllable PTR, a dedicated IP with its own reputation, and inbound for bounces. Treat it as ruled out.
- Pro ($20, about 1,630 ₽/month) is needed only if the backend talks SMTP to a VPS. Sending through an HTTPS API or an HTTP relay on the VPS works on any plan.
- Egress is negligible: 900,000 emails × 30 KB ≈ 27 GB ≈ $1.35/month (my estimate, assuming 30 KB per email).
- Railway is a foreign host, so its traffic to a Russian VPS crosses the border. Test the Railway-to-VPS path before committing; I found no measurement of it.

### Gaps
- No Railway page explicitly states that direct-to-MX port 25 works on Pro. The docs say "SMTP" and the staff reply lists the ports.
- Which plan this workspace is on was not checked (no Railway MCP, as instructed).
- Whether a non-standard submission port on one's own VPS passes on Hobby is unverified.

## Russian VPS and cloud providers: port 25, PTR, terms, price

### Takeaway
Only Selectel, Timeweb Cloud and Yandex Cloud publish a clear port 25 policy, and all three block by default. Selectel and Timeweb have an unblock path; Yandex's current docs give none. FirstVDS, RUVDS, REG.RU Cloud, VK Cloud, Cloud.ru and Beget could not be confirmed from a provider page, so each needs a support answer and a live test. Beget should be excluded for now, after the November 2026 wipe.

### Cited Findings

| Provider | Outbound 25 | PTR | Small VM with IPv4, ₽/month | Confidence |
|---|---|---|---|---|
| Selectel | Blocked (25, 465, 587); ticket, no guarantee | Self-service in panel | Cloud price not captured; VDS from 200 ₽, but VDS IPs are excluded from unblocking | High on policy (docs updated 2026-10-05) |
| Timeweb Cloud | Blocked by default; panel or support | VDS article only | 1,920 (MSK Ultra 15: 1 vCPU, 1 GB, 15 GB); IPv4 charge unclear | Medium: Timeweb's own pages conflict |
| Yandex Cloud | Blocked, except to Yandex Mail; no unblock path in current docs | Not confirmed | About 1,430 plus disk (my calculation) | High on block |
| VK Cloud | Not confirmed | Not confirmed | Not captured; public IP is billed | None |
| Cloud.ru Evolution | Not confirmed | Not confirmed | About 850–1,000 (my calculation from tariff) | Price high, policy none |
| Beget | Not confirmed | Not confirmed | VPS section "temporarily unavailable"; 330–1,050 from secondary sources | Exclude for now |
| RUVDS | Not confirmed | Self-service in panel | 349 (START HIT SSD: 1 GB, 20 GB, 1 IPv4) | Price and PTR high, port none |
| REG.RU Cloud | Blocked on shared hosting; Cloud/VPS not confirmed | Panel and API | Page did not load | Low |
| FirstVDS | Default not stated; blocked when a filter trips; whitelist exists | Self-service | 299 (catalogue) to from 1,829 (official Atlant page) | Medium |

**Selectel**
- Outgoing traffic to public addresses is blocked on TCP 25, 465 and 587. Requests are individual, approval is not guaranteed and may be refused without explanation. Unblocking can be reversed, for example after spam or a blacklisting — [Selectel docs](https://docs.selectel.ru/en/networks-services/blocked-ports).
- The ticket must state email types, examples, subjects, sending domain and weekly volume. Unblocking covers the account's public addresses except dedicated-server IPs in TAS-1, TAS-2, ALM-1, NBO-1; Cloud IPs in uz-1, uz-2, kz-1, ke-1; direct public IPs in all pools; and VDS server IPs — [Selectel docs](https://docs.selectel.ru/en/networks-services/blocked-ports).
- PTR is added in the panel under IP addresses, with no ticket — [Habr, Selectel blog, July 2025](https://habr.com/ru/articles/931018).
- The cheap VDS line starts from 200 ₽/month — [Trashbox, May 2026](https://trashbox.ru/link/2026-05-12-selectel-zapuskaet-dostupnye-vds-s-uproschyonnym-zapuskom).
- A commenter claimed in August 2025 that Selectel had reversed its plan to block — [Habr comments](https://habr.com/ru/articles/935606/comments/). The October 2026 docs override this.

**Timeweb Cloud**
- Blocked by default at server creation: 25, 3389, 389, 465, 587, 53413, 2525, 5060. Legitimate users can unblock in the panel, otherwise through support. Servers with "Сеть" marketplace software cannot be unblocked. The page also warns that spam can get a whole subnet blacklisted — [Timeweb docs](https://timeweb.cloud/docs/cloud-servers/limitations).
- Conflict: a blog post updated 2026-11-06 says ports are blocked "только на тех серверах, которые вызывают подозрения" — [Timeweb blog](https://timeweb.cloud/blog/zakrytye-porty-na-dashborde).
- Conflict: another post, also updated 2026-11-06, ties the restriction to accounts without a phone number. Automatic unblocking needs a phone, at least 4 GB RAM, an OS other than CentOS 9 and marketplace software; otherwise a ticket — [Timeweb blog](https://timeweb.cloud/blog/chto-takoe-gitlab-i-komu-on-neobkhodim).
- Prices: MSK Ultra 15 at 1,920 ₽, MSK Ultra 30 (2 GB) at 3,000 ₽, MSK Ultra 40 (2 vCPU, 2 GB) at 3,960 ₽, MSK Ultra 50 (2 vCPU, 4 GB) at 5,480 ₽. "Все IPv4-адреса — платные", amount not shown — [Timeweb pricing](https://timeweb.cloud/services/cloud-servers). A rating lists Timeweb VPS from 477 ₽ — [vc.ru](https://vc.ru/top-raiting/3048130-reyting-vps-s-serverami-v-rossii). The gap suggests different lines or locations; I did not reconcile it.
- A PTR guide exists for the older VDS panel and may differ for Cloud — [Timeweb community](https://timeweb.com/ru/community/articles/ptr-zapis-dlya-pochtovogo-servera-kak-dobavit-nastroit-i-proverit).

**Yandex Cloud**
- Traffic from public VPC addresses to TCP 25 on any internet server is automatically blocked. "Исключение — трафик, отправляемый на почтовые серверы Яндекс Почты." Postbox is recommended instead — [Yandex Cloud docs](https://yandex.cloud/ru/docs/vpc/concepts/address).
- Older sources describe unblocking by request — [LOR quoting older docs](https://www.linux.org.ru/forum/admin/16755875) — and an IP with open 25 after about 7–10 days — [Habr](https://habr.com/ru/post/679066). The current page, as fetched, gives no such route.
- Rates: vCPU at 20% 0.52 ₽/hour, RAM 0.33 ₽/GB-hour — [Compute pricing](https://yandex.cloud/ru/docs/compute/pricing). Public IP 0.26352 ₽/hour — [VPC pricing](https://yandex.cloud/ru/docs/vpc/pricing). My calculation for 2 vCPU at 20%, 2 GB: about 1,240 ₽ + 192 ₽ IP, plus disk.

**FirstVDS**
- "Любая массовая рассылка автоматически блокируется." When a filter triggers, port 25 is blocked, and filters can fire on non-spam, so apply for the whitelist in advance. The whitelist works only on KVM and explicitly covers automatic emails such as registration confirmations. Application needs proof of consent and samples (article updated 2024-04-26) — [FirstVDS](https://firstvds.ru/technology/bulk-mailing).
- A commenter reports a whole FirstVDS subnet listed in a regional government DNSBL — [Habr comments, August 2025](https://habr.com/ru/articles/935606/comments/).
- Prices: "от 1 829 ₽/мес" for VDS Атлант 3.1, extra IPv4 180 ₽/month — [FirstVDS](https://firstvds.ru/products/vds_vps_cloud). Прогрев (1 vCPU, 1 GB, 15 GB SSD) 299 ₽ in a catalogue updated 2026-09-06 — [VPSIndex](https://vpsindex.ru/tarify).

**RUVDS**
- PTR is set by the customer on the server's "Сеть" tab; the article does not cover port 25 — [RUVDS help](https://ruvds.com/ru/helpcenter/kak-nastroit-ptr-zapisi).
- From 2026-07-01: START 149, START SSD 219, START HIT 279, START HIT SSD 349 ₽ — [RUVDS](https://ruvds.com/ru/ruvds_price_2026_jul/). The Start line carries one IPv4 but was "временно недоступны" in the selected data centre — [RUVDS pricing](https://ruvds.com/ru-rub/pricing).

**REG.RU**
- Outbound 25 is blocked on all shared hosting servers — [REG.RU help](https://help.reg.ru/support/hosting/nastroyka-pochty-regru/problemyy/vozmozhnyye-problemy-pri-rabote-s-pochtoy-regru). For cloud servers, PTR is set through an API — [CloudVPS API](https://developers.cloudvps.reg.ru/ptr/index.html).
- A commenter reports Reg.ru did not block in 2025, and advises checking first — [Habr comments](https://habr.com/ru/articles/935606/comments/).

**Cloud.ru Evolution** (tariff version 261014, with 22% VAT, per hour)
- 1 vCPU/1 GB at 30% share 0.64233 ₽; 1 vCPU/2 GB at 30% 0.899262 ₽; 2 vCPU/4 GB at 30% 1.798524 ₽; public IP 0.2074 ₽; IP binding 0.2074 ₽; NVMe 0.01586 ₽/GB — [Cloud.ru tariffs](https://cdn.cloud.ru/docs/legal/tariffs/evolution/current-version/evolution-compute.pdf).
- The only mail tutorial configures Exim for local delivery and says nothing on port 25 or PTR — [Cloud.ru tutorial](https://cloud.ru/docs/tutorials-evolution/list/topics/free-tier-vm__mail-server).

**VK Cloud**
- Public IPs are billed and charged even when the VM is stopped; no rate, port 25 or SMTP information on the page — [VK Cloud tariffication](https://cloud.vk.ru/docs/en/computing/iaas/tariffication).

**Beget**
- On the night of 1–2 November 2026 attackers ran data-wiping malware; all servers were taken offline at 02:55 MSK. As of November 5, 60% of shared hosting and 40% of managed dedicated servers were restored, and the VPS section shows "Временно недоступно" — [Beget](https://beget.com/ru/vps).
- Attackers reached the control layer. VPS, S3 and Kubernetes data plus CDN and IP settings were wiped, and "бинарные бэкапы VPS потеряны полностью" — [Kod.ru, 2026-11-03](https://kod.ru/beget-poteryal-dannye-oblaka). Other outlets differ on details, so Beget's own updates remain the reference.
- Forum users report VPS prices rising from 370 to about 1,050 ₽ — [Searchengines.guru](https://searchengines.guru/ru/forum/1140828). A rating lists from 330 ₽ — [vc.ru](https://vc.ru/top-raiting/3048130-reyting-vps-s-serverami-v-rossii), and a catalogue lists 480 ₽ — [VPSIndex](https://vpsindex.ru/tarify).

**Market direction**
- RUVDS raised prices twice (from 2025-12-31 and 2026-07-01), citing VAT rising from 20% to 22% — [RUVDS](https://ruvds.com/ru/price_2026/).

### Inferences
- Shortlist to test: Selectel Cloud with a public (not direct) IP in a Russian pool, for its documented process and self-service PTR; FirstVDS KVM, because it explicitly whitelists registration emails; Timeweb Cloud; RUVDS as the cheap option if support confirms 25.
- Cheap Selectel VDS is a trap for this use: the advertised line sits on IPs the unblock policy excludes.
- Yandex Cloud can reach only Yandex Mail on 25, so it cannot be the whole solution. Postbox is the realistic Yandex option.
- The Beget incident shows that a provider-level failure can remove the VM, its backups and possibly IP settings. A mail server should be rebuildable from configuration, with DKIM keys stored off-provider.
- Unblocking is revocable everywhere it is documented, so a failover path is part of the design, not an extra.

### Gaps
- Port 25 policy from a provider page could not be confirmed for VK Cloud, Cloud.ru, Beget, RUVDS, REG.RU Cloud, and FirstVDS's default state. Ask support in writing and test with `nc`.
- Prices not captured from official pages: Selectel Cloud, VK Cloud, REG.RU Cloud, Timeweb IPv4 charge. Selectel's and REG.RU's pricing pages rendered without numbers.
- PTR on Yandex Cloud, VK Cloud and Cloud.ru: no provider instructions found.
- No provider publishes numeric sending limits in the pages read.

## Foreign VPS: port 25, PTR, reputation, and can a Russian company use them?

### Takeaway
Foreign options are mostly closed. Hetzner terminated Russian-address customers, AWS takes no new Russian customers, and DigitalOcean blocks SMTP with no documented exception and is subject to a Roskomnadzor ban. OVH VPS is the only one with port 25 documented as open by default, but Russian-company acceptance is unconfirmed. Using a nominee foreign entity or address to get around these limits is a sanctions and terms risk I would not treat as an option.

### Cited Findings

| Provider | Outbound 25 (primary source where available) | Russian company access |
|---|---|---|
| Hetzner | "we block ports 25 and 465 by default on all cloud servers"; request after one month and first paid invoice, case by case; 587 open — [Hetzner FAQ](https://docs.hetzner.com/cloud/servers/faq/) | Ended contracts for anyone with a Russian address, effective 2024-01-31 — [Habr](https://habr.com/en/news/779352) |
| OVH VPS | "Port 25 is not blocked by default on OVHcloud VPS servers"; Local Zones excepted — [OVH FAQ](https://docs.ovhcloud.com/en/guides/bare-metal-cloud/virtual-private-servers/vps-faq). Anti-spam can block at network level — [OVH antispam](https://docs.ovhcloud.com/en/guides/bare-metal-cloud/dedicated-servers/antispam-best-practices) | Not confirmed. One 2021 report of an account closed over Crimea ties, from a Krasnodar user — [HN](https://news.ycombinator.com/item?id=29680251) |
| OVH Public Cloud | Blocked by default — [OVH FAQ](https://docs.ovhcloud.com/en/guides/public-cloud/cross-functional/faq-pci) | As above |
| DigitalOcean | "SMTP ports 25, 465, and 587 are blocked on Droplets"; no exception process described — [DigitalOcean](https://docs.digitalocean.com/support/why-is-smtp-blocked/) | Roskomnadzor announced a ban on 2024-04-10 — [Digital Policy Alert](https://digitalpolicyalert.org/event/19120-announced-roskomnadzor-ban-of-digitalocean-llc-for-violating-local-operational-requirements) |
| Vultr | Blocked by default; ticket describing use, SPF/DKIM, volume; "not guaranteed" — [Vultr](https://docs.vultr.com/support/products/compute/why-is-smtp-blocked) | Not confirmed |
| Contabo | No official policy found; forum reports conflict — [LowEndTalk](https://lowendtalk.com/discussion/comment/4286238/), [Cloudron](https://forum.cloudron.io/topic/9422/outbound-smtp-issue). PTR in panel — [Contabo blog](https://contabo.com/blog/prevent-e-mails-marked-spam) | Terms reportedly reserve refusing Russian customers, with sanctions screening; third-party source only — [Mehbit](https://mehbit.com/en/services/cloud-hosting/contabo) |
| Linode/Akamai | 25, 465, 587 restricted by default on accounts since 2019-11-05; ticket; A record and rDNS expected (staff forum replies) — [Akamai community](https://www.akamai.com/community/questions/19082/i-just-created-my-first-linode-and-i-cant-send-emails-why-mailing-ports-25-465-a) | Not confirmed |
| AWS EC2 | 25 blocked to public addresses by default; removal on request — [AWS docs](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-resource-limits.html) | "not allowing new Russian customers since March 2022" — [The Record](https://therecord.media/russians-losing-access-microsoft-cloud-amazon) |
| GCP | Destination port 25 outside the VPC blocked; "some projects" exempt; 465/587 open — [Google docs](https://docs.cloud.google.com/compute/docs/tutorials/sending-mail) | Not confirmed |

Legal context, all from secondary sources, not legal advice:
- Foreign hosters outside the Roskomnadzor registry are listed as a threat under Decree 1667 of 2025-10-27, and blocking became theoretically possible from 2026-03-01. Experts quoted call it an exception — [Computerra](https://www.computerra.ru/328081/s-marta-2026-goda-v-rossii-mogut-nachat-blokirovat-resursy-inostrannyh-hosterov/). Only registry hosters may legally serve in Russia since 2024-02-01 — [Habr](https://habr.com/ru/news/790666/).
- 152-FZ: since 2025-07-01 most commentators read Art. 18(5) as banning primary collection of Russians' data in foreign databases, with separate advance notice to Roskomnadzor for cross-border transfer. Email and CRM integrations are named as check points — [b-152.ru](https://b-152.ru/hranenie-personalnyh-dannyh-za-granicej), [Business.ru](https://www.business.ru/article/5351-transgranichnaya-peredacha-personalnyh-dannyh-gg). A dissenting reading says transfer of data already collected in Russia is unaffected — [Comply](https://comply.ru/tpost/c43ezsout1-lokalizatsiya-i-transgranichnaya-peredac).
- The December 2023 EU package bars enterprise and design software, including cloud-based, for Russian-registered companies; this drove Microsoft's cutoff — [The Record](https://therecord.media/russians-losing-access-microsoft-cloud-amazon). I found nothing stating that plain VPS hosting falls under it.

### Inferences
- A foreign mail server adds an email-address transfer abroad to a stack that already has one. Postgres on Railway holding user emails looks like a separate 152-FZ question worth a lawyer's review, independent of this decision.
- Payment routes through intermediaries with virtual cards exist, but they do not change a provider's terms and an account can be closed without notice, which is unacceptable for a verification-code path.

### Gaps
- No current statement on Russian-company acceptance from OVH, Vultr, Linode, GCP, or Contabo itself. Current Vultr and OVH VPS prices were not verified; third-party blogs disagree. Hetzner lists shared plans from €5.99 (about 549 ₽, excl. VAT) — [Hetzner](https://www.hetzner.com/cloud/).
- IP reputation by foreign provider: anecdotes only, such as Hetzner deliverability complaints — [HN](https://news.ycombinator.com/item?id=47120901).

## Cross-border delivery: foreign IPs to Yandex/Mail.ru, Russian IPs to Gmail/Outlook, TSPU

### Takeaway
I found no evidence of TSPU blocking SMTP, of Yandex or Mail.ru penalising foreign IPs as such, or of Gmail or Outlook blocking Russian ranges as a class. Absence of reports is weak evidence: nobody appears to have measured this. Reputation is the documented factor, and each path should be tested from the actual IP.

### Cited Findings
- TSPU filters by IP, port and transport, so a port block is technically possible; no source describes one for SMTP — [pvsm.ru](https://www.pvsm.ru/vpn/460217).
- Since about 2025-06-09, connections to Cloudflare, Hetzner, DigitalOcean and OVH from Russia freeze after about 16 KB. Community reports; thresholds vary and the mechanism is unconfirmed — [NTC forum](https://ntc.party/t/%D0%B1%D0%BB%D0%BE%D0%BA%D0%B8%D1%80%D0%BE%D0%B2%D0%BA%D0%B0-cloudflare-ovh-hetzner-digitalocean-09062025-xxxxxxxx/17013), [Hosting Kitchen](https://hosting.kitchen/tendence-ru/sayty-na-cloudflare-stali-nedostupny-dlya-rossiyskih-polzovateley.html). Behaviour varied by city and direction for Hetzner — [Te-st.org, October 2026](https://te-st.org/2026/10/27/phpblock/).
- Mail.ru documents 421 errors as tied to IP reputation: spam-like volume, complaints, non-existent addresses — [Mail.ru help](https://help.mail.ru/notspam-support/errors/421try).
- A third-party blog holds that sender reputation, not IP geography, is the main factor — [LDM blog](https://check.live-direct-marketing.online/blog/limity-otpravki-mail-ru-i-yandex). This is opinion.
- Mail.ru rejects generic PTRs with "550 Sorry, we do not accept mail from hosts with dynamic IP or generic DNS PTR-records"; Yandex runs its own list `spamsource.mail.yandex.net` (vendor article, 2026-11-04, no data on geography) — [SSD Nodes](https://www.ssdnodes.com/learn/lang/ru/self-hosted-mail-to-yandex-and-mail-ru).
- Spamhaus has listed individual Russian hoster IPs (12 SBL entries for timeweb.ru from spring 2024, seen in a search snippet; the page refused direct fetch) — [Spamhaus](https://check.spamhaus.org/sbl/listings/timeweb.ru/). Its Q1 2022 report ranked Russian hosters among botnet C&C hosts — [Spamhaus report](https://content.spamhaus.org/1913347d-b14a-4fb6-8450-befa917f296a.pdf). These are per-IP and per-network signals, not a country policy.
- One Russian operator reports `sent` with no `deferred` to Gmail, Yandex and Mail.ru from an own Postfix after fixes; inbox placement was not checked — [Habr, autumn 2026](https://habr.com/ru/articles/1104308/).
- Microsoft is the most cited pain point, independent of country: repeat S3150 blocks after delisting — [Virtualmin forum](https://forum.virtualmin.com/t/outlook-blocking-emails-from-my-vps-mail-server-550-5-7-1-error-ip-reputation-deliverability-issue/135792), [HN](https://news.ycombinator.com/item?id=47120901).
- Whether Gmail gets blocked in Russia by end of 2026 is only a prediction-market question — [Manifold](https://manifold.markets/Egor/will-gmail-get-blocked-in-russia-by).

### Inferences
- A Russian IP to Yandex/Mail.ru is the shortest, least politically exposed path for the majority of recipients, and Gmail is the path to monitor.
- The reported freezes concern connections from Russia to foreign hosts. A foreign VPS opening SMTP to a Russian MX is a different direction and is unmeasured, so it cannot be assumed safe.
- Small transactional emails are near the 16 KB threshold only with large HTML or attachments, a further reason to keep them small if any foreign path is used.

### Gaps
- No measurements of SMTP over TSPU in either direction, and no official Roskomnadzor statement. Searches returned hoster blogs and VPN-focused material only.
- No Gmail or Microsoft statement about Russian ranges. No ASN-DROP or CSS data for specific Russian ASNs: Spamhaus pages returned 403.

## IP quality: checking before committing, replacement, redundancy

### Takeaway
Rent hourly, check the IP and its neighbours, test real connections, and only then submit the unblock ticket, DNS and warm-up. Redundancy is better bought as an API relay fallback than as a second idle server.

### Cited Findings
- ZEN combines SBL, CSS, XBL and PBL; a lookup reverses octets, for example `10.2.0.192.zen.spamhaus.org` — [Mailtester](https://mailtester.com/blog/sbl-xbl-pbl-zone-lookup-email-server-validation/).
- Trap: querying Spamhaus through a public resolver returns a refusal code that can be misread as "listed", so use a local recursive resolver — [DEV Community](https://dev.to/daanial/every-email-rejected-at-once-the-open-resolver-mistake-that-makes-spamhaus-answer-no-1j5d).
- SBL removals go through the network or hosting company, not the sender. CSS is automatic and also linked to domain reputation — [Spamhaus SBL](https://www.spamhaus.org/blocklists/spamhaus-blocklist/), [Spamhaus CSS](https://www.spamhaus.org/blocklists/combined-spam-sources/). PBL marks space that should not send directly; end users can exclude an IP if the network's policy allows — [Spamhaus PBL](https://www.spamhaus.org/blocklists/policy-blocklist/).
- Pre-flight test: `nc -vz -w 5 gmail-smtp-in.l.google.com 25`; a timeout points to a provider filter — [SSD Nodes](https://www.ssdnodes.com/learn/lang/ru/self-host-email-mailcow-vps). PTR, HELO and A must match; check `spamsource.mail.yandex.net`; if listed, ask for another IP before launch — [SSD Nodes](https://www.ssdnodes.com/learn/lang/ru/self-hosted-mail-to-yandex-and-mail-ru).
- Neighbourhood damage is real. Timeweb warns that spam can blacklist a subnet — [Timeweb docs](https://timeweb.cloud/docs/cloud-servers/limitations). An operator moved mail VMs to another subnet or provider when a data centre could not delist — [Habr comments](https://habr.com/ru/articles/935606/comments/).
- Recovery can be slow: one operator took about six months, mostly waiting, to clean a reputation-damaged IP — [HN](https://news.ycombinator.com/item?id=47120901).
- Yandex's Почтовый офис was closed in 2020, so there is no Yandex sender dashboard — [Emailtools](https://emailtools.ru/blog/postmaster-yandeks-zakryt). SSD Nodes says closed "as of October 2026"; the sources agree on closed and differ on date. Postmaster Mail.ru remains, keyed to the DKIM domain — [SSD Nodes](https://www.ssdnodes.com/learn/lang/ru/self-hosted-mail-to-yandex-and-mail-ru).
- Microsoft SNDS shows what Outlook sees for an authenticated IP owner. This is from third-party guidance in a search summary, and the exact page was not re-verified — [likely Generator Labs](https://generatorlabs.com/blog/reading-a-spamhaus-listing).

### Inferences
Suggested check sequence (my synthesis):
1. Create an hourly VM and note the IP, /24 and ASN.
2. Check ZEN from a local resolver, plus Yandex's list, for the IP and a sample of the /24.
3. Look at the Spamhaus SBL page for the provider and the Timeweb-style subnet warnings.
4. Test TCP 25 to Gmail, `mx.yandex.net` and `mxs.mail.ru` from that IP. If blocked, ask support about the policy before anything else.
5. Set PTR, send to seed mailboxes on all three providers and read headers plus folder placement.
6. If anything is dirty, destroy the VM and request another IP. Keep a good IP reserved, and note that some providers bill inactive IPs more, as [Yandex does](https://yandex.cloud/ru/docs/vpc/pricing).

Redundancy:
- A cold second server has no reputation and, once suddenly used, looks like a new sender. A warm one needs 5–20% of live traffic continuously.
- At 300/day, one server plus an API fallback (for example Postbox) is cheaper and better.
- At 3,000/day and above, keep two servers at two different providers and ASNs if self-hosting, with the fallback API retained. Rate-limit per recipient domain during warm-up.

### Gaps
- No source gives a method for scoring a /24; the check above is my reasoning.
- No Russian provider documents an IP replacement policy or a fee in the pages read.

## Total cost of ownership at 300, 3,000 and 30,000 emails per day

### Takeaway
All figures below are my estimates, not sourced data. Self-hosting costs roughly 13,500, 33,000 and 93,000 ₽/month against about 560, under 5,900 and under 55,000 ₽ for Postbox volume charges. Engineer time, not VMs, is about 85–95% of the cost, so own delivery only makes sense for control or provider-risk reasons, not to save money at these volumes.

### Cited Findings
Inputs that are sourced:
- Yandex Postbox: first 2,000 emails/month free; 2,001–10,000 at 80.32 ₽ per 1,000; 10,001–50,000 at 70.15 ₽; 50,001–65,500 at 59.98 ₽; 65,500 emails = 4,378.27 ₽ incl. VAT; all accepted emails are billed — [Postbox pricing, updated 2026-09-15](https://yandex.cloud/ru/docs/postbox/pricing).
- NotiSend: only an aggregator figure found, 2,900 / 8,900 / 22,900 ₽/month, not tied to volume, and unverified — [Toolfox](https://toolfox.ru/services/s/notisend). Treat as unknown and take actual figures from the invoice.
- Salary anchors: Habr Career median for web developers 152,000 ₽ in 2026, reported via Skillbox — [Skillbox](https://skillbox.ru/media/code/skolko-zarabatyvayut-web-razrabotchiki/); a weak blog estimate for backend average of 220–270k ₽, vaguely sourced, and the exact blog was not re-verified — [likely CheckROI](https://checkroi.ru/blog/zarplata-backend-razrabotchika/).
- VM prices come from the provider section above.

### Inferences
**Assumptions (estimates):** loaded engineer rate 2,500 ₽/hour (300,000 ₽ gross monthly × 1.3 employer cost ÷ 160 h, rounded); setup amortised over 24 months; 30 days/month; lost-business cost excluded from the totals.

| Monthly estimate | 300/day (9,000/mo) | 3,000/day (90,000/mo) | 30,000/day (900,000/mo) |
|---|---|---|---|
| Servers | 1 small VM | 2 VMs at 2 providers | 2–3 VMs, 2–4 IPs, 2 vCPU/4 GB |
| VMs, IPs, backups, monitoring | 1,000 ₽ | 4,000 ₽ | 13,000 ₽ |
| Setup hours (one-off) | 24 h (60,000 ₽) | 40 h (100,000 ₽) | 100 h (250,000 ₽) |
| Setup, amortised | 2,500 ₽ | 4,167 ₽ | 10,417 ₽ |
| Routine hours/month | 3 h (7,500 ₽) | 8 h (20,000 ₽) | 24 h (60,000 ₽) |
| Incidents/year × hours | 2 × 6 h (2,500 ₽/mo) | 3 × 8 h (5,000 ₽/mo) | 4 × 12 h (10,000 ₽/mo) |
| **Total, ₽/month** | **≈ 13,500** | **≈ 33,200** | **≈ 93,400** |
| At 1,500 ₽/hour | ≈ 8,500 | ≈ 21,500 | ≈ 61,300 |
| At 4,000 ₽/hour | ≈ 21,000 | ≈ 50,700 | ≈ 141,700 |
| Postbox volume charge | ≈ 562 ₽ | ≤ 5,848 ₽ | ≤ 54,434 ₽ |

- The Postbox figures above 65,500 emails are upper bounds that assume higher tiers do not exceed 59.98 ₽ per 1,000; those tiers were not on the page.
- What the hours cover: setup is Postfix, DKIM, TLS renewal, SPF/DMARC, PTR, unblock ticket, app integration, alerts and seed tests. At 3,000/day add failover and a 4–6 week warm-up. At 30,000/day add per-domain rate shaping, bounce handling, suppression list and complaint processing.
- Routine work is updates, certificate and blacklist checks, DMARC reports, seed tests and support tickets.

**Incident cost** (estimate, excluded from totals): lost codes × value of a code.
- Assume all mail is codes, sent evenly, with an 8-hour outage: 100, 1,000 and 10,000 lost codes.
- At an illustrative 100 ₽ per lost code that is 10,000, 100,000 and 1,000,000 ₽ per incident. The value is a placeholder, so replace it with the product's actual number.
- Detection time drives this figure more than any other input, as the 17-day case below shows. A warm API fallback shortens outage from hours to minutes.

**How to read it**
- If an existing engineer absorbs the work, cash cost is only the VM line, but the hours still displace product work and create an on-call duty with no one assigned.
- Self-hosting does not remove the API cost if a fallback is kept; add its minimum and low-volume charges.
- The cash crossover with Postbox at 2,500 ₽/hour is above 900,000 emails/month, under these assumptions.

### Gaps
- No published Russian dataset for hours spent running a transactional mail server; the hours are judgement informed by the reports below.
- Real NotiSend cost for this account was not available. Postbox rates above 65,500/month were not visible, and its deliverability was not assessed here.

## Real experience reports: time spent and failures

### Takeaway
Reports split sharply. Long-running small servers report 1–4 hours a year, while production systems fail in quiet ways that matter more than the hours: expired certificates, swallowed errors, sudden reputation blocks. There is little Russian evidence at transactional scale; most reports are personal servers at low volume, and they probably understate what a verification-code path needs.

### Cited Findings
**Best-matching Russian case** — ["Письмо отправлено". Как у нас дважды за месяц молча ломалась почта, Habr, autumn 2026](https://habr.com/ru/articles/1104308/):
- Own Postfix under Plesk, behind RabbitMQ.
- Incident 1: the SMTP host pointed at a bare domain migrated to a web server; about 17 days of failures, 110 emails failed, including all registration codes in the period.
- Incident 2: a Let's Encrypt certificate expired on October 5 after renewals had failed daily for a month, about 18 hours of silent failure.
- Contributing causes: swallowed exceptions, an unread dead-letter queue, 1 s and 4 s retries, API returning 200 on enqueue, Loki down during the first incident.

**Low-maintenance reports**
- About one to two hours a year since 2004; over 20 years with rare problems; 25 years without issues for themselves and business customers — [HN, about March 2026](https://news.ycombinator.com/item?id=47120901).
- Delivery problems "once every 3-4 years" over about 15 years, usually fixed by delisting — [HN](https://news.ycombinator.com/item?id=49328911).
- About 8 years of automated notifications without delivery problems, and over a year without lost emails — [Habr comments, August 2025](https://habr.com/ru/articles/935606/comments/).
- "10–15 минут в месяц", cited from an external source, in a promotional hoster article — [Habr, RUVDS blog, 2023](https://habr.com/ru/companies/ruvds/articles/729742/).

**Failure reports**
- A private server ended up on spam lists: Gmail refused, Mail.ru bounced, Yandex did not recognise the sender — [Habr comments, August 2025](https://habr.com/ru/articles/935606/comments/).
- SPF, DKIM, DMARC and a clean IP, still spam, so moved to SES or a hosted provider — [HN](https://news.ycombinator.com/item?id=49328911).
- One commenter calls the maintenance stressful because it tends to hit at critical moments. This came from a search summary across HN threads, and the exact thread was not re-verified — [HN, likely thread](https://news.ycombinator.com/item?id=26727289).
- About six months to clean an IP — [HN](https://news.ycombinator.com/item?id=47120901).
- A hybrid is common: own MX, third-party relay for outbound, with no reported delivery problems — [HN](https://news.ycombinator.com/item?id=49328911).
- Scale example: a 500M/month publisher checks IP reputation, rDNS, DKIM/DMARC and feedback loops; this is a specialist team, not a side task — [HN](https://news.ycombinator.com/item?id=49328911).
- The RIPE Labs article that started the thread has methodology and bias complaints from commenters, so its "decline" claim should not be relied on — [HN](https://news.ycombinator.com/item?id=49328911).

### Inferences
- Survivorship bias runs through the positive reports: long-time operators with old domains and aged IPs, low volume, and tolerance for a late email.
- A code that arrives an hour late is a failed login, so transactional operators need alerting on delivery rather than on process health.
- The Habr case's failures were in integration and monitoring rather than deliverability, so a minimum viable setup must include: end-to-end probe sending to seed mailboxes, certificate expiry alert, queue depth and deferred-rate alerts by recipient domain, longer retries, and a dead-letter reader.

### Gaps
- No detailed Russian report found of running 3,000–30,000 transactional emails/day on own infrastructure, with hours and costs.
- Comments were read through a summarising fetch, so numbers should be checked against the threads before being quoted verbatim.
