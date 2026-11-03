package com.ndregil.inchat.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ndregil.inchat.data.network.CaptchaChallengeData

@Composable
fun RegistrationCaptchaScreen(
    challenge: CaptchaChallengeData,
    onSolveCaptcha: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var captchaInputText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
            Text("Verifikasi CAPTCHA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Text(
            "Server ${challenge.domain} memerlukan verifikasi CAPTCHA (XEP-0158) untuk melanjutkan pendaftaran akun ${challenge.username}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                val bitmap = remember(challenge.captchaImageBytes) {
                    if (challenge.captchaImageBytes != null && challenge.captchaImageBytes.isNotEmpty()) {
                        try {
                            BitmapFactory.decodeByteArray(challenge.captchaImageBytes, 0, challenge.captchaImageBytes.size)
                        } catch (_: Exception) { null }
                    } else null
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "CAPTCHA Image",
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } else if (!challenge.captchaImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = challenge.captchaImageUrl,
                        contentDescription = "CAPTCHA Image",
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Gambar CAPTCHA tidak dapat dimuat.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        OutlinedTextField(
            value = captchaInputText,
            onValueChange = { captchaInputText = it },
            label = { Text("Jawaban CAPTCHA") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Batal")
            }
            Button(
                onClick = { onSolveCaptcha(captchaInputText) },
                modifier = Modifier.weight(1f).height(52.dp),
                enabled = captchaInputText.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Kirim Jawaban", fontWeight = FontWeight.Bold)
            }
        }
    }
}
