package com.pocketstat.app

import android.app.Fragment
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/**
 * Analyze tab: descriptive statistics, independent-samples t-test,
 * one-way ANOVA, chi-square test of independence, Pearson correlation
 * and simple linear regression — with copy / share of the results as text.
 */
class AnalyzeFragment : Fragment() {

    private lateinit var spinnerDesc: Spinner
    private lateinit var spinnerG1: Spinner
    private lateinit var spinnerG2: Spinner
    private lateinit var txtDesc: TextView
    private lateinit var txtTTest: TextView

    private lateinit var anovaGroupBox: LinearLayout
    private lateinit var txtAnova: TextView
    private lateinit var spinnerChiRow: Spinner
    private lateinit var spinnerChiCol: Spinner
    private lateinit var txtChiSq: TextView
    private lateinit var spinnerCorrX: Spinner
    private lateinit var spinnerCorrY: Spinner
    private lateinit var txtCorr: TextView
    private lateinit var spinnerRegX: Spinner
    private lateinit var spinnerRegY: Spinner
    private lateinit var txtRegress: TextView

    private var lastResultsText: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_analyze, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        spinnerDesc = view.findViewById(R.id.spinner_desc_col)
        spinnerG1 = view.findViewById(R.id.spinner_group1)
        spinnerG2 = view.findViewById(R.id.spinner_group2)
        txtDesc = view.findViewById(R.id.txt_desc_results)
        txtTTest = view.findViewById(R.id.txt_ttest_results)

        anovaGroupBox = view.findViewById(R.id.anova_groups)
        txtAnova = view.findViewById(R.id.txt_anova_results)
        spinnerChiRow = view.findViewById(R.id.spinner_chi_row)
        spinnerChiCol = view.findViewById(R.id.spinner_chi_col)
        txtChiSq = view.findViewById(R.id.txt_chisq_results)
        spinnerCorrX = view.findViewById(R.id.spinner_corr_x)
        spinnerCorrY = view.findViewById(R.id.spinner_corr_y)
        txtCorr = view.findViewById(R.id.txt_corr_results)
        spinnerRegX = view.findViewById(R.id.spinner_reg_x)
        spinnerRegY = view.findViewById(R.id.spinner_reg_y)
        txtRegress = view.findViewById(R.id.txt_regress_results)

        refreshSpinners()

        view.findViewById<Button>(R.id.btn_descriptives).setOnClickListener { runDescriptives() }
        view.findViewById<Button>(R.id.btn_ttest).setOnClickListener { runTTest() }
        view.findViewById<Button>(R.id.btn_anova).setOnClickListener { runAnova() }
        view.findViewById<Button>(R.id.btn_chisq).setOnClickListener { runChiSquare() }
        view.findViewById<Button>(R.id.btn_corr).setOnClickListener { runCorrelation() }
        view.findViewById<Button>(R.id.btn_regress).setOnClickListener { runRegression() }
        view.findViewById<Button>(R.id.btn_copy).setOnClickListener { copyResults() }
        view.findViewById<Button>(R.id.btn_share).setOnClickListener { shareResults() }

        DataStore.onDataChanged = { if (isAdded) refreshSpinners() }
    }

    override fun onDestroyView() {
        if (DataStore.onDataChanged != null) DataStore.onDataChanged = null
        super.onDestroyView()
    }

    private fun refreshSpinners() {
        val ctx = activity ?: return
        val names = DataStore.columnNames.toList()
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDesc.adapter = adapter
        spinnerG1.adapter = adapter
        spinnerG2.adapter = adapter
        spinnerChiRow.adapter = adapter
        spinnerChiCol.adapter = adapter
        spinnerCorrX.adapter = adapter
        spinnerCorrY.adapter = adapter
        spinnerRegX.adapter = adapter
        spinnerRegY.adapter = adapter
        if (names.size >= 2) {
            spinnerG2.setSelection(1)
            spinnerChiCol.setSelection(1)
            spinnerCorrY.setSelection(1)
            spinnerRegY.setSelection(1)
        }
        // Rebuild ANOVA group checkboxes, preserving previous ticks by column name.
        val ticked = mutableSetOf<String>()
        for (i in 0 until anovaGroupBox.childCount) {
            val cb = anovaGroupBox.getChildAt(i) as? CheckBox
            if (cb?.isChecked == true) ticked.add(cb.tag as String)
        }
        anovaGroupBox.removeAllViews()
        for (name in names) {
            val cb = CheckBox(ctx)
            cb.text = name
            cb.tag = name
            cb.isChecked = name in ticked
            anovaGroupBox.addView(cb)
        }
    }

    private fun runDescriptives() {
        val idx = spinnerDesc.selectedItemPosition
        if (idx < 0 || idx >= DataStore.columnCount()) {
            toast("No columns — add data first")
            return
        }
        val values = DataStore.numericColumn(idx)
        val skipped = DataStore.skippedCount(idx)
        val d = Stats.descriptives(values)
        if (d == null) {
            txtDesc.text = "Column \"${DataStore.columnNames[idx]}\" has no numeric values."
            lastResultsText = txtDesc.text.toString()
            return
        }
        val sb = StringBuilder()
        sb.appendLine("Descriptives: ${DataStore.columnNames[idx]}")
        sb.appendLine("Count (n)   = ${d.count}")
        sb.appendLine("Mean        = ${Stats.formatNum(d.mean)}")
        sb.appendLine("Median      = ${Stats.formatNum(d.median)}")
        sb.appendLine("Mode        = ${d.mode?.let { Stats.formatNum(it) } ?: "— (no repeat value)"}")
        sb.appendLine("SD (sample) = ${Stats.formatNum(d.sd)}")
        sb.appendLine("Variance    = ${Stats.formatNum(d.variance)}")
        sb.appendLine("Min         = ${Stats.formatNum(d.min)}")
        sb.appendLine("Max         = ${Stats.formatNum(d.max)}")
        if (skipped > 0) sb.appendLine("Note: $skipped non-numeric cell(s) skipped.")
        txtDesc.text = sb.toString().trimEnd()
        lastResultsText = txtDesc.text.toString()
    }

    private fun runTTest() {
        val i1 = spinnerG1.selectedItemPosition
        val i2 = spinnerG2.selectedItemPosition
        if (i1 < 0 || i2 < 0 || i1 >= DataStore.columnCount() || i2 >= DataStore.columnCount()) {
            toast("No columns — add data first")
            return
        }
        if (i1 == i2) {
            toast("Pick two different columns")
            return
        }
        val a = DataStore.numericColumn(i1)
        val b = DataStore.numericColumn(i2)
        val r = Stats.tTestIndependent(a, b)
        if (r == null) {
            txtTTest.text = "Each group needs at least 2 numeric values with some variation."
            lastResultsText = txtTTest.text.toString()
            return
        }
        val n1 = DataStore.columnNames[i1]
        val n2 = DataStore.columnNames[i2]
        val sb = StringBuilder()
        sb.appendLine("Independent-samples t-test")
        sb.appendLine("$n1 (n=${a.size}) vs $n2 (n=${b.size})")
        sb.appendLine("t(${r.df}) = ${Stats.formatNum(r.t)}")
        sb.appendLine("p (two-tailed) = ${Stats.formatP(r.pValue)}")
        sb.appendLine()
        sb.append(Stats.interpretTTest(r.pValue))
        txtTTest.text = sb.toString().trimEnd()
        lastResultsText = txtTTest.text.toString()
    }

    private fun runAnova() {
        val picked = mutableListOf<Int>()
        for (i in 0 until anovaGroupBox.childCount) {
            val cb = anovaGroupBox.getChildAt(i) as? CheckBox ?: continue
            if (cb.isChecked) {
                val idx = DataStore.columnNames.indexOf(cb.tag as String)
                if (idx >= 0) picked.add(idx)
            }
        }
        if (picked.size < 2) {
            txtAnova.text = "Tick at least 2 columns as groups."
            lastResultsText = txtAnova.text.toString()
            return
        }
        val groups = picked.map { DataStore.numericColumn(it) }
        val r = Stats.anovaOneWay(groups)
        if (r == null) {
            txtAnova.text = "Each group needs at least 2 numeric values with some variation."
            lastResultsText = txtAnova.text.toString()
            return
        }
        val sb = StringBuilder()
        sb.appendLine("One-way ANOVA")
        sb.appendLine("Groups: " + picked.joinToString(", ") { DataStore.columnNames[it] })
        sb.appendLine("F(${r.df1}, ${r.df2}) = ${Stats.formatNum(r.f)}")
        sb.appendLine("p = ${Stats.formatP(r.pValue)}")
        sb.appendLine()
        sb.append(Stats.interpretAnova(r.pValue))
        txtAnova.text = sb.toString().trimEnd()
        lastResultsText = txtAnova.text.toString()
    }

    private fun runChiSquare() {
        val i = spinnerChiRow.selectedItemPosition
        val j = spinnerChiCol.selectedItemPosition
        if (i < 0 || j < 0 || i >= DataStore.columnCount() || j >= DataStore.columnCount()) {
            toast("No columns — add data first")
            return
        }
        if (i == j) {
            toast("Pick two different columns")
            return
        }
        val (xs, ys) = DataStore.pairedStringColumns(i, j)
        if (xs.size < 5) {
            txtChiSq.text = "Need at least 5 rows with values in both columns."
            lastResultsText = txtChiSq.text.toString()
            return
        }
        val rowCats = xs.distinct()
        val colCats = ys.distinct()
        val table = rowCats.map { rc ->
            colCats.map { cc -> xs.indices.count { k -> xs[k] == rc && ys[k] == cc } }
        }
        val r = Stats.chiSquareIndependence(table)
        if (r == null) {
            txtChiSq.text = "Table is degenerate (a row or column is empty)."
            lastResultsText = txtChiSq.text.toString()
            return
        }
        val sb = StringBuilder()
        sb.appendLine("Chi-square test of independence")
        sb.appendLine("${DataStore.columnNames[i]} (${rowCats.size} categories) x " +
                "${DataStore.columnNames[j]} (${colCats.size} categories), n=${xs.size}")
        sb.appendLine("\u03c7\u00b2(${r.df}) = ${Stats.formatNum(r.chi2)}")
        sb.appendLine("p = ${Stats.formatP(r.pValue)}")
        sb.appendLine()
        sb.append(Stats.interpretChiSquare(r.pValue))
        txtChiSq.text = sb.toString().trimEnd()
        lastResultsText = txtChiSq.text.toString()
    }

    private fun runCorrelation() {
        val i = spinnerCorrX.selectedItemPosition
        val j = spinnerCorrY.selectedItemPosition
        if (i < 0 || j < 0 || i >= DataStore.columnCount() || j >= DataStore.columnCount()) {
            toast("No columns — add data first")
            return
        }
        if (i == j) {
            toast("Pick two different columns")
            return
        }
        val (xs, ys, skipped) = DataStore.pairedNumericColumns(i, j)
        val r = Stats.pearsonCorrelation(xs, ys)
        if (r == null) {
            txtCorr.text = "Need at least 3 paired numeric rows with some variation."
            lastResultsText = txtCorr.text.toString()
            return
        }
        val sb = StringBuilder()
        sb.appendLine("Pearson correlation")
        sb.appendLine("${DataStore.columnNames[i]} vs ${DataStore.columnNames[j]} (n=${xs.size})")
        sb.appendLine("t(${r.df}) = ${Stats.formatNum(r.t)}")
        sb.appendLine()
        sb.append(Stats.interpretCorrelation(r.r, r.pValue))
        if (skipped > 0) sb.appendLine("\nNote: $skipped row(s) skipped (missing/non-numeric).")
        txtCorr.text = sb.toString().trimEnd()
        lastResultsText = txtCorr.text.toString()
    }

    private fun runRegression() {
        val i = spinnerRegX.selectedItemPosition
        val j = spinnerRegY.selectedItemPosition
        if (i < 0 || j < 0 || i >= DataStore.columnCount() || j >= DataStore.columnCount()) {
            toast("No columns — add data first")
            return
        }
        if (i == j) {
            toast("Pick two different columns")
            return
        }
        val (xs, ys, skipped) = DataStore.pairedNumericColumns(i, j)
        val r = Stats.linearRegression(xs, ys)
        if (r == null) {
            txtRegress.text = "Need at least 3 paired numeric rows; X must vary."
            lastResultsText = txtRegress.text.toString()
            return
        }
        val sb = StringBuilder()
        sb.appendLine("Simple linear regression")
        sb.appendLine("${DataStore.columnNames[j]} ~ ${DataStore.columnNames[i]} (n=${r.n})")
        sb.appendLine("Slope     = ${Stats.formatNum(r.slope)} (SE ${Stats.formatNum(r.seSlope)})")
        sb.appendLine("Intercept = ${Stats.formatNum(r.intercept)}")
        sb.appendLine("R\u00b2        = ${Stats.formatNum(r.r2)}")
        sb.appendLine()
        sb.append(Stats.interpretRegression(r))
        if (skipped > 0) sb.appendLine("\nNote: $skipped row(s) skipped (missing/non-numeric).")
        txtRegress.text = sb.toString().trimEnd()
        lastResultsText = txtRegress.text.toString()
    }

    private fun copyResults() {
        if (lastResultsText.isBlank()) {
            toast("Nothing to copy — run an analysis first")
            return
        }
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PocketStat results", lastResultsText))
        toast("Results copied")
    }

    private fun shareResults() {
        if (lastResultsText.isBlank()) {
            toast("Nothing to share — run an analysis first")
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "PocketStat results\n\n$lastResultsText")
        }
        startActivity(Intent.createChooser(intent, "Share results"))
    }

    private fun toast(msg: String) =
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
}
