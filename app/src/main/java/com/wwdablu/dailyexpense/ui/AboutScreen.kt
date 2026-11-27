package com.wwdablu.dailyexpense.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wwdablu.dailyexpense.BuildConfig
import com.wwdablu.dailyexpense.R
import java.time.Year

/** App name and icon, build details, and the copyright notice at the bottom. */
@Composable
fun AboutScreen(contentPadding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colorResource(R.color.ic_bg)),
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Build details",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                val rows = listOf(
                    "Version" to BuildConfig.VERSION_NAME,
                    "Build number" to BuildConfig.VERSION_CODE.toString(),
                    "Build type" to BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() },
                    "Built on" to BuildConfig.BUILD_TIME,
                    "Commit" to BuildConfig.GIT_COMMIT,
                    "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                )
                rows.forEachIndexed { i, (label, value) ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(16.dp))
                        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
        // Copyright stays at the bottom of the screen.
        Text(
            "© ${Year.now().value} wwdablu. All rights reserved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
    }
}
