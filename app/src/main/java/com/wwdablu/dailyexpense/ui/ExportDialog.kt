package com.wwdablu.dailyexpense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wwdablu.dailyexpense.export.ExportFormat

/** Choose the period (the month or year shown in Summary) and the file format to export. */
@Composable
fun ExportDialog(
    monthLabel: String,
    yearLabel: String,
    initialYear: Boolean,
    onExport: (isYear: Boolean, format: ExportFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    var isYear by rememberSaveable { mutableStateOf(initialYear) }
    var format by rememberSaveable { mutableStateOf(ExportFormat.Pdf) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Period", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!isYear, { isYear = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(monthLabel) }
                    SegmentedButton(isYear, { isYear = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(yearLabel) }
                }
                Text("Format", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ExportFormat.entries.forEachIndexed { i, f ->
                        SegmentedButton(format == f, { format = f }, SegmentedButtonDefaults.itemShape(i, ExportFormat.entries.size)) {
                            Text(if (f == ExportFormat.Excel) "Excel (.xlsx)" else "PDF")
                        }
                    }
                }
                Text(
                    "Includes totals, amounts per type and every entry. You'll choose where to save it next.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onExport(isYear, format) }) { Text("Export") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
