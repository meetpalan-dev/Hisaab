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
 * Encodes the Split accounting fix: A's share posts as a plain Spent entry (not Loan Given), Me's
 * full outlay is tracked as one entry that shrinks live as others settle their own share, and
 * Split History reports each participant's recovered/remaining amount plus an overall status.
 * Mirrors TEST 1-3 and the multi-participant example from the spec. Amounts are in minor units
 * (paise): rupees(1) == 100L.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HisaabRepositorySplitTest {

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

    // -- TEST 1: split 500, Me=250, A=250, paid by Me --
    @Test
    fun test1_initialSplit_recordsPlainSpentNotLoanGiven() = runTest {
        repository.applySplit(
            description = "Dinner",
            shares = listOf(
                SplitShare(name = "Me", amountMinor = rupees(250), isSelf = true),
                SplitShare(name = "A", amountMinor = rupees(250))
            ),
            payerName = "Me",
            date = 0L
        )

        val aAccount = db.accountDao().getAllOnce().first { it.name == "A" }
        val aTxn = db.transactionDao().getForAccountOnce(aAccount.id).single()
        assertEquals(TransactionType.SPENT, aTxn.type) // not LOAN_GIVEN
        assertEquals(rupees(250), aTxn.amountMinor)
        assertEquals(rupees(250), repository.getOutstandingHisaabs(aAccount.id, TransactionType.RECEIVED).sumOf { it.remainingMinor })

        val meAccount = db.accountDao().getAllOnce().first { it.name == "Me" }
        val meTxn = db.transactionDao().getForAccountOnce(meAccount.id).single()
        assertEquals(TransactionType.SPENT, meTxn.type)
        assertEquals(rupees(500), meTxn.amountMinor) // full outlay, not just my own share

        // Nothing recovered yet, so the live "effective personal cost" is still the full 500 --
        // it only shrinks once someone actually settles (see spec sections 3-4).
        val overrides = repository.computeSplitTotalOverrides(meAccount.id)
        assertEquals(rupees(500), overrides[meTxn.id])
    }

    // -- TEST 2: A pays 100 via Settle Hisaab --
    @Test
    fun test2_partialSettlement_reducesMeEffectiveCost() = runTest {
        repository.applySplit(
            description = "Dinner",
            shares = listOf(
                SplitShare(name = "Me", amountMinor = rupees(250), isSelf = true),
                SplitShare(name = "A", amountMinor = rupees(250))
            ),
            payerName = "Me",
            date = 0L
        )
        val aAccount = db.accountDao().getAllOnce().first { it.name == "A" }
        val meAccount = db.accountDao().getAllOnce().first { it.name == "Me" }
        val meTxn = db.transactionDao().getForAccountOnce(meAccount.id).single()

        repository.settleHisaab(aAccount.id, TransactionType.RECEIVED, rupees(100), "A paid back", null)

        assertEquals(rupees(150), repository.getOutstandingHisaabs(aAccount.id, TransactionType.RECEIVED).sumOf { it.remainingMinor })
        val overrides = repository.computeSplitTotalOverrides(meAccount.id)
        assertEquals(rupees(400), overrides[meTxn.id]) // 500 - 100 recovered
    }

    // -- TEST 3: A pays the remaining 150 --
    @Test
    fun test3_fullSettlement_meEffectiveCostEqualsOwnShare() = runTest {
        repository.applySplit(
            description = "Dinner",
            shares = listOf(
                SplitShare(name = "Me", amountMinor = rupees(250), isSelf = true),
                SplitShare(name = "A", amountMinor = rupees(250))
            ),
            payerName = "Me",
            date = 0L
        )
        val aAccount = db.accountDao().getAllOnce().first { it.name == "A" }
        val meAccount = db.accountDao().getAllOnce().first { it.name == "Me" }
        val meTxn = db.transactionDao().getForAccountOnce(meAccount.id).single()

        repository.settleHisaab(aAccount.id, TransactionType.RECEIVED, rupees(100), "First payment", null)
        repository.settleHisaab(aAccount.id, TransactionType.RECEIVED, rupees(150), "Second payment", null)

        assertEquals(0L, repository.getOutstandingHisaabs(aAccount.id, TransactionType.RECEIVED).sumOf { it.remainingMinor })
        val overrides = repository.computeSplitTotalOverrides(meAccount.id)
        assertEquals(rupees(250), overrides[meTxn.id]) // down to just my own share

        val split = repository.observeSplitHistoryWithStatus().first().single()
        assertEquals(SplitOverallStatus.FULLY_SETTLED, split.overallStatus)
        assertEquals(rupees(250), split.totalRecoveredMinor)
        assertEquals(0L, split.totalRemainingMinor)
    }

    // -- Section 5: four-way split, Me/A/B/C = 200 each, Me paid --
    @Test
    fun multiParticipantSplit_tracksEachIndependently() = runTest {
        repository.applySplit(
            description = "Trip",
            shares = listOf(
                SplitShare(name = "Me", amountMinor = rupees(200), isSelf = true),
                SplitShare(name = "A", amountMinor = rupees(200)),
                SplitShare(name = "B", amountMinor = rupees(200)),
                SplitShare(name = "C", amountMinor = rupees(200))
            ),
            payerName = "Me",
            date = 0L
        )
        val meAccount = db.accountDao().getAllOnce().first { it.name == "Me" }
        val meTxn = db.transactionDao().getForAccountOnce(meAccount.id).single()
        assertEquals(rupees(800), meTxn.amountMinor)
        assertEquals(rupees(800), repository.computeSplitTotalOverrides(meAccount.id)[meTxn.id])

        val aAccount = db.accountDao().getAllOnce().first { it.name == "A" }
        repository.settleHisaab(aAccount.id, TransactionType.RECEIVED, rupees(200), "A paid in full", null)
        assertEquals(rupees(600), repository.computeSplitTotalOverrides(meAccount.id)[meTxn.id])

        val bAccount = db.accountDao().getAllOnce().first { it.name == "B" }
        repository.settleHisaab(bAccount.id, TransactionType.RECEIVED, rupees(100), "B paid half", null)
        assertEquals(rupees(500), repository.computeSplitTotalOverrides(meAccount.id)[meTxn.id])

        val split = repository.observeSplitHistoryWithStatus().first().single()
        assertEquals(SplitOverallStatus.PARTIALLY_SETTLED, split.overallStatus)
        val bStatus = split.participants.single { it.name == "B" }
        assertEquals(rupees(100), bStatus.recoveredMinor)
        assertEquals(rupees(100), bStatus.remainingMinor)
        assertTrue(!bStatus.settled)

        val cAccount = db.accountDao().getAllOnce().first { it.name == "C" }
        repository.settleHisaab(bAccount.id, TransactionType.RECEIVED, rupees(100), "B pays the rest", null)
        repository.settleHisaab(cAccount.id, TransactionType.RECEIVED, rupees(200), "C pays in full", null)

        assertEquals(rupees(200), repository.computeSplitTotalOverrides(meAccount.id)[meTxn.id])
        val finalSplit = repository.observeSplitHistoryWithStatus().first().single()
        assertEquals(SplitOverallStatus.FULLY_SETTLED, finalSplit.overallStatus)
    }

    // -- The actual bug this fix addresses: the ACCOUNT-LEVEL balance (not just the row's
    // "remaining" label) must reflect the recovery too. Previously the row displayed correctly
    // but the account's own Spent/Balance totals kept counting the full original amount forever. --
    @Test
    fun accountLevelBalance_reflectsSplitRecovery_notJustTheRowLabel() = runTest {
        repository.applySplit(
            description = "Dinner",
            shares = listOf(
                SplitShare(name = "Me", amountMinor = rupees(250), isSelf = true),
                SplitShare(name = "A", amountMinor = rupees(250))
            ),
            payerName = "Me",
            date = 0L
        )
        val aAccount = db.accountDao().getAllOnce().first { it.name == "A" }
        val meAccount = db.accountDao().getAllOnce().first { it.name == "Me" }

        val beforeById = repository.observeAccountSummaryById(meAccount.id).first()
        val beforeList = repository.observeAccountSummary(meAccount).first()
        assertEquals(rupees(500), beforeById.spent)
        assertEquals(-rupees(500), beforeById.balance)
        assertEquals(beforeById.spent, beforeList.spent) // Home list and Account screen must agree

        repository.settleHisaab(aAccount.id, TransactionType.RECEIVED, rupees(100), "A paid some", null)

        val afterPartialById = repository.observeAccountSummaryById(meAccount.id).first()
        val afterPartialList = repository.observeAccountSummary(meAccount).first()
        assertEquals(rupees(400), afterPartialById.spent)
        assertEquals(-rupees(400), afterPartialById.balance)
        assertEquals(afterPartialById.spent, afterPartialList.spent)

        repository.settleHisaab(aAccount.id, TransactionType.RECEIVED, rupees(150), "A paid the rest", null)

        val afterFullById = repository.observeAccountSummaryById(meAccount.id).first()
        val afterFullList = repository.observeAccountSummary(meAccount).first()
        assertEquals(rupees(250), afterFullById.spent) // down to just my own share
        assertEquals(-rupees(250), afterFullById.balance)
        assertEquals(afterFullById.spent, afterFullList.spent)
    }
}
