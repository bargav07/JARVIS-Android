package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var sr: SpeechRecognizer? = null
    private var isThinking = false
    private var isSpeaking = false
    private val handler = Handler(Looper.getMainLooper())
    private var flashOn = false

    private lateinit var tvStatus: TextView
    private lateinit var tvUser: TextView
    private lateinit var tvReply: TextView
    private lateinit var tvClock: TextView

    private val API_KEY = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContentView(R.layout.activity_main)
        tvStatus = findViewById(R.id.tvStatus)
        tvUser   = findViewById(R.id.tvUser)
        tvReply  = findViewById(R.id.tvReply)
        tvClock  = findViewById(R.id.tvClock)
        tts = TextToSpeech(this, this)
        requestPermissions()
        startClock()
        startWakeService()
        findViewById<Button>(R.id.btnStandby).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnTime).setOnClickListener     { processCmd("What time is it") }
        findViewById<Button>(R.id.btnCamera).setOnClickListener   { processCmd("Open camera") }
        findViewById<Button>(R.id.btnMaps).setOnClickListener     { processCmd("Open maps") }
        findViewById<Button>(R.id.btnMusic).setOnClickListener    { processCmd("Play music") }
        findViewById<Button>(R.id.btnWeather).setOnClickListener  { processCmd("Open weather") }
        findViewById<Button>(R.id.btnYoutube).setOnClickListener  { processCmd("Open YouTube") }
        findViewById<Button>(R.id.btnWhatsapp).setOnClickListener { processCmd("Open WhatsApp") }
        findViewById<Button>(R.id.btnTorch).setOnClickListener    { processCmd("Toggle flashlight") }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.UK
            tts.setPitch(0.85f)
            tts.setSpeechRate(0.93f)
            val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val timeGreet = if (hr < 12) "Good morning" else if (hr < 17) "Good afternoon" else "Good evening"
            respond("$timeGreet. JARVIS online. How may I assist?")
        }
    }

    private fun startListening() {
        if (isThinking || isSpeaking) return
        setStatus("LISTENING")
        setUser("Listening...")
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                setUser("\"$text\"")
                processCmd(text)
            }
            override fun onPartialResults(b: Bundle?) {
                val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                setUser("\"$text\"")
            }
            override fun onError(e: Int) {
                if (!isThinking && !isSpeaking) handler.postDelayed({ startListening() }, 1200)
            }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        sr?.startListening(intent)
    }

    private fun processCmd(cmd: String) {
        if (isThinking) return
        isThinking = true
        sr?.stopListening()
        setStatus("PROCESSING")
        setUser("\"$cmd\"")
        val lower = cmd.lowercase(Locale.US)
        when {
            lower.contains("time") ->
                respond("It is " + SimpleDateFormat("hh:mm a", Locale.US).format(Date()) + ".")
            lower.contains("date") || lower.contains("today") ->
                respond("Today is " + SimpleDateFormat("EEEE, MMMM d", Locale.US).format(Date()) + ".")
            lower.contains("hello") || lower.contains("hi") ->
                respond("Hello! I am fully operational and ready to assist.")
            lower.contains("how are you") ->
                respond("All systems functioning at optimal capacity, sir.")
            lower.contains("your name") || lower.contains("who are you") ->
                respond("I am JARVIS, Just A Rather Very Intelligent System.")
            lower.contains("camera") -> { openIntent(Intent("android.media.action.IMAGE_CAPTURE")); respond("Opening camera.") }
            lower.contains("maps") || lower.contains("navigation") -> { openUrl("https://maps.google.com"); respond("Launching Google Maps.") }
            lower.contains("youtube") -> { openUrl("https://youtube.com"); respond("Opening YouTube.") }
            lower.contains("whatsapp") -> { openPkg("com.whatsapp"); respond("Opening WhatsApp.") }
            lower.contains("instagram") -> { openPkg("com.instagram.android"); respond("Opening Instagram.") }
            lower.contains("spotify") || lower.contains("music") -> { openPkg("com.spotify.music"); respond("Opening Spotify.") }
            lower.contains("gmail") || lower.contains("email") -> { openUrl("https://mail.google.com"); respond("Opening Gmail.") }
            lower.contains("alarm") -> { startActivity(Intent(AlarmClock.ACTION_SET_ALARM)); respond("Opening alarm.") }
            lower.contains("flashlight") || lower.contains("torch") -> { toggleFlash(); respond(if (flashOn) "Flashlight on." else "Flashlight off.") }
            lower.contains("wifi") -> { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)); respond("Opening Wi-Fi settings.") }
            lower.contains("bluetooth") -> { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); respond("Opening Bluetooth settings.") }
            lower.contains("settings") -> { startActivity(Intent(Settings.ACTION_SETTINGS)); respond("Opening settings.") }
            lower.contains("weather") -> { openUrl("https://weather.com"); respond("Opening weather.") }
            lower.contains("call") || lower.contains("phone") -> { startActivity(Intent(Intent.ACTION_DIAL)); respond("Opening dialer.") }
            lower.contains("thank") -> respond("My pleasure, sir.")
            lower.contains("joke") -> respond("Why do programmers prefer dark mode? Because light attracts bugs.")
            lower.contains("bye") || lower.contains("goodbye") || lower.contains("standby") ->
                respond("Going to standby. Say Hey Jarvis to wake me.") { finish() }
            lower.contains("search") || lower.contains("google") -> {
                val q = lower.replace("search for","").replace("google","").replace("search","").trim()
                openUrl("https://www.google.com/search?q=" + Uri.encode(q))
                respond("Searching for $q.")
            }
            API_KEY.isNotEmpty() -> askAI(cmd)
            else -> {
                val replies = listOf("Understood.", "Command received.", "Acknowledged, sir.", "On it.")
                respond(replies[replies.indices.random()])
            }
        }
    }

    private fun respond(text: String, onDone: (() -> Unit)? = null) {
        isThinking = false
        setReply(text)
        setStatus("SPEAKING")
        isSpeaking = true
        val id = System.currentTimeMillis().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            override fun onDone(u: String?) {
                handler.post {
                    isSpeaking = false
                    onDone?.invoke()
                    handler.postDelayed({ startListening() }, 500)
                }
            }
            override fun onError(u: String?) {
                handler.post {
                    isSpeaking = false
                    onDone?.invoke()
                    startListening()
                }
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun askAI(cmd: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://api.anthropic.com/v1/messages")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-api-key", API_KEY)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                conn.doOutput = true
                val body = JSONObject()
                body.put("model", "claude-sonnet-4-20250514")
                body.put("max_tokens", 150)
                body.put("system", "You are JARVIS from Iron Man. 1-2 sentences max. Confident, British tone. Plain text only.")
                val messages = JSONArray()
                val msg = JSONObject()
                msg.put("role", "user")
                msg.put("content", cmd)
                messages.put(msg)
                body.put("messages", messages)
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                val reply = JSONObject(conn.inputStream.bufferedReader().readText())
                    .getJSONArray("content").getJSONObject(0).getString("text")
                withContext(Dispatchers.Main) { respond(reply) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { respond("I encountered a connectivity issue, sir.") }
            }
        }
    }

    private fun toggleFlash() {
        try {
            val cm = getSystemService(CAMERA_SERVICE) as CameraManager
            flashOn = !flashOn
            cm.setTorchMode(cm.cameraIdList[0], flashOn)
        } catch (e: Exception) {}
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {}
    }

    private fun openPkg(pkg: String) {
        try {
            startActivity(packageManager.getLaunchIntentForPackage(pkg) ?: throw Exception())
        } catch (e: Exception) {
            openUrl("https://play.google.com/store/apps/details?id=$pkg")
        }
    }

    private fun openIntent(i: Intent) {
        try { startActivity(i) } catch (e: Exception) {}
    }

    private fun setStatus(s: String) = runOnUiThread { tvStatus.text = s }
    private fun setUser(s: String)   = runOnUiThread { tvUser.text = s }
    private fun setReply(s: String)  = runOnUiThread { tvReply.text = s }

    private fun startClock() {
        handler.post(object : Runnable {
            override fun run() {
                tvClock.text = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun startWakeService() {
        val i = Intent(this, WakeWordService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.CAMERA)
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
        sr?.destroy()
        handler.removeCallbacksAndMessages(null)
    }
}
