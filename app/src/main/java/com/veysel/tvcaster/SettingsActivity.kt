package com.veysel.tvcaster

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("tvcaster", MODE_PRIVATE)

        val swAds = findViewById<SwitchMaterial>(R.id.switchAds)
        val swPopup = findViewById<SwitchMaterial>(R.id.switchPopup)
        val swDark = findViewById<SwitchMaterial>(R.id.switchDark)

        swAds.isChecked = prefs.getBoolean("adblock", true)
        swPopup.isChecked = prefs.getBoolean("popupblock", true)
        swDark.isChecked = prefs.getBoolean("dark", true)

        swAds.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("adblock", checked).apply()
        }
        swPopup.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("popupblock", checked).apply()
        }
        swDark.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("dark", checked).apply()
            if (checked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        findViewById<Button>(R.id.btnLimits).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }
}
