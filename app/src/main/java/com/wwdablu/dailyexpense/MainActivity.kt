package com.wwdablu.dailyexpense

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import com.wwdablu.dailyexpense.notify.AppIntents
import com.wwdablu.dailyexpense.notify.Notifications
import com.wwdablu.dailyexpense.reminder.DailyReminder
import com.wwdablu.dailyexpense.ui.SettingsScreen
import com.wwdablu.dailyexpense.ui.AboutScreen
import androidx.compose.material.icons.outlined.Info
import com.wwdablu.dailyexpense.ui.DownloadDialog
import com.wwdablu.dailyexpense.ui.monthFormatter
import com.wwdablu.dailyexpense.ui.SectionTitle
import com.wwdablu.dailyexpense.ui.SettingRow
import com.wwdablu.dailyexpense.ui.SwitchRow
import com.wwdablu.dailyexpense.data.Expense
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.wwdablu.dailyexpense.widget.TodayWidget
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import com.wwdablu.dailyexpense.export.CSV_MIME_TYPE
import com.wwdablu.dailyexpense.export.PDF_MIME_TYPE
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
    /** Set when a notification or the widget asks to open "add entry"; cleared once handled. */
    private var launchAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.ensureChannels(this)
        DailyReminder.schedule(this) // re-arm after install/update
        if (savedInstanceState == null) launchAction = intent?.getStringExtra(AppIntents.EXTRA_ACTION)
        setContent {
            AppTheme { App(launchAction = launchAction, onLaunchActionHandled = { launchAction = null }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchAction = intent.getStringExtra(AppIntents.EXTRA_ACTION)
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
    /** New entry pre-filled from a detected SMS; the suggestion is removed once saved. */
    data class FromSms(val pendingId: Long, val draft: Expense) : ExpenseEdit
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    launchAction: String? = null,
    onLaunchActionHandled: () -> Unit = {},
    vm: MainViewModel = viewModel(),
) {
    val day by vm.selectedDay.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val expenses by vm.dayExpenses.collectAsStateWithLifecycle()
    val summaryPeriod by vm.summaryPeriod.collectAsStateWithLifecycle()
    val summaryTotals by vm.summaryTotals.collectAsStateWithLifecycle()
    val summarySums by vm.summarySums.collectAsStateWithLifecycle()
    val monthSums by vm.monthSums.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val reminderEnabled by vm.reminderEnabled.collectAsStateWithLifecycle()
    val reminderMinutes by vm.reminderMinutes.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val insights by vm.insights.collectAsStateWithLifecycle()
    val smsDetection by vm.smsDetection.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var expenseEdit by remember { mutableStateOf<ExpenseEdit?>(null) }
    // Category dialog: open flag + the category being edited (null = new)
    var categoryDialogOpen by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Pair<Category, Int>?>(null) }

    // Export: the download button opens the system "save as" picker for the month or year shown
    // in Summary, then a snackbar offers to open the PDF.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pendingExportYear by rememberSaveable { mutableStateOf(false) }
    fun onFileChosen(uri: Uri?, csv: Boolean) {
        if (uri == null) return
        val kind = if (csv) "CSV" else "PDF"
        val mime = if (csv) CSV_MIME_TYPE else PDF_MIME_TYPE
        vm.export(pendingExportYear, uri, csv) { error ->
            scope.launch {
                if (error != null) {
                    snackbar.showSnackbar("Export failed: $error")
                } else {
                    val result = snackbar.showSnackbar(
                        "$kind file saved", actionLabel = "Open", duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try { context.startActivity(open) } catch (_: ActivityNotFoundException) {
                            snackbar.showSnackbar("No app installed to open $kind files")
                        }
                    }
                }
            }
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME_TYPE)) { onFileChosen(it, csv = false) }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME_TYPE)) { onFileChosen(it, csv = true) }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showSettings || showAbout) { showSettings = false; showAbout = false }

    // Opened from a notification or the widget's "+": go to today's expenses and start a new entry.
    LaunchedEffect(launchAction) {
        if (launchAction == AppIntents.ACTION_ADD) {
            showSettings = false
            showAbout = false
            tab = 0
            vm.selectDay(java.time.LocalDate.now())
            expenseEdit = ExpenseEdit.New
        }
        if (launchAction != null) onLaunchActionHandled()
    }

    // Turning on the reminder needs notification permission on Android 13+.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setReminderEnabled(true)
        else scope.launch { snackbar.showSnackbar("Allow notifications for Daily Expenses to get reminders") }
    }
    fun enableReminder(on: Boolean) {
        if (on && !Notifications.canPost(context) && Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.setReminderEnabled(on)
        }
    }

    // SMS detection needs RECEIVE_SMS (and notifications, to tell the user about a detected entry).
    val smsPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECEIVE_SMS] == true) vm.setSmsDetection(true)
        else scope.launch { snackbar.showSnackbar("SMS permission is needed to detect bank messages") }
    }
    fun enableSms(on: Boolean) {
        if (!on) { vm.setSmsDetection(false); return }
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECEIVE_SMS)
            }
            if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Manifest.permission.RECEIVE_SMS in needed) smsPermissions.launch(needed.toTypedArray())
        else vm.setSmsDetection(true)
    }

    // If SMS permission was revoked (or never granted), the setting can't work, so show it as off.
    LaunchedEffect(smsDetection) {
        if (smsDetection &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED
        ) vm.setSmsDetection(false)
    }

    var menuOpen by remember { mutableStateOf(false) }
    var downloadDialogOpen by remember { mutableStateOf(false) }
    val current = Tab.entries[tab]
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (showSettings) "Settings" else if (showAbout) "About" else current.title) },
                navigationIcon = {
                    if (showSettings || showAbout) {
                        IconButton(onClick = { showSettings = false; showAbout = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
                actions = {
                    if (!showSettings && !showAbout && current == Tab.Summary) {
                        IconButton(onClick = { downloadDialogOpen = true }) {
                            Icon(
                                Icons.Filled.FileDownload,
                                if (summaryPeriod == SummaryPeriod.Year) "Download yearly report" else "Download monthly report",
                            )
                        }
                    }
                    if (!showSettings && !showAbout) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                    onClick = { menuOpen = false; showSettings = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("About") },
                                    leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                    onClick = { menuOpen = false; showAbout = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!showSettings && !showAbout && current != Tab.Summary) {
                FloatingActionButton(onClick = {
                    if (current == Tab.Expenses) expenseEdit = ExpenseEdit.New
                    else { editingCategory = null; categoryError = null; categoryDialogOpen = true }
                }) { Icon(Icons.Filled.Add, if (current == Tab.Expenses) "Add expense or earning" else "Add expense type") }
            }
        },
        bottomBar = {
            if (!showSettings && !showAbout) NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null) }, label = { Text("Expenses") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.PieChart, null) }, label = { Text("Summary") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Category, null) }, label = { Text("Types") })
            }
        },
    ) { padding ->
        if (showAbout) {
            AboutScreen(contentPadding = padding)
        } else if (showSettings) {
            SettingsScreen(
                reminderEnabled = reminderEnabled,
                reminderMinutes = reminderMinutes,
                onReminderEnabled = ::enableReminder,
                onReminderMinutes = vm::setReminderMinutes,
                contentPadding = padding,
            ) {
                SectionTitle("Home screen")
                SettingRow(
                    title = "Add Today widget",
                    subtitle = "Shows today's spending and what's left, with a quick + button",
                ) {
                    if (!TodayWidget.requestPin(context)) {
                        scope.launch { snackbar.showSnackbar("Long-press your home screen and choose Widgets to add it") }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle("Bank SMS")
                SwitchRow(
                    title = "Detect expenses from bank SMS",
                    subtitle = "New debit and credit messages from banks are suggested on the Expenses tab " +
                        "for you to add. Messages are read on this phone only and are not stored.",
                    checked = smsDetection,
                    onChecked = ::enableSms,
                )
            }
        } else when (current) {
            Tab.Expenses -> ExpensesScreen(
                day = day,
                expenses = expenses,
                onPrevDay = { vm.shiftDay(-1) },
                onNextDay = { vm.shiftDay(1) },
                onPickDay = vm::selectDay,
                onEdit = { expenseEdit = ExpenseEdit.Existing(it) },
                pending = pending,
                onAddPending = { expenseEdit = ExpenseEdit.FromSms(it.id, vm.draftFrom(it)) },
                onDismissPending = { vm.dismissPending(it.id) },
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
                insights = insights,
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
            draft = (edit as? ExpenseEdit.FromSms)?.draft,
            categories = categories,
            onSave = {
                vm.saveExpense(it)
                (edit as? ExpenseEdit.FromSms)?.let { sms -> vm.dismissPending(sms.pendingId) }
                expenseEdit = null
            },
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

    if (downloadDialogOpen) {
        DownloadDialog(
            periodLabel = if (summaryPeriod == SummaryPeriod.Year) day.year.toString() else day.format(monthFormatter),
            onPick = { csv ->
                downloadDialogOpen = false
                pendingExportYear = summaryPeriod == SummaryPeriod.Year
                val name = vm.exportFileName(pendingExportYear, csv)
                if (csv) saveCsv.launch(name) else savePdf.launch(name)
            },
            onDismiss = { downloadDialogOpen = false },
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
