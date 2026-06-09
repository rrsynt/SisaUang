package com.example.presentation.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.presentation.viewmodel.MainViewModel
import com.example.security.SecurityHelper

@Composable
fun PinLockScreen(
    viewModel: MainViewModel,
    isConfigurationMode: Boolean = false, // If true, we are creating a PIN from settings
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val storedPinHash by viewModel.securePinHash.collectAsState()
    val isBioEnabled by viewModel.isBiometricEnabled.collectAsState()
    val languageActive by viewModel.languageState.collectAsState()
    
    var enteredPin by remember { mutableStateOf("") }
    var setupStep by remember { mutableStateOf(if (storedPinHash == null) 0 else 2) } // 0 = Enter new PIN, 1 = Confirm, 2 = Enter unlocking PIN
    var firstEnteredPin by remember { mutableStateOf("") }
    var attemptsRemaining by remember { mutableStateOf(5) }

    // Automatic biometric triggering upon enter
    LaunchedEffect(setupStep, isBioEnabled) {
        if (setupStep == 2 && isBioEnabled) {
            val activity = context as? Activity
            if (activity != null) {
                SecurityHelper.authenticateBiometrics(
                    activity = activity,
                    onSuccess = {
                        onSuccess()
                    },
                    onFailure = {
                        // Let falling back to manual PIN entry gracefully
                    }
                )
            }
        }
    }

    val titleText = when (setupStep) {
        0 -> stringResource(id = R.string.security_create_pin)
        1 -> stringResource(id = R.string.security_confirm_pin)
        else -> stringResource(id = R.string.security_enter_pin)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Large Lock Icon & Title
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                textAlign = TextAlign.Center
            )
            
            if (setupStep == 2 && attemptsRemaining < 5) {
                Text(
                    text = stringResource(id = R.string.security_pin_wrong, attemptsRemaining),
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Masked Circles Indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(6) { index ->
                val pulseScale = remember { Animatable(1f) }
                val filled = index < enteredPin.length
                
                LaunchedEffect(filled) {
                    if (filled) {
                        pulseScale.animateTo(1.3f, animationSpec = tween(100))
                        pulseScale.animateTo(1f, animationSpec = tween(100))
                    }
                }

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .scale(pulseScale.value)
                        .clip(CircleShape)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                )
            }
        }

        // Number Pad
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("bio", "0", "back")
            )

            keys.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.2f)
                                .clip(CircleShape)
                                .clickable(enabled = !(key == "bio" && !isBioEnabled)) {
                                    when (key) {
                                        "back" -> {
                                            if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                        }
                                        "bio" -> {
                                            val activity = context as? Activity
                                            if (activity != null) {
                                                SecurityHelper.authenticateBiometrics(
                                                    activity = activity,
                                                    onSuccess = { onSuccess() },
                                                    onFailure = {}
                                                )
                                            }
                                        }
                                        else -> {
                                            if (enteredPin.length < 6) {
                                                enteredPin += key
                                            }
                                        }
                                    }

                                    // Trigger security verification on typing 6 digits
                                    if (enteredPin.length == 6) {
                                        when (setupStep) {
                                            0 -> {
                                                // Save first input
                                                firstEnteredPin = enteredPin
                                                enteredPin = ""
                                                setupStep = 1
                                            }
                                            1 -> {
                                                if (enteredPin == firstEnteredPin) {
                                                    // Hash & Save matching PIN
                                                    viewModel.createOrUpdatePin(enteredPin)
                                                    val msg = if (languageActive == "en") "PIN saved successfully!" else "PIN berhasil disimpan!"
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    onSuccess()
                                                } else {
                                                    // Mismatch
                                                    Toast.makeText(context, context.getString(R.string.security_pin_mismatch), Toast.LENGTH_SHORT).show()
                                                    enteredPin = ""
                                                    setupStep = 0
                                                }
                                            }
                                            2 -> {
                                                val verifiedHash = SecurityHelper.hashPin(enteredPin)
                                                if (verifiedHash == storedPinHash) {
                                                    viewModel.unlockApp()
                                                    onSuccess()
                                                } else {
                                                    attemptsRemaining--
                                                    enteredPin = ""
                                                    if (attemptsRemaining <= 0) {
                                                        val msg = if (languageActive == "en") "Too many failed attempts!" else "Terlalu banyak kegagalan PIN!"
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            when (key) {
                                "back" -> Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onBackground)
                                "bio" -> {
                                    if (isBioEnabled) {
                                        Icon(Icons.Default.Fingerprint, contentDescription = "Biometric Launch", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                    }
                                }
                                else -> {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Simple modifier helper for beautiful animated scale bounce
private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(
        scaleX = scale,
        scaleY = scale
    )
)