# Copy and interaction rules

The product fails if it reads like a pentest report. These rules are enforced in
review, not just taste.

## Voice

- Second person, present tense: "Anyone holding the phone can unlock it."
- Short sentences. One idea per line.
- Concrete nouns: "one-time code", not "2FA token".
- No vendor jargon in the title. `accessibility` appears only in the fix, next to
  the Settings path.

## Three-part finding

Every finding must survive these three questions:

1. **What did we see?** — an observation, no inference.
2. **Why does it matter to me?** — the consequence, in this person's life.
3. **What do I tap?** — a path and a deep link, always both.

If part 2 is "attackers could…" with no concrete outcome, rewrite it.

## Severity and confidence

- Severity is the impact if true; confidence is how sure we are. They are separate
  fields and both are shown.
- `POSSIBLE` findings never use the imperative ("Turn it off"), only "check".
- Never call something "critical" unless it is both confirmed and immediately
  exploitable by a person, not a scanner.

## No scareware

- No countdowns, no "your phone is 3× more at risk", no red for information.
- The score is stated plainly and followed by what changed, not by a badge.
- An empty list says "Nothing needs attention" and adds what that does *not* mean.

## Fixes

- Start with the Settings path. Deep-link after.
- Name the screen exactly as the OS labels it.
- If the fix needs judgement, say the trade-off in one clause: "Leave it if you
  use it."
- Never auto-disable anything in v1. The user taps.

## Limits

- The limits sheet is reachable from the overflow menu on the main screen, not
  buried in settings.
- A clean scan always carries: "no known problem, not proven clean."

## Indicator findings

- Say "published spyware match", never "you are infected".
- Name the artifact strength in the observed line: exact file, signing key, or
  package name only.
- Always carry the family, the source tag and the corpus as-of date.
- The fix is "keep it for now", because uninstalling destroys evidence.

## AI findings

- Always visually distinct: `AI` badge, own section, provider/model/token line.
- State the authority every time: "results never change the score".
- An AI failure is `Last run: …`, never a missing section or a silent retry.
- Deep-scan copy promises "what the rules missed", not "better than the rules".

## Empty and error states

- Empty: calm, factual, points at the daily re-scan.
- Collector error: show the count and the failing area; do not present a partial
  scan as complete.
- No permission for notifications: scan still runs, result still visible in-app.

## Naming

Working name: **Triage**. "Check" is the verb, never "scan" in the UI (the API and
the code say scan; the user sees check). The app is a check-up, not a scanner.
