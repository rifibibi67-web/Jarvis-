package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.SmsManager
import android.view.Gravity
import android.view.WindowManager
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class JarvisService : Service(), TextToSpeech.OnInitListener {
    private val h = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var sr: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private var awake = 0L
    @Volatile private var speaking = false
    private var alive = true

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("j", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "j").setContentTitle("Jarvis sun raha hai")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
        if (web == null) { overlay(); tts = TextToSpeech(this, this); listen() }
        return START_STICKY
    }

    override fun onInit(s: Int) {
        tts.language = Locale("hi", "IN"); tts.setPitch(1.3f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            override fun onError(u: String?) { done() }
            override fun onDone(u: String?) { done() }
        })
    }
    private fun done() { speaking = false; h.post { web?.evaluateJavascript("speak(false)", null) } }

    private fun overlay() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        web = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            loadUrl("file:///android_asset/robot.html")
        }
        val lp = WindowManager.LayoutParams(450, 450, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM or Gravity.END }
        getSystemService(WindowManager::class.java).addView(web, lp)
    }

    private fun listen() { h.post {
        if (!alive) return@post
        if (speaking) { h.postDelayed({ listen() }, 800); return@post }
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this)
            sr!!.setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { handle(it) }
                    h.postDelayed({ listen() }, 400)
                }
                override fun onError(e: Int) { h.postDelayed({ listen() }, 700) }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        sr!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN"))
    } }

    private fun handle(raw: String) {
        val t = raw.lowercase().trim()
        val m = Regex("jarvis|जार्विस|जरविस|जारविस|जर्विस|जार्वी").find(t)
        if (m != null) {
            val c = t.substring(m.range.last + 1).trim()
            if (c.isEmpty()) { awake = System.currentTimeMillis() + 8000; say("Ji boliye!") } else run(c)
        } else if (awake > System.currentTimeMillis()) { awake = 0; run(t) }
    }

    private fun run(c: String) {
        val nf = Intent.FLAG_ACTIVITY_NEW_TASK
        when {
            Regex("home|होम").containsMatchIn(c) -> JarvisAccess.i?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            Regex("back|पीछे|वापस").containsMatchIn(c) -> JarvisAccess.i?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            Regex("time|samay|baje|समय|बजे").containsMatchIn(c) -> say("Abhi " + java.text.SimpleDateFormat("hh:mm a", Locale.US).format(Date()) + " ho rahe hain.")
            Regex("message|sms|मैसेज").containsMatchIn(c) -> {
                val r = Regex("(.+?) (?:ko|को) (?:message|sms|मैसेज)\\s*(?:bhejo|karo|भेजो|करो)?\\s*(.*)").find(c)
                val num = r?.let { number(it.groupValues[1]) }
                if (r == null || num == null) say("Naam ya number nahi mila.") else {
                    @Suppress("DEPRECATION") SmsManager.getDefault().sendTextMessage(num, null, r.groupValues[2], null, null)
                    say("Message bhej diya.")
                }
            }
            Regex("call|कॉल|फोन").containsMatchIn(c) -> {
                val name = c.replace(Regex("call|karo|kro|कॉल|करो|फोन|ko|को"), "").trim()
                val num = number(name)
                if (num == null) say("Number nahi mila.") else {
                    startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")).addFlags(nf)); say("Call laga raha hoon.")
                }
            }
            Regex("open|kholo|khol|खोलो|खोल|ओपन").containsMatchIn(c) -> {
                val n = c.replace(Regex("open|kholo|khol|खोलो|खोल|ओपन"), "").trim()
                val pm = packageManager
                val pkg = pm.getInstalledApplications(0).firstOrNull { pm.getApplicationLabel(it).toString().lowercase().contains(n) }?.packageName
                val li = pkg?.let { pm.getLaunchIntentForPackage(it) }
                if (li == null) say("Ye app nahi mila.") else { startActivity(li.addFlags(nf)); say("Khol diya.") }
            }
            else -> askClaude(c)
        }
    }

    private fun number(name: String): String? {
        if (name.replace(" ", "").matches(Regex("\\+?\\d{6,}"))) return name.replace(" ", "")
        return try {
            contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$name%"), null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) { null }
    }

    private fun askClaude(q: String) {
        val key = getSharedPreferences("j", 0).getString("key", "") ?: ""
        if (key.isEmpty()) { say("Pehle app mein API key daaliye."); return }
        Thread {
            val out = try {
                val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("x-api-key", key)
                c.setRequestProperty("anthropic-version", "2023-06-01")
                c.setRequestProperty("content-type", "application/json")
                val body = JSONObject().put("model", "claude-sonnet-5-5").put("max_tokens", 300)
                    .put("system", "Tum Jarvis ho, ek cute phone assistant. Hinglish mein 1-2 chhote vakya mein jawab do.")
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", q)))
                c.outputStream.write(body.toString().toByteArray())
                JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("content").getJSONObject(0).getString("text")
            } catch (e: Exception) { "Internet ya API key mein dikkat hai." }
            say(out)
        }.start()
    }

    private fun say(t: String) {
        speaking = true
        h.post { web?.evaluateJavascript("speak(true)", null) }
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u")
    }

    override fun onDestroy() {
        alive = false
        web?.let { getSystemService(WindowManager::class.java).removeView(it); it.destroy() }
        sr?.destroy(); tts.shutdown(); super.onDestroy()
    }
}
