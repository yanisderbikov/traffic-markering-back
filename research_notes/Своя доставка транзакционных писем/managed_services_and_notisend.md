# Managed transactional email services and NotiSend's unsubscribe rules (observed 2026-11-06)

Note on quoting: the brief asked for exact quotes. Source text is closely paraphrased here with clause numbers and links, so it can be checked; only one short verbatim quote is used. Read the linked clause before citing wording to NotiSend or to a lawyer. All prices were observed on 2026-11-06 unless stated otherwise.

## NotiSend: does the transactional API/SMTP require or insert an unsubscribe link?

### Takeaway
The premise is correct on paper: NotiSend's anti-spam policy, part of its user agreement, requires an unsubscribe link in every email and explicitly includes transactional ones. The API docs offer no parameter to turn it off, and they do not say whether the link is injected automatically, so actual behaviour needs one raw-message test and a written answer from support.

### Cited Findings

**Policy**
- The anti-spam policy declares itself an integral part of the user agreement, accepted on registration — [NotiSend anti-spam policy, preamble](https://notisend.ru/terms/antispam/).
- Clause 1.2 defines "email mailing" as any automated send, including mass, trigger and transactional messages, and says all provisions apply equally to all types — [clause 1.2](https://notisend.ru/terms/antispam/).
- Clause 3.2 lists mandatory elements for every email. The first is an unsubscribe mechanism: "видимую, легкодоступную и функционирующую ссылку", which must allow opting out in one click. The other two are the sender's name and physical address, and a short explanation of why the recipient is getting the email — [clause 3.2](https://notisend.ru/terms/antispam/).
- Clause 1.3 defines spam as mailing without prior, explicit, provable consent, and clause 2 lists only subscription forms, an unticked checkbox and paper forms as consent grounds — [clauses 1.3 and 2](https://notisend.ru/terms/antispam/). A login code has no natural fit in this consent model (see Inferences).
- Clauses 1.8 and 6.4 set limits of 1% hard bounces and 1% "negative reaction" (complaints plus unsubscribes over delivered), and exceeding them can be classed as bad practice — [clauses 1.8 and 6.4](https://notisend.ru/terms/antispam/).
- A June 2025 NotiSend blog post says every legal email has carried an unsubscribe link since the service was founded, and calls this a basic rule — [NotiSend blog, 17.06.2025](https://blog.notisend.ru/novyj-zakon-o-rassylkah-chto-izmenitsya-dlya-legalnogo-biznesa/).
- The main user agreement has no transactional carve-out: "транзакц" appears only in navigation links, and "отпис" appears only inside the definition of a recipient complaint. It attaches the anti-spam policy as Appendix 1 — [NotiSend terms](https://notisend.ru/terms/).
- Enforcement tools in the agreement: blocking the account for breach of the terms (3.3.1), limiting or suspending access if complaints stay above the limit after notice (3.3.5, with a 1% limit in 3.2.7), and one-sided termination for breach (8.1.1). A penalty of 100 ₽ per message applies specifically to clause 3.2.6 breaches (3.3.4) — [NotiSend terms](https://notisend.ru/terms/). I found no report of these being used against an API sender over a missing link.

**Marketing vs transactional streams**
- Marketing and API/SMTP streams are separated by default, so someone unsubscribing from marketing keeps receiving service notifications — [NotiSend blog, categories, 18.12.2025](https://blog.notisend.ru/kak-ispolzovat-kategorii-email-rassylok-v-notisend/).
- Single-message API sends and all SMTP traffic automatically fall into the "Transactional (API/SMTP)" category. Only the Campaigns method accepts category IDs — [same post](https://blog.notisend.ru/kak-ispolzovat-kategorii-email-rassylok-v-notisend/).
- If a recipient unsubscribes from a message in that category, they are unsubscribed from the whole "Transactional (API/SMTP)" category — [same post](https://blog.notisend.ru/kak-ispolzovat-kategorii-email-rassylok-v-notisend/).
- The same post describes two unsubscribe routes: the mailbox provider's interface, and a link in the email footer — [same post](https://blog.notisend.ru/kak-ispolzovat-kategorii-email-rassylok-v-notisend/).

**API (`POST https://api.notisend.ru/v1/email/messages`)**
- Documented parameters are `from_email`, `from_name`, `to`, `subject`, `text`, `html`, `attachments`, `payment`, `smtp_headers`. None disables or controls unsubscribe — [Email API docs](https://doc.msndr.net/api.notisend.ru/email.html) (loaded into [notisend.ru/dev/email/api](https://notisend.ru/dev/email/api/) by script).
- The response includes `events.unsubscribe`, and the docs describe that event as the user having unsubscribed — [Email API docs](https://doc.msndr.net/api.notisend.ru/email.html).
- Status `skipped` means not sent because the recipient unsubscribed or is on the problem-recipient list. Webhook events also include skipped — [Email API docs](https://doc.msndr.net/api.notisend.ru/email.html).
- Webhooks arrive once every 10 minutes in batches of up to 500 events — [Email API docs, Webhooks](https://doc.msndr.net/api.notisend.ru/email.html).
- The docs do not state that a link is inserted, required or removable. The transactional product page says only that statistics include complaints and unsubscribes — [Transactional page](https://notisend.ru/transactional-emails/).

**Moderation and limits**
- Direct `/email/messages` sends show no documented moderation. Templates do have it: editing an active template on a non-trusted account sends it to `pending`, and emails on it stop until it passes — [Email API docs, templates](https://doc.msndr.net/api.notisend.ru/email.html).
- An anti-spam mechanism can suspend API/SMTP sending. The sample response is a 429 with a retry time in seconds. The allowed rate depends on sending results and rises for clean senders. No number is published — [Email API docs, Restrictions](https://doc.msndr.net/api.notisend.ru/email.html).
- The marketing page advertises 4,000,000 emails/hour over API and 1,000,000/hour over SMTP — [Transactional page](https://notisend.ru/transactional-emails/). This is not a per-account guarantee given the dynamic limit above.
- The free tier is defined by recipients: up to 200 contacts with an unlimited number of emails, permanently — [NotiSend tariffs post](https://blog.notisend.ru/tarify-dlya-email-rassylok-notisend-kak-vybrat-podhodyashhij/); [price page](https://notisend.ru/price/stoimost-email-rassylki/). The aggregator [Toolfox](https://toolfox.ru/services/s/notisend) reports different numbers (1,000 subscribers, 5,000 emails); the primary page takes precedence.
- No page I read documents a 5 emails/minute free-tier limit. In this repo the 5/minute figure matches local task settings, `tasks.batch-size` default 5 and `tasks.fixed-rate-ms` default 60000 (`src/main/resources/application.properties`, lines 32-33). Login codes call `emailService.sendEmail` directly (`AuthServiceImpl.java`, line 165), so they do not go through that batch path.
- Repo observation: a search of `src/main` for "unsubscribe" and "отпис" finds nothing, so our templates contain no link. `NotiSendEmailService` checks only the HTTP status and logs success, so a later `skipped` would go unnoticed (`NotiSendEmailService.java`, lines 66-70).

**Pricing** ([price page data](https://notisend.ru/price/stoimost-email-rassylki/); transactional is not billed separately, per the [Transactional page](https://notisend.ru/transactional-emails/))

| Volume | "Письма" tariff | "Подписчики" tariff, unlimited emails, per month |
|---|---|---|
| 1,000 | 430 ₽ | 850 ₽ (1,000 recipients) |
| 10,000 | 2,160 ₽ | 4,190 ₽ (10,000 recipients) |
| 100,000 | 14,260 ₽ | 23,000 ₽ (100,000 recipients) |
| 500,000 | 45,360 ₽ | 73,180 ₽ (500,000 recipients) |

- A recipient counts against the "Подписчики" limit once emailed, and deleting them does not free the slot until the period ends — [price page FAQ](https://notisend.ru/price/stoimost-email-rassylki/).
- Payment: bank transfer for legal entities and sole proprietors, Russian cards, SBP, and EDO through SBIS and Diadoc — [price page FAQ](https://notisend.ru/price/stoimost-email-rassylki/). Webhooks appear as a row in the tariff comparison, but per-tariff availability is drawn as icons I could not read — [same page](https://notisend.ru/price/stoimost-email-rassylki/).
- Reviews: one catalogue shows 2.2 from 11 reviews, some about non-delivery. Small sample, unverified — [crmindex](https://crmindex.ru/services/notisend).

### Inferences
- The objection is valid as a contract matter. Sending login codes without the link would, on a literal reading of clauses 1.2 and 3.2, breach NotiSend's policy even though Gmail and others do not require it. Whether NotiSend enforces this on API sends is unknown.
- There are two possibilities. If NotiSend injects a footer, the unsubscribe button is unavoidable without their cooperation. If it does not, we can already send without one, but we are formally out of policy. Our templates have no link, so a real delivered login email will show which case applies.
- A one-click unsubscribe on a login-code email is an actual availability risk: the click moves the user into `skipped` for all API/SMTP mail, which would block their login codes while the API still looks healthy.
- "How to disable it": no documented way exists. The only route is asking support for an exception in writing.
- Leaving NotiSend does not require a self-hosted server. The other services below do not impose this rule on transactional mail, or allow it to be switched off.

### Gaps
- Whether `/email/messages` actually adds a footer link or `List-Unsubscribe`. Docs are silent. Test: open "show original" on a delivered login email, or send one test. I did not send one, since this would use the real key in `.env`.
- Whether NotiSend grants exceptions, and whether a non-compliant API sender gets suspended. No public source found. Ask support@notisend.ru.
- Whether API sends return an HTTP error or a 2xx with `skipped` for unsubscribed recipients. Docs list it as a status only; not tested.
- NotiSend's dedicated IP price and whether listed prices include VAT were not found on the pages read.
- No NotiSend knowledge base page on the subject was found; the site footer links to one, but I did not locate its URL.

## Russian services: unsubscribe rule, price, dedicated IP, webhooks, DKIM, location, payment

### Takeaway
Yandex Cloud Postbox, DashaMail transport and (with caveats) SMTP.bz and Selectel do not impose an unsubscribe link on transactional mail per the pages read, and Unisender Go inserts one by default but lets approved accounts turn it off. SendPulse is out because it stopped working with Russia, and Mindbox is the wrong size for this job.

### Cited Findings

**Comparison (₽ per month, observed 2026-11-06; "calc." means I computed it from the provider's tiers)**

| Service | Unsubscribe on transactional | 1,000 | 10,000 | 100,000 | Dedicated IP |
|---|---|---|---|---|---|
| Yandex Cloud Postbox | Opt-in; required only for marketing and mass mail | 0 (first 2,000 free) | ≈643 calc. | ≈6,448 calc. | Preview, on request; price not published on pages read |
| Unisender Go | Auto-inserted; `skip_unsubscribe=1` needs support approval | 800 (6,000 tier; free for first 2 months) | 1,200 | 5,000 by card; 6,000 by invoice | 3,200/month + 1,600 setup |
| DashaMail "Email-транспорт" | Opt-in `headers_unsub`; no auto-link documented | Not confirmed | Not confirmed | Not confirmed | 2,100/month |
| SMTP.bz | No link rule found; postal address required | 0 | 0 (limit 500/day) | 2,900 | Included in Premium; extra 600/month |
| Selectel Mail Service | No rule in terms | 0 (first 1,000) | ≈396 calc. | ≈4,356 calc. | Not covered |
| Sendsay | Not found | Contact-based | Contact-based | Contact-based | From 8 IPs, price not found |
| Mindbox | Not checked | From 15,000 | Manager quote | Manager quote | Not checked |

**Yandex Cloud Postbox**
- Unsubscribe is activated only when `ListManagementOptions` is passed, and only for single-recipient emails. It adds `List-Unsubscribe` and `List-Unsubscribe-Post` headers per RFC 8058 — [Postbox unsubscribe docs, updated 31.07.2025](https://yandex.cloud/ru/docs/postbox/concepts/unsubscribe).
- Terms section 3.2 lets Yandex restrict, slow or cut quotas without notice for reasons including no opt-out mechanism in marketing and other mass mailings, and complaints above 0.30% per day. There is no such rule for other messages, and the terms set no manual moderation process. Effective 21.06.2026 — [Postbox terms](https://yandex.ru/legal/cloud_terms_postbox).
- Price incl. VAT: first 2,000 free, 80.32 ₽/1,000 up to 10,000, 70.15 ₽ up to 50,000, 59.98 ₽ above that as shown in the page's example; every accepted email is billed even if undelivered. Page updated 15.09.2026 — [Postbox pricing](https://yandex.cloud/ru/docs/postbox/pricing). The service page says emails from 500,000 cost half as much — [Postbox service page](https://yandex.cloud/ru/services/postbox).
- Default quotas: 1 email/second and 200 emails/24 hours, raised by request in the console or through support. Updated 08.10.2026 — [Postbox limits](https://yandex.cloud/ru/docs/postbox/concepts/limits).
- Dedicated IP is Preview and issued on request. Warm-up is not managed; `WarmupStatus` always returns done. Updated 09.09.2026 — [Postbox dedicated IP](https://yandex.cloud/ru/docs/postbox/concepts/dedicated-ip).
- DKIM on own domain (Easy DKIM via two CNAMEs, or manual), SPF `include:spf.postbox.yandexcloud.net`, DMARC recommended — [Postbox DNS records](https://yandex.cloud/en/docs/postbox/concepts/dns-records).
- Events (Send, Delivery, Bounce, DeliveryDelay, Complaint, Subscription and others) go to Yandex Data Streams as JSON, at-least-once with `eventId` for dedupe — [Postbox notifications](https://yandex.cloud/ru/docs/postbox/concepts/notification). A separate guide covers webhook integration — referenced in [Postbox docs](https://yandex.cloud/ru/docs/postbox/concepts/).
- Vendor states 152-FZ compliance at level UZ-1, an AWS-SES-compatible API plus SMTP, and 99.9% deliverability without explaining how that is measured. The operator is ООО «Яндекс.Облако» — [Postbox service page](https://yandex.cloud/ru/services/postbox).

**Unisender Go**
- An unsubscribe link is inserted automatically unless `skip_unsubscribe=1` is passed. The parameter is forbidden by default for new accounts and projects, and support must allow it first. Custom `List-Unsubscribe` headers also need that permission — [Unisender Go SMTP API](https://godocs.unisender.ru/smtp-api); confirmed by [Anymail docs](https://anymail.dev/en/v12.0/esps/unisender_go/).
- Prices exempt from VAT: by card 6,000 for 800 ₽ (500/day limit; free first 2 months), 10,000 for 1,200 ₽, 100,000 for 5,000 ₽, 500,000 for 14,000 ₽. By invoice 100,000 for 6,000 ₽, 500,000 for 17,500 ₽, 1,000,000 for 28,000 ₽. Dedicated IP 3,200 ₽/month + 1,600 ₽ connection. Invoice tariffs are enabled through support — [Unisender Go](https://go.unisender.ru/).
- Vendor claims: 152-FZ compliance, servers in Russia — [Unisender Go](https://go.unisender.ru/); [Unisender page](https://www.unisender.com/ru/features/email/unisender-go-email-smtp-api/). A testimonial reports moving Jivo from Amazon SES in 5 days (vendor-selected, weak evidence) — [Unisender Go](https://go.unisender.ru/).

**DashaMail**
- The transactional API has an opt-in `headers_unsub` that adds a `List-Unsubscribe` link, and the unsubscribe webhook fires only if the email contains a link with `id` or `class="unsub_link"`. This implies no forced link. Methods require your own sending domain with DKIM and SPF — [DashaMail transactional API](https://dashamail.ru/transactional/).
- It has a dedicated "Email-транспорт" tariff with no subscriber limit and transport only; dedicated IP 2,100 ₽/month. On the free tier the API opens through support — [DashaMail pricing](https://dashamail.ru/pricing/); [transactional API](https://dashamail.ru/transactional/).
- The price calculator is script-driven; the static page shows 1,750 ₽ beside a default of 1,000, which I don't trust as a real per-volume price. An aggregator cites 50,000 emails for 1,925 ₽/month — [Toolfox](https://toolfox.ru/services/s/dashamail). Unconfirmed.
- Webhooks are signed with md5 and a key; footer links to a 152-FZ compliance page; operator ООО «Письмо» — [transactional API](https://dashamail.ru/transactional/).

**SMTP.bz**
- Free 15,000/month (up to 500/day); Premium 20,000 for 1,200 ₽, 50,000 for 1,750 ₽, 100,000 for 2,900 ₽, 600,000 for 9,800 ₽, 1,000,000 for 15,000 ₽. Dedicated IP included in every Premium plan; extra IP 600 ₽/month; overage 60 ₽/1,000 — [SMTP.bz price](https://smtp.bz/price).
- The anti-spam policy requires compliance with article 18 of 38-FZ, stops ads when a recipient objects, and requires a valid postal address in every email whatever its purpose. I found no requirement for an unsubscribe link on all emails — [SMTP.bz anti-spam](https://smtp.bz/anti-spam).
- The site shows payment to a bank account and the operator as ИП Тутаев А.М. — [SMTP.bz anti-spam, footer](https://smtp.bz/anti-spam). DKIM/SPF/DMARC and dedicated IP per aggregator — [Toolfox](https://toolfox.ru/services/s/smtp-bz).
- Fetch note: `curl` from this machine timed out twice against smtp.bz while other requests succeeded. One observation, proves nothing about uptime.

**Selectel Mail Service**
- Incl. 22% VAT: first 1,000 free, then 44 ₽/1,000 up to 1 million, unsuccessful sends also billed — [Selectel SMTP](https://selectel.ru/services/additional/smtp/). Hourly billing from balance — [Selectel payment docs](https://docs.selectel.ru/en/email-service/about/payment/).
- The terms (31.01.2025) name mailganer.com as a partner who also has access to forwarded messages. A search of the terms found no "отпис" — [Selectel conditions PDF](https://files.selectel.ru/docs/ru/conditions-mail-service.pdf).
- Rate limit conflict: the docs page says 20 emails per 5 minutes per resource, with increases by ticket and purpose — [Selectel about](https://docs.selectel.ru/en/email-service/about/about-email-service/); a release-notes search result says 100 per 5 minutes for those connected after 09.02.2026 — [Selectel release notes](https://docs.selectel.ru/en/email-service/release-notes). Either is far below our needs without an increase.
- Origin conflict: one docs version says sending only works from servers inside Selectel — [Selectel general info](https://docs.selectel.ru/en/networks-services/selectel-email-service/about-selectel-email-service); the current page says you send from your own server through the relay — [Selectel about](https://docs.selectel.ru/en/email-service/about/about-email-service/). This matters because our backend runs on Railway. Check before considering it.
- Vendor states 152-FZ-compliant servers and 99.99% deliverability, method unexplained; statistics on request — [Selectel SMTP](https://selectel.ru/services/additional/smtp/).

**Sendsay**
- Pricing is by contacts: 201–1,000 contacts for 1,060 ₽/month, 1,001–2,500 for 2,270 ₽; VAT-exempt. Transactional messages are counted separately, with a default limit of twice the contact limit (30/month on trial) — [Sendsay prices](https://sendsay.ru/price/).
- API and SMTP are offered, with real-time callbacks for delivery events; dedicated IPs start at 8 addresses; higher tiers list 152-FZ compliance — [Sendsay prices](https://sendsay.ru/price/).

**Mindbox**
- Prices start from 15,000 ₽/month, and a higher tier from 150,000 ₽/month; the final price is set by a manager — [Mindbox pricing](https://mindbox.ru/pricing/).

**SendPulse**
- It stopped registrations from Russia, ended support for registered Russian users, and won't renew or sign contracts there (published 21.03.2022, updated 30.11.2023) — [SendPulse notice](https://sendpulse.ua/ru/blog/sendpulse-suspends-sales-in-russia). Russian cards are not accepted, according to an intermediary's advertising article — [vc.ru](https://vc.ru/services/2142324-kak-oplatit-sendpulse-iz-rossii-cherez-payholder).
- For reference, unsubscribe in its SMTP is an optional `{{unsubscribe_url}}` variable — [SendPulse KB](https://sendpulse.com/ru/knowledge-base/smtp/other). Its [price feed](https://sendpulse.com/ru/pricing/smtp/feed.md) was served in KZT from this location, so the numbers were not used.

**VK / Mail.ru**
- I found no VK Cloud or Mail.ru transactional email API product — searches returned only third-party pages — [crmindex transactional ranking](https://crmindex.ru/ratings/transactional). The same ranking mentions Rusender and Tendence as other Russian options; not investigated.

### Inferences
- Rough cost at 20,000 emails/day (≈600,000/month), which is the top of the brief: SMTP.bz 9,800 ₽; Unisender Go by invoice roughly 17,500–21,000 ₽ (500k–700k tiers); Selectel ≈26,400 ₽ calc.; Postbox at most ≈36,400 ₽ calc. if 59.98 ₽ held above 100,000, and less given the 500,000 discount, which I didn't get in numbers; NotiSend 64,800 ₽ using the 750,000 pack, or 45,360 ₽ plus overage on 500,000.
- Best fit on the unsubscribe question: Postbox, since the rule is explicit in its terms and the feature is opt-in. Unisender Go is a fit if support grants `skip_unsubscribe` in writing before migrating. DashaMail transport looks fit, but its price needs confirming.
- Postbox is cheap to start but its default 200 emails/day quota must be raised before go-live, and section 3.2 allows restriction without notice, which is one more reason to keep a second provider.
- SMTP.bz is cheapest with a dedicated IP included, but it is run by a sole proprietor and has the least documentation I could verify. I'd treat it as a fallback candidate, not the primary.

### Gaps
- Unisender Go webhooks and DKIM on own domain: docs not opened for those points (assumed standard, not verified).
- Postbox: legal-entity payment, server location statement and dedicated IP price were not found on the pages read. Tiers above 65,500 emails were not visible in the fetched page.
- DashaMail per-volume prices need the calculator or sales. Sendsay unsubscribe rules and DKIM not found. Mindbox unsubscribe behaviour not checked.
- SMTP.bz webhooks, API unsubscribe behaviour and 152-FZ documents were not found on pages read.
- 152-FZ compliance claims are all vendor statements. Nobody verified the actual documents (assignment/processor agreement, attestation).
- Yandex's and Mail.ru's own sender rules on transactional mail were not retrieved: [help.mail.ru rules](https://help.mail.ru/mail-help/rules/info) is pointed to by [secondary sources](https://qna.habr.com/q/663340) but wasn't read.

## International services: unsubscribe rule, price, and whether Russian customers are accepted

### Takeaway
Internationally, not requiring a link on transactional mail is the norm, and Gmail treats OTPs as non-subscription mail, so NotiSend's blanket rule is the outlier. These providers are poor options for a Russian company anyway: AWS stopped new Russian sign-ups, one Mailgun user reports a sanctions suspension, and comparable US SaaS vendors ended Russian accounts, while card payment and 152-FZ cross-border duties add further friction. No provider's current Russia policy could be confirmed from primary text, except AWS's 2022 statement.

### Cited Findings

| Service | Unsubscribe on transactional | 10,000/month | 100,000/month | Dedicated IP | Russia |
|---|---|---|---|---|---|
| Amazon SES | Not checked in this pass | $1 at $0.10/1,000; plan rates $0.16-$0.23 | $10 à la carte; $16-$23 on plans | $24.95/IP/month | New sign-ups stopped 2022 |
| Postmark | Not required on transactional stream | $15-$18 | ≈$126-$177 est. | From $50/IP; 300k+/month, Pro plan or higher | Pricing page silent |
| Resend | AUP's opt-out rule targets bulk senders | $20 (50,000 Pro) | $35 Pro | $30/month add-on on Scale | AUP silent |
| Mailgun | Not required, per AUP | Not obtained (403) | Not obtained (403) | Not obtained | One suspension report |
| SendGrid | Not verified | Not extracted | Not extracted | Not extracted | No official notice found |
| Brevo | Not verified | Not extracted | Not extracted | Offered, price not extracted | Terms not readable |
| Mailtrap | Not verified | $15 | $30 Basic; $85 Business with dedicated IP | Business and Enterprise only | Pricing page silent |
| Scaleway TEM | Not verified | Not obtained (403) | Not obtained (403) | Not obtained | EU rules may apply |

- Amazon SES: $0.10/1,000 à la carte, plan-based $0.16/$0.22/$0.23, with new accounts starting on Essentials from 21.07.2026; dedicated IP standard $24.95/month, managed $15/month plus $0.08/1,000 — [SES pricing](https://aws.amazon.com/ses/pricing/).
- AWS stopped new sign-ups from Russia and Belarus in March 2022, citing uncertainty and lack of credit — [TechRadar](https://www.techradar.com/news/aws-blocks-new-russia-accounts); [DCD](https://datacenterdynamics.com/en/news/amazon-web-services-stops-accepting-new-customers-in-russia-and-belarus). I found no 2026 update either way.
- Postmark: free 100 emails/month; 10,000 for $15-$18 by plan; 100,000 is an estimate from overage; dedicated IP from $50 — [Postmark pricing](https://postmarkapp.com/pricing). Opt-out link marked not required on transactional and required on broadcast — [Postmark message streams](https://postmarkapp.com/message-streams).
- Resend: free 3,000/month and 100/day; Pro $20 for 50,000, $35 for 100,000; dedicated IP $30/month on Scale above 3,000 emails/day — [Resend pricing](https://resend.com/pricing). The AUP asks bulk senders to offer frictionless opt-out and honor it within 7 days, and does not mention sanctions or Russia — [Resend AUP](https://resend.com/legal/acceptable-use).
- Mailgun: section 1e requires a link in every marketing campaign and says transactional and confirmation emails don't need one. No mention of Russia in the AUP — [Mailgun AUP](https://www.mailgun.com/legal/aup/). One LinkedIn user reports Sinch Mailgun US suspended his account over a Russia attachment on an old profile and refused even after he changed his address to the Netherlands. One anecdote, not policy — [LinkedIn](https://nl.linkedin.com/in/edrokov).
- Mailtrap: free 4,000/month and 150/day; 10,000 for $15; 100,000 for $30 Basic or $85 Business — [Mailtrap pricing](https://mailtrap.io/pricing/).
- Brevo: all plans include transactional API, SMTP and webhooks; dedicated IP offered — [Brevo pricing](https://www.brevo.com/pricing/). The terms page could not be read through the fetch tool.
- Gmail: messages sent because of an explicit user action are not subscription messages; password resets, receipts and OTPs are its examples. One-click unsubscribe applies to subscription messages — [Google subscription guidelines](https://support.google.com/mail/answer/15263077?hl=en).
- OFAC's determination, effective 12.09.2024, prohibits US persons from providing to persons in Russia IT consultancy and design services, plus IT support and cloud services for enterprise management and design/manufacturing software. Listed categories include ERP and CRM but not email — [Miller & Chevalier](https://millerchevalier.com/publication/trade-compliance-flash-ofac-announces-prohibitions-provision-certain-it-and-software); [Arnold & Porter](https://www.arnoldporter.com/en/perspectives/advisories/2024/06/russias-access-to-it-services-and-software-restricted). These are law-firm summaries; the OFAC text was not read.
- Vendors went further than that minimum: Sentry ended paid accounts of customers in or organised under Russian law from 10.09.2024 — [Sentry help](https://intercom.help/sentryio/en/articles/13964968); Slack warned Russian users of suspension — [Cybersecurity News](https://cybersecuritynews.com/slack-warns-russians-users-suspension/).
- EU Article 5n restricts certain services and software to legal persons established in Russia — [Mondaq](https://www.mondaq.com/cyprus/government-contracts-procurement-ppp/1696606/provision-of-services-to-russian-entities-under-article-5n-of-eu-regulation-8332014). I found no statement from Scaleway or Brevo on how they apply this.
- 152-FZ side: using a foreign service that processes personal data requires prior notification to Roskomnadzor about cross-border transfer — [Sberbusiness](https://sberbusiness.live/publications/kak-uvedomit-o-transgranichnoi-peredache-personalnykh-dannykh-v-2023-godu); [Sovcombank](https://journal.sovcombank.ru/biznesu/transgranicnaia-peredaca-personalnyx-dannyx). A blog reports that from 01.07.2025 data can't be collected directly into foreign databases — [Habr](https://habr.com/ru/post/912250). Secondary sources; statute text not read.

### Inferences
- These prices are benchmarks, not options, for a Russian legal entity paying from a Russian account. At 100,000/month, SES à la carte ($10) and Resend ($35) sit near Postbox's ≈6,448 ₽ and Unisender Go's 5,000 ₽ once exchange rate is taken into account; I did not source a rate, so I'm not converting them.
- Account termination risk is the bigger concern for login codes: a sanctions-driven suspension could arrive with no warning. Using a foreign entity to get around restrictions is a legal question beyond this research.
- Practical conclusion: use these for policy benchmarking only. Their stance strengthens a request to NotiSend for an exception, and supports the claim that this requirement isn't an industry standard.

### Gaps
- Current (2026) Russia policy text for Postmark, Resend, SendGrid, Brevo, Mailtrap, Scaleway: not found. Treat as unknown, not as allowed. Confirming would need reading each terms page or asking sales.
- Mailgun and Scaleway pricing pages returned 403; SendGrid and Brevo prices render by script and weren't extracted.
- SES, SendGrid, Brevo, Mailtrap and Scaleway unsubscribe rules for transactional mail weren't confirmed in primary text.

## Hybrid: own domain and DKIM/SPF/DMARC, primary plus fallback, abstraction in code, and failover for OTP

### Takeaway
Yes, this is a standard and workable pattern: reputation stays tied mostly to your own domain if each provider signs with its own DKIM selector and both are in one merged SPF record. Failover is easy for hard send errors and hard for "accepted but never arrived", so OTP designs should combine automatic switching on API failure with a resend button that uses the other provider.

### Cited Findings
- A domain can publish only one SPF record, so both providers' includes must be merged, or each given its own return-path subdomain — [Suped](https://www.suped.com/email-deliverability/answers/how-do-i-transition-to-a-new-esp-while-maintaining-sends-on-my-current-platform-using-the-same-d). Example record with both includes, kept even while the backup is idle — [Mailflow Authority, vendor blog](https://mailflowauthority.com/email-infrastructure/email-failover-redundancy).
- Keep separate DKIM records per provider — from a search summary of [D-Engage docs](https://dev.dengage.com/docs/email-2) and [Suped](https://www.suped.com/email-deliverability/answers/how-do-i-transition-to-a-new-esp-while-maintaining-sends-on-my-current-platform-using-the-same-d), pages not opened. Postbox uses two CNAMEs for Easy DKIM, with SPF include `spf.postbox.yandexcloud.net` — [Postbox DNS records](https://yandex.cloud/en/docs/postbox/concepts/dns-records).
- A vendor blog recommends running two or more SMTP providers, either splitting traffic or staying ready to swap — [MailPace](https://mailpace.com/blog/guides/how-to-set-up-two-smtp-email-providers).
- Warm backup: route 5-10% of traffic through it continuously. The example uses a 90/10 split, moving to 100% on failover. Health check: SMTP check every 5 minutes, with 3 retries over 2 minutes before switching, and auto-return with an alert on recovery — [Mailflow Authority](https://mailflowauthority.com/email-infrastructure/email-failover-redundancy). This is one vendor's advice, not a measured standard; for OTP a 5-minute check is too slow on its own.
- OTP guidance (opinion piece, June 2026, no measurements): treat OTP as part of auth, not generic notifications; use a separate queue, keys and sender identity; prefer API over SMTP for error shape and message IDs; store each attempt with provider message ID and a hashed address; keep a short retry window; use an idempotency key; make only the newest code valid; treat hard bounces separately by prompting to fix the address; alert on provider error rate, queue age and bounce spikes. Add a second provider only for higher-risk systems, and warn that failing over to cold infrastructure can hurt delivery — [LavX News](https://news.lavx.hu/article/choosing-an-email-platform-for-otps-and-product-notifications).
- Duplicate sends between ESPs need explicit controls — same search summary, page not opened: [D-Engage docs](https://dev.dengage.com/docs/email-2) or [Suped](https://www.suped.com/email-deliverability/answers/how-do-i-transition-to-a-new-esp-while-maintaining-sends-on-my-current-platform-using-the-same-d).
- Framework prior art: Laravel's `failover` transport tries an ordered mailer list when the primary driver is down, with a configurable `retry_after` — [Laravel Mail docs](https://laravel.com/docs/12.x/mail#failover-configuration).
- Signals available by provider: NotiSend has `GET /email/messages/:id` with status, but webhooks batch every 10 minutes — [NotiSend API](https://doc.msndr.net/api.notisend.ru/email.html). Postbox emits Send, Delivery, Bounce and DeliveryDelay events through Data Streams — [Postbox notifications](https://yandex.cloud/ru/docs/postbox/concepts/notification). Sendsay advertises real-time callbacks — [Sendsay prices](https://sendsay.ru/price/). DashaMail has signed webhooks — [DashaMail API](https://dashamail.ru/transactional/).
- Repo: an `EmailService` interface already exists, and `NotiSendEmailService` implements it by overriding only `sendEmail(to, subject, htmlBody)`. A search of the repo finds no other NotiSend-specific callers (`src/main/java/ru/trafficmarkering/service/email/impl/NotiSendEmailService.java`).

### Inferences
My suggested design, not from a source:
- **Domain setup:** send from a subdomain such as `mail.<domain>` or `auth.<domain>`, with a provider-specific DKIM selector per provider, one merged SPF, and DMARC starting at `p=none` with reports. SPF has a 10-DNS-lookup limit and DMARC passes on aligned DKIM alone; both are from memory (RFC 7208, RFC 7489), not checked this session.
- **Reputation portability is partial:** domain reputation follows you, IP reputation doesn't. Moving to a new provider's shared pool is usually gentler than a new dedicated IP, which needs warm-up.
- **Synchronous switch:** on connect timeout, read timeout, HTTP 5xx or 429, and auth errors from the provider, send immediately through secondary. Do not switch on a 4xx validation error, since the same payload will fail again. A read timeout is ambiguous, since the provider may already have accepted, so accept a possible duplicate: both emails carry the same code or the newest wins.
- **Circuit breaker:** after a run of consecutive failures or a high error rate in a short window (for example 5 failures in 60 seconds, a starting guess to tune), route everything to secondary for a few minutes, then probe.
- **Accepted but slow:** do not auto-resend on a timer, as most delays are at the recipient's end. Show "Отправить ещё раз" after about 30-60 seconds and send that retry through the other provider, which makes each user's retry a natural failover. Log whether the second attempt was the one used.
- **Monitoring:** a synthetic probe sending every few minutes to seed boxes at Yandex, Mail.ru and Gmail through both providers, measuring time to arrival. This also keeps the backup warm at very low volume and gives you the delivery data no public source provides. Also track code-entered rate per provider, as that is the best real-world delivery proxy.
- **Suppression:** keep each provider's bounce and suppression list synced or checked, otherwise an address blocked at one will fail silently at the other, or vice versa. A `skipped`-style status must count as a failure, not success.
- **Natural pair:** Postbox plus Unisender Go (approved `skip_unsubscribe`) or SMTP.bz/DashaMail. Both are Russian, and Postbox's SES-compatible API keeps a future swap cheap. This is judgment, not a measured result.

### Gaps
- No primary or measured source gives thresholds for switching, or data on how often a backup provider actually saved OTP traffic. The threshold numbers above are starting guesses.
- No source was found on how Yandex and Mail.ru treat a domain sending through two providers. Their postmaster tools ([Yandex Postoffice](https://postoffice.yandex.ru), [postmaster.mail.ru](https://postmaster.mail.ru)) would show this per domain once live.

## Delivery time and inbox placement at Yandex, Mail.ru and Gmail: independent measurements

### Takeaway
I found no credible independent comparison of these services at Yandex and Mail.ru. What exists is vendor self-reporting, unexplained "99%+" claims, and a few Western tests with weak methods, so the only trustworthy number will be your own seed test run through the shortlisted providers.

### Cited Findings
- DashaMail publishes a live time-to-inbox page for transactional mail at Yandex, Mail.ru, Rambler, Gmail and iCloud: it sends identical content every 5 minutes and reports the last 24 hours. It's self-measured, and it notes other providers don't publish comparable figures. Figures load by script and weren't captured. The page text refers to "Haskimail", and I did not check how that relates to DashaMail — [DashaMail TTI](https://tti.dashamail.ru/).
- Postmark publishes its own Time to Inbox metric with tests every 5 minutes, having replaced seed-based inbox tests — [Postmark blog copy](https://www.plushcap.com/content/postmark/blog/postmark-why-we-removed-inbox-delivery-tests-from-our-status-page). Self-reported, and it doesn't cover Russian mailbox providers as far as I could see.
- Unexplained vendor claims: Postbox 99.9% — [Postbox](https://yandex.cloud/ru/services/postbox); Selectel 99.99% — [Selectel](https://selectel.ru/services/additional/smtp/). Likely acceptance-level "delivery", not inbox placement (my reading; neither page defines it).
- A Mailtrap-run test via GlockApps, reported second-hand: SendGrid 61.0% inbox with 20.9% missing, SES 77.1%, Mailgun 71.4%, on free plans, shared IPs and unwarmed domains — taken from a search summary pointing at [Mailtrap's blog](https://mailtrap.io/blog/test-email-deliverability/amp/) and [Prospeo](https://prospeo.io/s/mass-email-software); neither page was opened. Vendor-run, secondary, and not representative of a warmed domain.
- Prospeo says Brevo ranked last of 15 in EmailToolTester's 2026 benchmark, with an 83.1% average — [Prospeo](https://prospeo.io/s/brevo-alternatives). I couldn't reach the original, and practitioners have called this test data unreliable (opinions from 2021-2023) — [Suped](https://www.suped.com/email-deliverability/answers/is-the-emailtooltestercom-email-deliverability-test-data-reliable).
- Knock's benchmark tracks API response time, not arrival, and it reports different Postmark p50 values across sources (33 ms vs 16 ms) — [Knock](https://knock.app/email-api-benchmarks/postmark); [dev.to](https://dev.to/knocklabs/we-analyzed-a-billion-email-api-requests-heres-what-we-learned-j39).
- A Russian marketing article giving Mail.ru placement of both 42% and 70-85% contradicts itself and was discarded — [check.live-direct-marketing.online](https://check.live-direct-marketing.online/blog/proverka-dostavlyaemosti-rossiyskie-provaydery). Per-provider seed testing with at least 20-30 mailboxes is advised — [LuxePost](https://luxepost.ru/monitoring-inbox-placement-dlya-email-rassylok-kak-proverit-popadanie-pisem-vo-vhodyaschie-v-2025-2026). Seed tools with Yandex and VK boxes exist — [Respondo](https://respondo.ru/deliverability).
- GlockApps counts inbox plus promotions as "inbox", and notes the missing rate can be understated — [GlockApps 2021 report](https://glockapps.com/downloads/GlockApps_Inbox_Placement_Report_Jan_2021.pdf). So results aren't transferable to OTP mail, which is primary-inbox dependent.

### Inferences
- Western test results say little about Yandex and Mail.ru, and they measure marketing-like mail from cold setups, not low-volume OTP mail from a warmed domain.
- A small own test is cheap and decisive: identical login-code template, same domain, through NotiSend, Postbox and Unisender Go (or DashaMail/SMTP.bz), to about 10 boxes each at Yandex, Mail.ru and Gmail, for several days at intervals. Record folder and arrival time at p50/p95. All free tiers allow it except where API access needs support to enable.
- Expect shared-pool variance. Neighbours affect shared IPs, so a test run now can't be taken as a lasting guarantee. Keep the probes running.

### Gaps
- No independent per-provider inbox or time-to-inbox data for Unisender Go, Postbox, DashaMail, SMTP.bz, Selectel, Sendsay or NotiSend at Yandex or Mail.ru was found after searching in both languages.
- DashaMail's actual TTI numbers weren't captured, since they need a browser to load.
- RAMM's rating, mentioned in NotiSend's footer, wasn't examined; it isn't a delivery measurement as far as I know.
