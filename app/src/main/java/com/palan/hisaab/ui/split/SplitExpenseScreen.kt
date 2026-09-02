package com.palan.hisaab.ui.split

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.palan.hisaab.ui.common.InitialsAvatar
import com.palan.hisaab.ui.theme.Spacing
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
    val amountLocked: Boolean = false,
    val percentText: String = "",
    val percentLocked: Boolean = false,
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
    val exactMatch = remember(participantInput, allAccounts) {
        allAccounts.any { it.name.equals(participantInput.trim(), ignoreCase = true) }
    }
    val alreadyAdded = remember(participantInput, participants) {
        participants.any { it.name.equals(participantInput.trim(), ignoreCase = true) }
    }

    fun addParticipant(name: String, accountId: Long?, phone: String? = null) {
        if (name.isBlank()) return
        if (participants.any { it.name.equals(name.trim(), ignoreCase = true) }) {
            participantInput = ""
            return
        }
        participants = participants + Participant(name = name.trim(), existingAccountId = accountId, phoneNumber = phone)
        participantInput = ""
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
            Box(modifier = Modifier.fillMaxWidth().padding(Spacing.normal)) {
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
                ) { Text("Split Expense") }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxWidth().padding(Spacing.normal)) {
                Text("Total amount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it },
                    placeholder = { Text("₹ 0") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
                Text(
                    "Description",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.internal)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Dinner") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )

                Text(
                    "Split mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.section, bottom = 8.dp)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SplitMode.entries.forEachIndexed { index, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = SplitMode.entries.size)
                        ) { Text(m.label) }
                    }
                }

                Text(
                    "Participants",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.section, bottom = 8.dp)
                )
                OutlinedTextField(
                    value = participantInput,
                    onValueChange = { participantInput = it },
                    placeholder = { Text("Search or add person…") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (participantInput.isNotBlank()) {
                                    val exact = allAccounts.firstOrNull { it.name.equals(participantInput.trim(), ignoreCase = true) }
                                    addParticipant(participantInput, exact?.id, exact?.phoneNumber)
                                }
                            },
                            enabled = participantInput.isNotBlank()
                        ) { Icon(Icons.Filled.Add, contentDescription = "Add person") }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (participantInput.isNotBlank()) {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        suggestions.forEach { acc ->
                            SuggestionRow(
                                title = acc.name,
                                subtitle = acc.phoneNumber,
                                onClick = { addParticipant(acc.name, acc.id, acc.phoneNumber) }
                            )
                        }
                        if (!exactMatch && !alreadyAdded) {
                            SuggestionRow(
                                title = "Add \"${participantInput.trim()}\" as new account",
                                subtitle = null,
                                icon = Icons.Filled.PersonAdd,
                                onClick = { addParticipant(participantInput, null) }
                            )
                        }
                    }
                }
            }

            val shareAmounts = computeShares(mode, totalMinor, participants)

            if (mode == SplitMode.AMOUNT || mode == SplitMode.PERCENT) {
                AllocationSummary(mode = mode, totalMinor = totalMinor, participants = participants)
            }

            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.normal),
                verticalArrangement = Arrangement.spacedBy(Spacing.tight)
            ) {
                items(participants.size) { index ->
                    val p = participants[index]
                    ParticipantRow(
                        participant = p,
                        mode = mode,
                        shareAmountMinor = shareAmounts.getOrElse(index) { 0L },
                        onAmountChange = { text ->
                            participants = participants.toMutableList().also {
                                it[index] = p.copy(amountText = text, amountLocked = text.isNotBlank())
                            }
                        },
                        onPercentChange = { text ->
                            participants = participants.toMutableList().also {
                                it[index] = p.copy(percentText = text, percentLocked = text.isNotBlank())
                            }
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
private fun SuggestionRow(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
        }
        Column {
            Text(title, color = if (icon != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AllocationSummary(mode: SplitMode, totalMinor: Long, participants: List<Participant>) {
    val assignedMinor = when (mode) {
        SplitMode.AMOUNT -> participants.filter { it.amountLocked }.sumOf {
            runCatching { Money.rupeeStringToMinor(it.amountText) }.getOrDefault(0L)
        }
        SplitMode.PERCENT -> participants.filter { it.percentLocked }.sumOf {
            val pct = it.percentText.toDoubleOrNull() ?: 0.0
            (totalMinor * pct / 100.0).toLong()
        }
        else -> 0L
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.normal, vertical = Spacing.tight),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(Spacing.internal)) {
            when (mode) {
                SplitMode.AMOUNT -> {
                    SummaryLine("Total", Money.format(totalMinor))
                    SummaryLine("Assigned", Money.format(assignedMinor))
                    SummaryLine("Remaining", Money.format((totalMinor - assignedMinor).coerceAtLeast(0)))
                }
                SplitMode.PERCENT -> {
                    val assignedPct = participants.filter { it.percentLocked }.sumOf { it.percentText.toDoubleOrNull() ?: 0.0 }
                    SummaryLine("Total", "100%")
                    SummaryLine("Assigned", "${assignedPct.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }}%")
                    SummaryLine("Remaining", "${(100.0 - assignedPct).coerceAtLeast(0.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }}%")
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.internal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsAvatar(name = participant.name, size = 34.dp)
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
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
            if (!participant.isSelf) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                }
            }
        }
    }
}

private fun computeShares(mode: SplitMode, totalMinor: Long, participants: List<Participant>): List<Long> {
    if (participants.isEmpty() || totalMinor <= 0) return emptyList()
    return when (mode) {
        SplitMode.EVENLY -> evenSplit(totalMinor, participants.size)
        SplitMode.AMOUNT -> {
            val locked = participants.map { p ->
                if (p.amountLocked) runCatching { Money.rupeeStringToMinor(p.amountText) }.getOrDefault(0L) else null
            }
            distributeWithLocked(totalMinor, locked)
        }
        SplitMode.PERCENT -> {
            val locked = participants.map { p ->
                if (p.percentLocked) {
                    val pct = p.percentText.toDoubleOrNull() ?: 0.0
                    (totalMinor * pct / 100.0).toLong()
                } else null
            }
            distributeWithLocked(totalMinor, locked)
        }
        SplitMode.SHARES -> {
            val totalShares = participants.sumOf { it.shareCount }
            if (totalShares == 0) participants.map { 0L }
            else participants.map { p -> totalMinor * p.shareCount / totalShares }
        }
    }
}

private fun evenSplit(totalMinor: Long, count: Int): List<Long> {
    val base = totalMinor / count
    val remainder = totalMinor - base * count
    return (0 until count).map { i -> base + if (i < remainder) 1 else 0 }
}

/**
 * Amounts marked locked (the user typed them) are taken as-is. Whatever's left
 * of the total is divided evenly across the remaining (unlocked) participants —
 * so entering "Me: ₹100" on a ₹500 bill leaves ₹400 to auto-split across
 * everyone else, and keeps recalculating as more amounts are typed or cleared.
 */
private fun distributeWithLocked(totalMinor: Long, locked: List<Long?>): List<Long> {
    val lockedSum = locked.filterNotNull().sum()
    val unlockedIndices = locked.indices.filter { locked[it] == null }
    val remainder = (totalMinor - lockedSum).coerceAtLeast(0L)
    val autoShares = if (unlockedIndices.isEmpty()) emptyList() else evenSplit(remainder, unlockedIndices.size)
    var autoIdx = 0
    return locked.indices.map { i ->
        locked[i] ?: autoShares[autoIdx++]
    }
}
