package com.controlcal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.controlcal.app.data.off.OffProduct
import com.controlcal.app.ui.about.AboutScreen
import com.controlcal.app.ui.confirm.OcrConfirmationData
import com.controlcal.app.ui.confirm.ProductConfirmationRoute
import com.controlcal.app.ui.confirm.ProductConfirmationViewModel
import com.controlcal.app.ui.debug.DebugFeatures
import com.controlcal.app.ui.detail.MealDetailRoute
import com.controlcal.app.ui.detail.MealDetailViewModel
import com.controlcal.app.ui.entry.AddEntryRoute
import com.controlcal.app.ui.entry.AddEntryViewModel
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.app.ui.ocr.OcrCaptureRoute
import com.controlcal.app.ui.ocr.OcrCaptureViewModel
import com.controlcal.app.ui.panel.DailyPanelRoute
import com.controlcal.app.ui.panel.DailyPanelViewModel
import com.controlcal.app.ui.pantry.PantryRoute
import com.controlcal.app.ui.pantry.PantryViewModel
import com.controlcal.app.ui.scanner.ScannerRoute
import com.controlcal.app.ui.scanner.ScannerViewModel
import com.controlcal.app.ui.settings.SettingsRoute
import com.controlcal.app.ui.settings.SettingsViewModel
import com.controlcal.app.ui.tetris.MacroTetrisRoute
import com.controlcal.app.ui.tetris.MacroTetrisViewModel
import com.controlcal.app.ui.theme.ControlCalTheme
import com.controlcal.app.ui.weight.WeightRoute
import com.controlcal.app.ui.weight.WeightViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

data class MealEntryContext(
    val date: LocalDate,
    val mealId: String,
)

enum class MainDestination {
    DAILY_PANEL,
    MEAL_DETAIL,
    ADD_ENTRY,
    SCANNER,
    PRODUCT_CONFIRMATION,
    OCR_CAPTURE,
    ABOUT,
    MANUAL_ENTRY,
    DEBUG_OCR_COLLECTOR,
    WEIGHT,
    SETTINGS,
    PANTRY,
    MACRO_TETRIS,
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val dailyPanelViewModel: DailyPanelViewModel by viewModels()
    private val mealDetailViewModel: MealDetailViewModel by viewModels()
    private val addEntryViewModel: AddEntryViewModel by viewModels()
    private val scannerViewModel: ScannerViewModel by viewModels()
    private val confirmationViewModel: ProductConfirmationViewModel by viewModels()
    private val ocrCaptureViewModel: OcrCaptureViewModel by viewModels()
    private val weightViewModel: WeightViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val pantryViewModel: PantryViewModel by viewModels()
    private val macroTetrisViewModel: MacroTetrisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ControlCalTheme {
                var currentDestination by remember { mutableStateOf(MainDestination.DAILY_PANEL) }
                var selectedOffProduct by remember { mutableStateOf<OffProduct?>(null) }
                var selectedOcrData by remember { mutableStateOf<OcrConfirmationData?>(null) }
                var manualEntryBarcode by remember { mutableStateOf<String?>(null) }
                var activeMealContext by remember { mutableStateOf<MealEntryContext?>(null) }

                val navigateBack: () -> Unit = {
                    when (currentDestination) {
                        MainDestination.DAILY_PANEL -> {
                            // En el panel diario el BackHandler está desactivado, el sistema gestiona la salida
                        }
                        MainDestination.MEAL_DETAIL -> {
                            dailyPanelViewModel.loadDay(mealDetailViewModel.uiState.value.date)
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                        MainDestination.ADD_ENTRY -> {
                            val ctx = activeMealContext
                            if (ctx != null) {
                                mealDetailViewModel.initialize(ctx.date, ctx.mealId)
                                currentDestination = MainDestination.MEAL_DETAIL
                            } else {
                                dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                                currentDestination = MainDestination.DAILY_PANEL
                            }
                        }
                        MainDestination.SCANNER -> {
                            if (activeMealContext != null) {
                                currentDestination = MainDestination.ADD_ENTRY
                            } else {
                                currentDestination = MainDestination.DAILY_PANEL
                            }
                        }
                        MainDestination.OCR_CAPTURE -> {
                            currentDestination = MainDestination.SCANNER
                        }
                        MainDestination.PRODUCT_CONFIRMATION -> {
                            selectedOffProduct = null
                            selectedOcrData = null
                            val targetDate = activeMealContext?.date ?: dailyPanelViewModel.uiState.value.selectedDate
                            val targetMeal = activeMealContext?.mealId
                            dailyPanelViewModel.loadDay(targetDate)
                            if (targetMeal != null) {
                                mealDetailViewModel.initialize(targetDate, targetMeal)
                                currentDestination = MainDestination.MEAL_DETAIL
                            } else {
                                currentDestination = MainDestination.DAILY_PANEL
                            }
                        }
                        MainDestination.ABOUT -> {
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                        MainDestination.MANUAL_ENTRY -> {
                            manualEntryBarcode = null
                            currentDestination = MainDestination.SCANNER
                        }
                        MainDestination.DEBUG_OCR_COLLECTOR -> {
                            currentDestination = MainDestination.SCANNER
                        }
                        MainDestination.WEIGHT -> {
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                        MainDestination.SETTINGS -> {
                            dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                        MainDestination.PANTRY -> {
                            dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                        MainDestination.MACRO_TETRIS -> {
                            dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                    }
                }

                BackHandler(enabled = currentDestination != MainDestination.DAILY_PANEL) {
                    navigateBack()
                }

                when (currentDestination) {
                    MainDestination.DAILY_PANEL -> {
                        DailyPanelRoute(
                            viewModel = dailyPanelViewModel,
                            onNavigateToScanner = {
                                activeMealContext = null
                                scannerViewModel.onScanAgain()
                                currentDestination = MainDestination.SCANNER
                            },
                            onNavigateToWeight = {
                                weightViewModel.loadEntries()
                                currentDestination = MainDestination.WEIGHT
                            },
                            onNavigateToPantry = {
                                pantryViewModel.loadProducts()
                                currentDestination = MainDestination.PANTRY
                            },
                            onNavigateToSettings = {
                                settingsViewModel.loadSettings()
                                currentDestination = MainDestination.SETTINGS
                            },
                            onNavigateToMacroTetris = {
                                macroTetrisViewModel.loadData()
                                currentDestination = MainDestination.MACRO_TETRIS
                            },
                            onNavigateToMealDetail = { mealId ->
                                val date = dailyPanelViewModel.uiState.value.selectedDate
                                activeMealContext = MealEntryContext(date, mealId)
                                mealDetailViewModel.initialize(date, mealId)
                                currentDestination = MainDestination.MEAL_DETAIL
                            },
                            onNavigateToAddFood = { mealId ->
                                val date = dailyPanelViewModel.uiState.value.selectedDate
                                activeMealContext = MealEntryContext(date, mealId)
                                addEntryViewModel.initialize(date, mealId)
                                currentDestination = MainDestination.ADD_ENTRY
                            },
                        )
                    }

                    MainDestination.MEAL_DETAIL -> {
                        MealDetailRoute(
                            viewModel = mealDetailViewModel,
                            onBack = navigateBack,
                            onNavigateToAddFood = { mealId ->
                                val date = mealDetailViewModel.uiState.value.date
                                activeMealContext = MealEntryContext(date, mealId)
                                addEntryViewModel.initialize(date, mealId)
                                currentDestination = MainDestination.ADD_ENTRY
                            },
                        )
                    }

                    MainDestination.ADD_ENTRY -> {
                        AddEntryRoute(
                            viewModel = addEntryViewModel,
                            onBack = navigateBack,
                            onNavigateToScanner = {
                                val ctx = activeMealContext
                                if (ctx != null) {
                                    scannerViewModel.setContext(ctx.date, ctx.mealId)
                                }
                                scannerViewModel.onScanAgain()
                                currentDestination = MainDestination.SCANNER
                            },
                            onNavigateToOcrCapture = {
                                currentDestination = MainDestination.OCR_CAPTURE
                            },
                            onNavigateToManualEntry = {
                                manualEntryBarcode = null
                                currentDestination = MainDestination.MANUAL_ENTRY
                            },
                            onNavigateToConfirmation = { offProduct ->
                                selectedOffProduct = offProduct
                                selectedOcrData = null
                                val ctx = activeMealContext
                                confirmationViewModel.initialize(offProduct, ctx?.date, ctx?.mealId)
                                currentDestination = MainDestination.PRODUCT_CONFIRMATION
                            },
                            onEntryAdded = { saveResult ->
                                val ctx = activeMealContext
                                if (ctx != null) {
                                    mealDetailViewModel.initialize(ctx.date, ctx.mealId)
                                    dailyPanelViewModel.loadDay(ctx.date)
                                    when (saveResult) {
                                        is com.controlcal.core.nutrition.MealEntrySaveResult.Merged -> {
                                            mealDetailViewModel.notifyMerged(saveResult.mergedEntry, saveResult.previousEntry)
                                        }
                                        is com.controlcal.core.nutrition.MealEntrySaveResult.Created -> {
                                            mealDetailViewModel.notifyAdded(saveResult.entry)
                                        }
                                        null -> Unit
                                    }
                                    currentDestination = MainDestination.MEAL_DETAIL
                                } else {
                                    dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                                    when (saveResult) {
                                        is com.controlcal.core.nutrition.MealEntrySaveResult.Merged -> {
                                            dailyPanelViewModel.notifyMerged(saveResult.mergedEntry, saveResult.previousEntry)
                                        }
                                        is com.controlcal.core.nutrition.MealEntrySaveResult.Created -> {
                                            dailyPanelViewModel.notifyAdded(saveResult.entry)
                                        }
                                        null -> Unit
                                    }
                                    currentDestination = MainDestination.DAILY_PANEL
                                }
                            },
                        )
                    }

                    MainDestination.SCANNER -> {
                        ScannerRoute(
                            viewModel = scannerViewModel,
                            onBack = navigateBack,
                            onNavigateToConfirmation = { offProduct ->
                                selectedOffProduct = offProduct
                                selectedOcrData = null
                                val ctx = activeMealContext
                                confirmationViewModel.initialize(offProduct, ctx?.date, ctx?.mealId)
                                currentDestination = MainDestination.PRODUCT_CONFIRMATION
                            },
                            onNavigateToAbout = {
                                currentDestination = MainDestination.ABOUT
                            },
                            onNavigateToManualEntry = { barcode ->
                                manualEntryBarcode = barcode
                                currentDestination = MainDestination.MANUAL_ENTRY
                            },
                            onNavigateToOcrCapture = { barcode ->
                                manualEntryBarcode = barcode
                                currentDestination = MainDestination.OCR_CAPTURE
                            },
                            onNavigateToDebugOcr = {
                                currentDestination = MainDestination.DEBUG_OCR_COLLECTOR
                            }
                        )
                    }

                    MainDestination.OCR_CAPTURE -> {
                        OcrCaptureRoute(
                            viewModel = ocrCaptureViewModel,
                            targetBarcode = manualEntryBarcode,
                            onBack = navigateBack,
                            onNavigateToConfirmation = { photoFile, parsedNutrients, barcode ->
                                selectedOffProduct = null
                                selectedOcrData = OcrConfirmationData(
                                    photoFile = photoFile,
                                    parsedNutrients = parsedNutrients,
                                    barcode = barcode
                                )
                                val ctx = activeMealContext
                                confirmationViewModel.initializeFromOcr(
                                    photoFile = photoFile,
                                    parsedNutrients = parsedNutrients,
                                    barcode = barcode,
                                    targetDate = ctx?.date,
                                    targetMeal = ctx?.mealId,
                                )
                                currentDestination = MainDestination.PRODUCT_CONFIRMATION
                            }
                        )
                    }

                    MainDestination.PRODUCT_CONFIRMATION -> {
                        val offProduct = selectedOffProduct
                        val ocrData = selectedOcrData
                        if (offProduct != null || ocrData != null) {
                            ProductConfirmationRoute(
                                offProduct = offProduct,
                                ocrData = ocrData,
                                viewModel = confirmationViewModel,
                                onConfirmed = { saveResult ->
                                    val savedProductName = confirmationViewModel.uiState.value.name.trim()
                                    selectedOffProduct = null
                                    selectedOcrData = null
                                    val targetDate = activeMealContext?.date ?: dailyPanelViewModel.uiState.value.selectedDate
                                    val targetMeal = activeMealContext?.mealId
                                    dailyPanelViewModel.loadDay(targetDate)
                                    if (targetMeal != null) {
                                        mealDetailViewModel.initialize(targetDate, targetMeal)
                                        when (saveResult) {
                                            is com.controlcal.core.nutrition.MealEntrySaveResult.Merged -> {
                                                mealDetailViewModel.notifyMerged(saveResult.mergedEntry, saveResult.previousEntry)
                                            }
                                            is com.controlcal.core.nutrition.MealEntrySaveResult.Created -> {
                                                mealDetailViewModel.notifyAdded(saveResult.entry)
                                            }
                                            null -> {
                                                mealDetailViewModel.sendFeedback(
                                                    UiFeedbackEvent.Success(
                                                        UiText.StringResource(
                                                            R.string.feedback_product_saved,
                                                            savedProductName,
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                        currentDestination = MainDestination.MEAL_DETAIL
                                    } else {
                                        when (saveResult) {
                                            is com.controlcal.core.nutrition.MealEntrySaveResult.Merged -> {
                                                dailyPanelViewModel.notifyMerged(saveResult.mergedEntry, saveResult.previousEntry)
                                            }
                                            is com.controlcal.core.nutrition.MealEntrySaveResult.Created -> {
                                                dailyPanelViewModel.notifyAdded(saveResult.entry)
                                            }
                                            null -> {
                                                dailyPanelViewModel.sendFeedback(
                                                    UiFeedbackEvent.Success(
                                                        UiText.StringResource(
                                                            R.string.feedback_product_saved,
                                                            savedProductName,
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                        currentDestination = MainDestination.DAILY_PANEL
                                    }
                                },
                                onDiscard = navigateBack,
                            )
                        } else {
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                    }

                    MainDestination.ABOUT -> {
                        AboutScreen(
                            onBack = navigateBack
                        )
                    }

                    MainDestination.MANUAL_ENTRY -> {
                        ManualEntryPlaceholderScreen(
                            initialBarcode = manualEntryBarcode,
                            onOpenScanner = {
                                manualEntryBarcode = null
                                currentDestination = MainDestination.SCANNER
                            },
                            onNavigateToOcrCapture = { barcode ->
                                manualEntryBarcode = barcode
                                currentDestination = MainDestination.OCR_CAPTURE
                            }
                        )
                    }

                    MainDestination.DEBUG_OCR_COLLECTOR -> {
                        DebugFeatures.DebugOcrCollectorRoute(
                            onBack = navigateBack
                        )
                    }

                    MainDestination.WEIGHT -> {
                        WeightRoute(
                            viewModel = weightViewModel,
                            onBack = navigateBack
                        )
                    }

                    MainDestination.SETTINGS -> {
                        SettingsRoute(
                            viewModel = settingsViewModel,
                            onBack = navigateBack
                        )
                    }

                    MainDestination.PANTRY -> {
                        PantryRoute(
                            viewModel = pantryViewModel,
                            onBack = navigateBack
                        )
                    }

                    MainDestination.MACRO_TETRIS -> {
                        MacroTetrisRoute(
                            viewModel = macroTetrisViewModel,
                            onBack = navigateBack,
                            onNavigateToPantry = {
                                pantryViewModel.loadProducts()
                                currentDestination = MainDestination.PANTRY
                            },
                            onTetrisAdded = { mealName ->
                                dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                                dailyPanelViewModel.sendFeedback(
                                    UiFeedbackEvent.Success(
                                        UiText.StringResource(
                                            R.string.feedback_macro_tetris_added,
                                            mealName,
                                        )
                                    )
                                )
                                currentDestination = MainDestination.DAILY_PANEL
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryPlaceholderScreen(
    initialBarcode: String? = null,
    onOpenScanner: () -> Unit,
    onNavigateToOcrCapture: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.manual_entry_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenScanner) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.scanner_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (initialBarcode != null) {
                    "${stringResource(R.string.manual_entry_placeholder_message)} ($initialBarcode)"
                } else {
                    stringResource(R.string.manual_entry_placeholder_message)
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onNavigateToOcrCapture(initialBarcode) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.ocr_capture_action_from_manual))
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenScanner,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.open_scanner_action))
            }
        }
    }
}
