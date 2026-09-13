package com.fenyx.jtv.ui.login

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.foundation.layout.imePadding
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import com.fenyx.jtv.theme.LocalIsTouch
import com.fenyx.jtv.theme.Surface
import com.fenyx.jtv.theme.overscanH
import com.fenyx.jtv.theme.overscanV
import androidx.tv.material3.ClickableSurfaceDefaults
import com.fenyx.jtv.data.SettingsManager
import com.fenyx.jtv.data.JioApiClient
import kotlinx.coroutines.launch

import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun TvNumpad(
    onNumberClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    // Applied to the "1" key so the caller can land initial remote focus on the numpad.
    firstKeyModifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "➡")
    )

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Surface(
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                "➡" -> onSubmit()
                                else -> onNumberClick(key)
                            }
                        },
                        modifier = (if (key == "1") firstKeyModifier else Modifier).size(72.dp),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = com.fenyx.jtv.theme.TvDimens.FocusedScale),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    // When set, shows a "use a different sign-in method" affordance that returns to the setup chooser.
    onChangeMethod: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val focusManager = LocalFocusManager.current
    // Touch devices type into real fields with the IME; TVs use the D-pad numpad.
    val isTouch = LocalIsTouch.current

    var mobileNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) } // 1: Mobile, 2: OTP
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // OTP resend: SMS delivery does silently fail — without a resend affordance the only recovery
    // was "Change Number" → retype the whole number. A 30s countdown throttles re-sends.
    var resendCountdown by remember { mutableIntStateOf(0) }
    LaunchedEffect(step) {
        if (step == 2) {
            resendCountdown = 30
            while (resendCountdown > 0) {
                kotlinx.coroutines.delay(1_000)
                resendCountdown--
            }
        }
    }
    val resendOtp: () -> Unit = resendOtp@{
        if (isLoading || resendCountdown > 0) return@resendOtp
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = JioApiClient.sendOTP(mobileNumber)
            isLoading = false
            if (result.isSuccess) {
                otp = ""
                resendCountdown = 30
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to resend OTP"
            }
        }
    }

    // Error feedback: a short horizontal shake draws the eye to the message without any sound.
    val shake = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            val spec = androidx.compose.animation.core.tween<Float>(55)
            repeat(3) {
                shake.animateTo(12f, spec)
                shake.animateTo(-12f, spec)
            }
            shake.animateTo(0f, spec)
        }
    }

    // Hardware BACK: from the OTP step go back to the number step; from the number step return to the
    // setup chooser instead of exiting the app. Disabled when neither move exists — the old
    // unconditional handler swallowed Back on step 1 with no onChangeMethod and the user was stuck.
    androidx.activity.compose.BackHandler(enabled = step == 2 || onChangeMethod != null) {
        if (step == 2) { step = 1; otp = ""; errorMessage = null }
        else onChangeMethod?.invoke()
    }

    val onNumberClick = { digit: String ->
        if (step == 1) {
            if (mobileNumber.length < 10) mobileNumber += digit
        } else {
            if (otp.length < 6) otp += digit
        }
    }
    
    val onBackspace = {
        if (step == 1) {
            if (mobileNumber.isNotEmpty()) mobileNumber = mobileNumber.dropLast(1)
        } else {
            if (otp.isNotEmpty()) otp = otp.dropLast(1)
        }
    }
    
    val onSubmit: () -> Unit = onSubmit@{
        // In-flight guard: re-taps / re-OKs used to fire parallel sendOTP calls (double OTP
        // SMS, race on the step flip).
        if (isLoading) return@onSubmit
        if (step == 1) {
            if (mobileNumber.length >= 10) {
                isLoading = true
                errorMessage = null
                scope.launch {
                    val result = JioApiClient.sendOTP(mobileNumber)
                    isLoading = false
                    if (result.isSuccess) {
                        step = 2
                    } else {
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to send OTP"
                    }
                }
            } else {
                errorMessage = "Please enter a valid mobile number"
            }
        } else {
            if (otp.length >= 4) {
                isLoading = true
                errorMessage = null
                scope.launch {
                    val result = JioApiClient.verifyOTP(mobileNumber, otp)
                    isLoading = false
                    if (result.isSuccess) {
                        val authData = result.getOrNull()
                        if (authData != null) {
                            settingsManager.saveAuthData(authData)
                        }
                    } else {
                        errorMessage = result.exceptionOrNull()?.message ?: "Invalid OTP"
                    }
                }
            } else {
                errorMessage = "Please enter a valid OTP"
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // TV overscan on TVs; compact margins on touch devices.
            .padding(horizontal = overscanH(), vertical = overscanV())
            // Hardware-key digit/backspace support is for TV remotes with a number pad only.
            // On touch devices (incl. tablets/Chromebooks with a physical keyboard) consuming
            // digits here starves the real text fields of input.
            .then(
                if (!isTouch) Modifier.onPreviewKeyEvent {
                    if (it.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                        val digit = when (it.key) {
                            Key.Zero -> "0"; Key.One -> "1"; Key.Two -> "2"; Key.Three -> "3"
                            Key.Four -> "4"; Key.Five -> "5"; Key.Six -> "6"; Key.Seven -> "7"
                            Key.Eight -> "8"; Key.Nine -> "9"
                            else -> null
                        }
                        if (digit != null) {
                            onNumberClick(digit)
                            return@onPreviewKeyEvent true
                        }
                        if (it.key == Key.Backspace) {
                            onBackspace()
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                } else Modifier
            ),
        // Vertical scroll + IME padding: in phone landscape the IME used to cover the centered
        // form and the Send/Login button was unreachable while typing.
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Form
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 32.dp)
                    .graphicsLayer { translationX = shake.value },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "JTV Login",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Step indicator: which stage of the OTP flow the user is on.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(2) { i ->
                        val active = (i + 1) == step
                        Box(
                            modifier = Modifier
                                .size(if (active) 10.dp else 7.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                        if (i == 0) Spacer(modifier = Modifier.width(6.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (step == 1) "Step 1 · Number" else "Step 2 · OTP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onChangeMethod != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = onChangeMethod,
                        modifier = Modifier.heightIn(min = 44.dp),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            "← Use a different sign-in method",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                
                if (errorMessage != null) {
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (step == 1) {
                    Text(
                        "Enter your mobile number to receive an OTP",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isTouch) {
                        OutlinedTextField(
                            value = mobileNumber,
                            onValueChange = { v -> if (v.length <= 10 && v.all(Char::isDigit)) mobileNumber = v },
                            label = { Text("Mobile Number") },
                            placeholder = { Text("10-digit number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        TvInputDisplay(value = mobileNumber, label = "Mobile Number", placeholder = "10-digit number")
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Surface(
                        onClick = onSubmit,
                        modifier = Modifier.heightIn(min = 48.dp),
                        // Focused colour stays primary while loading (see the Login button below).
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isLoading) MaterialTheme.colorScheme.surfaceVariant
                                             else MaterialTheme.colorScheme.primaryContainer,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isLoading) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    if (isLoading) "Sending..." else "Send OTP",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "Enter the OTP sent to $mobileNumber",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isTouch) {
                        OutlinedTextField(
                            value = otp,
                            onValueChange = { v -> if (v.length <= 6 && v.all(Char::isDigit)) otp = v },
                            label = { Text("OTP") },
                            placeholder = { Text("Enter OTP") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        TvInputDisplay(value = otp, label = "OTP", placeholder = "Enter OTP")
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Surface(
                        onClick = onSubmit,
                        modifier = Modifier.heightIn(min = 48.dp),
                        // Focused colour stays primary while loading: the old swap to surfaceVariant
                        // made the FOCUSED button render identically focused or not on remotes.
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isLoading) MaterialTheme.colorScheme.surfaceVariant
                                             else MaterialTheme.colorScheme.primaryContainer,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isLoading) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    if (isLoading) "Verifying..." else "Login",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Resend affordance during the OTP wait (countdown re-arms on a successful send).
                    if (resendCountdown > 0) {
                        Text(
                            "Resend code in ${resendCountdown}s",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Surface(
                            onClick = resendOtp,
                            modifier = Modifier.heightIn(min = 44.dp),
                            colors = ClickableSurfaceDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                "Resend code",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = { step = 1; otp = ""; errorMessage = null },
                        modifier = Modifier.heightIn(min = 44.dp),
                        colors = ClickableSurfaceDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            "Change Number",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            if (!isTouch) {
                // Right side: Numpad. Land initial remote focus on the "1" key so the user can type
                // immediately without hunting for focus. Re-requested per step: the step-1/step-2
                // buttons swap above, and the removed focused node used to leave focus nowhere.
                val numpadFocus = remember { FocusRequester() }
                LaunchedEffect(step) { runCatching { numpadFocus.requestFocus() } }
                TvNumpad(
                    onNumberClick = onNumberClick,
                    onBackspace = onBackspace,
                    onSubmit = onSubmit,
                    firstKeyModifier = Modifier.focusRequester(numpadFocus)
                )
            }
        }
        }
    }
}

/** A TV-friendly read-only input display (the actual entry happens via the on-screen numpad). */
@Composable
private fun TvInputDisplay(value: String, label: String, placeholder: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                // Capped at the designed 300dp, but SHRINKS on narrow windows: the old hard
                // 300dp floor overflowed freeform/small emulator windows inside the weight(1f)
                // form column and pushed the digits past the edge.
                .fillMaxWidth(0.95f)
                .widthIn(max = 300.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text(
                text = value.ifEmpty { placeholder },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                // Only the entered digits get wide spacing (for readability). The placeholder uses
                // normal spacing so it reads as the same UI font as the rest of the screen.
                letterSpacing = if (value.isEmpty()) 0.sp else 4.sp,
                color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
