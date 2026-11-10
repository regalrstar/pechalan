# Store policy notes

Working notes for Play submission. Not legal advice; verify against current policy
before uploading.

## `QUERY_ALL_PACKAGES`

Triage qualifies under the "device security / inventory" use case. The declaration
must state:

> Triage inventories installed packages to report sideloaded apps, stalkerware
> fingerprints and dangerous permission combinations. The list never leaves the
> device unless the user shares the report.

Play also accepts a `<queries>` block for narrower use, but the scan needs the full
set, so the permission stays. Expect a review question; answer with the paragraph
above and a screen recording of the finding list.

## Data safety form

Current build: nothing is collected by default. If the user enables AI, the
evidence bundle goes to **their** endpoint, so the form must declare optional
"App activity → Installed apps" collection, encrypted in transit, deletable
locally. No account exists.

Also declare the `INTERNET` permission as used only for that optional layer.

## AI disclosure

- State in the listing that AI is optional, bring-your-own-key, and that the key is
  stored on-device.
- Keep the "rules decide, AI advises" sentence in the long description.
- Do not imply the AI runs offline; the built-in test model is labelled as such.

## Foreground service

`dataSync` type, declared in the manifest and in the Play Console. Justification:
the scan walks every installed package and must not be killed at screen-off. It
runs seconds, not minutes, and calls `stopSelf`.

## Notifications

One low-importance channel ("Scan progress"). The permission is requested on first
launch, with the result shown in-app if denied.

## Claims to avoid

- **"Antivirus"** — there is no scanning engine or definition feed. Use "security
  check-up".
- **"Detects spyware"** — say "flags known monitoring apps and the access they
  use".
- **"Protects you"** — say "shows you what to fix".

## Copy constraints on the listing

Screenshots must show real findings, not mockups with a scary score. The limits
("cannot see a fresh implant") belong in the long description, not only in-app.

## iOS App Store (phase 2)

- No app inventory: state it in the first screenshot caption.
- Profile installation requires a user-driven Safari flow; do not imply automatic
  install.
- Local Network permission is needed only if LAN probing ships.
- Network Extension requires an entitlement request; defer until phase 3.
