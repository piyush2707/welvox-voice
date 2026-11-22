package com.welvox.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("welvox", MODE_PRIVATE)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 120, 48, 64)
        }
        fun label(t: String, size: Float, color: String) = TextView(this).apply {
            text = t
            textSize = size
            setTextColor(Color.parseColor(color))
            setPadding(0, 0, 0, 36)
        }
        fun button(t: String, action: () -> Unit) = Button(this).apply {
            text = t
            isAllCaps = false
            setOnClickListener { action() }
        }

        col.addView(label("Welvox Voice keyboard", 26f, "#E8B86A"))
        col.addView(label("Set it up in three taps, then switch to it from any text box with the 🌐 key.", 16f, "#CDBA9C"))
        col.addView(button("1. Allow microphone") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        })
        col.addView(button("2. Enable the keyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        col.addView(button("3. Choose Welvox Voice") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        col.addView(label("My words", 20f, "#E8B86A").apply { setPadding(0, 48, 0, 8) })
        col.addView(label("Names and terms to always spell right, separated by commas.", 14f, "#CDBA9C").apply { setPadding(0, 0, 0, 16) })
        val words = EditText(this).apply {
            hint = "Welvox, Piyush, Graphic Era"
            setHintTextColor(Color.parseColor("#A88563"))
            setTextColor(Color.parseColor("#F6EFE2"))
            setBackgroundColor(Color.parseColor("#3A2518"))
            setPadding(32, 24, 32, 24)
            minLines = 2
            setText(prefs.getString("vocab", "") ?: "")
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    prefs.edit().putString("vocab", s?.toString() ?: "").apply()
                }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }
        col.addView(words, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        col.addView(label("Designed by Piyush Joshi", 13f, "#A88563").apply { setPadding(0, 48, 0, 0) })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1B120C"))
            addView(col)
        }
        setContentView(scroll)
    }
}
