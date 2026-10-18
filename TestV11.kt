import com.pocketstat.app.Stats

fun main() {
    val g1 = listOf(6.0, 8.0, 4.0, 5.0, 3.0, 4.0)
    val g2 = listOf(8.0, 12.0, 9.0, 11.0, 6.0, 8.0)
    val g3 = listOf(13.0, 9.0, 11.0, 8.0, 7.0, 12.0)
    val an = Stats.anovaOneWay(listOf(g1, g2, g3))!!
    println("anova F=${an.f} df1=${an.df1} df2=${an.df2} p=${an.pValue}")

    val table = listOf(listOf(10, 10, 20), listOf(20, 10, 10))
    val cs = Stats.chiSquareIndependence(table)!!
    println("chisq chi2=${cs.chi2} df=${cs.df} p=${cs.pValue}")

    val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
    val y = listOf(2.1, 4.0, 6.2, 7.9, 10.1, 12.0, 14.2, 15.8, 18.1, 20.0)
    val co = Stats.pearsonCorrelation(x, y)!!
    println("corr r=${co.r} t=${co.t} df=${co.df} p=${co.pValue}")

    val rg = Stats.linearRegression(x, y)!!
    println("regress slope=${rg.slope} intercept=${rg.intercept} r2=${rg.r2} se=${rg.seSlope} p=${rg.pSlope}")

    // edge: 2-group ANOVA vs t-test^2
    val a = listOf(78.0, 82.0, 85.0, 79.0, 88.0, 81.0, 76.0, 84.0, 80.0, 83.0)
    val b = listOf(72.0, 75.0, 70.0, 78.0, 74.0, 71.0, 76.0, 73.0, 69.0, 77.0)
    val an2 = Stats.anovaOneWay(listOf(a, b))!!
    val tt = Stats.tTestIndependent(a, b)!!
    println("edge F=${an2.f} t^2=${tt.t * tt.t} p_anova=${an2.pValue} p_ttest=${tt.pValue}")

    // CDF sanity checks
    println("fCdf(1,5,10)=${Stats.fCdf(1.0, 5, 10)} (expect ~0.494)")
    println("chiSqCdf(3.841,1)=${Stats.chiSquareCdf(3.841, 1)} (expect ~0.95)")
    println("tCdf(2.228,10)=${Stats.tCdf(2.228, 10)} (expect ~0.975)")
}
