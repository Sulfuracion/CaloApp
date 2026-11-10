package com.controlcal.app.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.model.ResolvedSavedMeal
import com.controlcal.core.nutrition.FoodPortionFormatter
import com.controlcal.core.nutrition.MealEntrySaveResult
import com.controlcal.core.nutrition.MealTimeCalculator
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.nutrition.NutritionCalculator
import com.controlcal.core.nutrition.SavedMealCalculator
import com.controlcal.core.repo.MealConfigRepository
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.ProductRepository
import com.controlcal.core.repo.SavedMealRepository
import com.controlcal.core.search.ProductSearchEngine
import com.controlcal.core.search.TextNormalizer
import com.controlcal.core.suggest.QuickLogRanker
import com.controlcal.core.suggest.SavedMealRanker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

import com.controlcal.app.data.off.OffClient
import com.controlcal.app.data.off.OffProduct
import com.controlcal.app.data.off.OffSearchByNameResult
import com.controlcal.app.data.off.OffSearchItem
import com.controlcal.app.data.off.OffSearchResult

@OptIn(FlowPreview::class)
@HiltViewModel
class AddEntryViewModel(
    private val productRepository: ProductRepository,
    private val mealEntryRepository: MealEntryRepository,
    private val mealConfigRepository: MealConfigRepository,
    private val savedMealRepository: SavedMealRepository,
    private val offClient: OffClient,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    @Inject
    constructor(
        productRepository: ProductRepository,
        mealEntryRepository: MealEntryRepository,
        mealConfigRepository: MealConfigRepository,
        savedMealRepository: SavedMealRepository,
        offClient: OffClient,
        clock: Clock,
    ) : this(
        productRepository = productRepository,
        mealEntryRepository = mealEntryRepository,
        mealConfigRepository = mealConfigRepository,
        savedMealRepository = savedMealRepository,
        offClient = offClient,
        clock = clock,
        defaultDispatcher = Dispatchers.Default,
    )

    private val _uiState = MutableStateFlow(AddEntryUiState())
    val uiState: StateFlow<AddEntryUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private var cachedActiveProducts: List<Product> = emptyList()
    private var cachedAllProducts: Map<Long, Product> = emptyMap()
    private var cachedRecentProductIds: List<Long> = emptyList()

    init {
        viewModelScope.launch(defaultDispatcher) {
            searchQueryFlow
                .debounce(150L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    executeSearch(query)
                }
        }
    }

    fun initialize(date: LocalDate, mealId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    targetDate = date,
                    targetMealId = mealId,
                    searchQuery = "",
                    selectedProductForPortion = null,
                    isAddedSuccessfully = false,
                    userMessage = null,
                    isLoading = true,
                    isOffSearching = false,
                    offSearchResults = emptyList(),
                    offSearchMessage = null,
                    isFetchingOffProductDetails = false,
                )
            }
            searchQueryFlow.value = ""

            val configs = mealConfigRepository.getMealConfigs()
            val activeMeals = configs.filter { it.isActive }.sortedBy { it.startTime }
            val mealName = configs.find { it.id == mealId }?.name ?: mealId

            cachedActiveProducts = productRepository.getActiveProducts()
            cachedAllProducts = productRepository.getAllIncludingArchived().associateBy { it.id }
            loadHabitualFoods(mealId)
            loadSavedMeals()
            executeSearch("")

            _uiState.update {
                it.copy(
                    targetMealName = mealName,
                    availableMeals = activeMeals,
                    isLoading = false,
                )
            }
        }
    }

    fun onSelectTargetMeal(mealId: String) {
        val state = _uiState.value
        if (state.targetMealId == mealId) return
        val mealName = state.availableMeals.find { it.id == mealId }?.name ?: mealId
        _uiState.update {
            it.copy(
                targetMealId = mealId,
                targetMealName = mealName,
            )
        }
        viewModelScope.launch {
            loadHabitualFoods(mealId)
        }
    }

    private suspend fun loadHabitualFoods(mealId: String) {
        val sixtyDaysAgo = clock.millis() - (60L * 24L * 60L * 60L * 1000L)
        val history = mealEntryRepository.getSince(sixtyDaysAgo)
        cachedRecentProductIds = history.mapNotNull { it.productId }.distinct()
        val activeProducts = cachedActiveProducts.associateBy { it.id }
        val fallbackServings = cachedActiveProducts
            .mapNotNull { p -> p.servingG?.let { p.id to it } }
            .toMap()

        val suggestions = QuickLogRanker.rankHabitualsForMeal(
            entries = history,
            mealId = mealId,
            activeProductIds = activeProducts.keys,
            fallbackServingByProduct = fallbackServings,
            limit = 6,
        )

        val rankedHabituals = suggestions.mapNotNull { suggestion ->
            val product = activeProducts[suggestion.productId] ?: return@mapNotNull null
            val macros = NutritionCalculator.calculatePortionMacros(product, suggestion.defaultGrams)
            HabitualFoodUiModel(
                product = product,
                defaultGrams = suggestion.defaultGrams,
                formattedQuantity = FoodPortionFormatter.formatQuantity(
                    grams = suggestion.defaultGrams,
                    unitG = product.unitG,
                    unitName = product.unitName,
                    isLiquid = product.isLiquid,
                ),
                formattedKcal = "${NumberFormatHelper.formatDecimal(macros.kcal)} kcal",
            )
        }

        _uiState.update { it.copy(habitualFoods = rankedHabituals) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                isOffSearching = false,
                offSearchResults = emptyList(),
                offSearchMessage = null,
            )
        }
        searchQueryFlow.value = query
    }

    fun onSearchInOff() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOffSearching = true,
                    offSearchMessage = null,
                    offSearchResults = emptyList(),
                )
            }

            when (val result = offClient.searchProductsByName(query)) {
                is OffSearchByNameResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isOffSearching = false,
                            offSearchResults = result.items,
                            offSearchMessage = null,
                        )
                    }
                }
                is OffSearchByNameResult.Empty -> {
                    _uiState.update {
                        it.copy(
                            isOffSearching = false,
                            offSearchResults = emptyList(),
                            offSearchMessage = "No se encontraron productos en Open Food Facts",
                        )
                    }
                }
                is OffSearchByNameResult.Offline -> {
                    _uiState.update {
                        it.copy(
                            isOffSearching = false,
                            offSearchMessage = "Sin conexión a internet. Mostrando solo resultados locales",
                        )
                    }
                }
                is OffSearchByNameResult.RateLimited -> {
                    _uiState.update {
                        it.copy(
                            isOffSearching = false,
                            offSearchMessage = "Límite de búsquedas en Open Food Facts alcanzado. Espera ${result.secondsRemaining} s",
                        )
                    }
                }
                is OffSearchByNameResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isOffSearching = false,
                            offSearchMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun onSelectOffProduct(item: OffSearchItem, onNavigateToConfirmation: (OffProduct) -> Unit) {
        viewModelScope.launch {
            val code = item.code?.trim()
            if (!code.isNullOrEmpty()) {
                val localProduct = productRepository.getByBarcode(code)
                if (localProduct != null) {
                    onSelectProductForPortion(localProduct)
                    return@launch
                }
            }

            val baseOffProduct = item.offProduct
            val productToConfirm = if (!baseOffProduct.hasMissingMandatoryFields || code.isNullOrEmpty()) {
                baseOffProduct
            } else {
                _uiState.update { it.copy(isFetchingOffProductDetails = true) }
                try {
                    when (val result = offClient.getProductByBarcode(code)) {
                        is OffSearchResult.Success -> result.product
                        else -> baseOffProduct
                    }
                } finally {
                    _uiState.update { it.copy(isFetchingOffProductDetails = false) }
                }
            }

            onNavigateToConfirmation(productToConfirm)
        }
    }

    fun onDismissOffSearchMessage() {
        _uiState.update { it.copy(offSearchMessage = null) }
    }

    suspend fun loadSavedMeals() {
        if (cachedAllProducts.isEmpty()) {
            cachedAllProducts = productRepository.getAllIncludingArchived().associateBy { it.id }
        }
        val rawMeals = savedMealRepository.getAll()
        val resolved = rawMeals.map { SavedMealCalculator.resolveMeal(it, cachedAllProducts, multiplier = 1.0) }
        val ranked = SavedMealRanker.rankResolved(resolved)
        _uiState.update { it.copy(savedMeals = ranked) }
    }

    private suspend fun executeSearch(query: String) {
        if (cachedActiveProducts.isEmpty()) {
            cachedActiveProducts = productRepository.getActiveProducts()
        }
        if (cachedAllProducts.isEmpty()) {
            cachedAllProducts = productRepository.getAllIncludingArchived().associateBy { it.id }
        }
        val results = withContext(defaultDispatcher) {
            ProductSearchEngine.search(
                query = query,
                products = cachedActiveProducts,
                recentProductIds = cachedRecentProductIds,
            )
        }
        val filteredMeals = if (query.isBlank()) {
            emptyList()
        } else {
            val normalizedQuery = TextNormalizer.normalize(query)
            _uiState.value.savedMeals.filter { meal ->
                TextNormalizer.normalize(meal.meal.name).contains(normalizedQuery, ignoreCase = true)
            }
        }
        _uiState.update {
            it.copy(
                searchResults = results,
                filteredSavedMeals = filteredMeals,
            )
        }
    }

    fun onSelectProductForPortion(product: Product, initialGrams: Double? = null) {
        val unitG = product.unitG
        val defaultG = initialGrams ?: product.servingG ?: unitG ?: 100.0
        val hasUnit = unitG != null && unitG > 0.0
        val isUnitsMode = hasUnit

        val initialValueText = if (isUnitsMode && hasUnit) {
            val units = NutritionCalculator.gramsToUnits(defaultG, unitG)
            NumberFormatHelper.formatDecimal(units)
        } else {
            NumberFormatHelper.formatDecimal(defaultG)
        }

        val portion = NutritionCalculator.calculatePortionMacros(product, defaultG)

        _uiState.update {
            it.copy(
                selectedProductForPortion = product,
                isMeasuringInUnits = isUnitsMode,
                inputQuantityText = initialValueText,
                inputQuantityError = null,
                portionPreviewKcal = portion.kcal,
                portionPreviewProtein = portion.protein,
                portionPreviewCarbs = portion.carbs,
                portionPreviewFat = portion.fat,
            )
        }
    }

    fun onToggleMeasuringUnit() {
        val state = _uiState.value
        val product = state.selectedProductForPortion ?: return
        val unitG = product.unitG ?: return
        if (unitG <= 0.0) return

        val newMeasuringInUnits = !state.isMeasuringInUnits
        val currentGrams = resolveGrams(state.inputQuantityText, state.isMeasuringInUnits, unitG) ?: 100.0

        val newText = if (newMeasuringInUnits) {
            val units = NutritionCalculator.gramsToUnits(currentGrams, unitG)
            NumberFormatHelper.formatDecimal(units)
        } else {
            NumberFormatHelper.formatDecimal(currentGrams)
        }

        val portion = NutritionCalculator.calculatePortionMacros(product, currentGrams)

        _uiState.update {
            it.copy(
                isMeasuringInUnits = newMeasuringInUnits,
                inputQuantityText = newText,
                inputQuantityError = null,
                portionPreviewKcal = portion.kcal,
                portionPreviewProtein = portion.protein,
                portionPreviewCarbs = portion.carbs,
                portionPreviewFat = portion.fat,
            )
        }
    }

    fun onQuantityInputChanged(text: String) {
        val parsed = NumberFormatHelper.parseDecimal(text)
        val error = when {
            text.isBlank() -> "Campo obligatorio"
            parsed == null -> "Número no válido"
            parsed <= 0.0 -> "Debe ser mayor que 0"
            else -> null
        }

        val state = _uiState.value
        val product = state.selectedProductForPortion
        val grams = if (product != null && parsed != null && parsed > 0.0) {
            resolveGrams(text, state.isMeasuringInUnits, product.unitG) ?: 0.0
        } else 0.0

        val portion = if (product != null && grams > 0.0) {
            NutritionCalculator.calculatePortionMacros(product, grams)
        } else null

        _uiState.update {
            it.copy(
                inputQuantityText = text,
                inputQuantityError = error,
                portionPreviewKcal = portion?.kcal ?: 0.0,
                portionPreviewProtein = portion?.protein ?: 0.0,
                portionPreviewCarbs = portion?.carbs ?: 0.0,
                portionPreviewFat = portion?.fat ?: 0.0,
            )
        }
    }

    private fun resolveGrams(text: String, inUnits: Boolean, unitG: Double?): Double? {
        val parsed = NumberFormatHelper.parseDecimal(text) ?: return null
        if (inUnits && unitG != null && unitG > 0.0) {
            return NutritionCalculator.unitsToGrams(parsed, unitG)
        }
        return parsed
    }

    fun onConfirmAddProduct(onSuccess: (com.controlcal.core.nutrition.MealEntrySaveResult) -> Unit = {}) {
        val state = _uiState.value
        if (state.isSaving) return
        val product = state.selectedProductForPortion ?: return
        val grams = resolveGrams(state.inputQuantityText, state.isMeasuringInUnits, product.unitG)

        if (grams == null || grams <= 0.0) {
            _uiState.update { it.copy(inputQuantityError = "Introduce una cantidad válida") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val result = saveEntry(product, grams)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        selectedProductForPortion = null,
                        isAddedSuccessfully = true,
                        lastSaveResult = result,
                    )
                }
                onSuccess(result)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        inputQuantityError = "Error al guardar. Comprueba los datos e inténtalo de nuevo",
                        userMessage = "Error al guardar. Comprueba los datos e inténtalo de nuevo",
                    )
                }
            }
        }
    }

    fun onQuickAddHabitual(habitual: HabitualFoodUiModel, onSuccess: (com.controlcal.core.nutrition.MealEntrySaveResult) -> Unit = {}) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val result = saveEntry(habitual.product, habitual.defaultGrams)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isAddedSuccessfully = true,
                        lastSaveResult = result,
                    )
                }
                onSuccess(result)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        userMessage = "Error al guardar. Comprueba los datos e inténtalo de nuevo",
                    )
                }
            }
        }
    }

    private suspend fun saveEntry(product: Product, grams: Double): com.controlcal.core.nutrition.MealEntrySaveResult {
        val state = _uiState.value
        val portion = NutritionCalculator.calculatePortionMacros(product, grams)
        val existingEntries = mealEntryRepository.getByDateAndMeal(state.targetDate.toString(), state.targetMealId)
        val configuredStartTime = mealConfigRepository.getMealConfigs().find { it.id == state.targetMealId }?.startTime
        val loggedAt = com.controlcal.core.nutrition.MealTimeCalculator.calculateLoggedAt(
            date = state.targetDate,
            existingEntries = existingEntries,
            configuredStartTime = configuredStartTime,
            zoneId = clock.zone,
            clock = clock,
        )

        val entry = MealEntry(
            id = 0L,
            date = state.targetDate.toString(),
            meal = state.targetMealId,
            productId = product.id,
            productName = product.name,
            grams = grams,
            kcal = portion.kcal,
            protein = portion.protein,
            carbs = portion.carbs,
            fat = portion.fat,
            loggedAt = loggedAt,
        )

        val result = mealEntryRepository.save(entry)
        cachedRecentProductIds = listOf(product.id) + cachedRecentProductIds.filter { it != product.id }
        return result
    }

    fun onDismissPortionDialog() {
        _uiState.update { it.copy(selectedProductForPortion = null) }
    }

    fun onSelectSavedMealForLog(meal: ResolvedSavedMeal) {
        val reResolved = SavedMealCalculator.resolveMeal(meal.meal, cachedAllProducts, multiplier = 1.0)
        _uiState.update {
            it.copy(
                selectedSavedMealForLog = reResolved,
                savedMealMultiplier = 1.0,
                customSavedMealMultiplierText = "",
            )
        }
    }

    fun onSavedMealMultiplierSelected(multiplier: Double) {
        val current = _uiState.value.selectedSavedMealForLog ?: return
        val reResolved = SavedMealCalculator.resolveMeal(current.meal, cachedAllProducts, multiplier = multiplier)
        _uiState.update {
            it.copy(
                selectedSavedMealForLog = reResolved,
                savedMealMultiplier = multiplier,
                customSavedMealMultiplierText = "",
            )
        }
    }

    fun onCustomSavedMealMultiplierChanged(text: String) {
        val current = _uiState.value.selectedSavedMealForLog ?: return
        val parsed = NumberFormatHelper.parseDecimal(text)
        val mult = if (parsed != null && parsed > 0.0) parsed else 1.0
        val reResolved = SavedMealCalculator.resolveMeal(current.meal, cachedAllProducts, multiplier = mult)
        _uiState.update {
            it.copy(
                selectedSavedMealForLog = reResolved,
                savedMealMultiplier = mult,
                customSavedMealMultiplierText = text,
            )
        }
    }

    fun onDismissSavedMealDialog() {
        _uiState.update {
            it.copy(
                selectedSavedMealForLog = null,
                savedMealMultiplier = 1.0,
                customSavedMealMultiplierText = "",
            )
        }
    }

    fun onConfirmLogSavedMeal(onSuccess: (ResolvedSavedMeal, List<MealEntrySaveResult>) -> Unit = { _, _ -> }) {
        val state = _uiState.value
        if (state.isSaving) return
        val selected = state.selectedSavedMealForLog ?: return
        val multiplier = state.savedMealMultiplier
        if (multiplier <= 0.0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val existingEntries = mealEntryRepository.getByDateAndMeal(state.targetDate.toString(), state.targetMealId)
                val configuredStartTime = mealConfigRepository.getMealConfigs().find { it.id == state.targetMealId }?.startTime
                val loggedAt = MealTimeCalculator.calculateLoggedAt(
                    date = state.targetDate,
                    existingEntries = existingEntries,
                    configuredStartTime = configuredStartTime,
                    zoneId = clock.zone,
                    clock = clock,
                )

                val entries = SavedMealCalculator.buildMealEntries(
                    meal = selected.meal,
                    productsById = cachedAllProducts,
                    multiplier = multiplier,
                    date = state.targetDate.toString(),
                    mealId = state.targetMealId,
                    loggedAt = loggedAt,
                )

                val results = mealEntryRepository.saveAll(entries)
                savedMealRepository.recordMealUsage(selected.meal.id, clock.millis())

                loadSavedMeals()

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        selectedSavedMealForLog = null,
                    )
                }
                onSuccess(selected, results)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        userMessage = "Error al guardar. Comprueba los datos e inténtalo de nuevo",
                    )
                }
            }
        }
    }

    fun onMessageConsumed() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
