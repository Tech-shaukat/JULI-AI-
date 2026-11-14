package com.example

import com.example.brain.BrainAction
import com.example.brain.JuliBrain
import com.example.intents.InstalledAppInfo
import com.example.intents.InstalledAppLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppLauncherTest {

    private val sampleApps = listOf(
        InstalledAppInfo("WhatsApp", "com.whatsapp", "com.whatsapp.Main", "whatsapp"),
        InstalledAppInfo("Instagram", "com.instagram.android", "com.instagram.main", "instagram"),
        InstalledAppInfo("Paytm", "net.one97.paytm", "net.one97.paytm.Main", "paytm"),
        InstalledAppInfo("PhonePe", "com.phonepe.app", "com.phonepe.Main", "phonepe"),
        InstalledAppInfo("Spotify", "com.spotify.music", "com.spotify.Main", "spotify"),
        InstalledAppInfo("Google Photos", "com.google.android.apps.photos", "com.photos.Main", "google photos"),
        InstalledAppInfo("Calculator", "com.google.android.calculator", "com.calc.Main", "calculator")
    )

    @Test
    fun extractAppTarget_stripsHindiAndEnglishFillers() {
        assertEquals("Instagram", InstalledAppLauncher.extractAppTarget("Instagram kholo"))
        assertEquals("Paytm", InstalledAppLauncher.extractAppTarget("Paytm चालू करो"))
        assertEquals("WhatsApp", InstalledAppLauncher.extractAppTarget("Open WhatsApp please"))
        assertEquals("इंस्टाग्राम", InstalledAppLauncher.extractAppTarget("इंस्टाग्राम खोलो"))
    }

    @Test
    fun findBestMatch_matchesEnglishHindiAliasesAndTypos() {
        assertEquals(
            "com.instagram.android",
            InstalledAppLauncher.findBestMatch("Instagram", sampleApps)?.packageName
        )
        assertEquals(
            "com.instagram.android",
            InstalledAppLauncher.findBestMatch("इंस्टाग्राम", sampleApps)?.packageName
        )
        assertEquals(
            "net.one97.paytm",
            InstalledAppLauncher.findBestMatch("पेटीएम", sampleApps)?.packageName
        )
        assertEquals(
            "com.phonepe.app",
            InstalledAppLauncher.findBestMatch("फोनपे", sampleApps)?.packageName
        )
        assertEquals(
            "com.instagram.android",
            InstalledAppLauncher.findBestMatch("instgram", sampleApps)?.packageName
        )
    }

    @Test
    fun juliBrain_parsesOpenAppWithoutHijackingSettings() {
        val brain = JuliBrain()
        val insta = brain.processQuery("Instagram kholo", "hi")
        assertTrue(insta is BrainAction.OpenApp)
        assertEquals("Instagram", (insta as BrainAction.OpenApp).appName)

        val paytm = brain.processQuery("Paytm चालू करो", "hi")
        assertTrue(paytm is BrainAction.OpenApp)
        assertEquals("Paytm", (paytm as BrainAction.OpenApp).appName)

        val settings = brain.processQuery("wifi settings kholo", "hi")
        assertTrue(settings is BrainAction.OpenSettings)
    }
}
