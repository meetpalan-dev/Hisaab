package com.palan.hisaab.ui.split

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.data.SplitOverallStatus
import com.palan.hisaab.data.SplitParticipantStatus
import com.palan.hisaab.data.SplitStatusSummary
import com.palan.hisaab.ui.theme.GreenReceived
import com.palan.hisaab.ui.theme.RedSpent
import com.palan.hisaab.ui.theme.Spacing
import com.palan.hisaab.util.Money
import com.palan.hisaab.util.toDisplayString
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitHistoryScreen(
    repository: HisaabRepository,
    onBack: () -> Unit,
    onOpenSplitDetails: (Long) -> Unit
) {
    val splits by repository.observeSplitHistoryWithStatus().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (splits.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.normal),
            ) {
                Text(
                    "No splits yet — expenses you split will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.normal),
                verticalArrangement = Arrangement.spacedBy(Spacing.tight)
            ) {
                items(splits, key = { it.record.id }) { split ->
                    SplitHistoryCard(split, onClick = { onOpenSplitDetails(split.record.id) })
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: SplitOverallStatus): Pair<String, Color> = when (status) {
    SplitOverallStatus.ACTIVE -> "Active" to MaterialTheme.colorScheme.onSurfaceVariant
    SplitOverallStatus.PARTIALLY_SETTLED -> "Partially Settled" to MaterialTheme.colorScheme.primary
    SplitOverallStatus.FULLY_SETTLED -> "Fully Settled" to GreenReceived
}

@Composable
private fun SplitHistoryCard(split: SplitStatusSummary, onClick: () -> Unit) {
    val (statusText, statusColor) = statusLabel(split.overallStatus)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(Spacing.internal)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(split.record.description, fontWeight = FontWeight.SemiBold)
                Text(Money.format(split.record.totalMinor), fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${Date(split.record.date).toDisplayString()} • Paid by ${split.record.payerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor, fontWeight = FontWeight.SemiBold)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
            split.participants.sortedByDescending { it.isPayer }.forEach { p ->
                ParticipantStatusRow(p)
            }
            if (split.totalRecoveredMinor > 0L || split.totalRemainingMinor > 0L) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Recovered: ${Money.format(split.totalRecoveredMinor)}", style = MaterialTheme.typography.labelSmall, color = GreenReceived)
                    Text("Remaining: ${Money.format(split.totalRemainingMinor)}", style = MaterialTheme.typography.labelSmall, color = RedSpent)
                }
            }
        }
    }
}

@Composable
private fun ParticipantStatusRow(p: SplitParticipantStatus) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            p.name + when {
                p.isPayer -> " (paid)"
                !p.recorded -> " (not tracked)"
                p.settled -> " — Settled"
                p.recoveredMinor > 0L -> " — ${Money.format(p.remainingMinor)} remaining"
                else -> " — Unsettled"
            },
            style = MaterialTheme.typography.bodySmall,
            color = when {
                p.isPayer -> MaterialTheme.colorScheme.onSurface
                !p.recorded -> MaterialTheme.colorScheme.onSurfaceVariant
                p.settled -> GreenReceived
                p.recoveredMinor > 0L -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        Text(Money.format(p.amountMinor), style = MaterialTheme.typography.bodySmall)
    }
}
