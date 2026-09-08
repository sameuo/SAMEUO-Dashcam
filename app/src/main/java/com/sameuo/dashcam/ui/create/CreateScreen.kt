package com.sameuo.dashcam.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sameuo.dashcam.ui.components.SectionCard
import com.sameuo.dashcam.ui.components.SectionLabel

/**
 * AI editing hub. Phase 1 ships the route to local clips; automatic highlight
 * cutting (G-sensor event + GPS-speed aware) is Phase 2 and is labelled as such
 * rather than faked.
 */
@Composable
fun CreateScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Create", style = MaterialTheme.typography.headlineMedium)
        SectionCard {
            SectionLabel("Coming in Phase 2")
            Feature("Auto highlight reel",
                "Detect braking, impacts (G-sensor) and fastest segments from the embedded GPS/G-sensor stream and stitch them into a shareable clip.")
            Feature("Picture-in-picture dual export", "Front + rear channel layouts with one tap.")
            Feature("Telemetry overlay", "Burn speed, heading and route into the exported video.")
            Feature("Vertical cut for social", "Re-frame 16:9 rides into 9:16 shorts automatically.")
        }
        SectionCard {
            SectionLabel("Available now")
            Feature("Offline source clips",
                "Everything in Album › On phone is already on-device — no upload, no cloud fee. Trim and share it with your system editor while Phase 2 lands.")
        }
    }
}

@Composable
private fun Feature(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
}
