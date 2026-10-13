package com.controlcal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.controlcal.app.ui.confirm.ProductConfirmationRoute
import com.controlcal.app.ui.confirm.ProductConfirmationViewModel
import com.controlcal.app.ui.scanner.ScannerRoute
import com.controlcal.app.ui.scanner.ScannerViewModel
import com.controlcal.app.ui.theme.ControlCalTheme
import dagger.hilt.android.AndroidEntryPoint

enum class MainDestination {
    SCANNER,
    PRODUCT_CONFIRMATION,
    ABOUT,
    MANUAL_ENTRY
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val scannerViewModel: ScannerViewModel by viewModels()
    private val confirmationViewModel: ProductConfirmationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ControlCalTheme {
                var currentDestination by remember { mutableStateOf(MainDestination.SCANNER) }
                var selectedOffProduct by remember { mutableStateOf<OffProduct?>(null) }
                var manualEntryBarcode by remember { mutableStateOf<String?>(null) }

                when (currentDestination) {
                    MainDestination.SCANNER -> {
                        ScannerRoute(
                            viewModel = scannerViewModel,
                            onBack = { currentDestination = MainDestination.MANUAL_ENTRY },
                            onNavigateToConfirmation = { offProduct ->
                                selectedOffProduct = offProduct
                                currentDestination = MainDestination.PRODUCT_CONFIRMATION
                            },
                            onNavigateToAbout = {
                                currentDestination = MainDestination.ABOUT
                            },
                            onNavigateToManualEntry = { barcode ->
                                manualEntryBarcode = barcode
                                currentDestination = MainDestination.MANUAL_ENTRY
                            }
                        )
                    }

                    MainDestination.PRODUCT_CONFIRMATION -> {
                        val product = selectedOffProduct
                        if (product != null) {
                            ProductConfirmationRoute(
                                offProduct = product,
                                viewModel = confirmationViewModel,
                                onConfirmed = {
                                    selectedOffProduct = null
                                    scannerViewModel.onScanAgain()
                                    currentDestination = MainDestination.SCANNER
                                },
                                onDiscard = {
                                    selectedOffProduct = null
                                    scannerViewModel.onScanAgain()
                                    currentDestination = MainDestination.SCANNER
                                }
                            )
                        } else {
                            currentDestination = MainDestination.SCANNER
                        }
                    }

                    MainDestination.ABOUT -> {
                        AboutScreen(
                            onBack = { currentDestination = MainDestination.SCANNER }
                        )
                    }

                    MainDestination.MANUAL_ENTRY -> {
                        ManualEntryPlaceholderScreen(
                            initialBarcode = manualEntryBarcode,
                            onOpenScanner = {
                                manualEntryBarcode = null
                                currentDestination = MainDestination.SCANNER
                            }
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
                onClick = onOpenScanner,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.open_scanner_action))
            }
        }
    }
}
