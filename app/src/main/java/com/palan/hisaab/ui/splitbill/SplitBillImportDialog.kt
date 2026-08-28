package com.palan.hisaab.ui.splitbill

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.palan.hisaab.util.SplitBill
import com.palan.hisaab.util.SplitBillTextFormat

@Composable
fun SplitBillImportDialog(
    onDismiss: () -> Unit,
    onImport: (SplitBill) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Split Bill") },
        text = {
            Column {
                Text("Paste a split bill exported from this screen to load it back in for editing.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text("Split Bill: Dinner\nTotal: ₹1,200\nPaid by: Me\n\nParticipants:\nMe - ₹300 (paid)\nAlex - ₹300\n…") }
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = SplitBillTextFormat.parse(text)
                if (parsed == null) {
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
