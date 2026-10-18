# PocketStat

Original Android statistics app — clean-room implementation, **not affiliated
with IBM/SPSS**. Fully offline, zero permissions.

**Package:** `com.pocketstat.app` · **Min SDK:** 24 · **Kotlin**

## Features (v1.1)

- Descriptives (mean, median, SD, quartiles), hand-drawn histogram & boxplot
- Independent t-test, one-way ANOVA, chi-square independence
- Pearson correlation, simple linear regression
- Plain-English interpretations for every test
- CSV import, sample dataset, copy/share results

All math verified against scipy to 12+ digits (see `verify_v11.py`).

## Build

The Gradle daemon is broken in sandboxed environments — use the manual pipeline:

```bash
./build-apk.sh
```

Needs: JDK 21, Android SDK (build-tools, platform-34). See `BUILD-NOTES.md`.

Debug APKs are build artifacts and are not committed here.
