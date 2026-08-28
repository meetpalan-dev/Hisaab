package com.palan.hisaab.ui.splitbill

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.util.Money
import com.palan.hisaab.util.SplitBill
import com.palan.hisaab.util.SplitBillCalculator
import com.palan.hisaab.util.SplitBillTextFormat
import com.palan.hisaab.util.SplitParticipant
import com.palan.hisaab.viewmodel.SplitBillViewModel
import kotlinx.coroutines.launch

private enum class SplitMode { EQUAL, CUSTOM }

private class ParticipantFieldState(name: String = "", amountText: String = "") {
    var name by mutableStateOf(name)
    var amountText by mutableStateOf(amountText)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitBillScreen(
    repository: HisaabRepository,
    onBack: () -> Unit
) {
    val factory = remember { viewModelFactory { initializer { SplitBillViewModel(repository) } } }
    val viewModel: SplitBillViewModel = viewModel(factory = factory)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var titleText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SplitMode.EQUAL) }
    var totalText by remember { mutableStateOf("") }
    var paidByText by remember { mutableStateOf("Me") }
    val participants = remember {
        mutableStateListOf(ParticipantFieldState(name = "Me"), ParticipantFieldState())
    }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showPasteImportDialog by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun buildBill(): SplitBill? {
        val validRows = participants.filter { it.name.isNotBlank() }
        if (validRows.isEmpty()) return null
        val shares: List<SplitParticipant> = if (mode == SplitMode.EQUAL) {
            val total = Money.tryParseRupeesToMinor(totalText) ?: return null
            if (total <= 0) return null
            SplitBillCalculator.splitEqually(total, validRows.map { it.name.trim() })
        } else {
            validRows.map { row ->
                val amount = Money.tryParseRupeesToMinor(row.amountText) ?: return null
                if (amount <= 0) return null
                SplitParticipant(row.name.trim(), amount)
            }
        }
        return SplitBill(
            title = titleText.trim().ifBlank { "Split Bill" },
            paidBy = paidByText.trim().ifBlank { "Me" },
            participants = shares
        )
    }

    fun loadBill(bill: SplitBill) {
        titleText = bill.title
        paidByText = bill.paidBy
        mode = SplitMode.CUSTOM
        participants.clear()
        bill.participants.forEach { p ->
            participants.add(ParticipantFieldState(name = p.name, amountText = Money.format(p.amountMinor, withSymbol = false)))
        }
    }

    val bill = buildBill()

    val exportFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val b = bill
        if (uri == null || b == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(SplitBillTextFormat.build(b).toByteArray()) }
            showMessage("Exported to file")
        } catch (e: Exception) {
            showMessage("Couldn't write the file")
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            val parsed = text?.let { SplitBillTextFormat.parse(it) }
            if (parsed == null) {
                showMessage("Couldn't recognize that file's format")
            } else {
                loadBill(parsed)
                showMessage("Loaded \"${parsed.title}\"")
            }
        } catch (e: Exception) {
            showMessage("Couldn't read that file")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split Bill", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Import from text") },
                            onClick = { showOverflowMenu = false; showPasteImportDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Import from text file") },
                            onClick = { showOverflowMenu = false; importFileLauncher.launch(arrayOf("text/plain")) }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text("What's this for?") },
                placeholder = { Text("Dinner, Trip, Rent…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.padding(top = 12.dp))

            Row {
                FilterChip(
                    selected = mode == SplitMode.EQUAL,
                    onClick = { mode = SplitMode.EQUAL },
                    label = { Text("Split equally") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = mode == SplitMode.CUSTOM,
                    onClick = { mode = SplitMode.CUSTOM },
                    label = { Text("Custom amounts") }
                )
            }

            if (mode == SplitMode.EQUAL) {
                Spacer(Modifier.padding(top = 12.dp))
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it },
                    label = { Text("Total bill amount") },
                    placeholder = { Text("₹ 0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.padding(top = 16.dp))
            Text("Participants", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.padding(top = 4.dp))

            participants.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = row.name,
                        onValueChange = { row.name = it },
                        placeholder = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (mode == SplitMode.CUSTOM) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = row.amountText,
                            onValueChange = { row.amountText = it },
                            placeholder = { Text("₹ 0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(110.dp)
                        )
                    }
                    IconButton(onClick = { participants.removeAt(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${row.name.ifBlank { "participant" }}")
                    }
                }
            }
            TextButton(onClick = { participants.add(ParticipantFieldState()) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add participant")
            }

            Spacer(Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = paidByText,
                onValueChange = { paidByText = it },
                label = { Text("Paid by") },
                supportingText = { Text("Type \"Me\" if you paid, or a participant's name if someone else did") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.padding(top = 20.dp))

            if (bill == null) {
                Text(
                    if (mode == SplitMode.EQUAL)
                        "Enter a valid total and at least one named participant."
                    else
                        "Enter a valid amount for each named participant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Total: ${Money.format(bill.totalMinor)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.padding(top = 8.dp))
                        val owed = bill.owedShares()
                        if (owed.isEmpty()) {
                            Text("Only one participant — nothing to split.")
                        } else {
                            owed.forEach { p ->
                                Text("${p.name} owes ${bill.paidBy}: ${Money.format(p.amountMinor)}")
                            }
                        }
                    }
                }

                Spacer(Modifier.padding(top = 16.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, SplitBillTextFormat.build(bill))
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share ${bill.title}"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = {
                        val safeName = bill.title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "SplitBill" }.replace(" ", "_")
                        exportFileLauncher.launch("$safeName.txt")
                    }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Export as text file")
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = {
                        viewModel.addToHisaab(bill) { result ->
                            result.onSuccess { count ->
                                showMessage(if (count == 1) "Added 1 entry to Hisaab" else "Added $count entries to Hisaab")
                            }.onFailure { e ->
                                showMessage(e.message ?: "Couldn't add to Hisaab")
                            }
                        }
                    }) { Text("Add to Hisaab") }
                }
            }

            Spacer(Modifier.padding(bottom = 32.dp))
        }
    }

    if (showPasteImportDialog) {
        SplitBillImportDialog(
            onDismiss = { showPasteImportDialog = false },
            onImport = { parsed ->
                loadBill(parsed)
                showPasteImportDialog = false
                showMessage("Loaded \"${parsed.title}\"")
            }
        )
    }
}
