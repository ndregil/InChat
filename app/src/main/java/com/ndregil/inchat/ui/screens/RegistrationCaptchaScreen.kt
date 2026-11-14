package com.ndregil.inchat.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ndregil.inchat.data.network.CaptchaChallengeData
import com.ndregil.inchat.data.network.RegistrationFieldType

@Composable
fun RegistrationCaptchaScreen(
    challenge: CaptchaChallengeData,
    onSolveCaptcha: (captchaAnswer: String, extraFields: Map<String, String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onCancel() }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val hasCaptcha = remember(challenge) {
        challenge.captchaImageBytes != null ||
            !challenge.captchaImageUrl.isNullOrBlank() ||
            challenge.challengeVar.isNotBlank()
    }

    var captchaInputText by remember(challenge) { mutableStateOf("") }
    val fieldValues = remember(challenge) {
        mutableStateMapOf<String, String>().apply {
            challenge.formFields.forEach { field ->
                val initial = when {
                    field.type == RegistrationFieldType.BOOLEAN ->
                        if (field.defaultValue == "1" || field.defaultValue.equals("true", true)) "1" else "0"
                    field.type == RegistrationFieldType.LIST_SINGLE && field.defaultValue.isBlank() ->
                        field.options.firstOrNull()?.second.orEmpty()
                    else -> field.defaultValue
                }
                this[field.variable] = initial
            }
        }
    }

    val canSubmit = remember(captchaInputText, fieldValues.toMap(), challenge) {
        val captchaOk = !hasCaptcha || captchaInputText.isNotBlank()
        val fieldsOk = challenge.formFields.all { field ->
            if (!field.isRequired) true
            else {
                val v = fieldValues[field.variable].orEmpty()
                if (field.type == RegistrationFieldType.BOOLEAN) v == "1" || v.equals("true", true)
                else v.isNotBlank()
            }
        }
        captchaOk && fieldsOk
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (hasCaptcha) "Verifikasi Pendaftaran" else "Lengkapi Form Pendaftaran",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Server ${challenge.domain} meminta data tambahan untuk akun ${challenge.username}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!challenge.instructions.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            ) {
                Text(
                    text = challenge.instructions,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (hasCaptcha) {
            if (challenge.captchaImageBytes != null || !challenge.captchaImageUrl.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
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
                                contentDescription = "Gambar CAPTCHA",
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else if (!challenge.captchaImageUrl.isNullOrBlank()) {
                            val req = remember(challenge.captchaImageUrl) {
                                ImageRequest.Builder(context)
                                    .data(challenge.captchaImageUrl)
                                    .crossfade(true)
                                    .build()
                            }
                            AsyncImage(
                                model = req,
                                contentDescription = "Gambar CAPTCHA",
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = captchaInputText,
                onValueChange = { captchaInputText = it },
                label = { Text("Jawaban CAPTCHA *") },
                placeholder = { Text("Ketik kode verifikasi") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_captcha_answer"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Dynamic fields from XEP-0077 DataForm (e.g. email, token, invite, tos checkbox, list-single)
        challenge.formFields.forEach { field ->
            val currentVal = fieldValues[field.variable] ?: ""
            val labelText = if (field.isRequired) "${field.label} *" else field.label

            when (field.type) {
                RegistrationFieldType.BOOLEAN -> {
                    val checked = currentVal == "1" || currentVal.equals("true", ignoreCase = true)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    fieldValues[field.variable] = if (isChecked) "1" else "0"
                                },
                                modifier = Modifier.testTag("reg_field_${field.variable}")
                            )
                        }
                    }
                }
                RegistrationFieldType.LIST_SINGLE -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        if (field.options.isNotEmpty()) {
                            field.options.forEach { (optLabel, optValue) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = currentVal == optValue,
                                        onClick = { fieldValues[field.variable] = optValue }
                                    )
                                    Text(text = optLabel, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = currentVal,
                                onValueChange = { fieldValues[field.variable] = it },
                                label = { Text(labelText) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_field_${field.variable}"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
                RegistrationFieldType.PASSWORD -> {
                    OutlinedTextField(
                        value = currentVal,
                        onValueChange = { fieldValues[field.variable] = it },
                        label = { Text(labelText) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reg_field_${field.variable}"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                RegistrationFieldType.TEXT -> {
                    OutlinedTextField(
                        value = currentVal,
                        onValueChange = { fieldValues[field.variable] = it },
                        label = { Text(labelText) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reg_field_${field.variable}"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        if (!challenge.oobUrl.isNullOrBlank()) {
            OutlinedButton(
                onClick = {
                    try {
                        uriHandler.openUri(challenge.oobUrl)
                    } catch (_: Exception) {}
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_open_oob_url"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Buka Halaman Pendaftaran Web")
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("btn_cancel_captcha"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Batal")
            }
            Button(
                onClick = { onSolveCaptcha(captchaInputText, fieldValues.toMap()) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("btn_submit_captcha"),
                enabled = canSubmit,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Kirim", fontWeight = FontWeight.Bold)
            }
        }
    }
}
