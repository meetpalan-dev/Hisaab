package com.palan.hisaab.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun EditAccountDialog(
    currentName: String,
    currentPhone: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String?) -> Unit,
    onDeleteAccount: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var phone by remember { mutableStateOf(currentPhone ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Account") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone number (optional)") },
                    placeholder = { Text("For sending Hisab via SMS") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onDeleteAccount, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Delete this account", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name, phone) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
