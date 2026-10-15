package com.controlcal.app.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R
import com.controlcal.app.camera.BarcodeAnalyzer
import com.controlcal.app.camera.CameraPreview
import com.controlcal.app.camera.VibrationHelper
import com.controlcal.app.data.off.OffProduct
import com.controlcal.app.ui.debug.DebugFeatures
import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.Product
import com.controlcal.core.nutrition.MealClassifier
import com.controlcal.core.nutrition.NumberFormatHelper
import java.time.LocalTime

@Composable
fun ScannerRoute(
    viewModel: ScannerViewModel,
    onBack: () -> Unit,
    onNavigateToConfirmation: (OffProduct) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToManualEntry: (String?) -> Unit,
    onNavigateToOcrCapture: (String?) -> Unit = {},
    onNavigateToDebugOcr: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val barcodeAnalyzer = remember {
        BarcodeAnalyzer(
            onBarcodeAccepted = { barcode ->
                viewModel.onBarcodeScanned(barcode)
            }
        )
    }

    DisposableEffect(barcodeAnalyzer) {
        onDispose {
            barcodeAnalyzer.close()
        }
    }

    LaunchedEffect(uiState.scannedBarcode) {
        val barcode = uiState.scannedBarcode
        if (barcode != null) {
            barcodeAnalyzer.isPaused = true
            VibrationHelper.vibrateShort(context)
        } else {
            barcodeAnalyzer.resetGate()
        }
    }

    LaunchedEffect(uiState.lookupState) {
        val state = uiState.lookupState
        if (state is BarcodeLookupState.OffProductFound) {
            onNavigateToConfirmation(state.offProduct)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionChecked(isGranted)
    }

    ScannerScreen(
        uiState = uiState,
        barcodeAnalyzer = barcodeAnalyzer,
        onRequestPermission = {
            viewModel.onRationaleAccepted()
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onDismissRationale = viewModel::onRationaleDismissed,
        onRetryPermission = {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onOpenSettings = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        },
        onToggleTorch = viewModel::onTorchToggled,
        onTorchAvailabilityChanged = viewModel::onTorchAvailabilityChanged,
        onScanAgain = viewModel::onScanAgain,
        onRetryLookup = viewModel::onRetryLookup,
        onAddLocalProductToDiary = viewModel::onAddLocalProductToDiary,
        onBack = onBack,
        onNavigateToAbout = onNavigateToAbout,
        onNavigateToManualEntry = onNavigateToManualEntry,
        onNavigateToOcrCapture = onNavigateToOcrCapture,
        onNavigateToDebugOcr = onNavigateToDebugOcr,
        modifier = modifier
    )
}

@Composable
fun ScannerScreen(
    uiState: ScannerUiState,
    barcodeAnalyzer: BarcodeAnalyzer? = null,
    onRequestPermission: () -> Unit,
    onDismissRationale: () -> Unit,
    onRetryPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTorch: () -> Unit,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    onScanAgain: () -> Unit,
    onRetryLookup: () -> Unit,
    onAddLocalProductToDiary: (Product, String, Double) -> Unit,
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToManualEntry: (String?) -> Unit,
    onNavigateToOcrCapture: (String?) -> Unit = {},
    onNavigateToDebugOcr: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (uiState.showRationaleDialog) {
        AlertDialog(
            onDismissRequest = onDismissRationale,
            title = {
                Text(text = stringResource(R.string.scanner_permission_rationale_title))
            },
            text = {
                Text(text = stringResource(R.string.scanner_permission_rationale_message))
            },
            confirmButton = {
                Button(onClick = onRequestPermission) {
                    Text(text = stringResource(R.string.scanner_permission_grant_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { onNavigateToManualEntry(null) }) {
                    Text(text = stringResource(R.string.scanner_manual_entry_action))
                }
            }
        )
    }

    when (uiState.permissionState) {
        CameraPermissionState.CHECKING,
        CameraPermissionState.RATIONALE -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                // Background surface while rationale dialog is shown
            }
        }

        CameraPermissionState.DENIED -> {
            PermissionDeniedContent(
                onBack = onBack,
                onNavigateToManualEntry = { onNavigateToManualEntry(null) },
                onRetryPermission = onRetryPermission,
                onOpenSettings = onOpenSettings,
                modifier = modifier
            )
        }

        CameraPermissionState.GRANTED -> {
            ScannerCameraContent(
                uiState = uiState,
                barcodeAnalyzer = barcodeAnalyzer,
                onBack = onBack,
                onNavigateToAbout = onNavigateToAbout,
                onNavigateToManualEntry = onNavigateToManualEntry,
                onNavigateToOcrCapture = onNavigateToOcrCapture,
                onNavigateToDebugOcr = onNavigateToDebugOcr,
                onToggleTorch = onToggleTorch,
                onTorchAvailabilityChanged = onTorchAvailabilityChanged,
                onScanAgain = onScanAgain,
                onRetryLookup = onRetryLookup,
                onAddLocalProductToDiary = onAddLocalProductToDiary,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ScannerCameraContent(
    uiState: ScannerUiState,
    barcodeAnalyzer: BarcodeAnalyzer?,
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToManualEntry: (String?) -> Unit,
    onNavigateToOcrCapture: (String?) -> Unit,
    onNavigateToDebugOcr: () -> Unit,
    onToggleTorch: () -> Unit,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    onScanAgain: () -> Unit,
    onRetryLookup: () -> Unit,
    onAddLocalProductToDiary: (Product, String, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera preview streaming layer with barcode analysis
        CameraPreview(
            isTorchOn = uiState.isTorchOn,
            onTorchAvailabilityChanged = onTorchAvailabilityChanged,
            imageAnalyzer = barcodeAnalyzer,
            modifier = Modifier.fillMaxSize()
        )

        // Viewfinder reticle overlay
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 280.dp, height = 180.dp)
                    .border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(16.dp)
                    )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Text(
                    text = stringResource(R.string.scanner_viewfinder_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Top bar overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.scanner_back),
                    tint = Color.White
                )
            }

            Text(
                text = stringResource(R.string.scanner_title),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (DebugFeatures.isDebugOcrAvailable) {
                    IconButton(onClick = onNavigateToDebugOcr) {
                        Icon(
                            painter = painterResource(R.drawable.ic_document_scanner),
                            contentDescription = stringResource(R.string.debug_ocr_action),
                            tint = Color.White
                        )
                    }
                }

                IconButton(onClick = onNavigateToAbout) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = stringResource(R.string.scanner_about_action),
                        tint = Color.White
                    )
                }

                TextButton(onClick = { onNavigateToManualEntry(null) }) {
                    Text(
                        text = stringResource(R.string.scanner_manual_entry_action),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Bottom torch toggle overlay (shown when torch is available and no card is covering it)
        if (uiState.isTorchAvailable && uiState.scannedBarcode == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 36.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                FilledIconButton(
                    onClick = onToggleTorch,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (uiState.isTorchOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Black.copy(alpha = 0.6f)
                        },
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        painter = painterResource(
                            if (uiState.isTorchOn) {
                                R.drawable.ic_flashlight_on
                            } else {
                                R.drawable.ic_flashlight_off
                            }
                        ),
                        contentDescription = stringResource(
                            if (uiState.isTorchOn) {
                                R.string.scanner_torch_turn_off
                            } else {
                                R.string.scanner_torch_turn_on
                            }
                        ),
                        tint = Color.White
                    )
                }
            }
        }

        // Overlay card based on BarcodeLookupState
        when (val lookup = uiState.lookupState) {
            is BarcodeLookupState.Idle -> {
                // No card shown
            }

            is BarcodeLookupState.Searching -> {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.scanner_searching_product),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = lookup.barcode,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is BarcodeLookupState.LocalProductFound -> {
                LocalProductCard(
                    product = lookup.product,
                    availableMeals = uiState.availableMeals,
                    defaultMeal = uiState.preselectedMeal,
                    onAddToDiary = { meal, grams ->
                        onAddLocalProductToDiary(lookup.product, meal, grams)
                    },
                    onScanAgain = onScanAgain,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            is BarcodeLookupState.OffProductFound -> {
                // Handled via LaunchedEffect navigation
            }

            is BarcodeLookupState.Offline -> {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.scanner_product_offline_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.scanner_product_offline_message),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onNavigateToManualEntry(lookup.barcode) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.scanner_permission_denied_manual_button))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRetryLookup,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.scanner_retry_action))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onScanAgain,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.scanner_barcode_scan_again))
                        }
                    }
                }
            }

            is BarcodeLookupState.NotFound -> {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.scanner_product_not_found_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.scanner_product_not_found_message, lookup.barcode),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onNavigateToOcrCapture(lookup.barcode) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.ocr_capture_action_from_scanner))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onNavigateToManualEntry(lookup.barcode) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.scanner_permission_denied_manual_button))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onScanAgain,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.scanner_barcode_scan_again))
                        }
                    }
                }
            }

            is BarcodeLookupState.Error -> {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.scanner_product_error_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = lookup.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRetryLookup,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.scanner_retry_action))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onNavigateToManualEntry(lookup.barcode) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.scanner_permission_denied_manual_button))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onScanAgain,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.scanner_barcode_scan_again))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalProductCard(
    product: Product,
    availableMeals: List<ConfiguredMeal>,
    defaultMeal: String,
    onAddToDiary: (String, Double) -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMeal by remember(defaultMeal) {
        mutableStateOf(defaultMeal)
    }
    val defaultGramsText = product.servingG?.let { NumberFormatHelper.formatDecimal(it) } ?: "100"
    var gramsText by remember { mutableStateOf(defaultGramsText) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 24.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.scanner_product_already_saved_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = product.name,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            val brand = product.brand
            if (!brand.isNullOrBlank()) {
                Text(
                    text = brand,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.scanner_product_already_saved_subtitle,
                    NumberFormatHelper.formatDecimal(product.kcal100g),
                    NumberFormatHelper.formatDecimal(product.protein100g)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Meal chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableMeals.forEach { meal ->
                    FilterChip(
                        selected = selectedMeal == meal.id,
                        onClick = { selectedMeal = meal.id },
                        label = { Text(meal.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = gramsText,
                onValueChange = { gramsText = it },
                label = { Text(stringResource(R.string.confirm_product_quantity_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            val parsedGrams = NumberFormatHelper.parseDecimal(gramsText)
            Button(
                onClick = {
                    if (parsedGrams != null && parsedGrams > 0) {
                        onAddToDiary(selectedMeal, parsedGrams)
                    }
                },
                enabled = parsedGrams != null && parsedGrams > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.scanner_product_already_saved_add_action))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onScanAgain,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.scanner_barcode_scan_again))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionDeniedContent(
    onBack: () -> Unit,
    onNavigateToManualEntry: () -> Unit,
    onRetryPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.scanner_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_camera_disabled),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.scanner_permission_denied_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.scanner_permission_denied_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onNavigateToManualEntry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.scanner_permission_denied_manual_button))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onRetryPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.scanner_permission_denied_retry_button))
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.scanner_permission_denied_settings_button))
            }
        }
    }
}
