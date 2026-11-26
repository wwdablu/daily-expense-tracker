package com.wwdablu.dailyexpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.wwdablu.dailyexpense.data.Category
import com.wwdablu.dailyexpense.data.Expense
import com.wwdablu.dailyexpense.data.ExpenseWithCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ExpensesScreen(
    day: LocalDate,
    expenses: List<ExpenseWithCategory>,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onPickDay: (LocalDate) -> Unit,
    onEdit: (ExpenseWithCategory) -> Unit,
    budget: Long?,
    monthSpent: Long,
    contentPadding: PaddingValues,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevDay) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
            Row(
                Modifier.weight(1f).clickable { showPicker = true }.padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(formatDay(day), style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onNextDay) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
        }

        val spent = expenses.filterNot { it.expense.isIncome }.sumOf { it.expense.amountMinor }
        val earned = expenses.filter { it.expense.isIncome }.sumOf { it.expense.amountMinor }
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Total expense", style = MaterialTheme.typography.labelLarge)
                        Text(formatMoney(spent), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Earned", style = MaterialTheme.typography.labelLarge)
                        Text(
                            formatMoney(earned),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen,
                        )
                    }
                }
                if (budget != null) {
                    // Daily limit = monthly budget spread evenly over the days of this month.
                    val dailyLimit = budget / day.lengthOfMonth()
                    val leftToday = dailyLimit - spent
                    val overToday = leftToday < 0
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Daily limit", style = MaterialTheme.typography.labelLarge)
                            Text(formatMoney(dailyLimit), style = MaterialTheme.typography.titleLarge)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                if (overToday) "Over limit by" else "Remaining",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                formatMoney(if (overToday) -leftToday else leftToday),
                                style = MaterialTheme.typography.titleLarge,
                                color = if (overToday) MaterialTheme.colorScheme.error else IncomeGreen,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val usedFraction = if (dailyLimit == 0L) 1f else spent.toFloat() / dailyLimit
                    LinearProgressIndicator(
                        progress = { usedFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = when {
                            overToday -> MaterialTheme.colorScheme.error
                            usedFraction >= 0.8f -> Color(0xFFF9A825)
                            else -> IncomeGreen
                        },
                        drawStopIndicator = {},
                    )
                    val leftMonth = budget - monthSpent
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (leftMonth >= 0) "Budget left this month: ${formatMoney(leftMonth)}"
                        else "Over monthly budget by ${formatMoney(-leftMonth)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (leftMonth >= 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (expenses.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing recorded for this day.\nTap + to add an expense or earning.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(expenses, key = { it.expense.id }) { item ->
                    ExpenseRow(item, onClick = { onEdit(item) })
                }
            }
        }
    }

    if (showPicker) {
        DayPickerDialog(day, onDismiss = { showPicker = false }, onPicked = { onPickDay(it); showPicker = false })
    }
}

@Composable
private fun ExpenseRow(item: ExpenseWithCategory, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(item.categoryName, item.categoryIcon, item.categoryColor, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.categoryName ?: "Uncategorized", style = MaterialTheme.typography.titleSmall)
                if (item.expense.note.isNotBlank()) {
                    Text(
                        item.expense.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                signedMoney(item.expense),
                style = MaterialTheme.typography.titleMedium,
                color = if (item.expense.isIncome) IncomeGreen else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Green for earnings; lighter in dark mode so it stays readable. */
val IncomeGreen: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

fun signedMoney(e: Expense) = (if (e.isIncome) "+ " else "− ") + formatMoney(e.amountMinor)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state) }
}

/** Add (existing == null) or edit/delete an expense. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDialog(
    existing: ExpenseWithCategory?,
    defaultDay: LocalDate,
    categories: List<Category>,
    onSave: (Expense) -> Unit,
    onDelete: (Expense) -> Unit,
    onDismiss: () -> Unit,
) {
    val base = existing?.expense
    var amount by rememberSaveable { mutableStateOf(base?.let { minorToInput(it.amountMinor) } ?: "") }
    var note by rememberSaveable { mutableStateOf(base?.note ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(base?.categoryId ?: categories.firstOrNull()?.id) }
    var isIncome by rememberSaveable { mutableStateOf(base?.isIncome ?: false) }
    var epochDay by rememberSaveable { mutableStateOf(base?.epochDay ?: defaultDay.toEpochDay()) }
    var menuOpen by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var triedSave by remember { mutableStateOf(false) }

    val parsed = parseMoney(amount)
    val selected = categories.firstOrNull { it.id == categoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add entry" else "Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isIncome,
                        onClick = { isIncome = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Expense") }
                    SegmentedButton(
                        selected = isIncome,
                        onClick = { isIncome = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Earning") }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    isError = triedSave && parsed == null,
                    supportingText = if (triedSave && parsed == null) {
                        { Text("Enter a positive amount (max 2 decimals)") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                    OutlinedTextField(
                        value = selected?.name ?: "Uncategorized",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        leadingIcon = { CategoryBadge(selected?.name, selected?.icon, selected?.color, 28.dp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuOpen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                leadingIcon = { CategoryBadge(c.name, c.icon, c.color, 28.dp) },
                                onClick = { categoryId = c.id; menuOpen = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(formatDay(LocalDate.ofEpochDay(epochDay)))
                }
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete entry", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                triedSave = true
                if (parsed != null) {
                    val e = (base ?: Expense(amountMinor = 0, categoryId = null, note = "", epochDay = epochDay))
                        .copy(
                            amountMinor = parsed,
                            categoryId = categoryId,
                            note = note.trim(),
                            epochDay = epochDay,
                            isIncome = isIncome,
                        )
                    onSave(e)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showPicker) {
        DayPickerDialog(
            LocalDate.ofEpochDay(epochDay),
            onDismiss = { showPicker = false },
            onPicked = { epochDay = it.toEpochDay(); showPicker = false },
        )
    }
    if (confirmDelete && base != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("${formatMoney(base.amountMinor)} will be removed.") },
            confirmButton = {
                TextButton(onClick = { onDelete(base) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
