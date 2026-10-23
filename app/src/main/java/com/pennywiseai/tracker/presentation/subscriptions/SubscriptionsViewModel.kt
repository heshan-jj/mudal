package com.pennywiseai.tracker.presentation.subscriptions

import com.pennywiseai.tracker.ui.UiText
import com.pennywiseai.tracker.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.domain.service.DetectedSubscription
import com.pennywiseai.tracker.domain.service.SubscriptionCharge
import com.pennywiseai.tracker.domain.service.SubscriptionDetector
import com.pennywiseai.tracker.domain.usecase.AddSubscriptionUseCase
import com.pennywiseai.tracker.domain.usecase.MarkSubscriptionPaidUseCase
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val markSubscriptionPaidUseCase: MarkSubscriptionPaidUseCase,
    private val addSubscriptionUseCase: AddSubscriptionUseCase,
    private val transactionRepository: com.pennywiseai.tracker.data.repository.TransactionRepository,
    accountBalanceRepository: AccountBalanceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    /**
     * Detected cards whose save is in flight. A second tap arrives before Room
     * emits the new row, so the card would still be on screen and [addDetected]
     * would insert a duplicate. The key stays here until that row is observed.
     */
    private val addingDetectedKeys = mutableSetOf<String>()

    /** Accounts offered as the funding source in the edit dialog (#570). */
    val accounts: StateFlow<List<AccountBalanceEntity>> =
        accountBalanceRepository.getAllLatestBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadSubscriptions()
    }

    private fun loadSubscriptions() {
        viewModelScope.launch {
            val ledger = combine(
                subscriptionRepository.getAllSubscriptions(),
                transactionRepository.getAllTransactions(),
                userPreferencesRepository.dismissedSubscriptionSuggestions,
            ) { subscriptions, transactions, dismissed ->
                SubscriptionLedger(subscriptions, transactions, dismissed)
            }
            combine(
                ledger,
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                userPreferencesRepository.baseCurrency
            ) { snapshot, isUnified, displayCurrency, baseCurrency ->
                snapshot to CurrencyPrefs(isUnified, displayCurrency, baseCurrency)
            }.collect { (snapshot, prefs) ->
                val subscriptions = snapshot.subscriptions.filter { it.state == SubscriptionState.ACTIVE }
                val endedSubscriptions = snapshot.subscriptions.filter { it.state == SubscriptionState.ENDED }
                val isUnified = prefs.isUnified
                val displayCurrency = prefs.displayCurrency
                val baseCurrency = prefs.baseCurrency
                val savedKeys = snapshot.subscriptions
                    .map { SubscriptionDetector.key(it.merchantName, it.currency) }
                    .toSet()
                val detected = withContext(Dispatchers.Default) {
                    SubscriptionDetector.detect(
                        charges = snapshot.transactions.mapNotNull { it.toSubscriptionCharge() },
                        existingKeys = savedKeys,
                        dismissedKeys = snapshot.dismissed,
                        today = java.time.LocalDate.now(),
                    )
                }
                // Drop keys Room has now persisted, then keep any still-saving
                // card off the list so a transaction emission can't bring it back.
                addingDetectedKeys.removeAll { it in savedKeys }
                val visibleDetected = detected.filterNot { it.key in addingDetectedKeys }
                // Unified mode collapses everything into displayCurrency, so a
                // single figure is honest. Native mode keeps each currency apart —
                // a ₹ + $ mix can't be one figure — so we expose a per-currency map
                // the header renders as "₹399 · $30" (no sub silently dropped).
                val totalMonthlyAmount = if (isUnified) {
                    var total = BigDecimal.ZERO
                    for (sub in subscriptions) {
                        total += currencyConversionService.convertAmountOrNull(
                            sub.amount, sub.currency, displayCurrency
                        ) ?: continue // never-rated pair — skip, don't face-value (#670)
                    }
                    total
                } else {
                    BigDecimal.ZERO
                }
                val totalByCurrency: Map<String, Money> = if (isUnified) {
                    emptyMap()
                } else {
                    subscriptions.sumByCurrency({ it.currency }, { it.amount })
                }

                val convertedAmounts = if (isUnified) {
                    val map = mutableMapOf<Long, BigDecimal>()
                    for (sub in subscriptions) {
                        if (!sub.currency.equals(displayCurrency, ignoreCase = true)) {
                            // Omit unconvertible subs; the row shows its native
                            // amount + currency rather than a mislabeled figure (#670).
                            currencyConversionService.convertAmountOrNull(
                                sub.amount, sub.currency, displayCurrency
                            )?.let { map[sub.id] = it }
                        }
                    }
                    map
                } else {
                    emptyMap()
                }

                // Order the active list so "needs attention" rises to the
                // top: unpaid-this-cycle subs first (sorted by next-payment
                // date ascending, nulls last), recently-paid subs at the
                // bottom (sorted by last-paid descending).
                //
                // Today-anchored cycle check (NOT schedule-anchored — the
                // schedule slides forward every time we advance, which used
                // to break this guard once any sub had been marked once).
                // Semantic: a sub is "paid this cycle" if today is still
                // within one billing cycle of lastPaidAt.
                val today = java.time.LocalDate.now()
                val isPaidThisCycle: (com.pennywiseai.tracker.data.database.entity.SubscriptionEntity) -> Boolean = { sub ->
                    sub.lastPaidAt?.let { lastPaid ->
                        val nextLegitMark = subscriptionRepository.advance(lastPaid, sub.billingCycle)
                        today.isBefore(nextLegitMark)
                    } ?: false
                }
                val (paid, unpaid) = subscriptions.partition(isPaidThisCycle)
                val ordered = unpaid.sortedWith(
                    compareBy(nullsLast()) { it.nextPaymentDate },
                ) + paid.sortedByDescending { it.lastPaidAt }
                val paidIds = paid.map { it.id }.toSet()

                _uiState.value = _uiState.value.copy(
                    activeSubscriptions = ordered,
                    paidThisCycleIds = paidIds,
                    paidThisCycleCount = paid.size,
                    endedSubscriptions = endedSubscriptions,
                    totalMonthlyAmount = totalMonthlyAmount,
                    totalByCurrency = totalByCurrency,
                    convertedAmounts = convertedAmounts,
                    displayCurrency = if (isUnified) displayCurrency else baseCurrency,
                    isUnifiedMode = isUnified,
                    isLoading = false,
                    detected = visibleDetected,
                )
            }
        }
    }
    
    fun hideSubscription(subscriptionId: Long) {
        viewModelScope.launch {
            subscriptionRepository.hideSubscription(subscriptionId)
            _uiState.value = _uiState.value.copy(
                lastHiddenSubscription = _uiState.value.activeSubscriptions.find { it.id == subscriptionId }
            )
        }
    }

    fun undoHide() {
        _uiState.value.lastHiddenSubscription?.let { subscription ->
            viewModelScope.launch {
                subscriptionRepository.unhideSubscription(subscription.id)
                _uiState.value = _uiState.value.copy(lastHiddenSubscription = null)
            }
        }
    }

    fun markAsEnded(subscriptionId: Long) {
        viewModelScope.launch {
            val snapshot = _uiState.value.activeSubscriptions.find { it.id == subscriptionId }
            subscriptionRepository.markAsEnded(subscriptionId)
            _uiState.value = _uiState.value.copy(lastEndedSubscription = snapshot)
        }
    }

    fun undoEnd() {
        _uiState.value.lastEndedSubscription?.let { subscription ->
            viewModelScope.launch {
                subscriptionRepository.reactivateSubscription(subscription.id)
                _uiState.value = _uiState.value.copy(lastEndedSubscription = null)
            }
        }
    }

    fun reactivateSubscription(subscriptionId: Long) {
        viewModelScope.launch {
            subscriptionRepository.reactivateSubscription(subscriptionId)
        }
    }

    fun updateSubscription(
        id: Long,
        merchantName: String,
        amount: BigDecimal,
        nextPaymentDate: java.time.LocalDate?,
        category: String?,
        account: AccountBalanceEntity?,
        accountChanged: Boolean,
    ) {
        viewModelScope.launch {
            val existing = subscriptionRepository.getSubscriptionById(id) ?: return@launch
            subscriptionRepository.updateSubscription(
                existing.copy(
                    merchantName = merchantName.trim(),
                    amount = amount,
                    nextPaymentDate = nextPaymentDate,
                    category = category?.trim()?.takeIf { it.isNotEmpty() },
                    // Only re-key the funding account when the user actually touched
                    // the picker. Otherwise keep what's stored — the dialog can't
                    // pre-select an account that isn't in the balance list yet (e.g.
                    // one auto-captured from an SMS mandate), so blindly writing the
                    // picker's null would wipe that assignment. Picking clears via
                    // "No account" still work because that flips accountChanged. (#570)
                    bankName = if (accountChanged) account?.bankName ?: "Manual Entry" else existing.bankName,
                    accountLast4 = if (accountChanged) account?.accountLast4 else existing.accountLast4,
                    updatedAt = java.time.LocalDateTime.now()
                )
            )
        }
    }

    fun addDetected(suggestion: DetectedSubscription) {
        // Guard and hide the card before the first suspend, so a second tap
        // in the same frame cannot start another insert.
        if (!addingDetectedKeys.add(suggestion.key)) return
        _uiState.value = _uiState.value.copy(
            detected = _uiState.value.detected.filterNot { it.key == suggestion.key }
        )
        viewModelScope.launch {
            try {
                val alreadySaved = subscriptionRepository.getAllSubscriptions().first().any { existing ->
                    SubscriptionDetector.key(existing.merchantName, existing.currency) == suggestion.key
                }
                if (alreadySaved) {
                    addingDetectedKeys.remove(suggestion.key)
                    return@launch
                }
                addSubscriptionUseCase.execute(
                    merchantName = suggestion.merchantName,
                    amount = suggestion.amount,
                    nextPaymentDate = suggestion.nextPaymentDate,
                    billingCycle = suggestion.billingCycle,
                    category = suggestion.category ?: "Others",
                    currency = suggestion.currency,
                    bankName = suggestion.bankName,
                    accountLast4 = suggestion.accountLast4,
                )
            } catch (error: Exception) {
                addingDetectedKeys.remove(suggestion.key)
                throw error
            }
        }
    }

    fun dismissDetected(suggestion: DetectedSubscription) {
        viewModelScope.launch {
            userPreferencesRepository.dismissSubscriptionSuggestion(suggestion.key)
        }
    }

    fun deleteSubscription(subscriptionId: Long) {
        viewModelScope.launch {
            subscriptionRepository.deleteSubscription(subscriptionId)
        }
    }

    /**
     * #412 — user-initiated "mark as paid" (or "mark as received" for
     * income-direction subscriptions). Creates the transaction + advances
     * the schedule, then publishes a snackbar message the UI consumes.
     */
    fun markAsPaid(subscriptionId: Long, paymentDate: java.time.LocalDate) {
        viewModelScope.launch {
            val result = markSubscriptionPaidUseCase.execute(subscriptionId, paymentDate)
            val merchant = _uiState.value.activeSubscriptions.find { it.id == subscriptionId }?.merchantName
                ?: "Subscription"
            val message = when (result) {
                is MarkSubscriptionPaidUseCase.Result.Created ->
                    UiText.Res(R.string.subscriptions_msg_marked_paid, listOf(merchant, "${result.nextPaymentDate}"))
                is MarkSubscriptionPaidUseCase.Result.Linked ->
                    UiText.Res(R.string.subscriptions_msg_linked, listOf(merchant, "${result.nextPaymentDate}"))
                is MarkSubscriptionPaidUseCase.Result.AlreadyMarked ->
                    UiText.Res(R.string.subscriptions_msg_already_marked, listOf(merchant, "${result.nextPaymentDate}"))
                MarkSubscriptionPaidUseCase.Result.NoScheduledDate ->
                    UiText.Res(R.string.subscriptions_msg_no_scheduled_date, listOf(merchant))
                MarkSubscriptionPaidUseCase.Result.SubscriptionNotFound ->
                    UiText.Res(R.string.subscriptions_msg_not_found)
            }
            _uiState.value = _uiState.value.copy(markPaidMessage = message)
        }
    }

    /** UI calls this after consuming the snackbar message. */
    fun clearMarkPaidMessage() {
        _uiState.value = _uiState.value.copy(markPaidMessage = null)
    }

    /**
     * Suggested-payment candidates for the mark-as-paid sheet (#412
     * follow-up). When an EXPENSE subscription is on auto-pay, the bank
     * SMS already created a transaction — we surface those here so the
     * user can link instead of creating a duplicate phantom.
     *
     * Matching: case-insensitive merchant exact + amount exact (±₹0.01)
     * over the last 30 days, EXPENSE only, excluding phantoms we've
     * created ourselves. Amount match is required — a one-off ₹789
     * Netflix purchase shouldn't surface as a candidate for a ₹499 sub.
     */
    suspend fun candidatesFor(
        sub: SubscriptionEntity,
    ): List<com.pennywiseai.tracker.data.database.entity.TransactionEntity> {
        val cutoff = java.time.LocalDate.now().minusDays(30).atStartOfDay()
        return transactionRepository.findRecentExpensesByMerchantAndAmount(
            merchant = sub.merchantName,
            amount = sub.amount,
            since = cutoff,
            limit = 5,
        )
    }

    /**
     * Link an existing bank-derived transaction as this subscription's
     * current-cycle payment. Advances the schedule; doesn't duplicate.
     */
    fun linkExistingTransaction(subscriptionId: Long, transactionId: Long) {
        viewModelScope.launch {
            val result = markSubscriptionPaidUseCase.linkExisting(subscriptionId, transactionId)
            val merchant = _uiState.value.activeSubscriptions.find { it.id == subscriptionId }?.merchantName
                ?: "Subscription"
            val message = when (result) {
                is MarkSubscriptionPaidUseCase.Result.Linked ->
                    UiText.Res(R.string.subscriptions_msg_linked_existing, listOf(merchant, "${result.nextPaymentDate}"))
                MarkSubscriptionPaidUseCase.Result.NoScheduledDate ->
                    UiText.Res(R.string.subscriptions_msg_no_scheduled_date, listOf(merchant))
                MarkSubscriptionPaidUseCase.Result.SubscriptionNotFound ->
                    UiText.Res(R.string.subscriptions_msg_not_found)
                else -> UiText.Res(R.string.subscriptions_msg_linked_generic)
            }
            _uiState.value = _uiState.value.copy(markPaidMessage = message)
        }
    }
}

data class SubscriptionsUiState(
    val activeSubscriptions: List<SubscriptionEntity> = emptyList(),
    /** IDs of subs in [activeSubscriptions] whose current cycle is paid — used to drive the row badge. */
    val paidThisCycleIds: Set<Long> = emptySet(),
    /** Count of subs in [activeSubscriptions] whose current cycle is paid. */
    val paidThisCycleCount: Int = 0,
    val endedSubscriptions: List<SubscriptionEntity> = emptyList(),
    /** Unified-mode header total, already converted to [displayCurrency]. */
    val totalMonthlyAmount: BigDecimal = BigDecimal.ZERO,
    /** Native-mode header totals, kept per-currency so no sub is dropped. */
    val totalByCurrency: Map<String, Money> = emptyMap(),
    val convertedAmounts: Map<Long, BigDecimal> = emptyMap(),
    val displayCurrency: String? = null,
    val isUnifiedMode: Boolean = false,
    val isLoading: Boolean = true,
    val detected: List<DetectedSubscription> = emptyList(),
    val lastHiddenSubscription: SubscriptionEntity? = null,
    val lastEndedSubscription: SubscriptionEntity? = null,
    /** Snackbar text after a mark-as-paid action; cleared by [SubscriptionsViewModel.clearMarkPaidMessage]. */
    val markPaidMessage: UiText? = null,
)

private data class SubscriptionLedger(
    val subscriptions: List<SubscriptionEntity>,
    val transactions: List<TransactionEntity>,
    val dismissed: Set<String>,
)

private data class CurrencyPrefs(
    val isUnified: Boolean,
    val displayCurrency: String,
    val baseCurrency: String,
)

private fun TransactionEntity.toSubscriptionCharge(): SubscriptionCharge? {
    if (isDeleted) return null
    if (transactionType != TransactionType.EXPENSE && transactionType != TransactionType.CREDIT) return null
    if (loanId != null) return null
    if (merchantName.isBlank() || amount.signum() <= 0) return null
    val digits = accountNumber?.filter { it.isDigit() }.orEmpty()
    return SubscriptionCharge(
        merchantName = merchantName,
        amount = amount,
        currency = currency.ifBlank { "LKR" },
        date = dateTime.toLocalDate(),
        category = category.takeIf { it.isNotBlank() && !it.equals("Others", ignoreCase = true) },
        bankName = bankName,
        accountLast4 = digits.takeLast(4).takeIf { it.length == 4 },
    )
}