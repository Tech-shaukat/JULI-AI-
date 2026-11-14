package com.example.brain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JuliBrain {

    private val hindiJokes = listOf(
        "अध्यापक: संजू, बताओ एक साल में कितनी रातें होती हैं?\nसंजू: सर, 10 रातें!\nअध्यापक: वो कैसे?\nसंजू: नवरात्र और शिवरात्रि!",
        "पत्नी: आपको मेरी सुंदरता ज्यादा अच्छी लगती है या अक्ल?\nपति: मुझे तो तुम्हारी यह मजाक करने की आदत बहुत अच्छी लगती है!",
        "पिंटू: डॉक्टर साहब, जब मैं चाय पीता हूँ तो मेरी दाहिनी आँख में दर्द होता है।\nडॉक्टर: चाय पीने से पहले चम्मच निकाल लिया करो!",
        "पप्पू: यार, आज सुबह मेरी बिल्ली ने पूरा दूध पी लिया।\nगप्पू: फिर तूने क्या किया?\nपप्पू: मैंने बिल्ली को मिक्सी में घुमा कर मक्खन निकाल लिया!",
        "टीचर: बताओ न्यूटन का चौथा नियम क्या है?\nछात्र: जब परीक्षा का पेपर कठिन हो, तो भगवान ही एकमात्र सहारा है!"
    )

    private val englishJokes = listOf(
        "Why don't scientists trust atoms? Because they make up everything!",
        "Why did the computer go to the doctor? Because it had a virus!",
        "What do you call fake spaghetti? An impasta!",
        "Why was the math book sad? Because it had too many problems.",
        "How do you organize a space party? You planet!"
    )

    private val bhojpuriJokes = listOf(
        "एक बेर एक पंडित जी से लइका पूछलस: 'महाराज, बियाह के बाद जीवन कइसन होला?'\nपंडित जी कहलें: 'जइसे समंदर में नाव, बिना पतवार के!'",
        "घपचू: 'का भाई, आज अतना खुश काहे बाड़ा?'\nलपचू: 'आज मेहरारू कहली कि आज खाना मत बनाव, बाहर से मंगा लीं!'"
    )

    private val hindiQuotes = listOf(
        "कोशिश करने वालों की कभी हार नहीं होती, लहरों से डर कर नौका पार नहीं होती।",
        "सफलता की सबसे खास बात यह है कि वह मेहनत करने वालों पर फिदा हो जाती है।",
        "उड़ान तो भरनी है, चाहे कई बार गिरना पड़े, सपनों को पूरा करना है, चाहे खुद से भी लड़ना पड़े।",
        "मुश्किलें केवल बेहतरीन लोगों के हिस्से में आती हैं, क्योंकि वही लोग उसे बेहतरीन तरीके से अंजाम देने की ताकत रखते हैं।"
    )

    fun processQuery(rawInput: String, languagePreference: String = "hi"): BrainAction {
        val query = rawInput.trim()
        val lower = query.lowercase(Locale.ROOT)
        val isBhojpuri = isBhojpuriText(lower) || languagePreference == "bho"
        val isHindi = isHindiText(lower) || languagePreference == "hi" || isBhojpuri

        // 1. Stop Speaking command
        if (isStopCommand(lower)) {
            return BrainAction.StopSpeaking
        }

        // 2. Bhojpuri Specific Greetings & Phrases
        if (isBhojpuri) {
            val bhojpuriResponse = handleBhojpuriSpecific(lower)
            if (bhojpuriResponse != null) {
                return bhojpuriResponse
            }
        }

        // 3. YouTube commands
        val ytMatch = matchYouTubeCommand(query, lower, isHindi, isBhojpuri)
        if (ytMatch != null) return ytMatch

        // 4. Chrome & Web Search commands
        val chromeMatch = matchChromeCommand(query, lower, isHindi)
        if (chromeMatch != null) return chromeMatch

        // 5. Camera commands
        if (isCameraCommand(lower)) {
            val msg = if (isBhojpuri) "कैमरा खोलल जा रहल बा।" else if (isHindi) "कैमरा खोला जा रहा है।" else "Opening camera for you."
            return BrainAction.OpenCamera(speakResponse = msg, isHindi = isHindi)
        }

        // 6. Settings commands (WiFi, Bluetooth, etc.)
        val settingsMatch = matchSettingsCommand(lower, isHindi)
        if (settingsMatch != null) return settingsMatch

        // 6.2. Google Maps Navigation commands
        val mapMatch = matchMapCommand(query, lower, isHindi)
        if (mapMatch != null) return mapMatch

        // 6.3. Read Notifications / Messages commands
        val notifMatch = matchNotificationReadCommand(lower, isHindi)
        if (notifMatch != null) return notifMatch

        // 6.5. Communication commands (Phone Call, SMS, WhatsApp)
        val commMatch = matchCommunicationCommand(query, lower, isHindi, isBhojpuri)
        if (commMatch != null) return commMatch

        // 7. Productivity: Tasks / To-Do commands
        val taskMatch = matchTaskCommand(query, lower, isHindi)
        if (taskMatch != null) return taskMatch

        // 8. Productivity: Notes commands
        val noteMatch = matchNoteCommand(query, lower, isHindi, isBhojpuri)
        if (noteMatch != null) return noteMatch

        // 9. Show sections commands (Notes, Tasks, Settings, Actions)
        val sectionMatch = matchSectionCommand(lower, isHindi)
        if (sectionMatch != null) return sectionMatch

        // 9.5. Open any installed app by name (e.g., "Instagram kholo", "Open Paytm", "WhatsApp चालू करो")
        val appMatch = matchOpenAppCommand(query, lower, isHindi, isBhojpuri)
        if (appMatch != null) return appMatch

        // 10. User Memory: "Remember that...", "Mera naam...", "Yaad rakho..."
        val memoryMatch = matchMemoryCommand(query, lower, isHindi)
        if (memoryMatch != null) return memoryMatch

        // 11. Time & Date
        if (isTimeQuery(lower)) {
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val currentTime = timeFormat.format(Date())
            val response = if (isBhojpuri) {
                "अभी समय $currentTime भइल बा।"
            } else if (isHindi) {
                "अभी समय $currentTime हुआ है।"
            } else {
                "The current time is $currentTime."
            }
            return BrainAction.Speak(text = response, isHindi = isHindi, actionBadge = "Time")
        }

        if (isDateQuery(lower)) {
            val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", if (isHindi) Locale("hi", "IN") else Locale.ENGLISH)
            val currentDate = dateFormat.format(Date())
            val response = if (isBhojpuri) {
                "आज के तारीख $currentDate बा।"
            } else if (isHindi) {
                "आज की तारीख $currentDate है।"
            } else {
                "Today is $currentDate."
            }
            return BrainAction.Speak(text = response, isHindi = isHindi, actionBadge = "Date")
        }

        // 12. Identity & Introduction
        if (isIdentityQuery(lower)) {
            val response = if (isBhojpuri) {
                "प्रणाम! हम जूलिया (JULI AI) हईं, रउआ खातिर आधुनिक AI पर्सनल वॉइस असिस्टेंट। रउआ 'Hey JULI' कह के भी बोला सकेनी।"
            } else if (isHindi) {
                "नमस्ते! मैं जूलिया (JULI AI) हूँ, आपकी आधुनिक वॉइस एवं पर्सनल असिस्टेंट। आप मुझे 'Hey JULI' बोलकर भी जगा सकते हैं।"
            } else {
                "Hello! I am JULI AI, your personal voice assistant. You can wake me up hands-free by saying 'Hey JULI'."
            }
            return BrainAction.Speak(text = response, isHindi = isHindi, actionBadge = "Identity")
        }

        // 13. Greetings
        if (isGreeting(lower)) {
            val response = if (isBhojpuri) {
                listOf(
                    "प्रणाम! का हाल-चाल बा? बताईं का सेवा करीं?",
                    "नमस्कार! जूलिया रउआ सेवा में हाजिर बिया।"
                ).random()
            } else if (isHindi) {
                listOf(
                    "नमस्ते! आप कैसे हैं? आज मैं आपकी क्या मदद कर सकती हूँ?",
                    "नमस्कार! जूलिया आपकी सेवा में उपस्थित है। कहिए क्या हुक्म है?",
                    "हेलो! मैं बहुत अच्छी हूँ। आशा है आपका दिन बहुत शुभ हो।"
                ).random()
            } else {
                listOf(
                    "Hello! I'm ready to assist you. What can I do for you today?",
                    "Greetings! JULI AI is here to help.",
                    "Hi there! How can I make your day easier?"
                ).random()
            }
            return BrainAction.Speak(text = response, isHindi = isHindi, actionBadge = "Greeting")
        }

        // 14. Jokes & Entertainment
        if (isJokeQuery(lower)) {
            val joke = if (isBhojpuri) bhojpuriJokes.random() else if (isHindi) hindiJokes.random() else englishJokes.random()
            return BrainAction.Speak(text = joke, isHindi = isHindi, actionBadge = "Joke")
        }

        // 15. Motivation & Quotes
        if (isShayariOrMotivation(lower)) {
            val quote = hindiQuotes.random()
            return BrainAction.Speak(text = quote, isHindi = isHindi, actionBadge = "Inspiration")
        }

        // 16. Simple Math
        val mathResult = tryEvaluateMath(lower)
        if (mathResult != null) {
            val response = if (isBhojpuri) {
                "एकर उत्तर $mathResult बा।"
            } else if (isHindi) {
                "इसका उत्तर $mathResult है।"
            } else {
                "The answer is $mathResult."
            }
            return BrainAction.Speak(text = response, isHindi = isHindi, actionBadge = "Calculator")
        }

        // 17. General smart response
        val defaultResponse = generateSmartResponse(query, isHindi, isBhojpuri)
        return BrainAction.Speak(text = defaultResponse, isHindi = isHindi)
    }

    private fun handleBhojpuriSpecific(lower: String): BrainAction? {
        if (lower.contains("kaisan ba") || lower.contains("ka haal ba") || lower.contains("kaise bada")) {
            return BrainAction.Speak(
                text = "हम एकदम नीक बानी! रउआ बताईं, कइसन चल रहल बा सब? का सेवा करीं?",
                isHindi = true,
                actionBadge = "Bhojpuri"
            )
        }
        if (lower.contains("kaha baadu") || lower.contains("kaha badi")) {
            return BrainAction.Speak(
                text = "हम रउआ मोबाइल में बानी, हमेशा रउआ साथ!",
                isHindi = true,
                actionBadge = "Bhojpuri"
            )
        }
        return null
    }

    private fun isStopCommand(text: String): Boolean {
        val keywords = listOf("stop", "chup", "shant", "be quiet", "ruk jao", "band karo", "stop speaking", "quiet", "chup raha")
        return keywords.any { text.contains(it) }
    }

    private fun matchYouTubeCommand(raw: String, lower: String, isHindi: Boolean, isBhojpuri: Boolean): BrainAction? {
        val isYt = lower.contains("youtube") || Regex("\\byt\\b").containsMatchIn(lower) ||
                lower.contains("gana chalao") || lower.contains("video chalao") ||
                lower.contains("gana bajao") || lower.contains("play song") ||
                lower.contains("gana bajawa")

        if (!isYt) return null

        var searchQuery: String? = null
        val patterns = listOf(
            "search youtube for ", "search on youtube ", "search for ", "youtube par search karo ",
            "youtube par ", "play on youtube ", "play ", "gana chalao ", "video chalao ", "kholo ", "bajawa "
        )

        for (pattern in patterns) {
            val idx = lower.indexOf(pattern)
            if (idx != -1) {
                val candidate = raw.substring(idx + pattern.length).trim()
                if (candidate.isNotEmpty() && !candidate.equals("youtube", ignoreCase = true)) {
                    searchQuery = candidate.replace("chalao", "", ignoreCase = true)
                        .replace("dikhao", "", ignoreCase = true)
                        .replace("search karo", "", ignoreCase = true)
                        .replace("bajawa", "", ignoreCase = true)
                        .trim()
                    break
                }
            }
        }

        val speak = if (searchQuery.isNullOrEmpty()) {
            if (isBhojpuri) "यूट्यूब खोलल जा रहल बाटे।" else if (isHindi) "यूट्यूब खोला जा रहा है।" else "Opening YouTube for you."
        } else {
            if (isBhojpuri) "यूट्यूब पर $searchQuery खोजल जा रहल बा।" else if (isHindi) "यूट्यूब पर $searchQuery खोजा जा रहा है।" else "Searching $searchQuery on YouTube."
        }

        return BrainAction.OpenYouTube(query = searchQuery, speakResponse = speak, isHindi = isHindi)
    }

    private fun matchChromeCommand(raw: String, lower: String, isHindi: Boolean): BrainAction? {
        val isChrome = lower.contains("chrome") || lower.contains("browser") ||
                lower.contains("google search") || lower.contains("search google") ||
                lower.contains("google par khojo") || lower.contains("internet par")

        if (!isChrome) return null

        val cleanQuery = raw.replace("open chrome", "", ignoreCase = true)
            .replace("chrome kholo", "", ignoreCase = true)
            .replace("google search", "", ignoreCase = true)
            .replace("search on google for", "", ignoreCase = true)
            .replace("search on chrome for", "", ignoreCase = true)
            .replace("google par search karo", "", ignoreCase = true)
            .trim()

        val queryOrUrl = if (cleanQuery.isBlank() || cleanQuery.equals("chrome", ignoreCase = true)) null else cleanQuery
        val speak = if (queryOrUrl.isNullOrBlank()) {
            if (isHindi) "क्रोम ब्राउज़र खोला जा रहा है।" else "Opening Chrome browser."
        } else {
            if (isHindi) "गूगल पर \"$queryOrUrl\" खोजा जा रहा है।" else "Searching \"$queryOrUrl\" on Google."
        }

        return BrainAction.OpenChrome(queryOrUrl = queryOrUrl, speakResponse = speak, isHindi = isHindi)
    }

    private fun isCameraCommand(text: String): Boolean {
        return text.contains("camera") || text.contains("photo khincho") || text.contains("tasveer") || text.contains("camera kholo")
    }

    private fun matchSettingsCommand(lower: String, isHindi: Boolean): BrainAction? {
        if (!lower.contains("settings") && !lower.contains("setting")) return null

        val type = when {
            lower.contains("wifi") || lower.contains("wi-fi") -> "wifi"
            lower.contains("bluetooth") -> "bluetooth"
            lower.contains("display") || lower.contains("brightness") -> "display"
            lower.contains("sound") || lower.contains("volume") -> "sound"
            else -> "general"
        }

        val speak = if (isHindi) "$type सेटिंग्स खोली जा रही है।" else "Opening $type settings."
        return BrainAction.OpenSettings(settingType = type, speakResponse = speak, isHindi = isHindi)
    }

    private fun matchMapCommand(raw: String, lower: String, isHindi: Boolean): BrainAction? {
        val isMap = lower.contains("map") || lower.contains("navigate") || lower.contains("navigation") ||
            lower.contains("rasta") || lower.contains("direction") || lower.contains("लोकेशन") ||
            lower.contains("मैप") || lower.contains("रास्ता")
        if (!isMap) return null

        val dest = raw.replace(Regex("(?i)(google maps?|map|set karo|set kar do|laga do|kholo|navigate to|directions? to|rasta dikhao|ka rasta|ka map|direction to|please)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (dest.isBlank()) return null

        val speak = if (isHindi) "$dest के लिए Google Maps नेविगेशन सेट किया जा रहा है।" else "Setting Google Maps navigation to $dest."
        return BrainAction.NavigateMap(
            destination = dest,
            travelMode = if (lower.contains("walk") || lower.contains("paidal")) "walking" else "driving",
            speakResponse = speak,
            isHindi = isHindi
        )
    }

    private fun matchNotificationReadCommand(lower: String, isHindi: Boolean): BrainAction? {
        val isNotif = lower.contains("notification") || lower.contains("नोटिफिकेशन") ||
            lower.contains("message padh") || lower.contains("message read") ||
            lower.contains("read message") || lower.contains("kiska message") ||
            lower.contains("मैसेज पढ़") || lower.contains("संदेश पढ़")
        if (!isNotif) return null

        val filter = when {
            lower.contains("whatsapp") || lower.contains("व्हाट्सएप") -> "whatsapp"
            lower.contains("sms") -> "sms"
            lower.contains("gmail") || lower.contains("mail") -> "gmail"
            else -> null
        }
        return BrainAction.ReadNotifications(appFilter = filter, isHindi = isHindi)
    }

    private fun matchCommunicationCommand(
        raw: String,
        lower: String,
        isHindi: Boolean,
        isBhojpuri: Boolean
    ): BrainAction? {
        val phoneMatch = Regex("(\\+?\\d[\\d\\s-]{7,14}\\d)").find(raw)
        val extractedPhone = phoneMatch?.value?.filter { it.isDigit() || it == '+' }.orEmpty()

        val isWhatsApp = lower.contains("whatsapp") || lower.contains("watsapp") || lower.contains("व्हाट्सएप")
        val isSms = (lower.contains("sms") || lower.contains("text message") || lower.contains("message bhejo") ||
            lower.contains("संदेश") || lower.contains("मैसेज")) && !lower.contains("padh") && !lower.contains("read")
        val isCall = lower.contains("call ") || lower.startsWith("call") || lower.contains("phone lagao") ||
            lower.contains("call karo") || lower.contains("dial ") || lower.contains("कॉल") || lower.contains("फोन लगाओ")

        if (isWhatsApp) {
            val msg = extractMessageBody(raw, listOf("whatsapp", "message", "bhejo", "send", "karo", "कि", "ki"))
            val speak = if (isBhojpuri) {
                "WhatsApp संदेश तैयार बा। कृपया स्क्रीन पर Confirm दबाईं।"
            } else if (isHindi) {
                "WhatsApp संदेश तैयार है। कृपया स्क्रीन पर Confirm दबाएँ।"
            } else {
                "WhatsApp message is ready. Please tap Confirm on screen."
            }
            return BrainAction.SendWhatsApp(
                recipientName = "",
                phoneNumber = extractedPhone,
                message = msg,
                speakResponse = speak,
                isHindi = isHindi
            )
        }

        if (isSms) {
            val msg = extractMessageBody(raw, listOf("sms", "text message", "message", "bhejo", "send", "karo", "कि", "ki"))
            val speak = if (isHindi) {
                "SMS संदेश तैयार है। कृपया स्क्रीन पर Confirm दबाएँ।"
            } else {
                "SMS is ready. Please tap Confirm on screen."
            }
            return BrainAction.SendSms(
                recipientName = "",
                phoneNumber = extractedPhone,
                message = msg,
                speakResponse = speak,
                isHindi = isHindi
            )
        }

        if (isCall) {
            val extractedName = if (extractedPhone.isBlank()) {
                raw.replace(Regex("(?i)(call|dial|phone|lagao|karo|ko|par|please|जल्दी|कॉल|फोन|लगाओ|करो|को)"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            } else {
                ""
            }
            val speak = if (isBhojpuri) {
                if (extractedName.isNotBlank()) "$extractedName के कॉल लगावल जा रहल बा।" else "कॉल लगावे खातिर स्क्रीन पर Confirm दबाईं।"
            } else if (isHindi) {
                if (extractedName.isNotBlank()) "$extractedName को कॉल लगाया जा रहा है।" else "कॉल करने से पहले कृपया स्क्रीन पर Confirm दबाएँ।"
            } else {
                if (extractedName.isNotBlank()) "Calling $extractedName." else "Please confirm the phone call on screen."
            }
            return BrainAction.CallPhone(
                recipientName = extractedName,
                phoneNumber = extractedPhone,
                speakResponse = speak,
                isHindi = isHindi
            )
        }

        return null
    }

    private fun extractMessageBody(raw: String, stripTokens: List<String>): String {
        val colonIdx = raw.indexOf(':')
        if (colonIdx != -1 && colonIdx < raw.lastIndex) {
            return raw.substring(colonIdx + 1).trim()
        }
        var cleaned = raw
        stripTokens.forEach { token ->
            cleaned = cleaned.replace(Regex("(?i)\\b${Regex.escape(token)}\\b"), " ")
        }
        return cleaned.replace(Regex("\\s+"), " ").trim()
    }

    private fun matchTaskCommand(raw: String, lower: String, isHindi: Boolean): BrainAction? {
        val isTask = lower.contains("task banao") || lower.contains("add task") ||
                lower.contains("to do") || lower.contains("todo") || lower.contains("kam jod")

        if (!isTask) return null

        val clean = raw.replace("add task", "", ignoreCase = true)
            .replace("task banao", "", ignoreCase = true)
            .replace("todo", "", ignoreCase = true)
            .replace(":", "")
            .trim()
            .ifBlank { "New Task" }

        val priority = if (lower.contains("urgent") || lower.contains("zaruri")) "High" else "Normal"
        val speak = if (isHindi) "टास्क जोड़ दिया गया है: $clean" else "Task added: $clean"

        return BrainAction.CreateTask(title = clean, priority = priority, speakResponse = speak, isHindi = isHindi)
    }

    private fun matchNoteCommand(raw: String, lower: String, isHindi: Boolean, isBhojpuri: Boolean): BrainAction? {
        val noteTriggers = listOf(
            "note likho", "note banao", "note save karo", "note karo", "mera note",
            "take a note", "take note", "save note", "create note", "write note", "remember that",
            "note likha", "note bana diha"
        )

        val trigger = noteTriggers.find { lower.contains(it) } ?: return null

        val idx = lower.indexOf(trigger)
        var noteContent = raw.substring(idx + trigger.length).trim()

        noteContent = noteContent.removePrefix(":")
            .removePrefix("that")
            .removePrefix("ki")
            .removePrefix("ye")
            .removePrefix("hai")
            .trim()

        if (noteContent.isEmpty()) {
            val title = if (isBhojpuri) "नया नोट" else if (isHindi) "नया नोट" else "Voice Note"
            val content = if (isHindi) "खाली नोट" else "Blank note"
            val msg = if (isBhojpuri) "नोट तैयार भइल।" else if (isHindi) "नोट तैयार किया गया।" else "Note created."
            return BrainAction.CreateNote(title, content, msg, isHindi)
        }

        val words = noteContent.split("\\s+".toRegex())
        val title = if (words.size > 3) words.take(3).joinToString(" ") else noteContent
        val response = if (isBhojpuri) {
            "रउआ नोट सुरक्षित कर दिहल गइल: $noteContent"
        } else if (isHindi) {
            "आपका नोट सुरक्षित कर लिया गया है: $noteContent"
        } else {
            "Your note has been saved: $noteContent"
        }

        return BrainAction.CreateNote(
            title = title.capitalizeFirst(),
            content = noteContent,
            speakResponse = response,
            isHindi = isHindi
        )
    }

    private fun matchSectionCommand(lower: String, isHindi: Boolean): BrainAction? {
        return when {
            lower.contains("show notes") || lower.contains("mere notes") || lower.contains("note dikhao") -> {
                val msg = if (isHindi) "ये रहे आपके सभी सुरक्षित नोट्स।" else "Here are your saved notes."
                BrainAction.ShowSection("notes", msg, isHindi)
            }
            lower.contains("show tasks") || lower.contains("mere task") || lower.contains("to do list") -> {
                val msg = if (isHindi) "ये रही आपकी टास्क सूची।" else "Here is your to-do list."
                BrainAction.ShowSection("tasks", msg, isHindi)
            }
            lower.contains("open apps") || lower.contains("mobile actions") || lower.contains("actions dikhao") -> {
                val msg = if (isHindi) "ये रहे मोबाइल एक्शन टूल्स।" else "Here are mobile action shortcuts."
                BrainAction.ShowSection("actions", msg, isHindi)
            }
            lower.contains("settings kholo") || lower.contains("open settings") || lower.contains("meri memory") -> {
                val msg = if (isHindi) "सेटिंग्स स्क्रीन खोली जा रही है।" else "Opening settings screen."
                BrainAction.ShowSection("settings", msg, isHindi)
            }
            else -> null
        }
    }

    private fun matchOpenAppCommand(
        raw: String,
        lower: String,
        isHindi: Boolean,
        isBhojpuri: Boolean
    ): BrainAction? {
        val openTriggers = listOf(
            "open ", "launch ", "start ",
            " kholo", " khol do", " khol", " chalao", " chalu karo", " chalu", " open karo",
            " खोलो", " खोल दो", " चालू करो", " चलाओ", " ओपन करो"
        )
        val hasTrigger = openTriggers.any { lower.contains(it) } ||
            lower.endsWith("kholo") || lower.endsWith("खोलो") ||
            lower.endsWith("chalao") || lower.endsWith("चलाओ")
        if (!hasTrigger) return null

        val target = com.example.intents.InstalledAppLauncher.extractAppTarget(raw)
        if (target.isBlank()) return null

        // Ignore internal app sections if already handled or ambiguous
        val ignoredTargets = setOf("note", "notes", "task", "tasks", "memory", "notification", "notifications")
        if (target.lowercase(Locale.ROOT) in ignoredTargets) return null

        val speak = if (isBhojpuri) {
            "$target खोलल जा रहल बा।"
        } else if (isHindi) {
            "$target खोला जा रहा है।"
        } else {
            "Opening $target."
        }
        return BrainAction.OpenApp(appName = target, speakResponse = speak, isHindi = isHindi)
    }

    private fun matchMemoryCommand(raw: String, lower: String, isHindi: Boolean): BrainAction? {
        if (!lower.contains("yaad rakho") && !lower.contains("remember that") && !lower.contains("mera naam")) return null

        val memoryContent = raw.replace("yaad rakho", "", ignoreCase = true)
            .replace("remember that", "", ignoreCase = true)
            .replace("ki", "")
            .trim()

        val key = if (memoryContent.contains("naam", ignoreCase = true) || memoryContent.contains("name", ignoreCase = true)) {
            "user_name"
        } else {
            "preference_${System.currentTimeMillis() % 1000}"
        }

        val speak = if (isHindi) "मैंने याद रख लिया: $memoryContent" else "I have remembered: $memoryContent"
        return BrainAction.SaveMemory(key = key, value = memoryContent, speakResponse = speak, isHindi = isHindi)
    }

    private fun isTimeQuery(text: String): Boolean {
        return text.contains("time") || text.contains("samay") || text.contains("kitne baje") || text.contains("baje hai") || text.contains("kitna bajal")
    }

    private fun isDateQuery(text: String): Boolean {
        return text.contains("date") || text.contains("tarikh") || text.contains("aaj kaun sa din") || text.contains("today's date") || text.contains("aaj ke tarikh")
    }

    private fun isIdentityQuery(text: String): Boolean {
        return text.contains("who are you") || text.contains("what is your name") ||
                text.contains("tum kaun ho") || text.contains("aap kaun ho") ||
                text.contains("naam kya hai") || text.contains("apna parichay") ||
                text.contains("tohar naam") || text.contains("ke haw")
    }

    private fun isGreeting(text: String): Boolean {
        val keywords = listOf("namaste", "namaskar", "hello", "hi", "hey", "kaise ho", "kya haal", "how are you", "good morning", "good evening", "pranam")
        return keywords.any { text.contains(it) }
    }

    private fun isJokeQuery(text: String): Boolean {
        return text.contains("joke") || text.contains("chutkula") || text.contains("hasao") || text.contains("funny") || text.contains("kahaani")
    }

    private fun isShayariOrMotivation(text: String): Boolean {
        return text.contains("shayari") || text.contains("motivation") || text.contains("quote") || text.contains("kavita") || text.contains("prerna")
    }

    private fun tryEvaluateMath(text: String): String? {
        try {
            val clean = text.replace("plus", "+")
                .replace("jod", "+")
                .replace("minus", "-")
                .replace("ghata", "-")
                .replace("times", "*")
                .replace("guna", "*")
                .replace("into", "*")
                .replace("divided by", "/")
                .replace("bhag", "/")

            val regex = "(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)".toRegex()
            val match = regex.find(clean)
            if (match != null) {
                val (num1Str, op, num2Str) = match.destructured
                val n1 = num1Str.toDouble()
                val n2 = num2Str.toDouble()
                val res = when (op) {
                    "+" -> n1 + n2
                    "-" -> n1 - n2
                    "*" -> n1 * n2
                    "/" -> if (n2 != 0.0) n1 / n2 else return "Cannot divide by zero"
                    else -> return null
                }
                return if (res % 1.0 == 0.0) res.toLong().toString() else "%.2f".format(res)
            }
        } catch (_: Exception) {}
        return null
    }

    private fun generateSmartResponse(query: String, isHindi: Boolean, isBhojpuri: Boolean): String {
        return if (isBhojpuri) {
            "हम रउआ बात समझ गइनी: \"$query\"। हम यूट्यूब खोल सकेनी, क्रोम खोल सकेनी, भा नोट लिख सकेनी।"
        } else if (isHindi) {
            "मैंने आपकी बात समझ ली है: \"$query\"। मैं आपके लिए यूट्यूब, क्रोम, कैमरा, नोट्स या टास्क जैसे काम कर सकती हूँ।"
        } else {
            "I heard: \"$query\". I can open apps, create notes, add tasks, or answer queries for you."
        }
    }

    private fun isBhojpuriText(text: String): Boolean {
        val markers = listOf(
            "baate", "baani", "bada", "baadu", "kaisan", "tohar", "hamaar", "kahaani", "larka", "larki",
            "kareen", "karab", "dehal", "batawa", "bajawa", "kholal", "gaeel", "kahal", "raur", "pranam"
        )
        return markers.any { text.contains(it) }
    }

    private fun isHindiText(text: String): Boolean {
        val hasDevanagari = text.any { it.code in 0x0900..0x097F }
        if (hasDevanagari) return true

        val hinglishMarkers = listOf(
            "kya", "kaise", "kholo", "likho", "batao", "samay", "tarikh", "aaj", "mere", "meri",
            "mera", "chalao", "sunao", "namaste", "namaskar", "hai", "hain", "karo", "tum", "aap", "hu"
        )
        return hinglishMarkers.any { text.contains(it) }
    }

    private fun String.capitalizeFirst(): String {
        return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}
