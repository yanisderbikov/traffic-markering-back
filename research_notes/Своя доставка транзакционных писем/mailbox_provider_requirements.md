# Mailbox provider requirements for a self-operated transactional sender (checked 2026-11-06)

Context: getoffer.now, a Russian-market product. It sends login codes and important notifications only, with no marketing, mainly to Yandex, Mail.ru and Gmail users.

## Do Gmail, Yahoo and Microsoft require List-Unsubscribe / one-click for transactional mail, who is exempt, at what volume, and what do Yandex and Mail.ru say?

### Takeaway
Gmail and Yahoo explicitly exempt transactional mail from one-click unsubscribe. Microsoft, Apple, Yandex and Mail.ru publish no explicit transactional carve-out, but their unsubscribe rules are written about mailings or bulk mail, so the exemption there is an inference. The 5,000/day figure is a Gmail and Microsoft threshold; Yahoo refuses to name a number.

### Cited Findings
**Gmail**
- Google's FAQ states: "One-click unsubscribe is required only for marketing and promotional messages." It excludes transactional messages and gives password resets and reservation confirmations as examples. — [Google sender FAQ](https://support.google.com/a/answer/14229414?hl=en)
- The guidelines place one-click unsubscribe and a visible body link under the over-5,000/day tier, and apply them to marketing and subscribed messages. The page itself does not use the word "transactional". Its only related example is that sales receipts should not carry promotions. — [Google sender guidelines](https://support.google.com/a/answer/81126?hl=en)
- A bulk sender is one sending close to 5,000 or more messages to personal Gmail accounts within 24 hours. — [Google sender FAQ](https://support.google.com/a/answer/14229414?hl=en)
- Volume is counted per primary domain. Google's example is 2,500 messages from a domain plus 2,500 from a subdomain, which together make the sender bulk. — [Google sender FAQ](https://support.google.com/a/answer/14229414?hl=en)
- Bulk status is permanent once reached: it has no expiry and later changes in sending practice do not remove it. — [Google sender FAQ](https://support.google.com/a/answer/14229414?hl=en)
- Requirements took effect February 2024, and Google says enforcement on non-compliant traffic has been ramping up since November 2025. — [Google sender FAQ](https://support.google.com/a/answer/14229414?hl=en)

**Yahoo**
- Yahoo's FAQ says the unsubscribe requirement does not apply to transactional messages, citing order confirmations and password resets, and that one-click is required only for promotional/marketing mail. — [Yahoo Sender Hub FAQ](https://senders.yahooinc.com/faqs/)
- Yahoo defines a bulk sender only as one sending a significant volume and says it will not specify a threshold. — [Yahoo Sender Hub FAQ](https://senders.yahooinc.com/faqs/)
- For marketing and subscribed mail from bulk senders: a working List-Unsubscribe header with one-click support, RFC 8058 POST highly recommended, mailto acceptable, a visible body link, and unsubscribes honored within 2 days. — [Yahoo best practices](https://senders.yahooinc.com/best-practices/)

**Microsoft (Outlook.com, Hotmail, Live)**
- The rules apply to domains sending over 5,000 emails per day and require SPF, DKIM and DMARC (at least p=none, aligned with SPF or DKIM). — [Microsoft Defender blog](https://techcommunity.microsoft.com/blog/microsoftdefenderforoffice365blog/strengthening-email-ecosystem-outlook%E2%80%99s-new-requirements-for-high%E2%80%90volume-senders/4399730)
- From May 5, 2025, non-compliant high-volume mail goes to Junk. Rejection with `550; 5.7.515` is promised for a future date still to be announced in the post body (version dated April 30, 2025). — [Microsoft Defender blog](https://techcommunity.microsoft.com/blog/microsoftdefenderforoffice365blog/strengthening-email-ecosystem-outlook%E2%80%99s-new-requirements-for-high%E2%80%90volume-senders/4399730)
- The same post lists an easy, visible opt-out as a practice, especially for marketing or bulk mail. It does not address transactional mail. Safe Sender entries do not bypass enforcement. — [Microsoft Defender blog](https://techcommunity.microsoft.com/blog/microsoftdefenderforoffice365blog/strengthening-email-ecosystem-outlook%E2%80%99s-new-requirements-for-high%E2%80%90volume-senders/4399730)

**Apple iCloud**
- Apple advises bulk senders to offer an unsubscribe link and honor requests quickly. The retrieved text gives no transactional carve-out and no numeric volume threshold. — [Apple postmaster information](https://support.apple.com/en-us/102322)

**Yandex**
- Yandex's "honest mailing" requirements mark as mandatory: recipient-confirmed addresses, unsubscribe instructions needing no login, unsubscribing possible within 10 minutes, an RFC-format `list-unsubscribe` header, and bulk/automated headers such as `Precedence: bulk`. Yandex reserves the right to send non-compliant mailings to spam or refuse them. — [Yandex honest mailing requirements](https://yandex.ru/support/yandex-360/customers/mail/ru/web/letter/create/send-many-letters)
- That page does not mention transactional or service mail at all. — [Yandex honest mailing requirements](https://yandex.ru/support/yandex-360/customers/mail/ru/web/letter/create/send-many-letters)
- A 2016 Yandex blog post, seen only as a search summary, said an "Unsubscribe" button appears on all mailings even without an author-provided mechanism, and that frequent use on mail lacking the function can move the sender to spam for all users. Dated. — [Yandex Mail blog, 2016](https://yandex.ru/blog/mail/20184)

**Mail.ru / VK Mail**
- Bulk mail must carry `Precedence: bulk`, a prominent one-click unsubscribe link is recommended, and unsubscribing must not require logging in. The technical page does not mention the List-Unsubscribe header or transactional mail. — [Mail.ru technical and administrative requirements](https://help.mail.ru/developers/mailing_rules/technical/)
- The general rules distinguish only one relevant case: do not send bulk or advertising mail from the same IP and mailbox as ordinary correspondence or important technical or financial messages. — [Mail.ru general rules](https://help.mail.ru/developers/mailing_rules/general/)
- List-Unsubscribe is a stated requirement for the Trusted Sender program. — [Mail.ru Trusted Sender](https://help.mail.ru/developers/mailing_rules/verified/)

**RFC 8058, if one-click is ever used**
- It requires one HTTPS URI in List-Unsubscribe, one List-Unsubscribe-Post header, a valid DKIM signature covering both headers, and no HTTPS redirect on the POST. — [RFC 8058](https://www.rfc-editor.org/rfc/rfc8058.txt)

### Inferences
- Login codes and security alerts can omit an unsubscribe link and header at Gmail and Yahoo on their published wording. At Yandex and Mail.ru, omission rests on reading the rules as applying to mailings, not on an explicit exemption.
- The exemption follows message type, not sender intent. A digest, "new offers in your cabinet" notice or re-engagement email could count as subscribed or promotional mail. Such messages should get unsubscribe handling, or be dropped from this stream.
- Transactional mail should not carry `Precedence: bulk`, since both Russian providers define that header as marking a bulk mailing. This is my reading; neither provider says so directly.
- A subdomain cannot be used to stay below Gmail's 5,000 threshold. A sender at hundreds or a few thousand per day should still build to the bulk tier, because one spike makes the status permanent.

### Gaps
- Source handling: pages were read through a summarizing fetch tool, and this file uses paraphrase with one short verbatim quote. Check exact wording at the linked page before quoting it in a final document.
- No official Yandex or Mail.ru statement was found that exempts transactional mail, and none that applies mailing rules to it.
- Could not establish whether Microsoft has since set a rejection date. The post body has no update after April 2025, though comments on the page run into 2026.

## What is the technical minimum: SPF, DKIM, DMARC, PTR/FCrDNS, TLS, ARC, BIMI, RFC compliance, spam-rate limits?

### Takeaway
A new sender should treat the following as the practical floor regardless of volume: SPF and DKIM (2048-bit) both passing, DMARC published and aligned on the From domain, matching forward and reverse DNS on a static IP, TLS, and RFC 5321/5322 compliance. ARC and BIMI are not requirements for this sender.

### Cited Findings
| Item | Gmail | Yahoo | Microsoft | iCloud | Yandex | Mail.ru |
|---|---|---|---|---|---|---|
| SPF/DKIM | SPF or DKIM for all; both above 5,000/day | SPF or DKIM for all; both for bulk | Both above 5,000/day | Both advised | Both mandatory for mailings | Must be set up and pass |
| DMARC | Over 5,000/day, p=none allowed, aligned | Bulk: at least p=none, must pass, relaxed alignment acceptable | Over 5,000/day, at least p=none, aligned | Required for bulk senders; policy honored | Recommended, not mandatory | Must be set up |
| PTR | Forward and reverse required, must match | Valid forward and reverse; non-generic advised | Not in retrieved text | Publish reverse DNS | Static IP with correct reverse DNS | Meaningful, not auto-generated PTR |
| TLS | Required | Not mentioned | Not in retrieved text | Not mentioned | Not mentioned | Not mentioned |
| RFC | 5322 | 5321 and 5322 | Not in retrieved text | Not in retrieved text | 5322 and MIME; valid Date and Message-ID | General technical standards |

Table sources: [Google guidelines](https://support.google.com/a/answer/81126?hl=en); [Yahoo best practices](https://senders.yahooinc.com/best-practices/); [Microsoft blog](https://techcommunity.microsoft.com/blog/microsoftdefenderforoffice365blog/strengthening-email-ecosystem-outlook%E2%80%99s-new-requirements-for-high%E2%80%90volume-senders/4399730); [Apple](https://support.apple.com/en-us/102322); [Yandex](https://yandex.ru/support/yandex-360/customers/mail/ru/web/letter/create/send-many-letters); [Mail.ru technical](https://help.mail.ru/developers/mailing_rules/technical/); [Mail.ru info](https://help.mail.ru/developers/mailing_rules/info/).

- DKIM key length: Google recommends 2048-bit, with 1024-bit as the fallback where the DNS host cannot support longer keys. — [Google Workspace DKIM setup](https://knowledge.workspace.google.com/admin/security/set-up-dkim?hl=en)
- Yahoo gives a 1024-bit minimum. — [Yahoo best practices](https://senders.yahooinc.com/best-practices/)
- Practitioner note: RSA-2048 is universally accepted, some verifiers return `dkim=neutral` for Ed25519 so it fits only as a second selector, and a 2048-bit key exceeds 255 characters and must be split into several TXT strings. — [Habr, ShyDamn](https://habr.com/ru/articles/1061824/)
- Alignment at Yandex: the From domain should match the domain in SPF or DKIM, and a mismatch can produce a yellow lock warning. — [Yandex security technologies](https://yandex.ru/support/mail/web/security/security-technologies.html)
- Gmail spam rate: stay below 0.1% and never reach 0.3%; rates are computed daily, and senders above 0.3% are ineligible for mitigation. — [Google sender FAQ](https://support.google.com/a/answer/14229414?hl=en)
- Yahoo spam rate: the best-practices page says below 0.3%, measured on mail delivered to the inbox, while the FAQ says it does not publish a specific complaint threshold. The two pages conflict. — [Yahoo best practices](https://senders.yahooinc.com/best-practices/); [Yahoo FAQ](https://senders.yahooinc.com/faqs/)
- Mail.ru complaint ceilings per month: up to 10,000 messages 1.1%; up to 500,000 1%; up to 10M 0.8%; up to 50M 0.5%; above 50M 0.3%. — [Mail.ru technical requirements](https://help.mail.ru/developers/mailing_rules/technical/)
- Gmail error codes for failures: `421 4.7.26` / `550 5.7.26` (unauthenticated), `451 4.7.23` / `550 5.7.25` (PTR), `421 4.7.29` / `550 5.7.29` (no TLS), `421 4.7.28` (unusual rate, rate limited). — [Gmail SMTP errors](https://knowledge.workspace.google.com/admin/support/troubleshooting/gmail-smtp-errors-and-codes?hl=en)
- BIMI at Gmail needs DMARC p=quarantine or reject with pct=100 and a VMC or CMC certificate; p=none is unsupported. — [Google BIMI setup](https://knowledge.workspace.google.com/admin/security/set-up-bimi?hl=en)
- Mail.ru offers its own sender avatar through Postmaster. — [Mail.ru Postmaster](https://postmaster.mail.ru/)
- Yandex's documentation shows p=none as a starting DMARC policy (search snippet; page not read in full). — [Yandex KIT DKIM/SPF/DMARC](https://yandex.ru/support/kit/ru/settings/dkim)
- Yandex also requires domain-owner WHOIS data to be current and public. Mail.ru requires WHOIS contacts for the IP to be current and reachable. — [Yandex](https://yandex.ru/support/yandex-360/customers/mail/ru/web/letter/create/send-many-letters); [Mail.ru technical](https://help.mail.ru/developers/mailing_rules/technical/)
- Mail.ru bars direct connections from dynamic or home IPs, open relays, and hardcoding its MX records in sender configuration. — [Mail.ru technical](https://help.mail.ru/developers/mailing_rules/technical/)

### Inferences
- DKIM with the From domain as `d=` is the alignment to rely on, because Mail.ru ties reputation to the DKIM domain and DKIM also survives forwarding. Starting at p=none with aggregate reports and moving to quarantine once reports are clean is a reasonable path.
- BIMI is not worth the cost for login mail. It needs a certificate and enforcement, and it does not affect placement in any source read.
- Public WHOIS may be hard to satisfy on a redacted gTLD such as .now. Check what the registrar can publish, since Yandex marks this item mandatory for mailings.

### Gaps
- ARC: the retrieved Google guidelines text did not mention it, and I found no provider requiring ARC from an originating sender. My understanding is that ARC matters for forwarders and intermediaries, but I did not verify this from a primary source.
- RFC 7489 (DMARC) was not read this session.
- No published complaint rate was found for Microsoft, Apple or Yandex.

## What are the specific rules and tools of Russian providers, and how do they treat new IPs, foreign IPs and Russian hosting ranges?

### Takeaway
Both Russian providers document rules for mailings and provide free postmaster tools, and both should be connected before the first send. Their treatment of new IPs is documented only qualitatively, and I found no reliable source on foreign versus Russian IP ranges.

### Cited Findings
**Mail.ru Postmaster**
- It shows reads, unread deletions, spam marks, sent/delivered/spam-folder/blocked counts, problem alerts and A/B data, offers FBL reports and sender avatars, and requires adding the domain and accepting a user agreement. — [Mail.ru Postmaster](https://postmaster.mail.ru/)
- Mail.ru's own definition of warm-up is gradually raising volume from a new IP or domain to your own subscribers. It says to avoid changing IP and domain at once, and to avoid sharp volume spikes even on an established sender. — [Mail.ru general rules](https://help.mail.ru/developers/mailing_rules/general/)
- DKIM is recommended so the domain keeps a consistent reputation regardless of sending IP. — [Mail.ru general rules](https://help.mail.ru/developers/mailing_rules/general/)
- More than 5% invalid addresses can lead to spam placement or a full block. — [Mail.ru general rules](https://help.mail.ru/developers/mailing_rules/general/)
- Compliance with the rules does not exempt mail from anti-spam checks. — [Mail.ru mailing info](https://help.mail.ru/developers/mailing_rules/info/)
- Trusted Sender requires at least 250,000 emails per month over the last 3 months, at least 6 months of sending, p=quarantine or reject, SPF, DKIM, List-Unsubscribe, a site with logo and company details, and Postmaster registration. Mail may decline without giving a reason. — [Mail.ru Trusted Sender](https://help.mail.ru/developers/mailing_rules/verified/)
- Link shorteners, redirectors, IP-address links, URL-encoded domains and active HTML objects are prohibited or risk blocking. Tracking pixels must have an explicit size of at most 1x1. — [Mail.ru technical](https://help.mail.ru/developers/mailing_rules/technical/)
- An older VK Habr article, seen via search summary, says complaints on mail without DKIM are not processed for FBL. Date not confirmed; likely dated. — [Habr, VK](https://habr.com/en/companies/vk/articles/147713)

**Yandex**
- Yandex shows trust markers: a red lock means sender data is likely forged, yellow means a possibly unreliable sender or domain mismatch, and grey means the sender is confirmed but the domain is new or has complaints. A green lock takes more time and fewer complaints. — [Yandex security technologies](https://yandex.ru/support/mail/web/security/security-technologies.html)
- Mandatory items include a static IP with correct reverse DNS, DKIM, SPF, a From address that exists and fully matches the authenticated account, checking server responses, and stopping sends to non-existent users. — [Yandex honest mailing requirements](https://yandex.ru/support/yandex-360/customers/mail/ru/web/letter/create/send-many-letters)
- Recommended items include a domain that identifies the source, sending from a different host than regular correspondence, DMARC, and avoiding shortened links. — [Yandex honest mailing requirements](https://yandex.ru/support/yandex-360/customers/mail/ru/web/letter/create/send-many-letters)
- Spam rejection strings include `Message rejected under suspicion of SPAM`, `Client host [IP_host] blocked using spamsource.mail.yandex.net` and `Blocked by spam statistics`. The page gives no separate remedy beyond the honest mailing rules. — [Yandex bounce reasons](https://yandex.ru/support/yandex-360/customers/mail/ru/bounces/yandex.md)
- Yandex mailbox limits are 300/day via SMTP, with a 24-hour block when exceeded, and filters may block template mail earlier, so an ordinary Yandex mailbox is not a production relay. — [Yandex sending limits](https://yandex.ru/support/yandex-360/customers/mail/ru/web/letter/create/send-many-letters)
- A secondary source, seen via search summary and described there as about 4.5 years old, says Yandex FBL access needed Return Path registration and a dedicated IP. Dated and unverified. — [Mailfit](https://mailfit.com/blog/kak-nastroit-postmastery-ili-dobro-pozhalovat-vo-vhodyashhie)

**Practitioner accounts**
- A Habr author running a Russian-hosted sending service says both Russian providers follow the same basics as Gmail but are noticeably stricter on new IPs and more attentive to DNS details, and advises registering at postmaster.mail.ru and postoffice.yandex.ru before the first send. Published 31 July; year not shown on the page. — [Habr, ShyDamn](https://habr.com/ru/articles/1061824/)
- Older Habr comments, seen via search summary, include a one-line message from a new server rejected at Mail.ru with `550 Spam message rejected`, and another operator reporting 6 trouble-free years with Google, Yandex and Yahoo but difficulties with Mail.ru. Anecdotal and dated, and the summary did not say which of these threads each comment came from. — [Habr comments A](https://habr.com/ru/articles/1027950/comments/); [Habr comments B](https://habr.com/en/articles/544376/comments/)
- Yandex and Mail.ru are reported to have closed IMAP/SMTP for domain mail without a paid plan (news snippet; year not confirmed). — [Habr news](https://habr.com/ru/news/1043988/); [ispmanager](https://www.ispmanager.ru/news/yandeks-i-mailru-zakryvaet-imap-i-smtp-bez-tarifa-kak-zabrat-pochtu-na-svoj-server-i-ne-platit-za-to-chto-ranshe-bylo-besplatno)

### Inferences
- Trusted Sender is out of reach at this volume and its List-Unsubscribe condition does not fit login mail, so the sender should plan without it.
- A new domain should expect a grey lock at Yandex for some time. That is a display of limited history, not a rejection, though users may read it as lower trust.
- Mail.ru's advice against changing IP and domain together argues for choosing the hosting and IP early and keeping them stable.

### Gaps
- I could not read the official Yandex Postoffice documentation: `yandex.ru/support/postoffice/ru/` returned only a title, and other guessed URLs returned 404. Metrics, thresholds and FBL conditions are unverified.
- No primary or reliable source was found on how Yandex or Mail.ru treat foreign IPs versus Russian hosting ranges, or on reputation of specific Russian hosters. Claims on this should be treated as unverified.
- No source was found on Russia-specific third-party blocklists. The only Russia-specific list I can evidence is Yandex's own `spamsource.mail.yandex.net`, seen in its bounce text.
- No published warm-up schedule or new-IP volume limit from either provider was found.
- No source on reputation effects of the .now TLD was found.

## How does reputation work: IP vs domain, cold start at low volume, warm-up, shared vs dedicated IP, blocklists, SNDS/JMRP, Gmail Postmaster Tools v2?

### Takeaway
Reputation attaches to both IP and domain, and warm-up is earned per domain/IP pair through wanted mail over weeks. Low volume is not suspicious in itself. The problem with a dedicated low-volume IP is too little signal, hard-to-maintain consistency and little reporting data.

### Cited Findings
**Warm-up**
- Word to the Wise (2017) expected 4-6 weeks to fully warm a mailstream at Gmail, warm-up of each domain/IP pair separately, and starting with 10-20 emails at a time every few hours, doubling once mail reaches the inbox. Dated. — [Word to the Wise, Laura Atkins](https://www.wordtothewise.com/2017/09/warmup-advice-gmail/)
- Steve Atkins (2024) describes warm-up as a conversation with the filter: slow sending gives it time to compare your stream, filters know many low-volume senders send wanted mail, and sudden changes are what they dislike. Warm-up works only for mail people want. — [Word to the Wise, 2024](https://www.wordtothewise.com/2024/06/warmup-is-communication)
- Al Iverson says providers are suspicious of servers with little history and advises no surprises, starting as low as you can stand. His example of 20,000-50,000/day doubling weekly is for much larger senders, and the post does not address low volume directly. — [Spam Resource, 2020](https://www.spamresource.com/2020/09/what-is-ip-warming.html)
- One Russian service's internal ladder runs 50, 500, 5,000 and 50,000/day, advancing after 3, 14 and 30 healthy days, with hard bounces under 2% and complaints under 0.1%. This is that author's policy, not a provider rule. — [Habr, ShyDamn](https://habr.com/ru/articles/1061824/)
- Aggregator guidance says user-requested low-volume transactional mail spread through the day can warm naturally, and that providers judge behavior, not intent. Secondary source. — [Suped](https://www.suped.com/learn/email-deliverability/how-do-i-warm-up-a-new-ip-address-for-transactional-emails)
- Aggregator consensus still recommends warming even low-volume dedicated IPs, while one quoted expert calls a low-volume dedicated IP somewhat risky. Secondary source. — [Suped](https://www.suped.com/email-deliverability/answers/is-ip-warming-necessary-for-low-volume-email-senders-with-a-dedicated-ip)
- Warming services are warned against. — [Spam Resource, 2022](https://www.spamresource.com/2022/03/ask-al-automating-ip-warming.html); [Suped summary of Laura Atkins](https://suped.com/email-deliverability/answers/are-ip-warming-services-effective-for-improving-email-deliverability)

**Shared vs dedicated**
- Laura Atkins (2018): maintaining a dedicated IP takes work, the two good reasons for one are certification and programs above 1 million emails per day, and shared IPs can give medium senders the same delivery, depending on the provider keeping co-tenants in check. — [Word to the Wise, 2018](https://www.wordtothewise.com/2018/11/dedicated-ips-pros-and-cons/)
- A search summary of a Word to the Wise post says smaller senders may lack the volume and frequency to build and keep reputation on a static IP, and that an IP cannot be warmed and then left idle. I did not read that passage directly. — [Word to the Wise, 2014](https://www.wordtothewise.com/2014/04/warmup-ip-addresses/)

**Gmail Postmaster Tools**
- Google says all old dashboards carry to v2 except Domain and IP Reputation, which will be retired because they are hard to act on, slow to show change and potentially misleading. The legacy UI deprecation is postponed with no date. API v2 is available and v1 will be retired. — [Google deprecation notice](https://support.google.com/mail/answer/16594218?hl=en)
- Google's help page for the tool still lists IP and Domain Reputation dashboards alongside Compliance status, Spam rate, Feedback loop, Authentication, Encryption and Delivery errors. — [Postmaster Tools help](https://support.google.com/a/answer/14668346?hl=en)
- Timing conflict: Twilio (October 2025) cited September 30, 2025 for retirement, while Warmy says Google stopped showing the data in August 2026 and stopped API sharing in September 2026. Neither date appears in Google's notice. — [Twilio](https://www.twilio.com/en-us/blog/insights/gmail-postmaster-tools-changes); [Warmy](https://www.warmy.io/blog/google-postmaster-retired-domain-reputation-warmy-is-building-its-own/)
- Compliance status covers primary domains including subdomain data, uses a rolling average so changes can take up to 7 days, and low-volume days may omit data. The Feedback Loop dashboard needs a minimum of spam reports, number unstated. — [Postmaster Tools help](https://support.google.com/a/answer/14668346?hl=en)
- Spam rate covers DKIM-authenticated inbox mail to engaged recipients and can look artificially low if Gmail is filtering much of it to spam. — [Postmaster Tools help](https://support.google.com/a/answer/14668346?hl=en)

**Microsoft SNDS and JMRP**
- SNDS gives per-IP reputation data and includes JMRP complaint reports. It needs a Microsoft Account and per-IP authorization, and access expires 10 months after approval, requiring periodic reattestation. The old URL now redirects to substrate.office.com. — [Microsoft SNDS](https://substrate.office.com/ip-domain-management-snds/snds)

**Blocklists**
- SORBS was shut down by Proofpoint on June 5, 2024 and no longer answers queries, so it should be removed from configs and any remaining "SORBS" check ignored. — [The Register](https://www.theregister.com/2024/06/07/sorbs_closed); [heise](https://heise.de/-9752522); [dnsbl.com](https://www.dnsbl.com/2024/06/)
- Spamhaus PBL lists end-user ranges that should not send directly, and a listing does not imply abuse. A self-hoster can remove an IP via check.spamhaus.org if it is static, an outbound mail server, has matching A and reverse DNS, and is assigned to the requester. Propagation takes about 15 minutes, and requests from free email domains are rejected. — [Spamhaus PBL](https://www.spamhaus.org/blocklists/policy-blocklist/)
- Yahoo advises against sending bulk mail from the same IPs as transactional mail. — [Yahoo best practices](https://senders.yahooinc.com/best-practices/)

### Inferences
- At 1,000 messages a day, one Gmail complaint equals 0.1%. At low volume, the rate is noisy and even a single complaint matters, and the true denominator is smaller still because only inbox mail counts.
- For a pure login stream, volume is dictated by user actions and cannot be scheduled. "Warm-up" here means launching in stages by real users, for example opening signups gradually, and not switching all mail to a new IP on one day.
- A dedicated IP with hundreds of messages a day is thin in signal, and may give less data in tools. A managed shared IP avoids this but puts part of the reputation under co-tenants, and the Russian and Gmail audiences may be served differently by the same provider.
- With IP and domain reputation dashboards going away, the working measures are Compliance status, spam rate, delivery errors, Mail.ru Postmaster, Postoffice and the sender's own SMTP logs.

### Gaps
- Barracuda's current status and self-removal process were not verified this session.
- No primary source gives a numeric low-volume threshold for dedicated IP viability. Figures circulating online appear to be vendor rules of thumb.
- Google's own warm-up wording was not retrieved in this session.

## How do greylisting and deferrals delay one-time codes, how can this be mitigated, and should streams be separated?

### Takeaway
Standard retry timing is too slow for short-lived codes, so an OTP queue needs its own fast retry policy, a realistic code lifetime and a resend path in the UI. Separation mainly matters if other mail types are added, and subdomains do not avoid volume thresholds.

### Cited Findings
- RFC 5321 says the retry interval should be at least 30 minutes and the give-up time generally at least 4-5 days, while allowing more sophisticated strategies when the client can determine the failure reason. — [RFC 5321](https://www.rfc-editor.org/rfc/rfc5321.txt)
- RFC 6647 recommends a default greylisting window of one minute to 24 hours, warns that an hour-long MTA retry causes costly delays for time-critical mail, and says consequences should be weighed where financial, transactional or security mail needs timely delivery. — [RFC 6647](https://www.rfc-editor.org/rfc/rfc6647.txt)
- RFC 6647 recommends a tuple of IP, envelope sender and first recipient, shared databases across MX servers, auto-allowing an IP after a successful retry, and a manual override for known senders. — [RFC 6647](https://www.rfc-editor.org/rfc/rfc6647.txt)
- Vendor guidance says a 4xx alone does not prove greylisting, since throttling and full mailboxes look similar. The signature is a 4xx followed by a later acceptance. Messages should stay queued rather than being manually resent, which can create duplicates and may look like a new first attempt. — [Bird](https://bird.com/explained/deliverability/what-is-greylisting); [Netdata](https://www.netdata.cloud/guides/postfix/postfix-greylisting-delays/)
- Netdata reports an observed Postfix delay around 300 seconds and advises not retrying faster than the destination's window. — [Netdata](https://www.netdata.cloud/guides/postfix/postfix-greylisting-delays/)
- Gmail's `421 4.7.28` is a temporary rate limit with variants for IP, netblock, SPF domain, DKIM domain and URL domain, so it can follow the netblock or linked domains and not only the sending IP. — [Gmail SMTP errors](https://knowledge.workspace.google.com/admin/support/troubleshooting/gmail-smtp-errors-and-codes?hl=en); [Zoho](https://help.zoho.com/portal/en/kb/campaigns-3-0/deliverability-guide/troubleshooting/articles/v3-why-gmail-rate-limits-your-marketing-emails)
- A Yandex employee on Habr Q&A attributed one delay to Mail.ru servers. Anecdotal, undated in my reading. — [Habr Q&A](https://qna.habr.com/q/1288532)
- OTP guidance from a vendor: a code that arrives after its expiry, 600 seconds in its example, is a failed login. Other sources default to 60 minutes. — [Nylas](https://developer.nylas.com/docs/cookbook/email/send-otp-emails/); [Kardow](https://kardow.com/help/guides/emails/otp-verification-email)
- Yandex recommends a separate host from regular correspondence, Mail.ru and Yahoo warn against sharing IPs between bulk and important mail. — [Yandex](https://yandex.ru/support/yandex-360/customers/mail/ru/web/letter/create/send-many-letters); [Mail.ru](https://help.mail.ru/developers/mailing_rules/general/); [Yahoo](https://senders.yahooinc.com/best-practices/)
- Gmail's Domain Reputation dashboard reflects only the exact domain used for authentication, while volume and compliance roll up to the primary domain. — [Postmaster Tools help](https://support.google.com/a/answer/14668346?hl=en); [Google sender FAQ](https://support.google.com/a/answer/14229414?hl=en)

### Inferences
- Do not use MTA default retry settings for OTPs. A schedule with retries around 1, 3, 5 and 10 minutes, then expiry and drop, fits better than 30-minute backoff, since a code past its lifetime has no value. This is my design suggestion and not a provider recommendation.
- A code lifetime around 10-15 minutes with a visible "resend" that reuses the same code would limit damage from a 5-minute greylist. A new code per resend can produce confusion when the older message arrives later.
- Dropping expired OTPs from the queue avoids delivering stale codes, which also avoids user confusion and possible complaints.
- A stable IP and envelope sender helps with greylisting, because the RFC 6647 tuple and auto-allow are tied to them. A fixed envelope sender (no per-message VERP) for a domain could reduce repeated greylist hits, but VERP is useful for bounce processing, so this is a trade-off to test.
- A separate subdomain such as one for auth mail makes sense if any other stream may come later. It does not bypass thresholds, and a new subdomain has to build its own history.

### Gaps
- I found no official statement on whether Yandex, Mail.ru, Gmail, Outlook, Yahoo or iCloud greylist new senders, or on typical delay values for them. Real numbers must come from the sender's own logs.
- M3AAWG documents were not retrieved in this session, so no M3AAWG position on stream separation or warm-up is reported here.
- No primary provider document on using subdomains to separate transactional from other mail was found.

## Should a transactional sender handle bounces and complaints, and what are best practices for OTP email contents?

### Takeaway
Yes. Bounce and complaint handling applies regardless of mail type, and Yandex and Mail.ru state it directly. OTP content guidance from providers is limited to general rules; subject-line and no-link advice has no primary source that I could find.

### Cited Findings
**Bounces and complaints**
- Yandex marks as mandatory that sending software checks server responses and stops sending to a user reported as non-existent. — [Yandex honest mailing requirements](https://yandex.ru/support/yandex-360/customers/mail/ru/web/letter/create/send-many-letters)
- Mail.ru requires removing or suspending addresses returning `550 user not found`, and warns that over 5% invalid addresses can cause spam placement or blocks. — [Mail.ru technical](https://help.mail.ru/developers/mailing_rules/technical/); [Mail.ru general](https://help.mail.ru/developers/mailing_rules/general/)
- Apple offers no FBL and tells senders to track temporary and permanent SMTP errors, suppress inactive subscribers and remove consistently bouncing addresses. Rejections usually include a URL with more detail. — [Apple postmaster](https://support.apple.com/en-us/102322)
- Yahoo provides a Complaint Feedback Loop, and Microsoft provides JMRP through SNDS. — [Yahoo FAQ](https://senders.yahooinc.com/faqs/); [Microsoft SNDS](https://substrate.office.com/ip-domain-management-snds/snds)
- Practitioner points: `250 OK` means accepted and not inboxed, 4xx is temporary and 5xx permanent, and hard bounces and complaints go on an internal suppression list. — [Habr, ShyDamn](https://habr.com/ru/articles/1061824/)
- Yandex requires a From that really exists. — [Yandex honest mailing requirements](https://yandex.ru/support/yandex-360/customers/mail/ru/web/letter/create/send-many-letters)

**OTP contents**
- A vendor guide says OTP mail should stay plain and immediate, with no scheduling or open tracking and no marketing content. — [Nylas](https://developer.nylas.com/docs/cookbook/email/send-otp-emails/)
- A sample template is a short message with the code, an expiry note and a warning not to share it; new requests invalidate older codes. — [Kardow](https://kardow.com/help/guides/emails/otp-verification-email)
- Yandex requires RFC 5322 and MIME format, valid `Date` and `Message-ID`, one real encoding per part, and no JavaScript or ActiveX. It recommends a clear, non-misleading subject and full domains in links rather than shorteners. — [Yandex honest mailing requirements](https://yandex.ru/support/yandex-360/customers/mail/ru/web/letter/create/send-many-letters)
- Mail.ru prohibits redirectors and IP-based links, and requires tracking pixels, if present, to be explicitly at most 1x1. — [Mail.ru technical](https://help.mail.ru/developers/mailing_rules/technical/)
- Gmail's `421 4.7.28` family includes URL-domain variants, and Zoho notes that domains linked in the body can trigger it. — [Gmail SMTP errors](https://knowledge.workspace.google.com/admin/support/troubleshooting/gmail-smtp-errors-and-codes?hl=en); [Zoho](https://help.zoho.com/portal/en/kb/campaigns-3-0/deliverability-guide/troubleshooting/articles/v3-why-gmail-rate-limits-your-marketing-emails)
- MojoAuth suggests monitoring OTP delivery and offering a fallback channel, since spam placement is the main failure. Vendor source. — [MojoAuth](https://mojoauth.com/blog/how-to-handle-users-who-dont-receive-otp-codes.md)

### Inferences
- Minimal-link OTP mail lowers exposure: no tracker domains, no shorteners, and if a link is needed then only the sender's own domain.
- A text/plain part alongside simple HTML is the safe default for a short message. It is standard MIME practice, but I found no provider source stating it affects placement.
- A code in the subject speeds login and shows in notifications, but also exposes the code on lock screens and in message lists. That trade-off is a product and security decision, not a deliverability rule.
- Suppressing an address after a complaint needs care for security mail, since a user could lose login access. A workable approach is to suppress hard bounces automatically, and on complaints stop non-essential notices while still allowing a user-initiated login code. This is my suggestion, unverified against any provider's policy.
- A hard-bounce check at signup can show the user a clear error, keeping invalid addresses from accumulating toward Mail.ru's 5% line.

### Gaps
- No primary source was found for putting the code in the subject, for omitting links in OTP mail, or for the effect of a text part on placement. The Zoho OTP article surfaced in search but was not read.
- Mail.ru's FBL setup and Yandex's complaint reporting were not verified from current official documents.
