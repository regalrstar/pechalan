# Threat model

Triage exists for one person: the owner of the phone. The model starts from "who
can reach this owner's life through this device", not from a corporate estate.

## Adversaries, in order of consumer relevance

### 1. Stalkerware / commercial monitoring

Someone with brief physical access (partner, ex, parent, employer) installs a
monitoring product, grants it SMS/accessibility/location, and hides it.

- Signals: known package fingerprints, unknown installer, install recency,
  permission breadth, device-admin/accessibility grants.
- What Triage can say: "this product is installed, here is who installed it and
  when, here is what it can reach".
- What it cannot say: whether the *account* behind it is still active. That needs
  the product's own console.

### 2. Scam and trojan apps

Store or sideloaded apps that ask for everything, show ads, or harvest OTPs.

- Signals: sideload provenance, SMS + screen combination, overlay + accessibility,
  obsolete target SDK, new-and-broad installs.
- Limitation: a store-reviewed app can still be malicious for a week. Triage
  reports capability, not reputation.

### 3. Network interception

Hostile Wi-Fi, proxy configuration, a corporate/Charles CA, an unfamiliar VPN.

- Signals: open/WEP Wi-Fi, global proxy, user CA count, active VPN, Private DNS
  off.
- Limitation: Triage reads configuration, not packets. A pinned app is unaffected
  by a proxy; a sloppy one is fully exposed. We can't tell which.

### 4. Account takeover via OTP

The highest-value consumer outcome, and mostly a permission problem: SMS read +
screen observation + notification access.

- Signals: `access.combo` rule, notification listeners, SMS grant.
- Limitation: it reports the *capability*, not a live compromise.

### 5. Physical / repair / resale

No lock, USB debugging, unknown sources, developer mode.

- Signals: lock state, ADB, install posture.
- Limitation: no forensic depth without root.

### 6. Targeted spyware (Pegasus-class)

Out of scope for a v1 app-level scan. The honest position: Triage reports the
*enablers* (patch age, profiles, config) and later accepts MVT artifacts; it does
not claim implant detection.

## Assets protected

| Asset | Exposure path | Checked by |
|---|---|---|
| Messages / OTPs | SMS permission, notification listener, accessibility | access.*, spy.* |
| Screen and typing | accessibility, overlay | access.accessibility, access.overlay |
| Location / routine | location perms, usage access | spy.recent_powerful_app, access.usage |
| Accounts | OTP capture, phishing overlay, proxy/CA | access.combo, net.* |
| Device control | device admin, ADB | access.device_admin, dev.usb_debugging |
| Traffic | Wi-Fi, proxy, CA, VPN | net.* |

## Trust boundaries

1. **Phone → app.** The app is read-only, but it is still a third party. Evidence
    is minimised; the export is the same minimal record.
2. **App → user.** The UI must distinguish observation ("a user CA exists") from
    inference ("someone may be reading your traffic") by carrying a confidence
    level.
3. **App → future backend.** If a cloud is ever added, the boundary is the
    evidence bundle, and it must stay opt-in per scan.

## Non-goals

- Replacing Play Protect or a real AV engine.
- Enterprise compliance reporting.
- Jailbreak/root detection as a security claim.
- "Hack-back" or exploit verification.

## Assumptions

- The OS API answers truthfully; OEM forks may not. Collector errors are surfaced
  rather than swallowed.
- The user can reach Settings. Every fix has a text path if the deep link fails.
- The device is not already fully compromised with a system-level implant; in that
  case every local signal is forgeable, which the limits sheet says.
