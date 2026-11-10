# Report schema (v1)

The exported JSON is the app's only external interface today and the future
hand-off format to Artex. Treat it as an API: additive changes only, bump `schema`
for breaking ones.

```json
{
  "schema": 1,
  "startedAt": 1762800000000,
  "finishedAt": 1762800003200,
  "score": 42,
  "checksRun": 24,
  "deviceLabel": "Google Pixel 8",
  "patchLevel": "2025-11-01",
  "androidRelease": "15",
  "collectorErrors": [],
  "gaps": [
    { "id": "gap.intrusion", "text": "DNS lookups, connection attempts and process starts — system logs need a USB collection (AndroidQF)." }
  ],
  "indicatorAsOf": "2026-11-02",
  "indicatorCount": 1054,
  "training": false,
  "aiFindings": [],
  "aiMeta": null,
  "aiError": null,
  "findings": [
    {
      "checkId": "lock.missing",
      "title": "No screen lock",
      "observed": "Anyone holding the phone can unlock it.",
      "why": "Without a lock, a thief gets the mail, the bank app and the saved passwords in one go. It also weakens encryption-at-rest.",
      "fix": "Settings → Security → Screen lock. Use a 6-digit PIN at minimum, ideally with fingerprint or face.",
      "severity": "HIGH",
      "confidence": "CONFIRMED",
      "impact": "THIS_DEVICE",
      "evidence": {},
      "appLabel": null,
      "appPackage": null,
      "fixTarget": "SCREEN_LOCK"
    }
  ]
}
```

## Field notes

| Field | Type | Notes |
|---|---|---|
| `schema` | int | `1`. Consumers must ignore unknown fields. |
| `score` | int 0–100 | Derived; never parse it as a verdict without `findings`. |
| `checksRun` | int | Denominator for progress and for detecting a partial rule pack. |
| `collectorErrors` | string[] | Non-empty means the scan is partial. |
| `gaps` | `{id,text}[]` | Places that were not checked. Always non-empty. |
| `indicatorAsOf` | string | Corpus snapshot date; display next to any indicator finding. |
| `indicatorCount` | int | Bundled values matched against. |
| `training` | bool | The report is the built-in example, not a device. |
| `aiFindings` | `Finding[]` | Advisory model output. Same shape, `origin: "AI"`. Never scored. |
| `aiMeta` | object? | Provider, model, steps, tokens, time, note. |
| `aiError` | string? | Last failure, shown as `Last run: …`. |
| `origin` | enum | `RULES` (default) or `AI`, per finding. |
| `severity` | enum | `INFO`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` — declared ascending. |
| `confidence` | enum | `CONFIRMED`, `LIKELY`, `POSSIBLE`. |
| `impact` | enum | `THIS_DEVICE`, `INSTALLED_APP`, `ACCOUNT`, `NETWORK`. |
| `checkId` | string | Stable rule id; app-scoped findings suffix `:<package>`. |
| `evidence` | map | Rule-specific key/values only (packages, ages, proxy host). |
| `appPackage` | string? | Present for app-scoped findings; safe to use as a `package:` URI. |
| `fixTarget` | enum? | Settings page hint; `null` means no deep link. |

## Privacy properties

- No timestamps per app, no paths, no accounts, no SSIDs beyond the connected one.
- Package names are the only high-entropy field.
- A report is a point-in-time snapshot; there is no device identifier.

## Consumer contract

1. Score = `clamp(100 - Σ(weight × factor), 0, 100)`.
2. Findings are sorted by descending cost.
3. `confidence` must be displayed wherever `severity` is.
4. `collectorErrors` and `gaps` must be shown, not swallowed.
5. `indicatorAsOf` must be shown with any `spy.ioc` finding; a stale date changes
   how much a hit is worth.
6. `training: true` must be labelled and must not overwrite the user's real report
   without telling them.
7. `aiFindings` must never be merged into `score`; keep the two lists separate and
   label AI rows.
