# Roadmap

Ordered by risk retired per unit of work, not by polish.

## Phase 0 — this repo (done)

- Threat model, evidence boundary, deterministic engine, 24 posture checks.
- Bundled indicator corpus (1,054 published values) with certificate/file/package
  matching, severity grading and source attribution.
- Permanent gaps section so a clean score states its coverage.
- Training example so a first report can be read before it matters.
- Working end-to-end app: scan → score → fix-first list → detail → settings intent.
- Unit tests (15), JSON export, daily/boot scan.

**Gate:** clean build, tests green, no crash on a cold install, score reproducible,
corpus counts and as-of date on every report.

## Phase 1 — credible v1 (next)

1. Release-time corpus refresh + integrity check (hash the assets, record as-of,
   fail the build on a stale corpus older than 60 days).
2. AI hardening: JSON-schema mode where supported, retry/backoff on 429, per-run
   cost estimate before sending, transcript export for support.
3. Hidden-launcher detection and per-app permission detail screen.
4. Accessibility capability flags, battery-exemption on monitoring apps.
5. "Explain this finding" copy review with 5 non-technical testers.
6. Performance pass: cache inventory, diff by `lastUpdateTime`, hard 4 s budget on
   a 300-app device, measure the hash budget honestly.
7. Onboarding that sets expectations before the first scan.

**Gate:** no finding fires on a stock Pixel without a defensible reason; false
positives under 10 % on a 30-device panel.

## Phase 2 — iPhone companion

iOS has no shell and no third-party app inventory. Scope it honestly:

- Configuration audit: passcode, Stolen Device Protection, Lockdown Mode, developer
  mode, VPN/proxy/DNS, profiles and supervision, iCloud backup state.
- Encrypted-backup / sysdiagnose import for MVT-style artifact checks.
- App Privacy Report import.
- Shared engine and report.

**Gate:** App Store review accepts the "no app inventory" limitation in copy.

## Phase 3 — optional cloud + Artex

- AndroidQF collection import (USB) to close the four shell-only gaps: intrusion
  logs, install history, Cellebrite traces, SMS links. Same evidence model, no root.
- Opt-in upload of the evidence bundle to an Artex task.
- Artex returns explanation, cross-scanner checks, and a plain-language action plan.
- Companion MCP tool source for richer probes (ports, DNS, TLS) with explicit
  per-probe consent.
- Multi-device view for one owner.

**Gate:** hosted mode publishes its modified Artex source (AGPL-3.0).

## Phase 4 — continuous monitoring

- Weekly diff: "3 new things since last week" instead of a full report.
- Change-triggered scan (new install, new admin, new CA).
- Breach exposure (opt-in email check).

**Gate:** notification budget under 1/week on a stable device.

## Explicitly parked

- Root/jailbreak inspection modes.
- Malware sandbox / APK dynamic analysis.
- Family multi-user dashboards.
- Windows/macOS agents (same engine, different collectors — large surface).

## Pre-submission checklist

- [ ] Play data-safety form matches the code's actual collection (currently: none).
- [ ] `QUERY_ALL_PACKAGES` declaration justification.
- [ ] Foreground-service dataSync declaration.
- [ ] Privacy policy URL, in-app privacy screen, and store listing copy.
- [ ] No "antivirus" claim without an engine behind it.
