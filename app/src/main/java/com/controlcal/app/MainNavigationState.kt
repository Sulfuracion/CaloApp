package com.controlcal.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Stable
class MainNavigationState(
    initialDestination: MainDestination = MainDestination.DAILY_PANEL,
    initialBackStack: List<MainDestination> = listOf(initialDestination),
    activeMealContext: MealEntryContext? = null,
    manualEntryBarcode: String? = null,
    returnToPantryAfterConfirmation: Boolean = false,
) {
    private var destinationStack by mutableStateOf(
        initialBackStack.ifEmpty { listOf(initialDestination) },
    )

    var currentDestination: MainDestination
        get() = destinationStack.last()
        set(value) {
            if (value == currentDestination) return
            val existingIndex = destinationStack.indexOfLast { it == value }
            destinationStack = if (existingIndex >= 0) {
                destinationStack.take(existingIndex + 1)
            } else {
                destinationStack + value
            }
        }

    internal val savedDestinationStack: List<MainDestination>
        get() = destinationStack

    var activeMealContext by mutableStateOf(activeMealContext)
    var manualEntryBarcode by mutableStateOf(manualEntryBarcode)
    var returnToPantryAfterConfirmation by mutableStateOf(returnToPantryAfterConfirmation)
}

private val mainNavigationStateSaver = Saver<MainNavigationState, List<Any?>>(
    save = { state ->
        listOf(
            state.savedDestinationStack.joinToString("|") { it.name },
            state.activeMealContext?.date?.toString(),
            state.activeMealContext?.mealId,
            state.manualEntryBarcode,
            state.returnToPantryAfterConfirmation,
        )
    },
    restore = { values ->
        val savedStack = (values.getOrNull(0) as? String)
            ?.split('|')
            ?.mapNotNull { name ->
                MainDestination.entries.firstOrNull { it.name == name }
            }
            .orEmpty()
        val destinationStack = savedStack.ifEmpty {
            listOf(MainDestination.DAILY_PANEL)
        }
        val date = (values.getOrNull(1) as? String)?.let { value ->
            runCatching { java.time.LocalDate.parse(value) }.getOrNull()
        }
        val mealId = values.getOrNull(2) as? String
        MainNavigationState(
            initialDestination = destinationStack.last(),
            initialBackStack = destinationStack,
            activeMealContext = if (date != null && !mealId.isNullOrBlank()) {
                MealEntryContext(date, mealId)
            } else {
                null
            },
            manualEntryBarcode = values.getOrNull(3) as? String,
            returnToPantryAfterConfirmation = values.getOrNull(4) as? Boolean ?: false,
        )
    },
)

@Composable
fun rememberMainNavigationState(): MainNavigationState = rememberSaveable(
    saver = mainNavigationStateSaver,
) {
    MainNavigationState()
}
