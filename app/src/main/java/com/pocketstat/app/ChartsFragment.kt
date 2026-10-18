package com.pocketstat.app

import android.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner

/**
 * Charts tab: histogram + boxplot of the selected column,
 * drawn by the hand-rolled Canvas views (no chart library).
 */
class ChartsFragment : Fragment() {

    private lateinit var spinner: Spinner
    private lateinit var histogram: HistogramView
    private lateinit var boxplot: BoxPlotView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_charts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        spinner = view.findViewById(R.id.spinner_chart_col)
        histogram = view.findViewById(R.id.histogram_view)
        boxplot = view.findViewById(R.id.boxplot_view)

        refreshSpinner()

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, v: View?, position: Int, id: Long
            ) = renderCharts(position)

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        DataStore.onDataChanged = {
            if (isAdded) {
                refreshSpinner()
                renderCharts(spinner.selectedItemPosition)
            }
        }
    }

    override fun onDestroyView() {
        if (DataStore.onDataChanged != null) DataStore.onDataChanged = null
        super.onDestroyView()
    }

    private fun refreshSpinner() {
        val ctx = activity ?: return
        val adapter = ArrayAdapter(
            ctx,
            android.R.layout.simple_spinner_item,
            DataStore.columnNames.toList()
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun renderCharts(position: Int) {
        val values = if (position in 0 until DataStore.columnCount()) {
            DataStore.numericColumn(position)
        } else emptyList()
        histogram.values = values
        boxplot.values = values
    }
}
