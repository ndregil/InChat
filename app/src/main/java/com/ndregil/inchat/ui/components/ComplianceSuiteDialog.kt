package com.ndregil.inchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.ui.i18n.LocalAppStrings
import com.ndregil.inchat.ui.theme.OnlineGreen

data class ProtocolComplianceItem(
    val standard: String,
    val title: String,
    val description: String,
    val status: String,
    val reference: String
)

@Composable
fun ComplianceSuiteDialog(
    serviceName: String = "search.jabber.network",
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val complianceItems = listOf(
        ProtocolComplianceItem(
            standard = "RFC 6120",
            title = strings.compRfc6120Title,
            description = strings.compRfc6120Desc,
            status = strings.compRfc6120Status,
            reference = "Core RFC"
        ),
        ProtocolComplianceItem(
            standard = "RFC 7590",
            title = strings.compRfc7590Title,
            description = strings.compRfc7590Desc,
            status = strings.compRfc7590Status,
            reference = "Security RFC"
        ),
        ProtocolComplianceItem(
            standard = "XEP-0030",
            title = strings.compXep0030Title,
            description = strings.compXep0030Desc,
            status = strings.compXep0030Status,
            reference = "search.jabber.network Ref"
        ),
        ProtocolComplianceItem(
            standard = "XEP-0045",
            title = strings.compXep0045Title,
            description = strings.compXep0045Desc,
            status = strings.compXep0045Status,
            reference = "MUC Spec"
        ),
        ProtocolComplianceItem(
            standard = "XEP-0048 & XEP-0402",
            title = strings.compXep0048Title,
            description = strings.compXep0048Desc,
            status = strings.compXep0048Status,
            reference = "PEP Storage"
        ),
        ProtocolComplianceItem(
            standard = "XEP-0055 & XEP-0004",
            title = strings.compXep0055Title,
            description = strings.compXep0055Desc,
            status = strings.compXep0055Status,
            reference = "Directory Search"
        ),
        ProtocolComplianceItem(
            standard = "XEP-0166 / XEP-0167",
            title = strings.compXep0166Title,
            description = strings.compXep0166Desc,
            status = strings.compXep0166Status,
            reference = "Jingle A/V"
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(strings.complianceSuiteTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("$serviceName ${strings.complianceReferenceSuffix}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = strings.close, modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${strings.complianceIntroPrefix} $serviceName ${strings.complianceIntroSuffix}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(complianceItems) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = item.standard,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(OnlineGreen)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = item.status,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OnlineGreen
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text(strings.close)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
