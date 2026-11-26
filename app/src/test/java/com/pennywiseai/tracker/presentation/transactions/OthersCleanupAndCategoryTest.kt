package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.shared.domain.mapping.SharedCategoryMapping
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class OthersCleanupAndCategoryTest {

    @Test
    fun `expanded Sri Lankan and digital keywords categorize out of Others`() {
        assertEquals("Food & Dining", SharedCategoryMapping.getCategory("CARAVAN FRESH NUGEGODA"))
        assertEquals("Groceries", SharedCategoryMapping.getCategory("SUPERMARKET COLOMBO"))
        assertEquals("Transportation", SharedCategoryMapping.getCategory("CEYPETCO FILLING STATION"))
        assertEquals("Shopping", SharedCategoryMapping.getCategory("CLAUDE.AI"))
        assertEquals("Shopping", SharedCategoryMapping.getCategory("TEMU.COM"))
        assertEquals("Entertainment", SharedCategoryMapping.getCategory("STEAM GAMES"))
        assertEquals("Healthcare", SharedCategoryMapping.getCategory("LANKA HOSPITALS"))
        assertEquals("Healthcare", SharedCategoryMapping.getCategory("AROGYA DENTAL"))
    }

    @Test
    fun `groupOthersMerchants groups case-insensitively and sorts by count descending`() {
        val now = LocalDateTime.of(2026, 11, 26, 10, 0)
        val txns = listOf(
            tx(1, "Local Kade", "Others", now),
            tx(2, "local kade", "Others", now.minusHours(1)),
            tx(3, "Local Kade", "Others", now.minusHours(2)),
            tx(4, "Corner Shop", "Others", now.minusHours(3)),
            tx(5, "Keells", "Groceries", now.minusHours(4)),
            tx(6, "   ", "Others", now.minusHours(5))
        )

        val groups = groupOthersMerchants(txns)

        assertEquals(2, groups.size)
        assertEquals("Local Kade", groups[0].merchantName)
        assertEquals(3, groups[0].count)
        assertEquals("Corner Shop", groups[1].merchantName)
        assertEquals(1, groups[1].count)
    }

    private fun tx(id: Long, merchant: String, category: String, dt: LocalDateTime) = TransactionEntity(
        id = id,
        amount = BigDecimal("500.00"),
        merchantName = merchant,
        category = category,
        transactionType = TransactionType.EXPENSE,
        dateTime = dt,
        transactionHash = "h-$id",
        currency = "LKR"
    )
}
