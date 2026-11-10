# Bundled spyware indicators

## Why

Fingerprint rules ("this package name looks like mSpy") only catch products someone
thought to add by hand. Published indicators catch **named samples** — the ones
researchers documented after real attacks. That is a different, stronger class of
evidence, and it is the part of [Zero Breach Toolkit](https://github.com/interplains/Zero-Breach-Toolkit)
an on-device app can honestly reproduce.

## What is bundled

`app/src/main/assets/iocs/`:

| File | Content | SHA-256 |
|---|---|---|
| `curated_targeted.txt` | 1,027 sample hashes + 17 Android package names, 2026-11-02 | `ec4f889d…30f533` |
| `curated_mercenary.txt` | 10 sample hashes (plus domains/IPs kept for later), 2026-11-02 | `34ccca32…aee9d1` |

1,054 values are matched: 1,037 digests (SHA-256/SHA-1) and 17 package names.
Domains, IPs, file paths and process names are parsed for their source/family
metadata but not yet matched — an app cannot see traffic or filesystem paths.

## Matching semantics

| Artifact | How | Severity | Why |
|---|---|---|---|
| **APK file hash** | SHA-256/SHA-1 of every APK file of the app (base + splits) | HIGH (CRITICAL with a certificate match) | Byte-identical to a published sample |
| **Signing certificate** | SHA-256/SHA-1 of every signer certificate | HIGH (CRITICAL with a file match) | Repackaged with the attacker's key |
| **Package name** | Exact, case-folded | MEDIUM, confidence LIKELY | A real app's name can be reused by the imitator |

Two artifact matches on one app escalate to CRITICAL/CONFIRMED. The finding names
the family and the source tag; the raw hashes stay in `evidence`.

Only non-system apps are considered. Certificates are read for all of them; APK
files are hashed only for apps that could plausibly match (published package,
sideloaded, or installed in the last 180 days), under a 400 MiB / 45 s budget.
Whatever the budget skips is listed as a gap, with the artifacts that *were*
checked named explicitly.

## Licence and attribution

The curated selection is **CC0 1.0** (upstream `iocs/LICENSE.md`); attribution is
not required but the publishers are credited because the values are theirs.

Values originate from published research by ESET, Cisco Talos, SentinelLabs, Meta,
Volexity, Palo Alto Unit 42, Lookout, Amnesty International's Security Lab, the
Citizen Lab and the MVT project. Each TSV line keeps its `source-tag`; the file
headers carry full citations, per-source licence notices and the as-of date.

Upstream code is Apache-2.0; we copied no code, only data.

## Staleness

This is the honest weak point: the corpus is a snapshot. Spyware is rebuilt per
target and traces expire in weeks. The report prints the as-of date and the count.
Refreshing is a release task, not a runtime download.

Update procedure:

```bash
curl -sSL -o app/src/main/assets/iocs/curated_targeted.txt \
  https://raw.githubusercontent.com/interplains/Zero-Breach-Toolkit/main/iocs/curated_targeted.txt
curl -sSL -o app/src/main/assets/iocs/curated_mercenary.txt \
  https://raw.githubusercontent.com/interplains/Zero-Breach-Toolkit/main/iocs/curated_mercenary.txt
shasum -a 256 app/src/main/assets/iocs/*.txt   # record in this file
```

Then bump the `as-of` note here, run the tests, and check that the counts on a
report changed.

## What still needs a USB collection

The upstream Android checks that need `dumpsys`/`sysfs` are declared as permanent
gaps in every report:

- DNS lookups, connection attempts, process starts (intrusion logs)
- install history, including removed apps
- Cellebrite unlock traces
- SMS/MMS links (needs the SMS permission)

An AndroidQF import (roadmap) would close all four without root.

## Interpreting a hit

A hit is "look closer", not "you are infected". Keep the app, share the report,
and involve a helpline if the phone handles work, activism or journalism. Deleting
the app first destroys the evidence.
