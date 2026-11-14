package com.example.brain

sealed class BrainAction {
    data class Speak(
        val text: String,
        val isHindi: Boolean = true,
        val actionBadge: String? = null
    ) : BrainAction()

    data class OpenYouTube(
        val query: String?,
        val speakResponse: String,
        val isHindi: Boolean = true
    ) : BrainAction()

    data class OpenChrome(
        val queryOrUrl: String?,
        val speakResponse: String,
        val isHindi: Boolean = true
    ) : BrainAction()

    data class OpenCamera(
        val speakResponse: String,
        val isHindi: Boolean = true
    ) : BrainAction()

    data class OpenSettings(
        val settingType: String,
        val speakResponse: String,
        val isHindi: Boolean = true
    ) : BrainAction()

    data class CallPhone(
        val recipientName: String,
        val phoneNumber: String,
        val speakResponse: String,
        val isHindi: Boolean = true
    ) : BrainAction()

    data class SendSms(
        val recipientName: String,
        val phoneNumber: String,
        val message: String,
        val speakResponse: String,
        val isHindi: Boolean = true
    ) : BrainAction()

    data class SendWhatsApp(
        val recipientName: String,
        val phoneNumber: String,
        val message: String,
        val speakResponse: String,
        val isHindi: Boolean = true
    ) : BrainAction()

    data class CreateNote(
        val title: String,
        val content: String,
        val speakResponse: String,
        val isHindi: Boolean = true
    ) : BrainAction()

    data class CreateTask(
        val title: String,
        val priority: String = "Normal",
        val speakResponse: String,
        val isHindi: Boolean = true
    ) : BrainAction()

    data class SaveMemory(
        val key: String,
        val value: String,
        val speakResponse: String,
        val isHindi: Boolean = true
    ) : BrainAction()

    data class ShowSection(
        val section: String,
        val speakResponse: String,
        val isHindi: Boolean = true
    ) : BrainAction()

    data class NavigateMap(
        val destination: String,
        val travelMode: String = "driving",
        val speakResponse: String,
        val isHindi: Boolean = true
    ) : BrainAction()

    data class ReadNotifications(
        val appFilter: String? = null,
        val isHindi: Boolean = true
    ) : BrainAction()

    data class OpenApp(
        val appName: String,
        val speakResponse: String,
        val isHindi: Boolean = true
    ) : BrainAction()

    object StopSpeaking : BrainAction()
}
