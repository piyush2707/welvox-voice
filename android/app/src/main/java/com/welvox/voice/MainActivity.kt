package com.welvox.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#1B120C"))
            setPadding(48, 120, 48, 48)
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
        col.addView(label("Designed by Piyush Joshi", 13f, "#A88563"))
        setContentView(col)
    }
}
