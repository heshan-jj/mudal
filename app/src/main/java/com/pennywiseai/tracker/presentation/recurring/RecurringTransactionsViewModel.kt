// Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-21 - see NOTICE.
package com.pennywiseai.tracker.presentation.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.RecurringFrequency
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.billing.EntitlementGate

import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.RecurringTransactionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.service.RecurringMatcher
import com.pennywiseai.tracker.domain.usecase.GetCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class RecurringTransactionsViewModel @Inject constructor(
    private val repository: RecurringTransactionRepository,
    accountBalanceRepository: AccountBalanceRepository,
    transactionRepository: TransactionRepository,
    getCategoriesUseCase: GetCategoriesUseCase,
    userPreferencesRepository: UserPreferencesRepository,
    entitlementGate: EntitlementGate
) : ViewModel() {

    val templates: StateFlow<List<RecurringTransactionEntity>> =
        repository.getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<AccountBalanceEntity>> =
        accountBalanceRepository.getAllLatestBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templateStatuses: StateFlow<Map<Long, RecurringTemplateStatus>> =
        combine(
            templates,
            transactionRepository.getLinkedTemplateTransactions()
        ) { allTemplates, linkedTxns ->
            val latestByTemplate = linkedTxns
                .filter { !it.isDeleted && it.recurringTemplateId != null }
                .groupBy { it.recurringTemplateId!! }
                .mapValues { (_, list) -> list.maxOf { it.dateTime.toLocalDate() } }
            val today = LocalDate.now()
            allTemplates.associate { tpl ->
                tpl.id to computeRecurringTemplateStatus(tpl, latestByTemplate[tpl.id], today)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val isProEntitled: StateFlow<Boolean> = entitlementGate.isProEntitled

    /**
     * True when the user may create another template — always true in Mudal (unlimited).
     */
    val canAddMore: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> =
        getCategoriesUseCase.execute()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val baseCurrency: StateFlow<String> =
        userPreferencesRepository.baseCurrency
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "INR")

    /**
     * Insert a new template ([id] == 0) or update an existing one. The next due
     * date is resolved from the chosen cadence + day-of-month / day-of-week so
     * the first materialisation lands on the user's intended day.
     */
    fun save(form: RecurringFormState) {
        viewModelScope.launch {
            val amount = form.amount.toBigDecimalOrNull() ?: return@launch
            if (form.id == 0L) {
                repository.insert(
                    RecurringTransactionEntity(
                        merchantName = form.merchantName.trim(),
                        amount = amount,
                        currency = form.currency,
                        category = form.category,
                        transactionType = form.transactionType,
                        frequency = form.frequency,
                        dayOfMonth = form.dayOfMonth,
                        dayOfWeek = form.dayOfWeek,
                        nextDueDate = resolveNextDueDate(form),
                        isActive = form.isActive,
                        bankName = form.bankName,
                        accountLast4 = form.accountLast4,
                        profileId = form.profileId,
                        note = form.note.trim().ifBlank { null }
                    )
                )
            } else {
                val existing = repository.getById(form.id) ?: return@launch
                // Only recompute the schedule when a cadence field actually changed;
                // a metadata-only edit (amount, merchant, category, note, active) must
                // keep the existing next due date rather than jumping it to today. (#706 Greptile)
                val scheduleChanged = existing.frequency != form.frequency ||
                    existing.dayOfMonth != form.dayOfMonth ||
                    existing.dayOfWeek != form.dayOfWeek
                val nextDue = if (scheduleChanged) resolveNextDueDate(form) else existing.nextDueDate
                repository.update(
                    existing.copy(
                        merchantName = form.merchantName.trim(),
                        amount = amount,
                        currency = form.currency,
                        category = form.category,
                        transactionType = form.transactionType,
                        frequency = form.frequency,
                        dayOfMonth = form.dayOfMonth,
                        dayOfWeek = form.dayOfWeek,
                        nextDueDate = nextDue,
                        isActive = form.isActive,
                        bankName = form.bankName,
                        accountLast4 = form.accountLast4,
                        profileId = form.profileId,
                        note = form.note.trim().ifBlank { null },
                        updatedAt = LocalDateTime.now()
                    )
                )
            }
        }
    }

    fun setActive(template: RecurringTransactionEntity, active: Boolean) {
        viewModelScope.launch {
            repository.update(template.copy(isActive = active))
        }
    }

    fun delete(template: RecurringTransactionEntity) {
        viewModelScope.launch { repository.delete(template) }
    }

    /**
     * Pick the first upcoming date matching the cadence + selected day. Today
     * counts as valid so a template created on its due day still fires today.
     */
    private fun resolveNextDueDate(form: RecurringFormState): LocalDate {
        val today = LocalDate.now()
        return when (form.frequency) {
            RecurringFrequency.DAILY -> today
            RecurringFrequency.WEEKLY -> {
                val target = form.dayOfWeek ?: today.dayOfWeek.value
                var d = today
                while (d.dayOfWeek.value != target) d = d.plusDays(1)
                d
            }
            RecurringFrequency.MONTHLY -> {
                val dom = (form.dayOfMonth ?: today.dayOfMonth)
                    .coerceIn(1, today.lengthOfMonth())
                val candidate = today.withDayOfMonth(dom)
                if (candidate.isBefore(today)) form.frequency.advance(candidate) else candidate
            }
        }
    }
}

/**
 * Editable form state for the add/edit sheet. Kept in the presentation layer so
 * the ViewModel only deals in validated primitives.
 */
data class RecurringFormState(
    val id: Long = 0L,
    val merchantName: String = "",
    val amount: String = "",
    val currency: String = "INR",
    val category: String = "Others",
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val dayOfMonth: Int? = null,
    val dayOfWeek: Int? = null,
    val isActive: Boolean = true,
    val bankName: String? = null,
    val accountLast4: String? = null,
    val profileId: Long = 1L,
    val note: String = ""
) {
    val isValid: Boolean
        get() = merchantName.isNotBlank() &&
            (amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true) &&
            category.isNotBlank()

    companion object {
        fun from(template: RecurringTransactionEntity): RecurringFormState = RecurringFormState(
            id = template.id,
            merchantName = template.merchantName,
            amount = template.amount.stripTrailingZeros().toPlainString(),
            currency = template.currency,
            category = template.category,
            transactionType = template.transactionType,
            frequency = template.frequency,
            dayOfMonth = template.dayOfMonth,
            dayOfWeek = template.dayOfWeek,
            isActive = template.isActive,
            bankName = template.bankName,
            accountLast4 = template.accountLast4,
            profileId = template.profileId,
            note = template.note.orEmpty()
        )
    }
}

enum class RecurringCycleState {
    PAUSED,
    PAID,
    OVERDUE,
    SCHEDULED
}

data class RecurringTemplateStatus(
    val state: RecurringCycleState,
    val paidDate: LocalDate? = null
)

internal fun computeRecurringTemplateStatus(
    template: RecurringTransactionEntity,
    latestPaidDate: LocalDate?,
    today: LocalDate = LocalDate.now()
): RecurringTemplateStatus {
    if (!template.isActive) {
        return RecurringTemplateStatus(RecurringCycleState.PAUSED, latestPaidDate)
    }
    val (beforeDays, _) = RecurringMatcher.windowOffsetsForFrequency(template.frequency)
    val withinPaidCycle = latestPaidDate != null &&
        !today.isBefore(latestPaidDate) &&
        today.isBefore(RecurringMatcher.cycleAdvance(latestPaidDate, template.frequency.name))
    val pastUnpaidDue = template.nextDueDate.isBefore(today) &&
        (latestPaidDate == null || latestPaidDate.isBefore(template.nextDueDate.minusDays(beforeDays)))
    val paidThisCycle = withinPaidCycle && !pastUnpaidDue
    return when {
        paidThisCycle -> RecurringTemplateStatus(RecurringCycleState.PAID, latestPaidDate)
        template.nextDueDate.isBefore(today) -> RecurringTemplateStatus(RecurringCycleState.OVERDUE, latestPaidDate)
        else -> RecurringTemplateStatus(RecurringCycleState.SCHEDULED, latestPaidDate)
    }
}
