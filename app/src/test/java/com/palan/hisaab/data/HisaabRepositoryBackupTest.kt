package com.palan.hisaab.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.palan.hisaab.data.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Backup/restore round trips: a Split and its settlement state must come back as a linked event
 * (not as a pile of unrelated transactions), restoring the same file twice must add nothing, a
 * single-account export must carry its own relationships, and old pre-isLoan backups must load.
 * Amounts are in minor units (paise): rupees(1) == 100L.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HisaabRepositoryBackupTest {

    private val openDbs = mutableListOf<AppDatabase>()

    private fun rupees(r: Long): Long = r * 100L

    private fun newDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { openDbs.add(it) }

    @Before
    fun setUp() { openDbs.clear() }

    @After
    fun tearDown() { openDbs.forEach { it.close() } }

    /** Split 500 (Me 250 / A 250, Me paid), then A pays back 100 via Settle Hisaab. */
    private suspend fun partiallySettledSplit(repo: HisaabRepository, db: AppDatabase) {
        repo.applySplit(
            description = "Dinner",
            shares = listOf(
                SplitShare(name = "Me", amountMinor = rupees(250), isSelf = true),
                SplitShare(name = "A", amountMinor = rupees(250))
            ),
            payerName = "Me",
            date = 1_000L
        )
        val a = db.accountDao().getAllOnce().first { it.name == "A" }
        repo.settleHisaab(a.id, TransactionType.RECEIVED, rupees(100), "A paid", 2_000L)
    }

    // -- Spec test 9/16: partially settled split must survive export -> import intact --
    @Test
    fun fullBackup_preservesSplitAndPartialSettlement() = runTest {
        val dbA = newDb(); val repoA = HisaabRepository(dbA)
        partiallySettledSplit(repoA, dbA)
        val json = repoA.exportAllToJson()

        val dbB = newDb(); val repoB = HisaabRepository(dbB)
        val result = repoB.restoreFromBackupJson(json)
        assertEquals(2, result.accountsCreated)
        assertEquals(1, result.splitsRestored)

        val split = repoB.observeSplitHistoryWithStatus().first().single()
        assertEquals(SplitOverallStatus.PARTIALLY_SETTLED, split.overallStatus)
        assertEquals(rupees(100), split.totalRecoveredMinor)
        assertEquals(rupees(150), split.totalRemainingMinor)

        val aB = dbB.accountDao().getAllOnce().first { it.name == "A" }
        assertEquals(rupees(150), repoB.getOutstandingHisaabs(aB.id, TransactionType.RECEIVED).sumOf { it.remainingMinor })

        val meB = dbB.accountDao().getAllOnce().first { it.name == "Me" }
        val meTxn = dbB.transactionDao().getForAccountOnce(meB.id).single()
        assertEquals(rupees(400), repoB.computeSplitTotalOverrides(meB.id)[meTxn.id]) // 500 - 100 recovered, still linked
    }

    // -- Restoring the same file twice must not duplicate anything --
    @Test
    fun restoringSameBackupTwice_addsNothing() = runTest {
        val db = newDb(); val repo = HisaabRepository(db)
        partiallySettledSplit(repo, db)
        val json = repo.exportAllToJson()
        val txnCountBefore = db.accountDao().getAllOnce().sumOf { db.transactionDao().getForAccountOnce(it.id).size }
        val me = db.accountDao().getAllOnce().first { it.name == "Me" }
        val balanceBefore = repo.observeAccountSummary(me).first().balance

        val result = repo.restoreFromBackupJson(json)

        assertEquals(0, result.accountsCreated)
        assertEquals(2, result.accountsMerged)
        assertEquals(0, result.transactionsAdded)
        assertEquals(0, result.splitsRestored)
        assertEquals(txnCountBefore, db.accountDao().getAllOnce().sumOf { db.transactionDao().getForAccountOnce(it.id).size })
        assertEquals(1, repo.observeSplitHistoryWithStatus().first().size)
        assertEquals(balanceBefore, repo.observeAccountSummary(me).first().balance)
    }

    // -- Single-account export carries only that account, with its settlement state --
    @Test
    fun singleAccountExport_containsOnlyThatAccount_withAllocations() = runTest {
        val dbA = newDb(); val repoA = HisaabRepository(dbA)
        partiallySettledSplit(repoA, dbA)
        val a = dbA.accountDao().getAllOnce().first { it.name == "A" }
        val json = repoA.exportAccountToJson(a.id)

        val dbB = newDb(); val repoB = HisaabRepository(dbB)
        val result = repoB.restoreFromBackupJson(json)

        assertEquals(1, result.accountsCreated)
        assertEquals(listOf("A"), dbB.accountDao().getAllOnce().map { it.name })
        val aB = dbB.accountDao().getAllOnce().single()
        assertEquals(2, dbB.transactionDao().getForAccountOnce(aB.id).size) // the split share + A's repayment
        assertEquals(rupees(150), repoB.getOutstandingHisaabs(aB.id, TransactionType.RECEIVED).sumOf { it.remainingMinor })
    }

    // -- "Export selected": only chosen transactions appear --
    @Test
    fun exportWithSelection_includesOnlySelectedTransactions() = runTest {
        val db = newDb(); val repo = HisaabRepository(db)
        val id = repo.createAccount("Pick", 0L)
        repo.addTransaction(com.palan.hisaab.data.entity.Transaction(accountId = id, type = TransactionType.SPENT, amountMinor = rupees(10), description = "keep", date = null))
        repo.addTransaction(com.palan.hisaab.data.entity.Transaction(accountId = id, type = TransactionType.SPENT, amountMinor = rupees(20), description = "drop", date = null))
        val keep = db.transactionDao().getForAccountOnce(id).first { it.description == "keep" }

        val json = repo.exportAccountToJson(id, onlyTransactionIds = setOf(keep.id))

        assertTrue(json.contains("keep"))
        assertTrue(!json.contains("drop"))
    }

    // -- Old backups (pre-isLoan, with LOAN_GIVEN / LOAN_TAKEN) still load --
    @Test
    fun legacyBackup_mapsOldLoanTypesToIsLoanFlag() = runTest {
        val legacy = """
            {"accounts":[{"name":"Old","phoneNumber":null,"transactions":[
              {"type":"LOAN_GIVEN","amountMinor":50000,"description":"Loan out","date":null,"category":null,"settled":false},
              {"type":"LOAN_TAKEN","amountMinor":20000,"description":"Loan in","date":null,"category":null,"settled":false}
            ]}]}
        """.trimIndent()
        val db = newDb(); val repo = HisaabRepository(db)

        val result = repo.restoreFromBackupJson(legacy)

        assertEquals(1, result.accountsCreated)
        val acc = db.accountDao().getAllOnce().single()
        val txns = db.transactionDao().getForAccountOnce(acc.id)
        assertTrue(txns.single { it.description == "Loan out" }.let { it.type == TransactionType.SPENT && it.isLoan })
        assertTrue(txns.single { it.description == "Loan in" }.let { it.type == TransactionType.RECEIVED && it.isLoan })
        val summary = repo.observeAccountSummary(acc).first()
        assertEquals(50000L, summary.loanGiven)
        assertEquals(20000L, summary.loanTaken)
        assertEquals(30000L, summary.balance)
    }
}
