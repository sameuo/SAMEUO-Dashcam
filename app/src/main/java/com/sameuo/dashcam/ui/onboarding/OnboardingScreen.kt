package com.sameuo.dashcam.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameuo.dashcam.data.repository.ConnectionState
import com.sameuo.dashcam.ui.components.CenteredProgress
import com.sameuo.dashcam.ui.components.ConfirmDialog
import com.sameuo.dashcam.ui.components.PrimaryRedButton
import com.sameuo.dashcam.ui.components.SecondaryButton

@Composable
fun OnboardingFlow(
    onConnected: () -> Unit,
    onEnterOffline: () -> Unit,
    vm: OnboardingViewModel = viewModel(),
) {
    val step by vm.step.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh detected SSID whenever the user returns from system Wi-Fi Settings.
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshSsid()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // React to a successful connection.
    val conn by vm.connectionState.collectAsStateWithLifecycle()
    LaunchedEffect(conn, step) {
        if (step == OnboardingStep.CONNECTING && conn is ConnectionState.Connected) onConnected()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
                )
            )
    ) {
        when (step) {
            OnboardingStep.SPLASH -> Splash(vm)
            OnboardingStep.WARNING -> WarningStep(vm)
            OnboardingStep.CONNECT -> ConnectStep(
                vm = vm,
                onOpenWifi = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                onOffline = onEnterOffline,
            )
            OnboardingStep.SELECT_MODEL -> SelectModelStep(vm)
            OnboardingStep.CONNECTING -> ConnectingStep()
        }

        val errorVisible by vm.errorVisible.collectAsStateWithLifecycle()
        if (errorVisible) {
            ConfirmDialog(
                title = "Connection Error",
                message = "Please check your camera. Make sure its power is On, and the Wi-Fi mode is at the APP Mode. " +
                    "Then go to your phone's Wi-Fi Settings and try to connect to the camera's Wi-Fi network again.",
                confirmText = "OK",
                onConfirm = vm::errorOk,
                onDismiss = vm::errorOk,
            )
        }
    }
}

/* ---------------- Splash ---------------- */

@Composable
private fun Splash(vm: OnboardingViewModel) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        vm.splashFinished()
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("SAMEUO", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "DASHCAM COMPANION",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 4.sp,
        )
    }
}

/* ---------------- Safety warning ---------------- */

@Composable
private fun WarningStep(vm: OnboardingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text("Safety Warning", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            "Do not operate this app while driving. Distracted driving can result in death, " +
                "serious personal injury, and property damage. By closing this warning you " +
                "acknowledge and accept responsibility for any consequences of using this " +
                "app while operating a vehicle.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(34.dp))
        PrimaryRedButton("I ACKNOWLEDGE AND ACCEPT", vm::acknowledgeWarning)
    }
}

/* ---------------- Connect camera ---------------- */

@Composable
private fun ConnectStep(
    vm: OnboardingViewModel,
    onOpenWifi: () -> Unit,
    onOffline: () -> Unit,
) {
    val ssid by vm.ssid.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(30.dp))
        Text("Connect Your Camera", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "To use the app, connect your camera via Wi-Fi.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(26.dp))
        PrimaryRedButton("OPEN WI-FI SETTINGS", onOpenWifi)
        Spacer(Modifier.height(24.dp))
        NumberedStep(1, "Open your phone's Wi-Fi Settings.")
        Spacer(Modifier.height(12.dp))
        NumberedStep(2, "Select your camera's Wi-Fi network (e.g. DVR_4f12).")
        Spacer(Modifier.height(12.dp))
        NumberedStep(3, "Return to this app and continue.")

        if (ssid.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Connected network: $ssid", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        SecondaryButton("I'VE CONNECTED", vm::goSelectModel)
        Spacer(Modifier.height(6.dp))
        OfflineButton(onOffline)
    }
}

@Composable
private fun OfflineButton(onOffline: () -> Unit) {
    Surface(
        onClick = onOffline,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "VIEW DOWNLOADED FILES",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun NumberedStep(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
    }
}

/* ---------------- Select model ---------------- */

@Composable
private fun SelectModelStep(vm: OnboardingViewModel) {
    val selected by vm.model.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(30.dp))
        Text("Select Your Camera Model", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "We auto-detect the connected DVR and default the selection for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        ModelCard(CameraModel.GEN4, selected == CameraModel.GEN4) { vm.selectModel(CameraModel.GEN4) }
        Spacer(Modifier.height(14.dp))
        ModelCard(CameraModel.GEN3, selected == CameraModel.GEN3) { vm.selectModel(CameraModel.GEN3) }
        Spacer(Modifier.weight(1f))
        PrimaryRedButton("NEXT", vm::startConnect)
    }
}

@Composable
private fun ModelCard(model: CameraModel, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(model.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(model.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/* ---------------- Connecting ---------------- */

@Composable
private fun ConnectingStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CenteredProgress(label = "Connecting to your camera…")
    }
}
