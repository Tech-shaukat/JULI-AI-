package com.example.intents

sealed class PendingAction {
    data class DeleteMemory(
        val key: String
    ) : PendingAction()

    object ClearAllData : PendingAction()

    data class MakeCall(
        val recipientName: String = "",
        val phoneNumber: String = ""
    ) : PendingAction()

    data class SendSms(
        val recipientName: String = "",
        val phoneNumber: String = "",
        val message: String = ""
    ) : PendingAction()

    data class SendWhatsApp(
        val recipientName: String = "",
        val phoneNumber: String = "",
        val message: String = ""
    ) : PendingAction()
}
