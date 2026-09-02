package com.palan.hisaab.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.palan.hisaab.data.AccountSummary
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.ui.common.InitialsAvatar
import com.palan.hisaab.ui.createaccount.CreateAccountDialog
import com.palan.hisaab.ui.theme.Spacing
import com.palan.hisaab.util.Money
import com.palan.hisaab.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: HisaabRepository,
    onOpenAccount: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSplit: () -> Unit
) {
    val factory = remember {
        viewModelFactory { initializer { HomeViewModel(repository) } }
    }
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val summaries by viewModel.accountSummaries.collectAsState()
    val query by viewModel.query.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val json = repository.exportAllToJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!json.isNullOrBlank()) repository.importFromJson(json)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("हिसाब", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Import Hisab") },
                            onClick = { showMenu = false; showImportDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Backup") },
                            onClick = { showMenu = false; exportBackupLauncher.launch("hisaab_backup.json") }
                        )
                        DropdownMenuItem(
                            text = { Text("Import Backup") },
                            onClick = { showMenu = false; importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            HomeFab(
                expanded = fabExpanded,
                onToggle = { fabExpanded = !fabExpanded },
                onSplit = { fabExpanded = false; onOpenSplit() },
                onNewAccount = { fabExpanded = false; showCreateDialog = true }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.normal, vertical = Spacing.tight),
                placeholder = { Text("Search accounts or people") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )

            if (summaries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No accounts yet.\nTap + to add Rudra, Cash Balance, or anyone else.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.normal, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.tight)
                ) {
                    items(summaries, key = { it.account.id }) { summary ->
                        AccountCard(summary = summary, onClick = { onOpenAccount(summary.account.id) })
                    }
                    item { androidx.compose.foundation.layout.Spacer(Modifier.padding(40.dp)) }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateAccountDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, startingBalance, phoneNumber ->
                viewModel.createAccount(name, startingBalance, phoneNumber) { newId ->
                    showCreateDialog = false
                    onOpenAccount(newId)
                }
            }
        )
    }

    if (showImportDialog) {
        ImportHisabDialog(
            onDismiss = { showImportDialog = false },
            onImport = { parsed ->
                viewModel.importHisab(parsed) { newId ->
                    showImportDialog = false
                    onOpenAccount(newId)
                }
            }
        )
    }
}

/**
 * Collapsed: a single [+] FAB. Expanded: "Split Expense" and "New Account" extended
 * FABs stack above it. Tapping the main FAB (now showing ×) or either action collapses it again.
 */
@Composable
private fun HomeFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    onSplit: () -> Unit,
    onNewAccount: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = onSplit,
                    icon = { Icon(Icons.Filled.CallSplit, contentDescription = null) },
                    text = { Text("Split Expense") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                ExtendedFloatingActionButton(
                    onClick = onNewAccount,
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    text = { Text("New Account") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
            }
        }
        FloatingActionButton(onClick = onToggle) {
            Icon(
                if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = if (expanded) "Close" else "Add"
            )
        }
    }
}

@Composable
private fun AccountCard(summary: AccountSummary, onClick: () -> Unit) {
    val isNegative = summary.balance < 0
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsAvatar(name = summary.account.name)
            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isNegative) {
                    Text(
                        "You owe",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = Money.format(summary.balance),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}
