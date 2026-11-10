# Architecture

## Shape

Triage is local-first by construction. There is no backend in v1, which removes
the two things consumers distrust most: an account and an upload.

```
┌────────────────────────────── Android app ───────────────────────────────┐
│                                                                          │
│  ScanService (foreground, dataSync)                                      │
│      └─ EvidenceCollector  ── read-only PackageManager / Settings /      │
│            │                   AppOps / Keyguard / WifiManager / APK hash│
│            │                                                             │
│            ├─ IndicatorStore (bundled CC0 corpus, 1,054 values)          │
│            ▼                                                             │
│         Evidence (normalised, no personal content) + IocHit[] + gaps     │
│            │                                                             │
│            ▼                                                             │
│         Engine ── Rules.all (25 pure functions) ── severity × confidence │
│            │                                         + permanent gaps     │
│            ▼                                                             │
│         Report ── app-private JSON (last-report.json)                    │
│            │                                                             │
│            ├── Compose UI (score, fix-first, gaps, detail, settings)     │
│            └── optional share intent (the exact JSON)                    │
└──────────────────────────────────────────────────────────────────────────┘
```

## AI layer (optional)

```
Evidence + rule titles
      │
      ▼
DeepScan loop ── your model (OpenAI-compatible / Anthropic / mock)
      │            └─ may call Tools (8 read-only views of the same evidence)
      ▼
aiFindings (origin = AI) ── advisory, never scored
      │
      └── Watch mode: baseline diff → delta prompt → notification at MEDIUM+
```

Rules remain authoritative. The model has no shell, no file access and no network
of its own; the host executes everything. Output is schema-validated before it
becomes a finding. See [AI.md](AI.md).

## Indicator matching

The corpus and its semantics are documented in [INDICATORS.md](INDICATORS.md).
Two rules matter architecturally:

- **Certificate digests are cheap**, so they are collected for every non-system
  app. **File hashes are expensive**, so only plausible candidates are hashed,
  under a byte/time budget; what the budget skips becomes a gap.
- Matching happens in the collector and is handed to the engine as `IocHit[]`, so
  rules stay pure and testable and the engine never does IO.

## Why a deterministic engine, not an LLM

A consumer scan must be:

- **fast** — seconds, offline, no token bill per check;
- **stable** — the same device must score the same twice in a row;
- **explainable** — every point of the score must trace to a rule.

The agentic layer is genuinely better at *explaining* and at *novel* questions
("is this app sketchy?"). It is worse at being a scoring function. So the split
is: rules decide, a model (later, optional) narrates.

Consequences: rules are pure `Evidence -> Finding?` functions with no Android
types. They are unit-tested without a device or emulator (see
`app/src/test/.../EngineTest.kt`).

## Evidence, not raw data

The collector normalises Android APIs into a small record. Notably it does **not**
collect contacts, messages, files, accounts, location or SSIDs' BSSIDs. A package
is reduced to: name, label, system flag, installer, target SDK, granted dangerous
permissions, launcher presence, first-install/update times.

This is the privacy boundary and also the integration boundary: the same record
is what a future Artex task would receive.

## Scoring

Start at 100. Deduct `severity.weight × confidence.factor`, clamped to `[0,100]`:

| severity | weight | × confirmed | × likely (0.85) | × possible (0.6) |
|---|---|---|---|---|
| critical | 25 | 25 | 21 | 15 |
| high | 15 | 15 | 12 | 9 |
| medium | 8 | 8 | 6 | 4 |
| low | 3 | 3 | 2 | 1 |
| note | 1 | 1 | 1 | 1 |

`Severity` is declared in ascending order so `severity >= MEDIUM` is a plain
ordinal comparison. This was a real bug once; keep it.

The score is a progress indicator, not a verdict. The finding list is the source
of truth and is always sorted by cost.

## Failure isolation

Every collector block is wrapped: an OEM that breaks a hidden setting degrades
one section, not the scan. Collector failures are carried into the report and
shown as "collector error(s)" so the UI never claims a clean sweep it did not do.

Each rule is also wrapped in `runCatching`; a rule fault drops that rule only.

## Extension points

- **Rule pack** — adding a check is one `Rule { e -> ... }` entry plus a test.
- **Fingerprint corpus** — `stalkerware/Fingerprints.kt`; substring match on
  package + label, deliberately conservative.
- **Platform layer** — an iOS module produces the same `Evidence`; the engine and
  UI do not change.
- **Artex hand-off** — export the report JSON to an Artex task; the companion can
  expose richer probes over MCP/http. Artex never needs root on the phone.

## Performance budget

Target: full scan < 4 s on a mid-range 2021 device with 300 packages.

Costs: `getInstalledPackages` (dominant), one AppOps check per package for two
ops, and `queryIntentActivities` for launchers. No network, no disk I/O beyond the
final report write. If the budget is missed, the fix is to cache the package
inventory and diff it, not to sample.

## App-level threat model

| Risk | Mitigation |
|---|---|
| The report leaks a device profile | app-private storage, `allowBackup=false`, no internet permission |
| Share intent over-shares | exported JSON is the same minimal record, no paths/accounts |
| Malicious deep link | all intents are `settings` actions built from constants, package URI only from the scanned set |
| Scan pegged to a foreground service forever | `START_NOT_STICKY`, `stopSelf` in `finally` |
| User mistakes a clean scan for proof | explicit limits sheet inside the app |
