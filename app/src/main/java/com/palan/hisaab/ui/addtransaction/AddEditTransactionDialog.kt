package com.palan.hisaab.ui.addtransaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import com.palan.hisaab.util.Money
import com.palan.hisaab.util.toDisplayString
import java.util.Date

val DEFAULT_CATEGORIES = listOf(
    "Payment", "Food", "Shopping", "Recharge", "Bills", "Insurance", "Travel", "Loan", "Other"
)

private data class TypeOption(val type: TransactionType, val label: String)

// Loan Taken is no longer offered for new transactions — a "received money that's a loan"
// is now just Received, optionally tagged with the "Loan" category. It's appended back into
// the picker (further down) only when editing a transaction that's already that legacy type,
// so old data stays fully editable without reintroducing the type for new entries.
private val TYPE_OPTIONS = listOf(
    TypeOption(TransactionType.RECEIVED, "Received"),
    TypeOption(TransactionType.SPENT, "Spent"),
    TypeOption(TransactionType.LOAN_GIVEN, "Loan Given")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionDialog(
    existing: Transaction?,
    autoFillTodayDate: Boolean,
    onDismiss: () -> Unit,
    onSave: (type: TransactionType, amountMinor: Long, description: String, date: Long?, category: String?) -> Unit,
    onDelete: (() -> Unit)? = null,
    onToggleSettled: (() -> Unit)? = null,
    /** When set, a new Received/Spent transaction can be marked "Repayment / Settle Hisaab" — instead of saving normally, this is invoked so the caller can open the outstanding-hisaab allocation flow. Only offered when creating a new transaction (not editing one). */
    onStartRepayment: ((type: TransactionType, amountMinor: Long, description: String, date: Long?) -> Unit)? = null
) {
    var type by remember { mutableStateOf(existing?.type ?: TransactionType.SPENT) }
    var amountText by remember {
        mutableStateOf(existing?.let { Money.format(it.amountMinor, withSymbol = false) } ?: "")
    }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var date by remember {
        mutableStateOf(
            existing?.date ?: (if (autoFillTodayDate) System.currentTimeMillis() else null)
        )
    }
    var category by remember { mutableStateOf(existing?.category) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isRepayment by remember { mutableStateOf(false) }
    val repaymentEligible = existing == null && onStartRepayment != null &&
        (type == TransactionType.RECEIVED || type == TransactionType.SPENT)
    // If editing an existing legacy Loan Taken transaction, keep it selectable so
    // its type doesn't silently change out from under the user — just don't offer
    // it as a choice when adding something new.
    val typeOptions = if (existing?.type == TransactionType.LOAN_TAKEN) {
        TYPE_OPTIONS + TypeOption(TransactionType.LOAN_TAKEN, "Loan Taken (legacy)")
    } else TYPE_OPTIONS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Transaction" else "Edit Transaction") },
        text = {
            Column {
                Text(
                    "Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                ) {
                    items(typeOptions) { option ->
                        FilterChip(
                            selected = type == option.type,
                            onClick = { type = option.type },
                            label = { Text(option.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    placeholder = { Text("₹ 0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("What was this payment for?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )

                TextButton(onClick = { showDatePicker = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Date: " + (date?.let { Date(it).toDisplayString() } ?: "None (tap to set)"))
                }
                if (date != null) {
                    TextButton(onClick = { date = null }) { Text("Clear date") }
                }

                Text(
                    "Category (optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DEFAULT_CATEGORIES) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = if (category == cat) null else cat },
                            label = { Text(cat) }
                        )
                    }
                }

                if (repaymentEligible) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Repayment / Settle Hisaab", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (type == TransactionType.SPENT) "Pays off an outstanding hisaab you owe" else "Settles an outstanding hisaab owed to you",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = isRepayment, onCheckedChange = { isRepayment = it })
                    }
                }

                if (onToggleSettled != null) {
                    TextButton(onClick = onToggleSettled, modifier = Modifier.padding(top = 8.dp)) {
                        Text(if (existing?.settled == true) "Restore to Active" else "Mark as Settled")
                    }
                }

                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Delete transaction", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minor = Money.rupeeStringToMinor(amountText)
                    if (minor > 0 && description.isNotBlank()) {
                        if (isRepayment && repaymentEligible) {
                            onStartRepayment?.invoke(type, minor, description.trim(), date)
                        } else {
                            onSave(type, minor, description.trim(), date, category)
                        }
                    }
                }
            ) { Text(if (isRepayment && repaymentEligible) "Next" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    date = pickerState.selectedDateMillis ?: date
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
