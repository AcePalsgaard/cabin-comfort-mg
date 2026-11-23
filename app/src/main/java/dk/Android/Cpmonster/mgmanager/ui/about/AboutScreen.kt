package dk.Android.Cpmonster.mgmanager.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dk.Android.Cpmonster.mgmanager.AppLinks
import dk.Android.Cpmonster.mgmanager.R

private const val API_REPO_URL = "https://github.com/SAIC-iSmart-API/saic-python-client-ng"
private const val API_ORG_URL = "https://github.com/SAIC-iSmart-API"

@Composable
fun AboutScreen(
    appVersion: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var showLicense by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(88.dp))
            }
            Text("Cabin Comfort for MG", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Version $appVersion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "A simple way to get your MG warm or cool before you leave.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        // The celebration: credit to the people who made this possible.
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Celebration, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Standing on the shoulders of giants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Cabin Comfort for MG would not exist without the SAIC-iSmart-API project. " +
                        "Its contributors reverse-engineered the MG iSmart API and shared it as open source " +
                        "in saic-python-client-ng, which this app's car communication is based on.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Thank you to everyone who contributed!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { uriHandler.openUri(API_REPO_URL) }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View project")
                    }
                    TextButton(onClick = { uriHandler.openUri(API_ORG_URL) }) { Text("SAIC-iSmart-API") }
                }
            }
        }

        // Licenses
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Open-source licenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "saic-python-client-ng — MIT License",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { showLicense = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Show license text")
                }
            }
        }

        // Privacy
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Your login is stored encrypted on this phone and only sent to MG's own servers. " +
                        "The app has no ads, no analytics and no servers of its own.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = { uriHandler.openUri(AppLinks.PRIVACY_POLICY_URL) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text("Read the privacy policy")
                }
            }
        }

        Text(
            "Cabin Comfort for MG is an independent project and is not affiliated with, endorsed by or " +
                "connected to MG Motor or SAIC Motor. MG and iSmart are trademarks of their respective owners.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("MIT License") },
            text = {
                Text(
                    MIT_LICENSE,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("Close") } },
        )
    }
}

private val MIT_LICENSE = """
Copyright (c) the SAIC-iSmart-API contributors

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
""".trimIndent()
