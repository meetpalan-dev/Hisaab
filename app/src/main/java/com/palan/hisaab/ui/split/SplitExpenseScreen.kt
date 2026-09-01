package com.palan.hisaab.ui.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.data.SplitShare
import com.palan.hisaab.data.entity.Account
import com.palan.hisaab.util.Money
import kotlinx.coroutines.launch

private enum class SplitMode(val label: String) {
    EVENLY("Evenly"),
    AMOUNT("Amount"),
    PERCENT("Percent"),
    SHARES("Shares")
}

private data class Participant(
    val name: String,
    val existingAccountId: Long? = null,
    val phoneNumber: String? = null,
    val isSelf: Boolean = false,
    // raw editable inputs per mode; interpreted based on the active mode
    val amountText: String = "",
    val percentText: String = "",
    val shareCount: Int = 1
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitExpenseScreen(
    repository: HisaabRepository,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    var allAccounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    LaunchedEffect(Unit) { allAccounts = repository.getAllAccountsOnce() }

    var totalText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SplitMode.EVENLY) }
    var participants by remember { mutableStateOf(listOf(Participant(name = "Me", isSelf = true))) }
    var participantInput by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

    val totalMinor = runCatching { Money.rupeeStringToMinor(totalText) }.getOrDefault(0L)
    val coroutineScope = rememberCoroutineScope()

    val suggestions = remember(participantInput, allAccounts, participants) {
        if (participantInput.isBlank()) emptyList()
        else allAccounts.filter { acc ->
            participants.none { it.existingAccountId == acc.id } &&
                (acc.name.contains(participantInput, ignoreCase = true) ||
                    acc.phoneNumber?.contains(participantInput) == true)
        }
    }

    fun addParticipant(name: String, accountId: Long?, phone: String? = null) {
        if (name.isBlank()) return
        participants = participants + Participant(name = name.trim(), existingAccountId = accountId, phoneNumber = phone)
        participantInput = ""
        showSuggestions = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split Expense") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = {
                        val shares = computeShares(mode, totalMinor, participants)
                        coroutineScope.launch {
                            repository.applySplit(
                                description = description,
                                shares = shares.mapIndexed { i, minor ->
                                    SplitShare(
                                        name = participants[i].name,
                                        amountMinor = minor,
                                        existingAccountId = participants[i].existingAccountId,
                                        isSelf = participants[i].isSelf
                                    )
                                },
                                date = System.currentTimeMillis()
                            )
                            onDone()
                        }
                    },
                    enabled = totalMinor > 0 && participants.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Split") }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Total amount", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it },
                    placeholder = { Text("₹ 0") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("What's this for?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }

            TabRow(selectedTabIndex = mode.ordinal) {
                SplitMode.values().forEach { m ->
                    Tab(
                        selected = mode == m,
                        onClick = { mode = m },
                        text = { Text(m.label) }
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Box {
                    OutlinedTextField(
                        value = participantInput,
                        onValueChange = { participantInput = it; showSuggestions = true },
                        placeholder = { Text("Add participant by name or phone…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = showSuggestions && suggestions.isNotEmpty(),
                        onDismissRequest = { showSuggestions = false }
                    ) {
                        suggestions.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name + (acc.phoneNumber?.let { " • $it" } ?: "")) },
                                onClick = { addParticipant(acc.name, acc.id, acc.phoneNumber) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Add \"$participantInput\" as a new person") },
                            onClick = { addParticipant(participantInput, null) }
                        )
                    }
                }
            }

            val shareAmounts = computeShares(mode, totalMinor, participants)

            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(participants.size) { index ->
                    val p = participants[index]
                    ParticipantRow(
                        participant = p,
                        mode = mode,
                        shareAmountMinor = shareAmounts.getOrElse(index) { 0L },
                        onAmountChange = { text ->
                            participants = participants.toMutableList().also { it[index] = p.copy(amountText = text) }
                        },
                        onPercentChange = { text ->
                            participants = participants.toMutableList().also { it[index] = p.copy(percentText = text) }
                        },
                        onSharesChange = { count ->
                            participants = participants.toMutableList().also { it[index] = p.copy(shareCount = count.coerceAtLeast(0)) }
                        },
                        onRemove = { participants = participants.toMutableList().also { it.removeAt(index) } }
                    )
                }
                item { androidx.compose.foundation.layout.Spacer(Modifier.padding(60.dp)) }
            }
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: Participant,
    mode: SplitMode,
    shareAmountMinor: Long,
    onAmountChange: (String) -> Unit,
    onPercentChange: (String) -> Unit,
    onSharesChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(participant.name + if (participant.isSelf) " (You)" else "", fontWeight = FontWeight.Medium)
                Text(
                    Money.format(shareAmountMinor),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when (mode) {
                SplitMode.EVENLY -> {}
                SplitMode.AMOUNT -> OutlinedTextField(
                    value = participant.amountText,
                    onValueChange = onAmountChange,
                    singleLine = true,
                    modifier = Modifier.width(100.dp)
                )
                SplitMode.PERCENT -> OutlinedTextField(
                    value = participant.percentText,
                    onValueChange = onPercentChange,
                    singleLine = true,
                    modifier = Modifier.width(80.dp)
                )
                SplitMode.SHARES -> Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSharesChange(participant.shareCount - 1) }) { Text("−") }
                    Text("${participant.shareCount}")
                    IconButton(onClick = { onSharesChange(participant.shareCount + 1) }) { Text("+") }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove")
            }
        }
    }
}

private fun computeShares(mode: SplitMode, totalMinor: Long, participants: List<Participant>): List<Long> {
    if (participants.isEmpty() || totalMinor <= 0) return emptyList()
    return when (mode) {
        SplitMode.EVENLY -> {
            val base = totalMinor / participants.size
            val remainder = totalMinor - base * participants.size
            participants.indices.map { i -> base + if (i < remainder) 1 else 0 }
        }
        SplitMode.AMOUNT -> participants.map { runCatching { Money.rupeeStringToMinor(it.amountText) }.getOrDefault(0L) }
        SplitMode.PERCENT -> participants.map { p ->
            val pct = p.percentText.toDoubleOrNull() ?: 0.0
            (totalMinor * pct / 100.0).toLong()
        }
        SplitMode.SHARES -> {
            val totalShares = participants.sumOf { it.shareCount }
            if (totalShares == 0) participants.map { 0L }
            else participants.map { p -> totalMinor * p.shareCount / totalShares }
        }
    }
}
