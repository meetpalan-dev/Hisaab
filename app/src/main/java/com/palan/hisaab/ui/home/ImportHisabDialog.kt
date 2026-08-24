package com.palan.hisaab.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.palan.hisaab.util.HisabTextImporter

@Composable
fun ImportHisabDialog(
    onDismiss: () -> Unit,
    onImport: (com.palan.hisaab.util.ParsedHisab) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Hisab") },
        text = {
            Column {
                Text("Paste a Hisab shared as text (from this app's Share button) to recreate it as a new account.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    placeholder = { Text("Rudra\n\nInitial Balance: ₹0\n\nTransactions:\n24 Aug 2026 - Loan - - ₹200\n…") }
                )
                error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = HisabTextImporter.parse(text)
                if (parsed == null || parsed.accountName.isBlank()) {
                    error = "Couldn't recognize that format."
                } else {
                    onImport(parsed)
                }
            }) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
