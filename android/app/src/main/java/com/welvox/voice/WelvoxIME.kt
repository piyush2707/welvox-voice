package com.welvox.voice

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

const val BASE_URL = "https://welvox-voice.onrender.com"
val LANGS = listOf("hinglish" to "Hinglish", "english" to "English", "hindi" to "हिन्दी")

class WelvoxIME : InputMethodService() {
    private val ui = Handler(Looper.getMainLooper())
    private val pool = Executors.newSingleThreadExecutor()
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var recording = false
    private var heard = false
    private var lastVoice = 0L
    private var startedAt = 0L
    private lateinit var status: TextView
    private lateinit var mic: Button
    private lateinit var modeBtn: Button

    private fun prefs() = getSharedPreferences("welvox", MODE_PRIVATE)
    private fun langIndex() = prefs().getInt("lang", 0).coerceIn(0, LANGS.size - 1)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun circle(c: String) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor(c))
    }

    private fun chip(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        textSize = 16f
        minWidth = 0
        minHeight = 0
        setPadding(0, 0, 0, 0)
        setTextColor(Color.parseColor("#E9DCC6"))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor("#3A2518"))
        }
        setOnClickListener { onClick() }
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B120C"))
            setPadding(dp(12), dp(12), dp(12), dp(14))
        }
        status = TextView(this).apply {
            text = "Tap the mic and speak"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#CDBA9C"))
        }
        mic = Button(this).apply {
            text = "🎤"
            textSize = 30f
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            background = circle("#C8854A")
            setOnClickListener { toggle() }
        }
        modeBtn = chip(LANGS[langIndex()].second) { cycleLang() }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun add(v: View, w: Int, h: Int) = row.addView(
            v, LinearLayout.LayoutParams(dp(w), dp(h)).apply { setMargins(dp(4), 0, dp(4), 0) }
        )
        add(chip("🌐") { pickKeyboard() }, 54, 54)
        add(modeBtn, 88, 54)
        add(mic, 76, 76)
        add(chip("⌫") { currentInputConnection?.deleteSurroundingText(1, 0) }, 54, 54)
        add(chip("⏎") { sendEnter() }, 54, 54)

        root.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        )
        root.addView(row)
        return root
    }

    private fun pickKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    private fun sendEnter() {
        if (!sendDefaultEditorAction(true)) currentInputConnection?.commitText("\n", 1)
    }

    private fun cycleLang() {
        prefs().edit().putInt("lang", (langIndex() + 1) % LANGS.size).apply()
        modeBtn.text = LANGS[langIndex()].second
    }

    private fun toggle() {
        if (recording) stopAndSend() else start()
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()

    private fun start() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Open the Welvox Voice app once and allow the microphone"
            return
        }
        try {
            val f = File.createTempFile("rec", ".m4a", cacheDir)
            val r = newRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16000)
            r.setAudioEncodingBitRate(64000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = f
            recording = true
            heard = false
            startedAt = SystemClock.uptimeMillis()
            lastVoice = startedAt
            mic.background = circle("#D9735A")
            status.text = "Listening… pause to finish"
            ui.postDelayed(watch, 150)
        } catch (e: Exception) {
            status.text = "Couldn’t start the microphone"
            release()
        }
    }

    // Stops on its own about 2 seconds after you stop talking
    private val watch = object : Runnable {
        override fun run() {
            if (!recording) return
            val amp = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }
            val now = SystemClock.uptimeMillis()
            if (amp > 2500) {
                heard = true
                lastVoice = now
            }
            if ((heard && now - lastVoice > 1800) || now - startedAt > 120000) {
                stopAndSend()
                return
            }
            ui.postDelayed(this, 150)
        }
    }

    private fun release() {
        try { recorder?.release() } catch (e: Exception) { }
        recorder = null
    }

    private fun stopAndSend() {
        if (!recording) return
        recording = false
        val f = file
        try { recorder?.stop() } catch (e: Exception) { }
        release()
        mic.background = circle("#C8854A")
        if (f == null || f.length() < 1500) {
            status.text = "That was too short. Try again."
            return
        }
        status.text = "Cleaning up your words…"
        val lang = LANGS[langIndex()].first
        pool.execute {
            val result = try {
                upload(f, lang)
            } catch (e: Exception) {
                "ERR:Couldn’t reach the server. It may be waking up. Try again."
            }
            f.delete()
            ui.post { handle(result) }
        }
    }

    private fun handle(result: String) {
        if (result.startsWith("ERR:")) {
            status.text = result.removePrefix("ERR:")
        } else if (result.isBlank()) {
            status.text = "Didn’t catch that. Try again."
        } else {
            currentInputConnection?.commitText("$result ", 1)
            status.text = "Done. Tap the mic to speak again."
        }
    }

    private fun upload(f: File, lang: String): String {
        val boundary = "----welvox" + System.currentTimeMillis()
        val c = URL("$BASE_URL/dictate").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 20000
        c.readTimeout = 90000
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        DataOutputStream(c.outputStream).use { o ->
            o.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"mode\"\r\n\r\n")
            o.write(lang.toByteArray())
            o.writeBytes("\r\n--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n")
            o.write(f.readBytes())
            o.writeBytes("\r\n--$boundary--\r\n")
        }
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) {
            val d = try { JSONObject(body).getString("detail") } catch (e: Exception) { "Server error $code" }
            return "ERR:$d"
        }
        return JSONObject(body).getString("clean").trim()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (recording) {
            recording = false
            release()
        }
    }
}
