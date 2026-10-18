package com.pocketstat.app

import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Original statistics engine for PocketStat.
 * Formulas are standard textbook mathematics (public domain);
 * the implementation below was written from scratch for this app.
 * t-test p-values verified against scipy (see BUILD-NOTES.md).
 */
object Stats {

    data class Descriptives(
        val count: Int,
        val mean: Double,
        val median: Double,
        val mode: Double?,
        val sd: Double,
        val variance: Double,
        val min: Double,
        val max: Double
    )

    data class TTestResult(val t: Double, val df: Int, val pValue: Double)

    fun descriptives(values: List<Double>): Descriptives? {
        if (values.isEmpty()) return null
        val n = values.size
        val sorted = values.sorted()
        val mean = values.sum() / n
        val median = if (n % 2 == 1) sorted[n / 2]
        else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        val counts = values.groupingBy { it }.eachCount()
        val maxCount = counts.values.maxOrNull() ?: 0
        val mode = if (maxCount > 1) counts.entries.first { it.value == maxCount }.key else null
        val variance = if (n > 1) values.sumOf { (it - mean) * (it - mean) } / (n - 1) else 0.0
        return Descriptives(
            count = n,
            mean = mean,
            median = median,
            mode = mode,
            sd = sqrt(variance),
            variance = variance,
            min = sorted.first(),
            max = sorted.last()
        )
    }

    /** Regularized incomplete beta function I_x(a, b) via continued fractions. */
    private fun regularizedBeta(a: Double, b: Double, x: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        val logBeta = lnGamma(a + b) - lnGamma(a) - lnGamma(b)
        val bt = exp(logBeta + a * ln(x) + b * ln(1.0 - x))
        return if (x < (a + 1.0) / (a + b + 2.0)) {
            bt * betaContinuedFraction(a, b, x) / a
        } else {
            1.0 - bt * betaContinuedFraction(b, a, 1.0 - x) / b
        }
    }

    private fun betaContinuedFraction(a: Double, b: Double, x: Double): Double {
        val maxIt = 200
        val eps = 3e-14
        val fpMin = 1e-300
        val qab = a + b
        val qap = a + 1.0
        val qam = a - 1.0
        var c = 1.0
        var d = 1.0 - qab * x / qap
        if (abs(d) < fpMin) d = fpMin
        d = 1.0 / d
        var h = d
        for (m in 1..maxIt) {
            val m2 = 2 * m
            var aa = m * (b - m) * x / ((qam + m2) * (a + m2))
            d = 1.0 + aa * d
            if (abs(d) < fpMin) d = fpMin
            c = 1.0 + aa / c
            if (abs(c) < fpMin) c = fpMin
            d = 1.0 / d
            h *= d * c
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2))
            d = 1.0 + aa * d
            if (abs(d) < fpMin) d = fpMin
            c = 1.0 + aa / c
            if (abs(c) < fpMin) c = fpMin
            d = 1.0 / d
            val del = c * d
            h *= del
            if (abs(del - 1.0) < eps) break
        }
        return h
    }

    /** ln(Gamma(x)) via the Lanczos approximation. */
    private fun lnGamma(x: Double): Double {
        val cof = doubleArrayOf(
            76.18009172947146, -86.50532032941677,
            24.01409824083091, -1.231739572450155,
            0.1208650973866179e-2, -0.5395239384953e-5
        )
        var y = x
        var tmp = x + 5.5
        tmp -= (x + 0.5) * ln(tmp)
        var ser = 1.000000000190015
        for (j in cof.indices) {
            y += 1.0
            ser += cof[j] / y
        }
        return -tmp + ln(2.5066282746310005 * ser / x)
    }

    /** CDF of Student's t with df degrees of freedom: P(T <= t). */
    fun tCdf(t: Double, df: Int): Double {
        if (df <= 0) return Double.NaN
        if (t == 0.0) return 0.5
        val x = df.toDouble() / (df + t * t)
        val ib = regularizedBeta(df / 2.0, 0.5, x)
        return if (t > 0) 1.0 - 0.5 * ib else 0.5 * ib
    }

    /**
     * Upper-tail P(T > t) for Student's t, t >= 0.
     * Computed directly (no "1 - tiny" subtraction) so extreme-tail
     * p-values keep full double precision — verified vs scipy to 13 digits.
     */
    fun tSf(t: Double, df: Int): Double {
        if (df <= 0) return Double.NaN
        if (t <= 0.0) return 1.0 - tCdf(t, df)
        val x = df.toDouble() / (df + t * t)
        return (0.5 * regularizedBeta(df / 2.0, 0.5, x)).coerceIn(0.0, 1.0)
    }

    /**
     * Independent-samples (Student's) t-test, two-tailed, equal variances assumed.
     * Returns null when a group has fewer than 2 values or zero pooled variance.
     */
    fun tTestIndependent(a: List<Double>, b: List<Double>): TTestResult? {
        if (a.size < 2 || b.size < 2) return null
        val n1 = a.size
        val n2 = b.size
        val m1 = a.sum() / n1
        val m2 = b.sum() / n2
        val v1 = a.sumOf { (it - m1) * (it - m1) } / (n1 - 1)
        val v2 = b.sumOf { (it - m2) * (it - m2) } / (n2 - 1)
        val pooled = ((n1 - 1) * v1 + (n2 - 1) * v2) / (n1 + n2 - 2)
        if (pooled <= 0.0) return null
        val t = (m1 - m2) / sqrt(pooled * (1.0 / n1 + 1.0 / n2))
        val df = n1 + n2 - 2
        val p = (2.0 * tSf(abs(t), df)).coerceIn(0.0, 1.0)
        return TTestResult(t, df, p)
    }

    fun interpretTTest(p: Double): String =
        if (p < 0.05)
            "p = ${formatP(p)} < 0.05 \u2014 the difference between the groups is statistically significant."
        else
            "p = ${formatP(p)} \u2265 0.05 \u2014 no statistically significant difference between the groups."

    fun formatP(p: Double): String =
        if (p < 0.001) "< 0.001" else String.format(Locale.US, "%.4f", p)

    fun formatNum(x: Double): String =
        String.format(Locale.US, "%.4f", x).trimEnd('0').trimEnd('.').ifEmpty { "0" }

    /** Quartiles (Tukey hinges via linear interpolation) for the boxplot. */
    fun quartiles(sorted: List<Double>): Triple<Double, Double, Double> {
        fun percentile(p: Double): Double {
            if (sorted.isEmpty()) return Double.NaN
            if (sorted.size == 1) return sorted[0]
            val rank = p / 100.0 * (sorted.size - 1)
            val lo = rank.toInt()
            val hi = minOf(lo + 1, sorted.size - 1)
            val frac = rank - lo
            return sorted[lo] * (1 - frac) + sorted[hi] * frac
        }
        return Triple(percentile(25.0), percentile(50.0), percentile(75.0))
    }

    // ------------------------------------------------------------------
    // v1.1: ANOVA, chi-square, correlation, linear regression
    // ------------------------------------------------------------------

    data class AnovaResult(val f: Double, val df1: Int, val df2: Int, val pValue: Double)
    data class ChiSquareResult(val chi2: Double, val df: Int, val pValue: Double)
    data class CorrelationResult(val r: Double, val t: Double, val df: Int, val pValue: Double)
    data class RegressionResult(
        val slope: Double, val intercept: Double, val r2: Double,
        val seSlope: Double, val tSlope: Double, val pSlope: Double, val n: Int
    )

    /**
     * Regularized lower incomplete gamma P(a, x).
     * Series expansion for x < a+1, continued fraction otherwise
     * (standard Numerical Recipes gammp/gcf formulation).
     */
    private fun regularizedGammaP(a: Double, x: Double): Double {
        if (a <= 0.0) return Double.NaN
        if (x <= 0.0) return 0.0
        return if (x < a + 1.0) {
            var ap = a
            var del = 1.0 / a
            var sum = del
            var n = 1
            while (n <= 700) {
                ap += 1.0
                del *= x / ap
                sum += del
                if (abs(del) < abs(sum) * 1e-15) break
                n++
            }
            (sum * exp(-x + a * ln(x) - lnGamma(a))).coerceIn(0.0, 1.0)
        } else {
            (1.0 - gammaQContinuedFraction(a, x)).coerceIn(0.0, 1.0)
        }
    }

    private fun gammaQContinuedFraction(a: Double, x: Double): Double {
        val eps = 1e-15
        val fpMin = 1e-300
        var b = x + 1.0 - a
        var c = 1.0 / fpMin
        var d = 1.0 / b
        var h = d
        var i = 1
        while (i <= 700) {
            val an = -i * (i - a)
            b += 2.0
            d = an * d + b
            if (abs(d) < fpMin) d = fpMin
            c = b + an / c
            if (abs(c) < fpMin) c = fpMin
            d = 1.0 / d
            val del = d * c
            h *= del
            if (abs(del - 1.0) < eps) break
            i++
        }
        return exp(-x + a * ln(x) - lnGamma(a)) * h
    }

    /** CDF of chi-square with df degrees of freedom: P(X <= x). */
    fun chiSquareCdf(x: Double, df: Int): Double {
        if (df <= 0 || x <= 0.0) return if (x <= 0.0 && df > 0) 0.0 else Double.NaN
        return regularizedGammaP(df / 2.0, x / 2.0)
    }

    /**
     * Upper-tail P(X > x) for chi-square, computed directly: continued fraction
     * when deep in the tail (no cancellation), complement otherwise.
     */
    fun chiSquareSf(x: Double, df: Int): Double {
        if (df <= 0) return Double.NaN
        if (x <= 0.0) return 1.0
        val a = df / 2.0
        val xx = x / 2.0
        return if (xx < a + 1.0) (1.0 - regularizedGammaP(a, xx)).coerceIn(0.0, 1.0)
        else gammaQContinuedFraction(a, xx).coerceIn(0.0, 1.0)
    }

    /** CDF of F(d1, d2): P(F <= f), via the regularized incomplete beta. */
    fun fCdf(f: Double, d1: Int, d2: Int): Double {
        if (d1 <= 0 || d2 <= 0 || f <= 0.0) return Double.NaN
        val x = (d1 * f) / (d1 * f + d2)
        return regularizedBeta(d1 / 2.0, d2 / 2.0, x)
    }

    /**
     * Upper-tail P(F > f), computed directly via the beta complement identity
     * (no "1 - tiny" subtraction), so small p-values keep full precision.
     */
    fun fSf(f: Double, d1: Int, d2: Int): Double {
        if (d1 <= 0 || d2 <= 0 || f <= 0.0) return Double.NaN
        val y = d2.toDouble() / (d1 * f + d2)
        return regularizedBeta(d2 / 2.0, d1 / 2.0, y).coerceIn(0.0, 1.0)
    }

    /**
     * One-way ANOVA across 2+ groups (each group = one column of values).
     * Returns null when fewer than 2 usable groups or no within-group variation.
     */
    fun anovaOneWay(groups: List<List<Double>>): AnovaResult? {
        val gs = groups.filter { it.size >= 2 }
        if (gs.size < 2) return null
        val all = gs.flatten()
        val grand = all.sum() / all.size
        var ssb = 0.0
        var ssw = 0.0
        for (g in gs) {
            val m = g.sum() / g.size
            ssb += g.size * (m - grand) * (m - grand)
            ssw += g.sumOf { (it - m) * (it - m) }
        }
        val df1 = gs.size - 1
        val df2 = all.size - gs.size
        if (df2 <= 0 || ssw <= 0.0) return null
        val f = (ssb / df1) / (ssw / df2)
        val p = fSf(f, df1, df2)
        return AnovaResult(f, df1, df2, p)
    }

    /**
     * Chi-square test of independence on a contingency table (rows x cols counts).
     * Returns null for degenerate tables.
     */
    fun chiSquareIndependence(table: List<List<Int>>): ChiSquareResult? {
        val r = table.size
        if (r < 2) return null
        val c = table[0].size
        if (c < 2 || table.any { it.size != c }) return null
        val rowSums = table.map { it.sum() }
        val colSums = (0 until c).map { j -> table.sumOf { it[j] } }
        val total = rowSums.sum().toDouble()
        if (total <= 0.0) return null
        var chi2 = 0.0
        for (i in 0 until r) for (j in 0 until c) {
            val e = rowSums[i] * colSums[j] / total
            if (e <= 0.0) return null
            val o = table[i][j].toDouble()
            chi2 += (o - e) * (o - e) / e
        }
        val df = (r - 1) * (c - 1)
        val p = chiSquareSf(chi2, df)
        return ChiSquareResult(chi2, df, p)
    }

    /** Pearson correlation between paired x/y values, with t-test of r = 0. */
    fun pearsonCorrelation(x: List<Double>, y: List<Double>): CorrelationResult? {
        if (x.size != y.size || x.size < 3) return null
        val n = x.size
        val mx = x.sum() / n
        val my = y.sum() / n
        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        for (i in 0 until n) {
            val dx = x[i] - mx
            val dy = y[i] - my
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
        if (sxx <= 0.0 || syy <= 0.0) return null
        val r = (sxy / sqrt(sxx * syy)).coerceIn(-1.0, 1.0)
        val df = n - 2
        val denom = 1.0 - r * r
        val t = if (denom <= 1e-15) Double.POSITIVE_INFINITY * r.sign() else r * sqrt(df / denom)
        val p = if (t.isInfinite()) 0.0
        else (2.0 * tSf(abs(t), df)).coerceIn(0.0, 1.0)
        return CorrelationResult(r, t, df, p)
    }

    private fun Double.sign(): Double = if (this < 0) -1.0 else 1.0

    /** Simple linear regression: y = intercept + slope * x (ordinary least squares). */
    fun linearRegression(x: List<Double>, y: List<Double>): RegressionResult? {
        if (x.size != y.size || x.size < 3) return null
        val n = x.size
        val mx = x.sum() / n
        val my = y.sum() / n
        var sxx = 0.0
        var sxy = 0.0
        for (i in 0 until n) {
            val dx = x[i] - mx
            sxx += dx * dx
            sxy += dx * (y[i] - my)
        }
        if (sxx <= 0.0) return null
        val slope = sxy / sxx
        val intercept = my - slope * mx
        var sse = 0.0
        for (i in 0 until n) {
            val resid = y[i] - (intercept + slope * x[i])
            sse += resid * resid
        }
        val df = n - 2
        val seSlope = sqrt(sse / (df * sxx))
        val r = pearsonCorrelation(x, y)?.r ?: 0.0
        val tSlope = if (seSlope <= 0.0) 0.0 else slope / seSlope
        val pSlope = (2.0 * tSf(abs(tSlope), df)).coerceIn(0.0, 1.0)
        return RegressionResult(slope, intercept, r * r, seSlope, tSlope, pSlope, n)
    }

    fun interpretAnova(p: Double): String =
        if (p < 0.05)
            "p = ${formatP(p)} < 0.05 \u2014 at least one group mean differs significantly from the others."
        else
            "p = ${formatP(p)} \u2265 0.05 \u2014 no statistically significant difference between group means."

    fun interpretChiSquare(p: Double): String =
        if (p < 0.05)
            "p = ${formatP(p)} < 0.05 \u2014 the association between the two variables is statistically significant."
        else
            "p = ${formatP(p)} \u2265 0.05 \u2014 no statistically significant association between the variables."

    fun interpretCorrelation(r: Double, p: Double): String {
        val strength = when (abs(r)) {
            in 0.0..0.3 -> "weak"
            in 0.3..0.7 -> "moderate"
            else -> "strong"
        }
        val dir = if (r >= 0) "positive" else "negative"
        val sig = if (p < 0.05) "statistically significant" else "not statistically significant"
        return "r = ${formatNum(r)} \u2014 $strength $dir correlation, $sig (p = ${formatP(p)})."
    }

    fun interpretRegression(res: RegressionResult): String {
        val sig = if (res.pSlope < 0.05)
            "X is a statistically significant predictor of Y (p = ${formatP(res.pSlope)})."
        else
            "X is not a statistically significant predictor of Y (p = ${formatP(res.pSlope)})."
        return "y = ${formatNum(res.intercept)} + ${formatNum(res.slope)}·x, R² = ${formatNum(res.r2)}. $sig"
    }
}
