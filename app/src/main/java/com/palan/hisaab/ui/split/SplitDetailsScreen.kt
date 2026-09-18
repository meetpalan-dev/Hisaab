package com.palan.hisaab.ui.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.data.SplitOverallStatus
import com.palan.hisaab.data.SplitParticipantStatus
import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.ui.theme.GreenReceived
import com.palan.hisaab.ui.theme.RedSpent
import com.palan.hisaab.ui.theme.Spacing
import com.palan.hisaab.util.Money
import com.palan.hisaab.util.toDisplayString
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Full detail view for a single Split — description, who paid, each participant's live
 * settlement state, a merged settlement-history timeline across every participant, and a summary.
 * This is what tapping any Split-linked transaction opens instead of the generic Edit Transaction
 * dialog; amounts/participants aren't editable here on purpose (see [HisaabRepository.renameSplit]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitDetailsScreen(
    repository: HisaabRepository,
    splitId: Long,
    onBack: () -> Unit
) {
    val status by repository.observeSplitStatus(splitId).collectAsState(initial = null)
    var settlementHistory by remember { mutableStateOf<List<Triple<String, Transaction, Long>>>(emptyList()) }
    var showEditSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(status) {
        val s = status ?: return@LaunchedEffect
        settlementHistory = s.participants
            .filter { !it.isPayer && it.transactionId != null }
            .flatMap { p -> repository.getSettlementHistory(p.transactionId!!).map { (txn, amt) -> Triple(p.name, txn, amt) } }
            .sortedBy { (_, txn, _) -> txn.date ?: 0L }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditSheet = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Split")
                    }
                }
            )
        }
    ) { padding ->
        val s = status
        if (s == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.normal)) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.normal)
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(Money.format(s.record.totalMinor), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "${s.record.description} • ${Date(s.record.date).toDisplayString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val (label, color) = statusLabel(s.overallStatus)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = Spacing.section))

            DetailCard(title = "Paid by") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.record.payerName)
                    Text("${Money.format(s.record.totalMinor)} fronted", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            DetailCard(title = "Participants") {
                s.participants.forEach { p -> ParticipantLine(p) }
            }

            DetailCard(title = "Settlement History") {
                if (settlementHistory.isEmpty()) {
                    Text("No repayments yet", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else {
                    settlementHistory.forEach { (name, txn, amount) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${Date(txn.date ?: 0L).toDisplayString()} — $name", style = MaterialTheme.typography.bodySmall)
                            Text(Money.format(amount), style = MaterialTheme.typography.bodySmall, color = GreenReceived)
                        }
                    }
                }
            }

            DetailCard(title = "Summary") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Recovered"); Text(Money.format(s.totalRecoveredMinor), color = GreenReceived)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Remaining"); Text(Money.format(s.totalRemainingMinor), color = RedSpent)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${s.record.payerName}'s effective cost")
                    Text(Money.format(s.record.totalMinor - s.totalRecoveredMinor), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showEditSheet) {
        var text by remember { mutableStateOf(status?.record?.description ?: "") }
        AlertDialog(
            onDismissRequest = { showEditSheet = false },
            title = { Text("Edit Split") },
            text = {
                Column {
                    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Description") })
                    Text(
                        "Amounts, participants, and who paid can't be changed here — settlements already recorded against the original shares would no longer add up.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch { repository.renameSplit(splitId, text) }
                    showEditSheet = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditSheet = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun statusLabel(status: SplitOverallStatus): Pair<String, Color> = when (status) {
    SplitOverallStatus.ACTIVE -> "Active" to MaterialTheme.colorScheme.onSurfaceVariant
    SplitOverallStatus.PARTIALLY_SETTLED -> "Partially Settled" to MaterialTheme.colorScheme.primary
    SplitOverallStatus.FULLY_SETTLED -> "Fully Settled" to GreenReceived
}

@Composable
private fun DetailCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.tight),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(Spacing.internal)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
            content()
        }
    }
}

@Composable
private fun ParticipantLine(p: SplitParticipantStatus) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            p.name + when {
                p.isPayer -> " (own share)"
                !p.recorded -> " (not tracked)"
                p.settled -> " — Settled"
                p.recoveredMinor > 0L -> " — ${Money.format(p.remainingMinor)} remaining"
                else -> " — Unsettled"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                p.isPayer -> MaterialTheme.colorScheme.onSurface
                !p.recorded -> MaterialTheme.colorScheme.onSurfaceVariant
                p.settled -> GreenReceived
                p.recoveredMinor > 0L -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        Text(Money.format(p.amountMinor), style = MaterialTheme.typography.bodyMedium)
    }
}
