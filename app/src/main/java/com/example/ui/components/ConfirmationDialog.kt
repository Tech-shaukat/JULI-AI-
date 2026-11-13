package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.intents.MobileActionDispatcher
import com.example.intents.PendingAction
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkCosmos
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.StateError
import com.example.ui.theme.StateSuccess
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ConfirmationDialog(
    pendingAction: PendingAction,
    onConfirm: (PendingAction) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var recipientName by remember(pendingAction) {
        mutableStateOf(
            when (pendingAction) {
                is PendingAction.MakeCall -> pendingAction.recipientName
                is PendingAction.SendSms -> pendingAction.recipientName
                is PendingAction.SendWhatsApp -> pendingAction.recipientName
                else -> ""
            }
        )
    }
    var phoneNumber by remember(pendingAction) {
        mutableStateOf(
            when (pendingAction) {
                is PendingAction.MakeCall -> pendingAction.phoneNumber
                is PendingAction.SendSms -> pendingAction.phoneNumber
                is PendingAction.SendWhatsApp -> pendingAction.phoneNumber
                else -> ""
            }
        )
    }
    var messageText by remember(pendingAction) {
        mutableStateOf(
            when (pendingAction) {
                is PendingAction.SendSms -> pendingAction.message
                is PendingAction.SendWhatsApp -> pendingAction.message
                else -> ""
            }
        )
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@rememberLauncherForActivityResult
            try {
                context.contentResolver.query(
                    contactUri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (nameIdx >= 0) {
                            val pickedName = cursor.getString(nameIdx)?.trim().orEmpty()
                            if (pickedName.isNotEmpty()) recipientName = pickedName
                        }
                        if (numIdx >= 0) {
                            val pickedNum = cursor.getString(numIdx)?.trim().orEmpty()
                            val clean = MobileActionDispatcher.sanitizePhoneNumber(pickedNum)
                            if (clean.isNotEmpty()) phoneNumber = clean
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore query error cleanly
            }
        }
    }

    val whatsAppGreen = Color(0xFF25D366)
    val (title, icon, accentColor, actionText) = when (pendingAction) {
        is PendingAction.DeleteMemory -> Quad("Delete Memory", Icons.Default.DeleteForever, StateError, "Delete")
        is PendingAction.ClearAllData -> Quad("Clear All Stored Data", Icons.Default.Warning, StateError, "Clear Everything")
        is PendingAction.MakeCall -> Quad("Confirm Phone Call", Icons.Default.Call, StateSuccess, "Open Dialer")
        is PendingAction.SendSms -> Quad("Confirm SMS", Icons.Default.Sms, CyberCyan, "Open SMS")
        is PendingAction.SendWhatsApp -> Quad("Confirm WhatsApp", Icons.AutoMirrored.Filled.Chat, whatsAppGreen, "Open WhatsApp")
    }

    val isCommunicationAction = pendingAction is PendingAction.MakeCall ||
        pendingAction is PendingAction.SendSms ||
        pendingAction is PendingAction.SendWhatsApp

    val canConfirm = when (pendingAction) {
        is PendingAction.MakeCall -> phoneNumber.isNotBlank()
        is PendingAction.SendSms -> phoneNumber.isNotBlank() && messageText.isNotBlank()
        is PendingAction.SendWhatsApp -> messageText.isNotBlank()
        else -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCosmos,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (pendingAction) {
                    is PendingAction.DeleteMemory -> {
                        Text(
                            text = "Are you sure you want to delete this memory? Key: ${pendingAction.key}",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    is PendingAction.ClearAllData -> {
                        Text(
                            text = "This will erase all conversation history, local notes, tasks, and stored user memory. This action cannot be undone.",
                            color = StateError,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }

                    is PendingAction.MakeCall,
                    is PendingAction.SendSms,
                    is PendingAction.SendWhatsApp -> {
                        if (recipientName.isNotBlank()) {
                            Text(
                                text = "Recipient: $recipientName",
                                color = CyberCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = {
                                Text(
                                    if (pendingAction is PendingAction.SendWhatsApp) {
                                        "Phone Number (optional for WhatsApp picker)"
                                    } else {
                                        "Phone Number"
                                    },
                                    fontSize = 12.sp
                                )
                            },
                            placeholder = { Text("+91 9876543210", color = TextDim) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = SurfaceBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard,
                                focusedLabelColor = accentColor,
                                unfocusedLabelColor = TextSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_phone_input")
                        )

                        OutlinedButton(
                            onClick = {
                                val pickIntent = Intent(
                                    Intent.ACTION_PICK,
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                )
                                try {
                                    contactPickerLauncher.launch(pickIntent)
                                } catch (_: Exception) {
                                    // Contacts app unavailable
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_contact_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contacts,
                                contentDescription = "Pick Contact",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pick from Contacts", color = CyberCyan, fontSize = 12.sp)
                        }

                        if (pendingAction is PendingAction.SendSms || pendingAction is PendingAction.SendWhatsApp) {
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                label = { Text("Message", fontSize = 12.sp) },
                                placeholder = { Text("अपना संदेश लिखें...", color = TextDim) },
                                minLines = 2,
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = SurfaceBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedContainerColor = SurfaceCard,
                                    unfocusedContainerColor = SurfaceCard,
                                    focusedLabelColor = accentColor,
                                    unfocusedLabelColor = TextSecondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm_message_input")
                            )
                        }

                        Text(
                            text = when (pendingAction) {
                                is PendingAction.MakeCall -> "सुरक्षा पुष्टि: Confirm दबाने पर आपका Phone Dialer नंबर के साथ खुलेगा।"
                                is PendingAction.SendSms -> "सुरक्षा पुष्टि: Confirm दबाने पर SMS ऐप संदेश के साथ खुलेगा।"
                                is PendingAction.SendWhatsApp -> if (phoneNumber.isBlank()) {
                                    "नंबर खाली रखने पर WhatsApp खुलने के बाद आप कॉन्टैक्ट चुन सकते हैं।"
                                } else {
                                    "सुरक्षा पुष्टि: Confirm दबाने पर WhatsApp चैट तैयार संदेश के साथ खुलेगी।"
                                }
                                else -> ""
                            },
                            color = TextDim,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = when (pendingAction) {
                        is PendingAction.MakeCall -> pendingAction.copy(
                            recipientName = recipientName.trim(),
                            phoneNumber = MobileActionDispatcher.sanitizePhoneNumber(phoneNumber)
                        )
                        is PendingAction.SendSms -> pendingAction.copy(
                            recipientName = recipientName.trim(),
                            phoneNumber = MobileActionDispatcher.sanitizePhoneNumber(phoneNumber),
                            message = messageText.trim()
                        )
                        is PendingAction.SendWhatsApp -> pendingAction.copy(
                            recipientName = recipientName.trim(),
                            phoneNumber = MobileActionDispatcher.sanitizePhoneNumber(phoneNumber),
                            message = messageText.trim()
                        )
                        else -> pendingAction
                    }
                    onConfirm(updated)
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    disabledContainerColor = SurfaceBorder
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_action_button")
            ) {
                Text(
                    text = actionText,
                    color = if (isCommunicationAction && accentColor != StateError) DeepSpace else Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("dismiss_action_button")
            ) {
                Text(text = "Cancel", color = TextSecondary)
            }
        }
    )
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
