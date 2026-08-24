package com.palan.hisaab.ui.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.data.SettingsRepository
import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import com.palan.hisaab.ui.addtransaction.AddEditTransactionDialog
import com.palan.hisaab.ui.theme.GreenReceived
import com.palan.hisaab.ui.theme.RedSpent
import com.palan.hisaab.util.Money
import com.palan.hisaab.util.HisabTextExporter
import com.palan.hisaab.util.HisabTextImporter
import com.palan.hisaab.util.toDisplayString
import com.palan.hisaab.viewmodel.AccountUiState
import com.palan.hisaab.viewmodel.AccountViewModel
import com.palan.hisaab.viewmodel.SettingsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    repository: HisaabRepository,
    settingsRepository: SettingsRepository,
    accountId: Long,
    onBack: () -> Unit,
    onOpenAccount: (Long) -> Unit
) {
    val factory = remember {
        viewModelFactory { initializer { AccountViewModel(repository, accountId) } }
    }
    val viewModel: AccountViewModel = viewModel(factory = factory)
    val settingsFactory = remember {
        viewModelFactory { initializer { SettingsViewModel(settingsRepository, repository) } }
    }
    val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory)
    val settings by settingsViewModel.settings.collectAsState()

    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showInitialBalanceDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }

    // Transactions the user just deleted: hidden immediately, actually removed
    // from the DB only once the Undo snackbar times out without being tapped.
    var hiddenTransactionIds by remember { mutableStateOf(setOf<Long>()) }
    val pendingDeleteJobs = remember { mutableMapOf<Long, Job>() }

    fun requestDeleteTransaction(txn: Transaction) {
        hiddenTransactionIds = hiddenTransactionIds + txn.id
        pendingDeleteJobs[txn.id]?.cancel()
        pendingDeleteJobs[txn.id] = coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Deleted \"${txn.description}\"",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                hiddenTransactionIds = hiddenTransactionIds - txn.id
            } else {
                viewModel.deleteTransaction(txn)
                hiddenTransactionIds = hiddenTransactionIds - txn.id
            }
            pendingDeleteJobs.remove(txn.id)
        }
    }

    val visibleTransactions = state.transactions.filterNot { it.id in hiddenTransactionIds }

    fun showMessage(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(buildShareText(state).toByteArray())
            }
            showMessage("Exported ${state.accountName} to file")
        } catch (e: Exception) {
            showMessage("Couldn't write the file")
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            when {
                text == null -> showMessage("Couldn't read that file")
                HisabTextImporter.isFullBackup(text) -> showMessage("That's a full backup file — import it from Settings instead")
                else -> {
                    val parsed = HisabTextImporter.parse(text)
                    if (parsed == null || parsed.accountName.isBlank()) {
                        showMessage("Couldn't recognize that file's format")
                    } else {
                        viewModel.importHisab(parsed) { newId -> onOpenAccount(newId) }
                    }
                }
            }
        } catch (e: Exception) {
            showMessage("Couldn't read that file")
        }
    }

    Scaffold(
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
                        val text = buildShareText(state)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share ${state.accountName} Hisab"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share Hisab")
                    }
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename account") },
                            onClick = { showOverflowMenu = false; showRenameDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as text file") },
                            onClick = {
                                showOverflowMenu = false
                                exportFileLauncher.launch("${state.accountName.ifBlank { "Hisab" }}.txt")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import from text file") },
                            onClick = {
                                showOverflowMenu = false
                                importFileLauncher.launch(arrayOf("text/plain"))
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete account", color = MaterialTheme.colorScheme.error) },
                            onClick = { showOverflowMenu = false; showDeleteAccountConfirm = true }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            if (visibleTransactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No transactions yet.\nTap + to add Received, Spent, or a Loan.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleTransactions, key = { it.id }) { txn ->
                        TransactionRow(
                            transaction = txn,
                            onClick = { editingTransaction = txn }
                        )
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
                editingTransaction = null
                requestDeleteTransaction(txn)
            }
        )
    }

    if (showRenameDialog) {
        var renameText by remember { mutableStateOf(state.accountName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename account") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameAccount(renameText.trim())
                        showRenameDialog = false
                    },
                    enabled = renameText.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            title = { Text("Delete ${state.accountName}?") },
            text = { Text("This permanently deletes the account and all ${state.transactions.size} transaction(s) in it. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAccountConfirm = false
                    viewModel.deleteAccount(onDone = onBack)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) { Text("Cancel") }
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
}

@Composable
private fun BalanceHeader(state: AccountUiState, onEditInitialBalance: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Current Balance", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = Money.format(state.balance),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (state.balance < 0) RedSpent else MaterialTheme.colorScheme.primary
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

@Composable
private fun TransactionRow(transaction: Transaction, onClick: () -> Unit) {
    val isPositive = transaction.type == TransactionType.RECEIVED || transaction.type == TransactionType.LOAN_GIVEN
    val typeLabel = when (transaction.type) {
        TransactionType.LOAN_GIVEN -> "Loan given"
        TransactionType.LOAN_TAKEN -> "Loan taken"
        else -> null
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.description, fontWeight = FontWeight.Medium)
                val dateText = transaction.date?.let { Date(it).toDisplayString() } ?: "No date"
                val meta = listOfNotNull(dateText, typeLabel, transaction.category).joinToString(" • ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = Money.formatSigned(transaction.amountMinor, transaction.type),
                fontWeight = FontWeight.Bold,
                color = if (isPositive) GreenReceived else RedSpent
            )
        }
    }
}

private fun buildShareText(state: AccountUiState): String =
    HisabTextExporter.buildAccountText(
        accountName = state.accountName,
        initialBalanceMinor = state.initialBalance,
        transactions = state.transactions
    )
