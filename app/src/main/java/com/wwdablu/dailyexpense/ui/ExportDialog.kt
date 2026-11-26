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

/** Choose whether to export the month or the year shown in Summary as a PDF report. */
@Composable
fun ExportDialog(
    monthLabel: String,
    yearLabel: String,
    initialYear: Boolean,
    onExport: (isYear: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var isYear by rememberSaveable { mutableStateOf(initialYear) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export PDF report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Period", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!isYear, { isYear = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(monthLabel) }
                    SegmentedButton(isYear, { isYear = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(yearLabel) }
                }
                Text(
                    "Includes totals, amounts per type and every entry. You'll choose where to save it next.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onExport(isYear) }) { Text("Export") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
