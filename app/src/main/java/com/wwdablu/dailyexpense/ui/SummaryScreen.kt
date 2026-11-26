package com.wwdablu.dailyexpense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wwdablu.dailyexpense.data.CategoryTotal
import com.wwdablu.dailyexpense.data.PeriodTotals
import com.wwdablu.dailyexpense.insights.Insight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Lightbulb
import java.time.LocalDate

@Composable
fun SummaryScreen(
    day: LocalDate,
    sums: PeriodTotals,
    totals: List<CategoryTotal>,
    period: SummaryPeriod,
    onPeriodChange: (SummaryPeriod) -> Unit,
    insights: List<Insight>,
    budget: Long?,
    onSetBudget: (Long?) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    contentPadding: PaddingValues,
) {
    val isMonth = period == SummaryPeriod.Month
    var editBudget by remember { mutableStateOf(false) }
    val spent = sums.expense

    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                SegmentedButton(
                    selected = isMonth,
                    onClick = { onPeriodChange(SummaryPeriod.Month) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Monthly") }
                SegmentedButton(
                    selected = !isMonth,
                    onClick = { onPeriodChange(SummaryPeriod.Year) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Yearly") }
            }
        }
        item {
            val unit = if (isMonth) "month" else "year"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous $unit") }
                Text(
                    if (isMonth) day.format(monthFormatter) else day.year.toString(),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next $unit") }
            }
        }

        // Earned / spent / net
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SumLine("Earned", formatMoney(sums.income), IncomeGreen)
                    SumLine("Spent", formatMoney(spent), MaterialTheme.colorScheme.onSurface)
                    val net = sums.income - spent
                    SumLine(
                        "Net",
                        (if (net < 0) "− " else "") + formatMoney(kotlin.math.abs(net)),
                        if (net < 0) MaterialTheme.colorScheme.error else IncomeGreen,
                        bold = true,
                    )
                }
            }
        }

        if (insights.isNotEmpty()) item { InsightsCard(insights) }

        // Monthly budget (the budget is per month, so it is only shown in the monthly view)
        if (isMonth) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Monthly budget", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { editBudget = true }) { Text(if (budget == null) "Set budget" else "Change") }
                    }
                    if (budget == null) {
                        Text(
                            "Set a maximum amount to spend each month.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val fraction = (spent.toFloat() / budget).coerceAtLeast(0f)
                        val over = spent > budget
                        Text(
                            "${formatMoney(spent)} of ${formatMoney(budget)} used",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { fraction.coerceAtMost(1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = when {
                                over -> MaterialTheme.colorScheme.error
                                fraction >= 0.8f -> Color(0xFFF9A825)
                                else -> IncomeGreen
                            },
                            drawStopIndicator = {},
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (over) "Over budget by ${formatMoney(spent - budget)}"
                            else "${formatMoney(budget - spent)} left",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (totals.isNotEmpty()) {
            item {
                Text(
                    "Spending by type",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        items(totals, key = { it.categoryId ?: -1L }) { t ->
            val fraction = if (spent == 0L) 0f else t.total.toFloat() / spent
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(t.categoryName, t.categoryIcon, t.categoryColor, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(t.categoryName ?: "Uncategorized", Modifier.weight(1f))
                    Text("${formatMoney(t.total)}  ·  ${(fraction * 100).toInt()}%")
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(t.categoryColor ?: 0xFFBDBDBD),
                    drawStopIndicator = {},
                )
            }
        }
    }

    if (editBudget) {
        BudgetDialog(
            current = budget,
            onSave = { onSetBudget(it); editBudget = false },
            onDismiss = { editBudget = false },
        )
    }
}

@Composable
private fun SumLine(label: String, value: String, color: Color, bold: Boolean = false) {
    Row {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color,
        )
    }
}

@Composable
fun BudgetDialog(current: Long?, onSave: (Long?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current?.let { minorToInput(it) } ?: "") }
    var tried by remember { mutableStateOf(false) }
    val parsed = parseMoney(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Maximum monthly budget") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Amount (₹)") },
                singleLine = true,
                isError = tried && parsed == null,
                supportingText = if (tried && parsed == null) {
                    { Text("Enter a positive amount (max 2 decimals)") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { tried = true; if (parsed != null) onSave(parsed) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Short observations about the period: change vs last period, biggest movers, highest day, pace. */
@Composable
private fun InsightsCard(insights: List<Insight>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Insights", style = MaterialTheme.typography.titleMedium)
            insights.forEach { insight ->
                val (icon, tint) = when (insight.tone) {
                    Insight.Tone.Bad -> Icons.AutoMirrored.Filled.TrendingUp to MaterialTheme.colorScheme.error
                    Insight.Tone.Good -> Icons.AutoMirrored.Filled.TrendingDown to IncomeGreen
                    Insight.Tone.Neutral -> Icons.Filled.Lightbulb to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(icon, null, Modifier.padding(top = 2.dp).size(18.dp), tint = tint)
                    Spacer(Modifier.width(10.dp))
                    Text(insight.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
