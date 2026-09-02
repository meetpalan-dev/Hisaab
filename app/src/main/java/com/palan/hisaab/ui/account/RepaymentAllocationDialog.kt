package com.palan.hisaab.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.palan.hisaab.data.RepaymentAllocationInput
import com.palan.hisaab.data.dao.OutstandingHisaab
import com.palan.hisaab.data.entity.TransactionType
import com.palan.hisaab.util.Money

/**
 * Shown after the user enables "Repayment / Settle Hisaab" on a new Received/Spent
 * transaction. Lets them choose which outstanding hisaab(s) the payment covers,
 * with a manual amount per hisaab — "Oldest first" just pre-fills a sensible
 * starting point, it never allocates without the user seeing and confirming it.
 */
@Composable
fun RepaymentAllocationDialog(
    repaymentType: TransactionType,
    amountMinor: Long,
    description: String,
    outstanding: List<OutstandingHisaab>,
    onDismiss: () -> Unit,
    onConfirm: (List<RepaymentAllocationInput>) -> Unit
) {
    // transactionId -> raw rupee text the user typed for that row
    var allocationText by remember { mutableStateOf(mapOf<Long, String>()) }

    fun allocatedFor(id: Long): Long =
        runCatching { Money.rupeeStringToMinor(allocationText[id].orEmpty()) }.getOrDefault(0L)

    val totalAllocated = outstanding.sumOf { allocatedFor(it.transaction.id) }
    val remainingToAllocate = (amountMinor - totalAllocated).coerceAtLeast(0L)
    val overAllocated = totalAllocated > amountMinor
    val anyRowOverOutstanding = outstanding.any { allocatedFor(it.transaction.id) > it.remainingMinor }
    val canConfirm = totalAllocated > 0 && !overAllocated && !anyRowOverOutstanding

    fun fillOldestFirst() {
        var left = amountMinor
        val next = mutableMapOf<Long, String>()
        for (item in outstanding) {
            if (left <= 0) break
            val take = minOf(left, item.remainingMinor)
            if (take > 0) {
                next[item.transaction.id] = Money.format(take, withSymbol = false)
                left -= take
            }
        }
        allocationText = next
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allocate repayment") },
        text = {
            Column {
                Text(
                    "\"$description\" — ${Money.format(amountMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (outstanding.isEmpty()) {
                    Text(
                        "No outstanding hisaab to settle here yet.",
                        modifier = Modifier.padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    TextButton(onClick = { fillOldestFirst() }, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Oldest first")
                    }

                    LazyColumn(
                        modifier = Modifier.padding(top = 4.dp).heightIn(max = 320.dp)
                    ) {
                        items(outstanding, key = { it.transaction.id }) { item ->
                            OutstandingRow(
                                item = item,
                                valueText = allocationText[item.transaction.id].orEmpty(),
                                onValueChange = { text ->
                                    allocationText = allocationText.toMutableMap().apply { put(item.transaction.id, text) }
                                }
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Allocated", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(totalAllocated), fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Remaining", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            Money.format(remainingToAllocate),
                            fontWeight = FontWeight.SemiBold,
                            color = if (overAllocated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (overAllocated) {
                        Text(
                            "Allocated more than the repayment amount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (anyRowOverOutstanding) {
                        Text(
                            "One or more amounts exceed what's still outstanding",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    val allocations = outstanding.mapNotNull { item ->
                        val amt = allocatedFor(item.transaction.id)
                        if (amt > 0) RepaymentAllocationInput(item.transaction.id, amt) else null
                    }
                    onConfirm(allocations)
                }
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun OutstandingRow(
    item: OutstandingHisaab,
    valueText: String,
    onValueChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.transaction.description, fontWeight = FontWeight.Medium)
                Text(
                    "Outstanding ${Money.format(item.remainingMinor)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = valueText,
                onValueChange = onValueChange,
                placeholder = { Text("₹ 0") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(110.dp)
            )
        }
    }
}
