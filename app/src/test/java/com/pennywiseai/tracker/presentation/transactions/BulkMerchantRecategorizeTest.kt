package com.pennywiseai.tracker.presentation.transactions

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BulkMerchantRecategorizeTest {

    @Test
    fun `single update only touches the transaction id`() = runBlocking {
        val calls = mutableListOf<String>()
        applyCategoryChange(
            transactionId = 42L,
            merchantName = "Keells Super",
            newCategory = "Groceries",
            applyToAllFromMerchant = false,
            updateSingle = { id, cat -> calls += "single:$id:$cat" },
            updateMerchant = { m, cat -> calls += "merchant:$m:$cat" },
            saveMapping = { m, cat -> calls += "mapping:$m:$cat" }
        )
        assertEquals(listOf("single:42:Groceries"), calls)
    }

    @Test
    fun `bulk merchant update updates all existing transactions and saves future mapping`() = runBlocking {
        val calls = mutableListOf<String>()
        applyCategoryChange(
            transactionId = 42L,
            merchantName = "Keells Super",
            newCategory = "Groceries",
            applyToAllFromMerchant = true,
            updateSingle = { id, cat -> calls += "single:$id:$cat" },
            updateMerchant = { m, cat -> calls += "merchant:$m:$cat" },
            saveMapping = { m, cat -> calls += "mapping:$m:$cat" }
        )
        assertEquals(
            listOf("merchant:Keells Super:Groceries", "mapping:Keells Super:Groceries"),
            calls
        )
    }

    @Test
    fun `blank merchant name falls back to single transaction update`() = runBlocking {
        val calls = mutableListOf<String>()
        applyCategoryChange(
            transactionId = 7L,
            merchantName = "   ",
            newCategory = "Food & Dining",
            applyToAllFromMerchant = true,
            updateSingle = { id, cat -> calls += "single:$id:$cat" },
            updateMerchant = { m, cat -> calls += "merchant:$m:$cat" },
            saveMapping = { m, cat -> calls += "mapping:$m:$cat" }
        )
        assertEquals(listOf("single:7:Food & Dining"), calls)
    }
}
