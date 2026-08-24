package com.palan.hisaab.ui.createaccount

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.palan.hisaab.util.Money

@Composable
fun CreateAccountDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, startingBalance: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var startingBalance by remember { mutableStateOf("") }
    var balanceError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Account") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account name") },
                    placeholder = { Text("e.g. Rudra, Cash Balance") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = startingBalance,
                    onValueChange = { startingBalance = it; balanceError = null },
                    label = { Text("Starting balance (optional)") },
                    placeholder = { Text("₹ 0") },
                    singleLine = true,
                    isError = balanceError != null,
                    supportingText = balanceError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) return@TextButton
                    if (startingBalance.isBlank() || Money.tryParseRupeesToMinor(startingBalance) != null) {
                        onCreate(name, startingBalance)
                    } else {
                        balanceError = "Enter a valid amount"
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
