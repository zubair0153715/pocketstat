# PocketStat v1.1 — Build Notes

**App:** PocketStat — original Android statistics app (clean-room, NOT affiliated with IBM/SPSS).
**Package:** `com.pocketstat.app` · **Min SDK:** 24 (Android 7.0) · **Target/compile SDK:** 34
**Language:** Kotlin 1.9.24 · **Fully offline** — no INTERNET permission declared.
**Current:** v1.1 (versionCode 2) — `PocketStat-v1.1-debug.apk` (734 KB, 2026-10-18)

## What was built (v1.1 — 2026-10-18)
v1.0 (descriptives, independent t-test, histogram, boxplot) plus:
1. **One-way ANOVA** — F(df1, df2), p; groups picked by ticking 2+ columns (checkboxes).
2. **Chi-square test of independence** — χ²(df), p; contingency table built from two
   categorical columns (paired rows, categories = distinct values in order of appearance).
3. **Pearson correlation** — r, t(df), p; paired numeric rows (both cells numeric).
4. **Simple linear regression** — slope (SE), intercept, R², equation, p for slope;
   paired numeric rows. Plain-English interpretations for all four.

## Math verification (v1.1)
New engine code (`Stats.kt`: `regularizedGammaP`, `chiSquareCdf/Sf`, `fCdf/Sf`,
`tSf`, `anovaOneWay`, `chiSquareIndependence`, `pearsonCorrelation`,
`linearRegression`) verified against **scipy** on fixed datasets — ALL MATCH to 12+ digits:
- ANOVA: F=9.264705882352942, p=0.002398777329393156 ✓ · 2-group ANOVA: F=t², p identical to t-test ✓
- Chi-square: χ²=6.666666666666667, p=0.035673993347252395 ✓
- Correlation: r=0.9997890977591745, p=8.65352229979888e-15 ✓
- Regression: slope=1.9915151515151517, R²=0.9995782399981041, p=8.653522299800232e-15 ✓
- CDF sanity: F(1;5,10)=0.5348805734622235, χ²(3.841;1)=0.9499863162360456, t(2.228;10)=0.9749941140914454 ✓
- Reference script: `verify_v11.py` → `verify_v11_ref.json`; Kotlin JVM run: `TestV11.kt`.

**Precision fix (v1.1):** extreme-tail p-values (p < 1e-12) were losing ~4 digits to
catastrophic cancellation in `1.0 - tCdf(...)` (double can't hold 1-tiny). Fixed by
computing upper tails directly: `tSf` = 0.5·I_x (no subtraction), `fSf` via the beta
complement identity, `chiSquareSf` via the continued-fraction Q. After the fix the
1e-15 p-values match scipy to 13 digits. Same fix applied to the v1.0 t-test path.

## v1.0 notes (kept)

**App:** PocketStat — original Android statistics app (clean-room, NOT affiliated with IBM/SPSS).
**Package:** `com.pocketstat.app` · **Min SDK:** 24 (Android 7.0) · **Target/compile SDK:** 34
**Language:** Kotlin 1.9.24 · **Fully offline** — no INTERNET permission declared.

## What was built (MVP)
1. **Data tab** — spreadsheet-like grid: add/remove rows & columns, tap cell to edit,
   tap column header to rename/delete, tap row number to delete row. CSV import
   (Storage Access Framework, no storage permission needed). Sample dataset preloaded
   (Group A vs Group B exam scores, n=15 each).
2. **Analyze tab** — descriptive statistics per column (n, mean, median, mode, sample SD,
   variance, min, max; non-numeric cells skipped and counted) and an independent-samples
   (Student's) t-test between two columns with t, df, two-tailed p and a plain-English
   interpretation line. Copy-to-clipboard + share-as-text of results.
3. **Charts tab** — histogram (Sturges' rule bins) + boxplot (min/Q1/median/Q3/max),
   hand-drawn on Canvas, no chart library.

## Math verification
The stats engine (`Stats.kt`) was verified against **scipy 1.11.4** before porting to Kotlin:
- Dataset 1 (10+10 values): mine t=6.832313, p=0.00000215 — scipy identical → MATCH
- Dataset 2 (random 40+35 values): mine t=-4.126201, p=0.00009667 — scipy identical → MATCH
- t-CDF sanity: t_cdf(0,10)=0.5; t_cdf(2.228,10)≈0.975 (textbook critical value) ✓
- The t-distribution CDF uses the regularized incomplete beta function
  (continued-fraction evaluation) + ln-Gamma (Lanczos) — standard textbook numerical
  methods, implemented from scratch for this app.

## How to rebuild
Gradle does NOT work in this sandbox (its daemon needs localhost TCP data transfer,
which is blocked). Use the manual pipeline instead:

```bash
~/workspace/pocketstat/build-apk.sh
```

Pipeline: `aapt2 compile` → `aapt2 link` → `javac` (R.java) → `kotlinc`
(compiler-embeddable jar, no daemon) → `d8` → zipalign → `apksigner`.
Prerequisites (one-time, already on this VM):
- Android SDK at `~/Android` (platforms/android-34, build-tools/34.0.0)
- Kotlin compiler jars at `~/kotlin-compiler/`
- JDK 21 (`javac`, `keytool`)

Output: `~/workspace/pocketstat/PocketStat-v1.0-debug.apk` (debug-signed).

## Known limitations (v1.1)
- t-test is Student's (equal-variance) only; no Welch variant yet.
- No paired t-test, two-way/repeated-measures ANOVA, nonparametrics (Mann-Whitney,
  Kruskal-Wallis, Wilcoxon), multiple regression, factor analysis — planned v1.2+.
- Chi-square: no Yates correction, no Fisher exact for small tables yet.
- No data persistence across app restarts (in-memory only) — save/load planned.
- English UI only (Urdu toggle planned as differentiator).
- No automated UI tests on this VM (no emulator); verify on a real device.
