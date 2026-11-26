package com.wwdablu.dailyexpense

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import com.wwdablu.dailyexpense.export.PDF_MIME_TYPE
import com.wwdablu.dailyexpense.ui.ExportDialog
import com.wwdablu.dailyexpense.ui.monthFormatter
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wwdablu.dailyexpense.data.Category
import com.wwdablu.dailyexpense.data.ExpenseWithCategory
import com.wwdablu.dailyexpense.ui.CategoriesScreen
import com.wwdablu.dailyexpense.ui.CategoryDialog
import com.wwdablu.dailyexpense.ui.DeleteCategoryDialog
import com.wwdablu.dailyexpense.ui.ExpenseDialog
import com.wwdablu.dailyexpense.ui.ExpensesScreen
import com.wwdablu.dailyexpense.ui.MainViewModel
import com.wwdablu.dailyexpense.ui.SummaryPeriod
import com.wwdablu.dailyexpense.ui.SummaryScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { App() } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = if (android.os.Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}

private enum class Tab(val title: String) { Expenses("Daily Expenses"), Summary("Summary"), Types("Expense Types") }

/** State for the add/edit expense dialog: null = closed. */
private sealed interface ExpenseEdit {
    data object New : ExpenseEdit
    data class Existing(val item: ExpenseWithCategory) : ExpenseEdit
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    val day by vm.selectedDay.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val expenses by vm.dayExpenses.collectAsStateWithLifecycle()
    val summaryPeriod by vm.summaryPeriod.collectAsStateWithLifecycle()
    val summaryTotals by vm.summaryTotals.collectAsStateWithLifecycle()
    val summarySums by vm.summarySums.collectAsStateWithLifecycle()
    val monthSums by vm.monthSums.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var expenseEdit by remember { mutableStateOf<ExpenseEdit?>(null) }
    // Category dialog: open flag + the category being edited (null = new)
    var categoryDialogOpen by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Pair<Category, Int>?>(null) }

    // Export: dialog, then the system "save as" picker, then a snackbar to open the file.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var exportDialogOpen by remember { mutableStateOf(false) }
    var pendingExportYear by rememberSaveable { mutableStateOf(false) }
    fun onFileChosen(uri: Uri?) {
        if (uri == null) return
        vm.export(pendingExportYear, uri) { error ->
            scope.launch {
                if (error != null) {
                    snackbar.showSnackbar("Export failed: $error")
                } else {
                    val result = snackbar.showSnackbar(
                        "PDF report saved", actionLabel = "Open", duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, PDF_MIME_TYPE)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try { context.startActivity(open) } catch (_: ActivityNotFoundException) {
                            snackbar.showSnackbar("No app installed to open PDF files")
                        }
                    }
                }
            }
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME_TYPE)) { onFileChosen(it) }

    val current = Tab.entries[tab]
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(current.title) },
                actions = {
                    if (current == Tab.Summary) {
                        IconButton(onClick = { exportDialogOpen = true }) { Icon(Icons.Filled.FileDownload, "Export report") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (current != Tab.Summary) {
                FloatingActionButton(onClick = {
                    if (current == Tab.Expenses) expenseEdit = ExpenseEdit.New
                    else { editingCategory = null; categoryError = null; categoryDialogOpen = true }
                }) { Icon(Icons.Filled.Add, if (current == Tab.Expenses) "Add expense or earning" else "Add expense type") }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null) }, label = { Text("Expenses") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.PieChart, null) }, label = { Text("Summary") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Category, null) }, label = { Text("Types") })
            }
        },
    ) { padding ->
        when (current) {
            Tab.Expenses -> ExpensesScreen(
                day = day,
                expenses = expenses,
                onPrevDay = { vm.shiftDay(-1) },
                onNextDay = { vm.shiftDay(1) },
                onPickDay = vm::selectDay,
                onEdit = { expenseEdit = ExpenseEdit.Existing(it) },
                budget = budget,
                monthSpent = monthSums.expense,
                contentPadding = padding,
            )
            Tab.Summary -> SummaryScreen(
                day = day,
                sums = summarySums,
                totals = summaryTotals,
                period = summaryPeriod,
                onPeriodChange = vm::setSummaryPeriod,
                budget = budget,
                onSetBudget = vm::setBudget,
                onPrev = { if (summaryPeriod == SummaryPeriod.Month) vm.shiftMonth(-1) else vm.shiftYear(-1) },
                onNext = { if (summaryPeriod == SummaryPeriod.Month) vm.shiftMonth(1) else vm.shiftYear(1) },
                contentPadding = padding,
            )
            Tab.Types -> CategoriesScreen(
                categories = categories,
                onEdit = { editingCategory = it; categoryError = null; categoryDialogOpen = true },
                onDelete = { c -> vm.expenseCountFor(c) { count -> deleting = c to count } },
                contentPadding = padding,
            )
        }
    }

    expenseEdit?.let { edit ->
        ExpenseDialog(
            existing = (edit as? ExpenseEdit.Existing)?.item,
            defaultDay = day,
            categories = categories,
            onSave = { vm.saveExpense(it); expenseEdit = null },
            onDelete = { vm.deleteExpense(it); expenseEdit = null },
            onDismiss = { expenseEdit = null },
        )
    }

    if (categoryDialogOpen) {
        CategoryDialog(
            existing = editingCategory,
            error = categoryError,
            onSave = { c ->
                vm.saveCategory(c) { err ->
                    categoryError = err
                    if (err == null) categoryDialogOpen = false
                }
            },
            onDismiss = { categoryDialogOpen = false },
        )
    }

    if (exportDialogOpen) {
        ExportDialog(
            monthLabel = day.format(monthFormatter),
            yearLabel = day.year.toString(),
            initialYear = summaryPeriod == SummaryPeriod.Year,
            onExport = { isYear ->
                exportDialogOpen = false
                pendingExportYear = isYear
                savePdf.launch(vm.exportFileName(isYear))
            },
            onDismiss = { exportDialogOpen = false },
        )
    }

    deleting?.let { (c, count) ->
        DeleteCategoryDialog(
            category = c,
            expenseCount = count,
            onConfirm = { vm.deleteCategory(c); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}
