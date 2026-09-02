package com.palan.hisaab.ui.account

import androidx.compose.foundation.layout.fillMaxWidth
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
import com.palan.hisaab.util.Money

@Composable
fun InitialBalanceDialog(
    currentMinor: Long,
    onDismiss: () -> Unit,
    onSave: (amountMinor: Long, date: Long) -> Unit
) {
    var amountText by remember {
        mutableStateOf(if (currentMinor == 0L) "" else Money.format(currentMinor, withSymbol = false))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Initial Balance") },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount") },
                placeholder = { Text("₹ 0") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Money.rupeeStringToMinor(amountText), System.currentTimeMillis())
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
