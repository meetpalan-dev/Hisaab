package com.palan.hisaab.ui.account

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.data.RepaymentResult
import com.palan.hisaab.data.SettingsRepository
import com.palan.hisaab.data.dao.OutstandingHisaab
import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import com.palan.hisaab.ui.addtransaction.AddEditTransactionDialog
import com.palan.hisaab.ui.theme.GreenReceived
import com.palan.hisaab.ui.theme.RedSpent
import com.palan.hisaab.ui.theme.Spacing
import com.palan.hisaab.util.HisabDocumentExporter
import com.palan.hisaab.util.Money
import com.palan.hisaab.util.toDisplayString
import com.palan.hisaab.viewmodel.AccountUiState
import com.palan.hisaab.viewmodel.AccountViewModel
import com.palan.hisaab.viewmodel.SettingsViewModel
import java.util.Date

private enum class HistoryFilter(val label: String) { ACTIVE("Active"), ALL("All"), CLEARED("Cleared") }

private enum class SwipeDirection { NONE, SETTLE, RESTORE }

/** "August 2026" for a dated transaction, or "No Date" grouped last (the transaction list is already
 * sorted newest-date-first with nulls last, so this groupBy naturally yields months newest-first
 * with "No Date" trailing, matching the monthly-grouping spec). */
private fun monthGroupLabel(dateMillis: Long?): String {
    if (dateMillis == null) return "No Date"
    return java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(Date(dateMillis))
}

/** Holds a repayment that's mid-flow: amount/description entered, waiting on the user to pick which outstanding hisaab(s) it covers. */
private data class PendingRepayment(val type: TransactionType, val amountMinor: Long, val description: String, val date: Long?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    repository: HisaabRepository,
    settingsRepository: SettingsRepository,
    accountId: Long,
    onBack: () -> Unit
) {
    val factory = remember {
        viewModelFactory { initializer { AccountViewModel(repository, accountId) } }
    }
    val viewModel: AccountViewModel = viewModel(factory = factory)
    val settingsFactory = remember {
        viewModelFactory { initializer { SettingsViewModel(settingsRepository) } }
    }
    val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory)
    val settings by settingsViewModel.settings.collectAsState()

    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var transactionPendingDelete by remember { mutableStateOf<Transaction?>(null) }
    var showInitialBalanceDialog by remember { mutableStateOf(false) }
    var showEditAccountDialog by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(HistoryFilter.ACTIVE) }

    // Repayment flow: amount entered -> outstanding hisaabs loaded -> allocation dialog -> confirmation.
    var pendingRepayment by remember { mutableStateOf<PendingRepayment?>(null) }
    var outstandingForRepayment by remember { mutableStateOf<List<OutstandingHisaab>>(emptyList()) }
    var repaymentResult by remember { mutableStateOf<RepaymentResult?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Quick manual settlement (swipe gesture): applies the change immediately, then offers
    // a brief Undo via snackbar that flips it straight back — never deletes the transaction.
    fun handleQuickSettle(txn: Transaction, newSettled: Boolean) {
        viewModel.setSettled(txn, newSettled)
        coroutineScope.launch {
            val message = if (newSettled) "Marked \"${txn.description}\" as settled" else "Restored \"${txn.description}\" to active"
            val result = snackbarHostState.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.setSettled(txn, !newSettled)
            }
        }
    }

    val visibleTransactions = when (filter) {
        HistoryFilter.ACTIVE -> state.activeTransactions
        HistoryFilter.ALL -> state.transactions
        HistoryFilter.CLEARED -> state.clearedTransactions
    }
    // Swipe RIGHT in Active settles a transaction; swipe LEFT in Cleared restores it. All has no swipe.
    val swipeDirection = when (filter) {
        HistoryFilter.ACTIVE -> SwipeDirection.SETTLE
        HistoryFilter.CLEARED -> SwipeDirection.RESTORE
        HistoryFilter.ALL -> SwipeDirection.NONE
    }
    val groupedTransactions = remember(visibleTransactions) {
        visibleTransactions.groupBy { monthGroupLabel(it.date) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.accountName.ifBlank { "…" }, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val phone = state.phoneNumber
                        val text = buildShareText(state)
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("smsto:${phone ?: ""}")
                            putExtra("sms_body", text)
                        }
                        context.startActivity(smsIntent)
                    }, enabled = !state.phoneNumber.isNullOrBlank()) {
                        Icon(Icons.Filled.Sms, contentDescription = "Send Hisab via SMS")
                    }
                    IconButton(onClick = { showShareMenu = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share Hisab")
                    }
                    DropdownMenu(expanded = showShareMenu, onDismissRequest = { showShareMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Share as Text") },
                            onClick = {
                                showShareMenu = false
                                val text = buildShareText(state)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share ${state.accountName} Hisab"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share as PDF") },
                            onClick = {
                                showShareMenu = false
                                val file = HisabDocumentExporter.createPdf(context, state.accountName, state)
                                HisabDocumentExporter.shareFile(context, file, "application/pdf", "Share ${state.accountName} Hisab")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share as Image") },
                            onClick = {
                                showShareMenu = false
                                val file = HisabDocumentExporter.createImage(context, state.accountName, state)
                                HisabDocumentExporter.shareFile(context, file, "image/png", "Share ${state.accountName} Hisab")
                            }
                        )
                    }
                    IconButton(onClick = { showAccountMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit Account") },
                            onClick = { showAccountMenu = false; showEditAccountDialog = true }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            BalanceHeader(
                state = state,
                onEditInitialBalance = { showInitialBalanceDialog = true }
            )

            if (state.transactions.isNotEmpty()) {
                TabRow(selectedTabIndex = filter.ordinal) {
                    HistoryFilter.entries.forEach { f ->
                        Tab(
                            selected = filter == f,
                            onClick = { filter = f },
                            text = { Text(f.label) }
                        )
                    }
                }
            }

            if (visibleTransactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.transactions.isEmpty())
                            "No transactions yet.\nTap + to add Received, Spent, or a Loan."
                        else when (filter) {
                            HistoryFilter.CLEARED -> "Nothing cleared yet."
                            else -> "Nothing here."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.normal),
                    verticalArrangement = Arrangement.spacedBy(Spacing.tight)
                ) {
                    groupedTransactions.forEach { (month, txnsInMonth) ->
                        item(key = "header_$month") {
                            Text(
                                month,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(txnsInMonth, key = { it.id }) { txn ->
                            SwipeableTransactionRow(
                                transaction = txn,
                                remainingMinor = state.remainingFor(txn),
                                isPartiallyPaid = state.isPartiallyPaid(txn),
                                swipeDirection = swipeDirection,
                                onClick = { editingTransaction = txn },
                                onSwipeAction = { newSettled -> handleQuickSettle(txn, newSettled) }
                            )
                        }
                    }
                    item { androidx.compose.foundation.layout.Spacer(Modifier.padding(40.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditTransactionDialog(
            existing = null,
            autoFillTodayDate = settings.autoFillTodayDate,
            onDismiss = { showAddDialog = false },
            onSave = { type, amountMinor, description, date, category ->
                viewModel.addTransaction(type, amountMinor, description, date, category)
                showAddDialog = false
            },
            onStartRepayment = { type, amountMinor, description, date ->
                showAddDialog = false
                pendingRepayment = PendingRepayment(type, amountMinor, description, date)
                viewModel.loadOutstandingHisaabs(type) { outstandingForRepayment = it }
            }
        )
    }

    editingTransaction?.let { txn ->
        AddEditTransactionDialog(
            existing = txn,
            autoFillTodayDate = settings.autoFillTodayDate,
            onDismiss = { editingTransaction = null },
            onSave = { type, amountMinor, description, date, category ->
                viewModel.updateTransaction(
                    txn.copy(type = type, amountMinor = amountMinor, description = description, date = date, category = category)
                )
                editingTransaction = null
            },
            onDelete = {
                transactionPendingDelete = txn
                editingTransaction = null
            },
            onToggleSettled = if (txn.type != TransactionType.INITIAL_BALANCE) {
                {
                    viewModel.toggleSettled(txn)
                    editingTransaction = null
                }
            } else null
        )
    }

    pendingRepayment?.let { pending ->
        RepaymentAllocationDialog(
            repaymentType = pending.type,
            amountMinor = pending.amountMinor,
            description = pending.description,
            outstanding = outstandingForRepayment,
            onDismiss = { pendingRepayment = null },
            onConfirm = { allocations ->
                viewModel.submitRepayment(pending.type, pending.amountMinor, pending.description, pending.date, allocations) { result ->
                    repaymentResult = result
                }
                pendingRepayment = null
            }
        )
    }

    repaymentResult?.let { result ->
        AlertDialog(
            onDismissRequest = { repaymentResult = null },
            title = { Text("Repayment recorded") },
            text = {
                Column {
                    Text("${Money.format(result.cleared.sumOf { it.amountMinor } + result.partiallyPaid.sumOf { it.first.amountMinor - it.second })} allocated")
                    result.cleared.forEach { Text("✓ ${it.description} cleared") }
                    result.partiallyPaid.forEach { (t, remaining) -> Text("${t.description}: ${Money.format(remaining)} remaining") }
                }
            },
            confirmButton = {
                TextButton(onClick = { repaymentResult = null }) { Text("Done") }
            }
        )
    }

    transactionPendingDelete?.let { txn ->
        AlertDialog(
            onDismissRequest = { transactionPendingDelete = null },
            title = { Text("Delete this transaction?") },
            text = { Text(txn.description) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransaction(txn)
                    transactionPendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { transactionPendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showInitialBalanceDialog) {
        InitialBalanceDialog(
            currentMinor = state.initialBalance,
            onDismiss = { showInitialBalanceDialog = false },
            onSave = { minor, date ->
                viewModel.setInitialBalance(minor, date)
                showInitialBalanceDialog = false
            }
        )
    }

    if (showEditAccountDialog) {
        EditAccountDialog(
            currentName = state.accountName,
            currentPhone = state.phoneNumber,
            onDismiss = { showEditAccountDialog = false },
            onSave = { name, phone ->
                viewModel.updateDetails(name, phone)
                showEditAccountDialog = false
            },
            onDeleteAccount = {
                showEditAccountDialog = false
                showDeleteAccountConfirm = true
            }
        )
    }

    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            title = { Text("Delete ${state.accountName}?") },
            text = { Text("This permanently deletes the account and all its transactions.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAccountConfirm = false
                    viewModel.deleteAccount(onBack)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BalanceHeader(state: AccountUiState, onEditInitialBalance: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(Spacing.normal),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Current Balance", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = Money.format(state.balance),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (state.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn(
                    label = "Initial Balance",
                    value = Money.format(state.initialBalance),
                    modifier = Modifier.clickable { onEditInitialBalance() }
                )
                StatColumn(label = "Received", value = Money.format(state.received), valueColor = GreenReceived)
                StatColumn(label = "Spent", value = Money.format(state.spent), valueColor = RedSpent)
            }
            if (state.hasLoans) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatColumn(label = "They owe you (Loan Given)", value = Money.format(state.loanGiven), valueColor = GreenReceived)
                    StatColumn(label = "You owe (Loan Taken)", value = Money.format(state.loanTaken), valueColor = RedSpent)
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

/**
 * Wraps [TransactionRow] with a swipe-to-settle (Active tab) or swipe-to-restore (Cleared tab)
 * gesture. The swipe always snaps back after firing [onSwipeAction] — the row then disappears
 * on its own once the DB update flows back through the account's transaction list for this tab,
 * rather than the dismiss animation trying to remove it itself. No transaction is ever deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTransactionRow(
    transaction: Transaction,
    remainingMinor: Long,
    isPartiallyPaid: Boolean,
    swipeDirection: SwipeDirection,
    onClick: () -> Unit,
    onSwipeAction: (newSettled: Boolean) -> Unit
) {
    if (swipeDirection == SwipeDirection.NONE) {
        TransactionRow(transaction, remainingMinor, isPartiallyPaid, onClick)
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when {
                value == SwipeToDismissBoxValue.StartToEnd && swipeDirection == SwipeDirection.SETTLE ->
                    onSwipeAction(true)
                value == SwipeToDismissBoxValue.EndToStart && swipeDirection == SwipeDirection.RESTORE ->
                    onSwipeAction(false)
            }
            false // always snap back; the item leaves the list once the underlying data changes
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = swipeDirection == SwipeDirection.SETTLE,
        enableDismissFromEndToStart = swipeDirection == SwipeDirection.RESTORE,
        backgroundContent = {
            val isSettle = swipeDirection == SwipeDirection.SETTLE
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                contentAlignment = if (isSettle) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Text(
                    if (isSettle) "Settle" else "Restore",
                    color = if (isSettle) GreenReceived else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) {
        TransactionRow(transaction, remainingMinor, isPartiallyPaid, onClick)
    }
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    remainingMinor: Long,
    isPartiallyPaid: Boolean,
    onClick: () -> Unit
) {
    val isPositive = transaction.type == TransactionType.RECEIVED || transaction.type == TransactionType.LOAN_GIVEN
    val typeLabel = when (transaction.type) {
        TransactionType.LOAN_GIVEN -> "Loan given"
        TransactionType.LOAN_TAKEN -> "Loan taken"
        else -> if (transaction.isRepayment) "Repayment" else null
    }
    val dimmed = transaction.settled
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        transaction.description,
                        fontWeight = FontWeight.Medium,
                        color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (dimmed) {
                        Text("  •  Cleared", style = MaterialTheme.typography.labelSmall, color = GreenReceived)
                    } else if (isPartiallyPaid) {
                        Text("  •  Partially paid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                val dateText = transaction.date?.let { Date(it).toDisplayString() } ?: "No date"
                val meta = listOfNotNull(dateText, typeLabel, transaction.category).joinToString(" • ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isPartiallyPaid) {
                    Text(
                        "Remaining ${Money.format(remainingMinor)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = Money.formatSigned(transaction.amountMinor, transaction.type),
                fontWeight = FontWeight.Bold,
                color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant
                        else if (isPositive) GreenReceived else RedSpent
            )
        }
    }
}

private fun buildShareText(state: AccountUiState): String {
    val sb = StringBuilder()
    sb.appendLine(state.accountName)
    sb.appendLine()
    sb.appendLine("Initial Balance: ${Money.format(state.initialBalance)}")
    sb.appendLine()
    sb.appendLine("Transactions:")
    state.transactions.sortedBy { it.date ?: 0L }.forEach { txn ->
        val sign = if (txn.type == TransactionType.RECEIVED || txn.type == TransactionType.LOAN_GIVEN) "+" else "-"
        val dateText = txn.date?.let { Date(it).toDisplayString() } ?: "No date"
        val typeSuffix = when (txn.type) {
            TransactionType.LOAN_GIVEN -> if (txn.settled) " (Loan given, Paid)" else " (Loan given)"
            TransactionType.LOAN_TAKEN -> if (txn.settled) " (Loan taken, Paid)" else " (Loan taken)"
            else -> if (txn.isRepayment) " (Repayment)" else ""
        }
        sb.appendLine("$dateText - ${txn.description}$typeSuffix - $sign ${Money.format(txn.amountMinor, withSymbol = true)}")
    }
    sb.appendLine()
    sb.appendLine("Received Total: ${Money.format(state.received)}")
    sb.appendLine("Spent Total: ${Money.format(state.spent)}")
    if (state.hasLoans) {
        sb.appendLine("Loan Given Total: ${Money.format(state.loanGiven)}")
        sb.appendLine("Loan Taken Total: ${Money.format(state.loanTaken)}")
    }
    sb.appendLine("Current Balance: ${Money.format(state.balance)}")
    return sb.toString()
}
