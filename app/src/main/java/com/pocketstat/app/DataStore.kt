package com.pocketstat.app

/**
 * In-memory dataset for PocketStat MVP.
 * Columns are numeric; cells stored as strings, parsed on demand.
 * Non-numeric / blank cells are skipped by numericColumn() and reported via skippedCount().
 */
object DataStore {

    val columnNames = mutableListOf<String>()
    val rows = mutableListOf<MutableList<String>>()

    var onDataChanged: (() -> Unit)? = null

    private fun notifyChanged() = onDataChanged?.invoke()

    fun columnCount(): Int = columnNames.size

    fun addColumn(name: String = "") {
        val base = if (name.isBlank()) "Var" else name
        var finalName = base
        var i = 1
        while (columnNames.contains(finalName)) {
            i++
            finalName = "$base$i"
        }
        columnNames.add(finalName)
        rows.forEach { it.add("") }
        notifyChanged()
    }

    fun addRow() {
        rows.add(MutableList(columnNames.size) { "" })
        notifyChanged()
    }

    fun removeColumn(index: Int) {
        if (index in columnNames.indices) {
            columnNames.removeAt(index)
            rows.forEach { if (index < it.size) it.removeAt(index) }
            notifyChanged()
        }
    }

    fun removeRow(index: Int) {
        if (index in rows.indices) {
            rows.removeAt(index)
            notifyChanged()
        }
    }

    fun renameColumn(index: Int, name: String) {
        if (index in columnNames.indices && name.isNotBlank()) {
            columnNames[index] = name.trim()
            notifyChanged()
        }
    }

    fun setCell(row: Int, col: Int, value: String) {
        if (row in rows.indices && col in columnNames.indices) {
            rows[row][col] = value
        }
    }

    fun clear() {
        columnNames.clear()
        rows.clear()
        notifyChanged()
    }

    /** Parsed numeric values of a column; blanks and non-numeric cells are skipped. */
    fun numericColumn(index: Int): List<Double> {
        if (index !in columnNames.indices) return emptyList()
        return rows.mapNotNull { row ->
            row.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        }
    }

    fun skippedCount(index: Int): Int {
        if (index !in columnNames.indices) return 0
        var skipped = 0
        rows.forEach { row ->
            val cell = row.getOrNull(index)?.trim().orEmpty()
            if (cell.isNotEmpty() && cell.toDoubleOrNull() == null) skipped++
        }
        return skipped
    }

    /** Raw (trimmed, non-blank) cell strings of a column — for categorical analysis. */
    fun stringColumn(index: Int): List<String> {
        if (index !in columnNames.indices) return emptyList()
        return rows.mapNotNull { row ->
            row.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Paired numeric values from two columns, row by row.
     * A row is kept only when BOTH cells parse as numbers (pairing preserved).
     * Returns (x values, y values, rows skipped).
     */
    fun pairedNumericColumns(i: Int, j: Int): Triple<List<Double>, List<Double>, Int> {
        if (i !in columnNames.indices || j !in columnNames.indices)
            return Triple(emptyList(), emptyList(), 0)
        val xs = mutableListOf<Double>()
        val ys = mutableListOf<Double>()
        var skipped = 0
        for (row in rows) {
            val a = row.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            val b = row.getOrNull(j)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            if (a != null && b != null) {
                xs.add(a); ys.add(b)
            } else skipped++
        }
        return Triple(xs, ys, skipped)
    }

    /**
     * Paired category strings from two columns, row by row.
     * A row is kept only when BOTH cells are non-blank.
     */
    fun pairedStringColumns(i: Int, j: Int): Pair<List<String>, List<String>> {
        if (i !in columnNames.indices || j !in columnNames.indices)
            return Pair(emptyList(), emptyList())
        val xs = mutableListOf<String>()
        val ys = mutableListOf<String>()
        for (row in rows) {
            val a = row.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
            val b = row.getOrNull(j)?.trim()?.takeIf { it.isNotEmpty() }
            if (a != null && b != null) {
                xs.add(a); ys.add(b)
            }
        }
        return Pair(xs, ys)
    }

    // ---------- CSV ----------

    fun importCsv(text: String) {
        val parsed = parseCsv(text)
        if (parsed.isEmpty()) return
        clear()
        val header = parsed[0]
        header.forEachIndexed { i, h ->
            val name = h.trim().ifEmpty { "Var${i + 1}" }
            columnNames.add(name)
        }
        // de-duplicate headers
        val seen = mutableSetOf<String>()
        for (i in columnNames.indices) {
            var n = columnNames[i]
            var k = 1
            while (!seen.add(n)) {
                k++
                n = "${columnNames[i]}_$k"
            }
            columnNames[i] = n
        }
        parsed.drop(1).forEach { fields ->
            if (fields.any { it.isNotBlank() }) {
                val row = MutableList(columnNames.size) { i -> fields.getOrNull(i)?.trim().orEmpty() }
                rows.add(row)
            }
        }
        notifyChanged()
    }

    /** Minimal CSV parser: handles quoted fields with embedded commas and escaped quotes. */
    private fun parseCsv(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"'); i += 2; continue
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    row.add(field.toString()); field = StringBuilder()
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    row.add(field.toString()); field = StringBuilder()
                    if (row.any { it.isNotEmpty() }) result.add(row)
                    row = mutableListOf()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString())
        if (row.any { it.isNotEmpty() }) result.add(row)
        return result
    }

    // ---------- Sample dataset ----------

    fun loadSample() {
        clear()
        columnNames.add("Group A")
        columnNames.add("Group B")
        val a = listOf(78, 82, 85, 79, 88, 81, 76, 84, 80, 83, 77, 86, 82, 79, 84)
        val b = listOf(72, 75, 70, 78, 74, 71, 76, 73, 69, 77, 72, 75, 70, 74, 76)
        for (i in a.indices) {
            rows.add(mutableListOf(a[i].toString(), b[i].toString()))
        }
        notifyChanged()
    }
}
