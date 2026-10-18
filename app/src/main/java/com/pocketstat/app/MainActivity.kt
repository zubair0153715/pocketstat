package com.pocketstat.app

import android.app.Activity
import android.app.Fragment
import android.os.Bundle
import android.widget.Button

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        actionBar?.title = getString(R.string.app_name)

        if (DataStore.columnNames.isEmpty()) {
            DataStore.loadSample()
        }

        val btnData = findViewById<Button>(R.id.tab_btn_data)
        val btnAnalyze = findViewById<Button>(R.id.tab_btn_analyze)
        val btnCharts = findViewById<Button>(R.id.tab_btn_charts)

        btnData.setOnClickListener { showFragment(DataFragment()) }
        btnAnalyze.setOnClickListener { showFragment(AnalyzeFragment()) }
        btnCharts.setOnClickListener { showFragment(ChartsFragment()) }

        if (savedInstanceState == null) {
            showFragment(DataFragment())
        }
    }

    private fun showFragment(fragment: Fragment) {
        fragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
