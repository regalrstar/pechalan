# AI layer

Optional, bring-your-own model. The app is fully functional without it; the rule
score never depends on it.

## Two modes

1. **Deep scan** — one bounded agent loop over evidence already on the phone.
2. **Dynamic protection** — a baseline diff on install/access/network changes,
   with the model asked about the delta only.

## Authority

| | Rules | AI |
|---|---|---|
| Produces the score | yes | no |
| Can invent a new check | no | yes |
| Runs when offline | yes | no |
| Labelled in UI | no | `AI` badge |
| Stored separately | `findings` | `aiFindings` |

AI findings never overwrite rule findings and never move the score. They are
advisory and shown under their own heading with provider, model, steps and tokens.

## The loop

- Input: normalised `Evidence`, existing rule titles, an overview JSON.
- The model answers with exactly one JSON object: a tool call, or findings.
- Tools are read-only and operate on in-memory evidence only:
  `overview`, `permission_combos`, `sideloaded_apps`, `access_grants`,
  `network_config`, `os_posture`, `app_detail`, `indicator_hits`.
- Budget: 2–10 steps (default 6), 1,200 tokens per reply, 6 findings max.
- Stop conditions: findings returned, step limit, HTTP/parse failure.
- Malformed JSON gets one repair turn; unknown tools return `{"error":...}`.

The model cannot run shell, read files, or hit the network itself. The host is the
only actor.

## Output validation

- Severity/confidence are parsed against the enums; unknown values fall back to
  `MEDIUM`/`POSSIBLE`.
- Empty titles are dropped, text is capped, findings are capped.
- `checkId` is derived (`ai.<slug>`), `origin = AI`.
- Parsing tolerates prose and code fences: the first balanced JSON object is taken.

## Prompt-injection surface

App labels and package names are attacker-controlled (anyone can publish an app
called `"Ignore previous instructions"`). Mitigations:

- labels are whitespace-collapsed and capped at 60 chars before serialisation;
- tools are a fixed enum, so injected text cannot name a new capability;
- the model can only return findings, never a score or an action;
- findings are rendered as text only, with no auto-execution.

Residual risk: injected text can bias the *content* of an advisory finding. That is
acceptable because a rule-backed report is still underneath.

## Privacy

- Off by default; no key, no call.
- Payload: build/patch/flags, package list (labels only if the user turns package
  names off), granted permission names, access lists, network configuration,
  indicator matches. Never messages, photos, files, accounts, location.
- Key stored in `EncryptedSharedPreferences` (AES-256-GCM master key in Keystore),
  with a plain-prefs fallback if the Keystore fails.
- Sent only to the base URL the user typed — including a local server, which keeps
  the data on their own network.
- Retention is the provider's; the app stores no transcript, only the resulting
  findings and token counts.

## Providers

- **OpenAI-compatible** (`/chat/completions`, bearer key).
- **Anthropic** (`/v1/messages`, `x-api-key`, `anthropic-version`).
- **Built-in test model** — scripted, offline, deterministic. Used for demos and
  the unit tests.

Base URL is free text so any compatible gateway works. There is no provider
preset list and no key validation beyond a live `Test connection`.

## Watch mode

- Events: package added/removed/replaced, device admin enabled/disabled, plus a
  15-minute periodic check for accessibility, certificate, VPN, DNS/proxy and
  developer-flag drift that has no broadcast.
- The receiver is exported so the periodic job and a manual
  `adb shell am broadcast -a dev.triage.app.WATCH -p dev.triage.app.debug` reach it.
- Diff is deterministic and human-readable; the model only explains it.
- Without a configured model, a hard-coded severity still notifies for new admins,
  new accessibility access, new CAs and scanning disabled.
- Routine installs stay silent below MEDIUM.
- Baseline lives in app-private storage; enabling the mode writes one immediately.
- A "Check for changes now" button in settings runs the same worker on demand.

## Cost and failure

Every call is billed by the provider. The card shows steps and tokens after a run;
the transcript is not stored. Failures (401, 429, timeout, malformed output) are
shown as `Last run: …` and never replace the rule report. The AI block is retryable
without re-scanning.

A new posture scan replaces the report and with it the previous AI findings: they
belong to the evidence they were derived from. Re-run the deep scan after a rescan
if you want them back.

## Non-goals

- No automatic fix execution.
- No background streaming or chat UI.
- No model-chosen scoring weights.
- No transcript sync between devices.

## Connection compatibility (0.1.1)

Anthropic requests omit `temperature`, which newer Claude models reject. The
connection probe and AI review share this transport. Provider failures display
HTTP status plus the provider's message, with the configured API key redacted.
Local HTTP regression tests reproduce the reported rejection and verify the
actual request headers, request body, connection probe and complete review path.
These tests use a local stub and a fake key; they do not call a paid provider.
