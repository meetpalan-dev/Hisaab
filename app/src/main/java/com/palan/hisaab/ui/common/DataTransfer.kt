package com.palan.hisaab.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.palan.hisaab.data.BackupRestoreResult
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.data.entity.Account
import com.palan.hisaab.ui.home.ImportHisabDialog
import com.palan.hisaab.util.HisabExcel
import com.palan.hisaab.util.ParsedHisab
import com.palan.hisaab.util.XlsxCodec
import kotlinx.coroutines.launch

/**
 * Everything Import / Export Backup needs, in one place — the Home menu and the Settings screen
 * both call [rememberDataTransfer], so there is exactly one implementation of each format's
 * importer and of the duplicate-account handling, not two copies that could drift apart.
 * Whoever calls it must also call [DataTransfer.dialogs] once somewhere in its own composition.
 */
class DataTransfer(
    val importFromText: () -> Unit,
    val importFromExcel: () -> Unit,
    val importFromJson: () -> Unit,
    val exportBackup: () -> Unit,
    val dialogs: @Composable () -> Unit
)

@Composable
fun rememberDataTransfer(repository: HisaabRepository, onOpenAccount: (Long) -> Unit): DataTransfer {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTextImport by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<ParsedHisab?>(null) }
    var duplicateAccount by remember { mutableStateOf<Account?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    /** Text and Excel imports both arrive as a [ParsedHisab] and share this: ask before duplicating an existing account's name. */
    fun startImport(parsed: ParsedHisab) {
        scope.launch {
            val existing = repository.findAccountByName(parsed.accountName)
            if (existing != null) {
                pendingImport = parsed
                duplicateAccount = existing
            } else {
                onOpenAccount(repository.importParsedHisab(parsed))
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val json = repository.exportAllToJson()
                message = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
                    .fold(onSuccess = { "Backup saved." }, onFailure = { "Couldn't save the backup file." })
            }
        }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val json = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                message = if (json.isNullOrBlank()) "Couldn't read that file."
                else runCatching { describe(repository.restoreFromBackupJson(json)) }
                    .getOrElse { "That file isn't a valid Hisaab backup." }
            }
        }
    }
    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                val parsed = bytes?.let { runCatching { HisabExcel.parse(XlsxCodec.read(it)) }.getOrNull() }
                if (parsed == null) message = "That file isn't a Hisaab Excel export, so there was nothing to import."
                else startImport(parsed)
            }
        }
    }

    return DataTransfer(
        importFromText = { showTextImport = true },
        importFromExcel = { excelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*")) },
        importFromJson = { jsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        exportBackup = { exportLauncher.launch("hisaab_backup.json") },
        dialogs = {
            if (showTextImport) {
                ImportHisabDialog(
                    onDismiss = { showTextImport = false },
                    onImport = { parsed ->
                        showTextImport = false
                        startImport(parsed)
                    }
                )
            }

            val toResolve = pendingImport
            val existingMatch = duplicateAccount
            if (toResolve != null && existingMatch != null) {
                AlertDialog(
                    onDismissRequest = { pendingImport = null; duplicateAccount = null },
                    title = { Text("Account already exists") },
                    text = { Text("An account named \"${existingMatch.name}\" already exists.") },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                val added = repository.mergeParsedHisab(existingMatch.id, toResolve)
                                pendingImport = null
                                duplicateAccount = null
                                message = if (added == 1) "Added 1 new transaction to ${existingMatch.name}."
                                else "Added $added new transactions to ${existingMatch.name} (duplicates were skipped)."
                            }
                        }) { Text("Merge with Existing Account") }
                    },
                    dismissButton = {
                        Column {
                            TextButton(onClick = {
                                scope.launch {
                                    val newId = repository.importParsedHisab(toResolve)
                                    pendingImport = null
                                    duplicateAccount = null
                                    onOpenAccount(newId)
                                }
                            }) { Text("Create Separate Account") }
                            TextButton(onClick = { pendingImport = null; duplicateAccount = null }) { Text("Cancel") }
                        }
                    }
                )
            }

            message?.let { text ->
                AlertDialog(
                    onDismissRequest = { message = null },
                    title = { Text("Import / Export") },
                    text = { Text(text) },
                    confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
                )
            }
        }
    )
}

private fun describe(r: BackupRestoreResult): String =
    "Restored ${r.accountsTotal} account(s) — ${r.accountsCreated} new, ${r.accountsMerged} merged into existing.\n" +
        "${r.transactionsAdded} transactions added, ${r.transactionsSkippedAsDuplicates} duplicates skipped" +
        (if (r.splitsRestored > 0) ", ${r.splitsRestored} split(s) restored." else ".")
