package com.palan.hisaab.ui.account

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import com.palan.hisaab.ui.addtransaction.AddEditTransactionDialog
import com.palan.hisaab.ui.theme.GreenReceived
import com.palan.hisaab.ui.theme.RedSpent
import com.palan.hisaab.util.Money
import com.palan.hisaab.util.toDisplayString
import com.palan.hisaab.viewmodel.AccountUiState
import com.palan.hisaab.viewmodel.AccountViewModel
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    repository: HisaabRepository,
    accountId: Long,
    onBack: () -> Unit
) {
    val factory = remember {
        viewModelFactory { initializer { AccountViewModel(repository, accountId) } }
    }
    val viewModel: AccountViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var transactionPendingDelete by remember { mutableStateOf<Transaction?>(null) }
    var showInitialBalanceDialog by remember { mutableStateOf(false) }

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

            if (state.transactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No transactions yet.\nTap + to add Received or Spent.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.transactions, key = { it.id }) { txn ->
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
                color = MaterialTheme.colorScheme.primary
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
                Text(
                    Date(transaction.date).toDisplayString() + (transaction.category?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = Money.formatSigned(transaction.amountMinor, transaction.type),
                fontWeight = FontWeight.Bold,
                color = if (transaction.type == TransactionType.RECEIVED) GreenReceived else RedSpent
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
    state.transactions.sortedBy { it.date }.forEach { txn ->
        val sign = if (txn.type == TransactionType.RECEIVED) "+" else "-"
        sb.appendLine("${Date(txn.date).toDisplayString()} - ${txn.description} - $sign ${Money.format(txn.amountMinor, withSymbol = true)}")
    }
    sb.appendLine()
    sb.appendLine("Received Total: ${Money.format(state.received)}")
    sb.appendLine("Spent Total: ${Money.format(state.spent)}")
    sb.appendLine("Current Balance: ${Money.format(state.balance)}")
    return sb.toString()
}
