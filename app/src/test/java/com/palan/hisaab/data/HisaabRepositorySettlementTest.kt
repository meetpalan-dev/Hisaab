package com.palan.hisaab.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.palan.hisaab.data.entity.Transaction
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
 * Encodes the "Settle Hisaab" validation scenarios (A-G) from the spec, plus the balance/loan
 * sync fix, against a real in-memory Room database -- not a hand-rolled fake -- so these actually
 * exercise the SQL the app runs. Amounts are in minor units (paise): rupees(1) == 100L.
 *
 * Direction convention used throughout (matches HisaabRepository.eligibleTargetTypes):
 *  - A SPENT repayment (I'm paying money out) settles outstanding RECEIVED targets (loan-flagged or not)
 *    -- i.e. those represent money *I* owe *them*.
 *  - A RECEIVED repayment (money coming back to me) settles outstanding SPENT targets (loan-flagged or not)
 *    targets -- i.e. those represent money *they* owe *me*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HisaabRepositorySettlementTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: HisaabRepository

    private fun rupees(r: Long): Long = r * 100L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HisaabRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** A plain outstanding Received transaction -- money they gave me that I still owe back (settled by a later SPENT repayment). */
    private suspend fun givenIOweThem(accountId: Long, amountRupees: Long, description: String) {
        repository.addTransaction(
            Transaction(accountId = accountId, type = TransactionType.RECEIVED, amountMinor = rupees(amountRupees), description = description, date = null)
        )
    }

    /** A plain outstanding Spent transaction -- money I spent on their behalf that they still owe me back (settled by a later RECEIVED repayment). */
    private suspend fun givenTheyOweMe(accountId: Long, amountRupees: Long, description: String) {
        repository.addTransaction(
            Transaction(accountId = accountId, type = TransactionType.SPENT, amountMinor = rupees(amountRupees), description = description, date = null)
        )
    }

    // -- Scenario A: 500 outstanding, 250 settled -> 250 remaining --
    @Test
    fun scenarioA_partialSettlement_leavesRemainder() = runTest {
        val accountId = repository.createAccount("A", 0L)
        givenIOweThem(accountId, 500, "Owed")

        val result = repository.settleHisaab(accountId, TransactionType.SPENT, rupees(250), "Partial payback", null)

        assertEquals(0, result.cleared.size)
        assertEquals(1, result.partiallyPaid.size)
        assertEquals(rupees(250), result.partiallyPaid[0].second)
        assertEquals(rupees(250), totalOutstanding(accountId, TransactionType.SPENT))
    }

    // -- Scenario B: four 100-rupee outstanding transactions, 250 settled --
    @Test
    fun scenarioB_fifoAllocation_acrossMultipleTransactions() = runTest {
        val accountId = repository.createAccount("B", 0L)
        givenIOweThem(accountId, 100, "Txn1")
        givenIOweThem(accountId, 100, "Txn2")
        givenIOweThem(accountId, 100, "Txn3")
        givenIOweThem(accountId, 100, "Txn4")

        val result = repository.settleHisaab(accountId, TransactionType.SPENT, rupees(250), "Part payment", null)

        assertEquals(setOf("Txn1", "Txn2"), result.cleared.map { it.description }.toSet())
        assertEquals(1, result.partiallyPaid.size)
        assertEquals("Txn3", result.partiallyPaid[0].first.description)
        assertEquals(rupees(50), result.partiallyPaid[0].second)
        assertEquals(rupees(150), totalOutstanding(accountId, TransactionType.SPENT))

        // Txn4 must be untouched: still Active (uncleared) and un-allocated.
        val txn4 = db.transactionDao().getForAccountOnce(accountId).first { it.description == "Txn4" }
        assertTrue(!txn4.settled)
        assertEquals(0L, db.repaymentAllocationDao().sumForTarget(txn4.id))
    }

    // -- Scenario C: continuing Scenario B with another 150 -> everything fully cleared --
    @Test
    fun scenarioC_secondSettlement_clearsEverything() = runTest {
        val accountId = repository.createAccount("C", 0L)
        givenIOweThem(accountId, 100, "Txn1")
        givenIOweThem(accountId, 100, "Txn2")
        givenIOweThem(accountId, 100, "Txn3")
        givenIOweThem(accountId, 100, "Txn4")
        repository.settleHisaab(accountId, TransactionType.SPENT, rupees(250), "First payment", null)

        val second = repository.settleHisaab(accountId, TransactionType.SPENT, rupees(150), "Second payment", null)

        assertEquals(setOf("Txn3", "Txn4"), second.cleared.map { it.description }.toSet())
        assertEquals(0, second.partiallyPaid.size)
        assertEquals(0L, totalOutstanding(accountId, TransactionType.SPENT))
        val all = db.transactionDao().getForAccountOnce(accountId).filter { it.type == TransactionType.RECEIVED }
        assertTrue(all.all { it.settled })
    }

    // -- Scenario D: someone owes me 1,500 across several transactions, they pay me 1,000 --
    @Test
    fun scenarioD_theyOweMe_partialRepayment() = runTest {
        val accountId = repository.createAccount("D", 0L)
        givenTheyOweMe(accountId, 500, "First")
        givenTheyOweMe(accountId, 500, "Second")
        givenTheyOweMe(accountId, 500, "Third")

        val result = repository.settleHisaab(accountId, TransactionType.RECEIVED, rupees(1000), "They paid me back", null)

        assertEquals(rupees(1000), result.totalAllocatedMinor)
        assertEquals(setOf("First", "Second"), result.cleared.map { it.description }.toSet())
        assertEquals(rupees(500), totalOutstanding(accountId, TransactionType.RECEIVED))
    }

    /** Regression: a repayment recorded in the wrong direction for its own outstanding hisaab must not settle anything -- proves direction is actually enforced, not just assumed. */
    @Test
    fun wrongDirectionRepayment_settlesNothing() = runTest {
        val accountId = repository.createAccount("WrongDir", 0L)
        givenTheyOweMe(accountId, 500, "Owed to me") // a SPENT target, eligible only for a RECEIVED repayment

        val result = repository.settleHisaab(accountId, TransactionType.SPENT, rupees(500), "Wrong-direction entry", null)

        assertEquals(0, result.cleared.size)
        assertEquals(0, result.partiallyPaid.size)
        assertEquals(0L, result.totalAllocatedMinor)
    }

    // -- Scenario D via Loan Given: same shape, using the formal loan type --
    @Test
    fun scenarioD_viaLoanGiven_partialRepayment() = runTest {
        val accountId = repository.createAccount("D2", 0L)
        repository.addTransaction(Transaction(accountId = accountId, type = TransactionType.SPENT, isLoan = true, amountMinor = rupees(500), description = "First", date = null))
        repository.addTransaction(Transaction(accountId = accountId, type = TransactionType.SPENT, isLoan = true, amountMinor = rupees(500), description = "Second", date = null))
        repository.addTransaction(Transaction(accountId = accountId, type = TransactionType.SPENT, isLoan = true, amountMinor = rupees(500), description = "Third", date = null))

        val result = repository.settleHisaab(accountId, TransactionType.RECEIVED, rupees(1000), "They paid me back", null)

        assertEquals(rupees(1000), result.totalAllocatedMinor)
        assertEquals(rupees(500), totalOutstanding(accountId, TransactionType.RECEIVED))
    }

    // -- Scenario E: I owe someone 500, I pay 250 --
    @Test
    fun scenarioE_iOweThem_partialPayment() = runTest {
        val accountId = repository.createAccount("E", 0L)
        repository.addTransaction(Transaction(accountId = accountId, type = TransactionType.RECEIVED, isLoan = true, amountMinor = rupees(500), description = "Owed to them", date = null))

        val result = repository.settleHisaab(accountId, TransactionType.SPENT, rupees(250), "I paid them back", null)

        assertEquals(1, result.partiallyPaid.size)
        assertEquals(rupees(250), result.partiallyPaid[0].second)
        assertEquals(rupees(250), totalOutstanding(accountId, TransactionType.SPENT))
    }

    // -- Overpayment: 500 outstanding, 600 settled -> only 500 allocated, 100 left unallocated on the real transaction --
    @Test
    fun overpayment_capsAllocationAtOutstandingTotal() = runTest {
        val accountId = repository.createAccount("Over", 0L)
        givenIOweThem(accountId, 500, "Owed")

        val result = repository.settleHisaab(accountId, TransactionType.SPENT, rupees(600), "Overpaid", null)

        assertEquals(rupees(500), result.totalAllocatedMinor)
        assertEquals(rupees(600), result.repaymentAmountMinor)
        assertEquals(rupees(100), result.unallocatedMinor)
        assertEquals(0L, totalOutstanding(accountId, TransactionType.SPENT))
    }

    // -- Manual "Mark as Cleared" still works independently of Settle Hisaab --
    @Test
    fun manualMarkAsCleared_stillWorksIndependently() = runTest {
        val accountId = repository.createAccount("Manual", 0L)
        givenIOweThem(accountId, 300, "Manual clear me")
        val txn = db.transactionDao().getForAccountOnce(accountId).first { it.description == "Manual clear me" }

        repository.setSettled(txn, true)

        val updated = db.transactionDao().getById(txn.id)!!
        assertTrue(updated.settled)
        // No repayment/allocation was created by a manual clear.
        assertEquals(0L, db.repaymentAllocationDao().sumForTarget(txn.id))
    }

    // -- Scenario F: Loan Given reflects in balance + summary consistently --
    @Test
    fun scenarioF_loanGiven_reflectsInBalanceAndSummary() = runTest {
        val accountId = repository.createAccount("F", 0L)
        repository.addTransaction(Transaction(accountId = accountId, type = TransactionType.SPENT, isLoan = true, amountMinor = rupees(500), description = "Loan out", date = null))

        val summary = snapshotSummary(accountId)
        assertEquals(rupees(500), summary.loanGiven)
        assertEquals(rupees(500), summary.balance) // matches the app's existing sign rule: +loanGiven
    }

    // -- Scenario G: Loan Taken reflects in balance + summary consistently --
    @Test
    fun scenarioG_loanTaken_reflectsInBalanceAndSummary() = runTest {
        val accountId = repository.createAccount("G", 0L)
        repository.addTransaction(Transaction(accountId = accountId, type = TransactionType.RECEIVED, isLoan = true, amountMinor = rupees(500), description = "Loan in", date = null))

        val summary = snapshotSummary(accountId)
        assertEquals(rupees(500), summary.loanTaken)
        assertEquals(-rupees(500), summary.balance) // matches the app's existing sign rule: -loanTaken
    }

    // -- Item 8 regression: a repayment against a plain (non-loan) outstanding transaction must
    // actually move the balance, not vanish (the bug found and fixed alongside this feature). --
    @Test
    fun repaymentAgainstPlainReceived_movesBalance_doesNotVanish() = runTest {
        val accountId = repository.createAccount("Sync", 0L)
        givenIOweThem(accountId, 500, "They gave me money")
        val beforeRepay = snapshotSummary(accountId).balance
        assertEquals(rupees(500), beforeRepay)

        repository.settleHisaab(accountId, TransactionType.SPENT, rupees(500), "I paid it all back", null)

        val afterRepay = snapshotSummary(accountId).balance
        assertEquals(0L, afterRepay) // fully cancelled out, not left at 500 (vanished repayment) or -500 (double-counted)
    }

    // -- Item 8 regression: a repayment against a Loan Given must not double-count against the balance either. --
    @Test
    fun repaymentAgainstLoanGiven_doesNotDoubleCount() = runTest {
        val accountId = repository.createAccount("SyncLoan", 0L)
        repository.addTransaction(Transaction(accountId = accountId, type = TransactionType.SPENT, isLoan = true, amountMinor = rupees(500), description = "Loan out", date = null))
        assertEquals(rupees(500), snapshotSummary(accountId).balance)

        repository.settleHisaab(accountId, TransactionType.RECEIVED, rupees(500), "They repaid the loan", null)

        assertEquals(0L, snapshotSummary(accountId).balance)
    }

    private suspend fun totalOutstanding(accountId: Long, repaymentType: TransactionType): Long =
        repository.getOutstandingHisaabs(accountId, repaymentType).sumOf { it.remainingMinor }

    private suspend fun snapshotSummary(accountId: Long): AccountSummary {
        val acc = db.accountDao().getById(accountId)!!
        return repository.observeAccountSummary(acc).first()
    }
}
