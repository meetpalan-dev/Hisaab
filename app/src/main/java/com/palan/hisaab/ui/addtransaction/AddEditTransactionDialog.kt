package com.palan.hisaab.ui.addtransaction

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import com.palan.hisaab.util.Money
import com.palan.hisaab.util.toDisplayString
import java.util.Calendar
import java.util.Date

val DEFAULT_CATEGORIES = listOf(
    "Payment", "Recharge", "Insurance", "Travel", "Shopping", "Bills", "Food", "Other"
)

@Composable
fun AddEditTransactionDialog(
    existing: Transaction?,
    onDismiss: () -> Unit,
    onSave: (type: TransactionType, amountMinor: Long, description: String, date: Long, category: String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(existing?.type ?: TransactionType.SPENT) }
    var amountText by remember {
        mutableStateOf(existing?.let { Money.format(it.amountMinor, withSymbol = false) } ?: "")
    }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: System.currentTimeMillis()) }
    var category by remember { mutableStateOf(existing?.category) }

    val focusRequester = remember { FocusRequester() }

    fun pickDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = date }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val newCal = Calendar.getInstance()
                newCal.set(year, month, day)
                date = newCal.timeInMillis
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Transaction" else "Edit Transaction") },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = type == TransactionType.RECEIVED,
                        onClick = { type = TransactionType.RECEIVED },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Received") }
                    SegmentedButton(
                        selected = type == TransactionType.SPENT,
                        onClick = { type = TransactionType.SPENT },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Spent") }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    placeholder = { Text("₹ 0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).focusRequester(focusRequester)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("What was this payment for?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )

                TextButton(onClick = { pickDate() }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Date: ${Date(date).toDisplayString()}")
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
                        onSave(type, minor, description.trim(), date, category)
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
