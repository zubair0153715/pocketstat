package com.pocketstat.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Fragment
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast

/**
 * Spreadsheet-like data editor.
 * Tap a cell to edit, tap a column header to rename/delete,
 * tap a row number to delete the row.
 */
class DataFragment : Fragment() {

    private lateinit var table: TableLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_data, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        table = view.findViewById(R.id.data_table)

        view.findViewById<Button>(R.id.btn_add_row).setOnClickListener {
            if (DataStore.columnCount() == 0) DataStore.addColumn("Var1")
            DataStore.addRow()
        }
        view.findViewById<Button>(R.id.btn_add_col).setOnClickListener {
            promptText("New column", "Var${DataStore.columnCount() + 1}") { name ->
                DataStore.addColumn(name)
            }
        }
        view.findViewById<Button>(R.id.btn_import).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("text/csv", "text/comma-separated-values", "text/plain")
                )
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_CSV)
        }
        view.findViewById<Button>(R.id.btn_sample).setOnClickListener {
            DataStore.loadSample()
            toast("Sample dataset loaded")
        }
        view.findViewById<Button>(R.id.btn_clear).setOnClickListener {
            confirm("Clear all data?") { DataStore.clear() }
        }

        DataStore.onDataChanged = { renderTable() }
        renderTable()
    }

    override fun onDestroyView() {
        if (DataStore.onDataChanged != null) DataStore.onDataChanged = null
        super.onDestroyView()
    }

    @Deprecated("Framework onActivityResult for the CSV picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CSV && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val text = activity.contentResolver
                    .openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                if (text.isBlank()) {
                    toast("Empty file")
                    return
                }
                DataStore.importCsv(text)
                toast("Imported ${DataStore.columnCount()} columns, ${DataStore.rows.size} rows")
            } catch (e: Exception) {
                toast("Import failed: ${e.message}")
            }
        }
    }

    private fun renderTable() {
        if (!isAdded) return
        val ctx = activity ?: return
        table.removeAllViews()

        // Header row
        val headerRow = TableRow(ctx)
        headerRow.addView(headerCell("#"))
        DataStore.columnNames.forEachIndexed { col, name ->
            val cell = headerCell(name)
            cell.setOnClickListener { showColumnMenu(col) }
            headerRow.addView(cell)
        }
        table.addView(headerRow)

        // Data rows
        DataStore.rows.forEachIndexed { r, rowData ->
            val tr = TableRow(ctx)
            val rowNum = TextView(ctx).apply {
                text = (r + 1).toString()
                setPadding(24, 20, 24, 20)
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setBackgroundColor(0xFFE8EEF3.toInt())
            }
            rowNum.setOnClickListener {
                confirm("Delete row ${r + 1}?") { DataStore.removeRow(r) }
            }
            tr.addView(rowNum)
            for (c in DataStore.columnNames.indices) {
                val cell = TextView(ctx).apply {
                    text = rowData.getOrNull(c).orEmpty()
                    setPadding(24, 20, 24, 20)
                    gravity = Gravity.CENTER
                    minWidth = 220
                    setBackgroundResource(android.R.drawable.edit_text)
                }
                cell.setOnClickListener { editCell(r, c) }
                tr.addView(cell)
            }
            table.addView(tr)
        }

        if (DataStore.columnNames.isEmpty()) {
            val tr = TableRow(ctx)
            val tv = TextView(ctx).apply {
                text = "No data — tap + Column, Import CSV, or load the sample dataset."
                setPadding(24, 40, 24, 40)
            }
            tr.addView(tv)
            table.addView(tr)
        }
    }

    private fun headerCell(text: String): TextView =
        TextView(activity).apply {
            this.text = text
            setPadding(24, 20, 24, 20)
            gravity = Gravity.CENTER
            minWidth = 220
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(0xFF1B6CA8.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }

    private fun editCell(r: Int, c: Int) {
        val ctx = activity ?: return
        val input = EditText(ctx).apply {
            setText(DataStore.rows[r].getOrNull(c).orEmpty())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            selectAll()
        }
        AlertDialog.Builder(ctx)
            .setTitle("Edit cell (${DataStore.columnNames[c]}, row ${r + 1})")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                DataStore.setCell(r, c, input.text.toString().trim())
                renderTable()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showColumnMenu(col: Int) {
        val ctx = activity ?: return
        val name = DataStore.columnNames[col]
        AlertDialog.Builder(ctx)
            .setTitle("Column: $name")
            .setItems(arrayOf("Rename", "Delete column")) { _, which ->
                when (which) {
                    0 -> promptText("Rename column", name) { DataStore.renameColumn(col, it) }
                    1 -> confirm("Delete column \"$name\"?") { DataStore.removeColumn(col) }
                }
            }
            .show()
    }

    private fun promptText(title: String, initial: String, onOk: (String) -> Unit) {
        val ctx = activity ?: return
        val input = EditText(ctx).apply { setText(initial); selectAll() }
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirm(message: String, onYes: () -> Unit) {
        val ctx = activity ?: return
        AlertDialog.Builder(ctx)
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ -> onYes() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun toast(msg: String) =
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_CSV = 1001
    }
}
