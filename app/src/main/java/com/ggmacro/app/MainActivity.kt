package com.ggmacro.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlay: TextView
    private lateinit var tvAccess: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ── UI ─────────────────────────────────────────────────────────────────

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(30), dp(22), dp(30))
            setBackgroundColor(0xFF0D1117.toInt())
        }

        // Logo
        TextView(this).apply {
            text = "GG Macro"; textSize = 30f; gravity = Gravity.CENTER
            setTextColor(0xFF00E5FF.toInt()); setPadding(0, 0, 0, dp(3))
        }.also { root.addView(it, mw()) }

        TextView(this).apply {
            text = "Oyun içi otomatik tıklama"; textSize = 13f; gravity = Gravity.CENTER
            setTextColor(0xFF9E9E9E.toInt())
        }.also { root.addView(it, mw()) }

        root.addView(sp(dp(20)))

        // Permission cards
        tvOverlay = permCard(root, "1. Ekran Üstü İzin (Overlay)") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        root.addView(sp(dp(8)))
        tvAccess = permCard(root, "2. Erişilebilirlik Servisi") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        root.addView(sp(dp(20)))

        // How-to
        howtoCard(root)

        root.addView(sp(dp(22)))

        // START button
        btnStart = Button(this).apply {
            text = "🚀  BAŞLAT"; textSize = 16f; isAllCaps = false
            setTextColor(0xFF000000.toInt()); setBackgroundColor(0xFF00E5FF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onStart() }
        }
        root.addView(btnStart, mw().also { (it as LinearLayout.LayoutParams).setMargins(dp(16), 0, dp(16), 0) })

        root.addView(sp(dp(8)))

        // STOP button
        btnStop = Button(this).apply {
            text = "■  DURDUR"; textSize = 16f; isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFFEF5350.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            visibility = View.GONE
            setOnClickListener { onStop() }
        }
        root.addView(btnStop, mw().also { (it as LinearLayout.LayoutParams).setMargins(dp(16), 0, dp(16), 0) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF0D1117.toInt())
            addView(root)
        })
    }

    private fun permCard(parent: LinearLayout, title: String, onClick: () -> Unit): TextView {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF161B22.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        TextView(this).apply {
            text = title; textSize = 13f
            setTextColor(0xFFDDDDDD.toInt())
        }.also { card.addView(it) }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        val tv = TextView(this).apply {
            text = "Kontrol ediliyor..."; textSize = 12f
            setTextColor(0xFF9E9E9E.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btn = Button(this).apply {
            text = "Ayarları Aç"; textSize = 11f; isAllCaps = false
            setTextColor(0xFF00E5FF.toInt()); setBackgroundColor(0x00000000)
            setPadding(dp(6), 0, 0, 0)
            setOnClickListener { onClick() }
        }
        row.addView(tv); row.addView(btn); card.addView(row)
        parent.addView(card, mw())
        return tv
    }

    private fun howtoCard(parent: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF161B22.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        listOf(
            "✅ İzinleri ver (yukarıdaki 2 adım)",
            "✅ BAŞLAT'a bas → ekran üstünde + butonu çıkar",
            "✅ + butonuna bas → tıklama butonu oluşur",
            "✅ Butonu sürükle → oyunun istediğin yerine taşı",
            "✅ BAS & TUT → o noktaya sürekli otomatik tıklama",
            "✅ Parmağını kaldır → tıklama durur",
            "✅ Bırakınca × rozetine bas → butonu sil",
            "💡 İstediğin kadar buton ekleyebilirsin",
            "💡 Her buton bağımsız pozisyona sahip"
        ).forEach { line ->
            TextView(this).apply {
                text = line; textSize = 12.5f
                setTextColor(0xFFB0BEC5.toInt())
                setPadding(0, dp(3), 0, dp(3))
            }.also { card.addView(it) }
        }
        parent.addView(card, mw())
    }

    // ── Logic ──────────────────────────────────────────────────────────────

    private fun onStart() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Önce Ekran Üstü İzni ver!")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }
        if (!isAccessibility()) {
            toast("Önce Erişilebilirlik Servisini aç!")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        TapButtonService.start(this)
        Handler(Looper.getMainLooper()).postDelayed({ moveTaskToBack(true) }, 250)
        Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 400)
    }

    private fun onStop() {
        TapButtonService.stop(this)
        Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 300)
    }

    private fun refresh() {
        val ov = Settings.canDrawOverlays(this)
        val ac = isAccessibility()
        val svc = TapButtonService.running

        tvOverlay.text = if (ov) "✓ İzin verildi" else "✗ İzin gerekli — sağdaki butona bas"
        tvOverlay.setTextColor(if (ov) 0xFF4CAF50.toInt() else 0xFFEF5350.toInt())

        tvAccess.text = if (ac) "✓ Servis aktif" else "✗ Kapalı — sağdaki butona bas, GG Macro'yu etkinleştir"
        tvAccess.setTextColor(if (ac) 0xFF4CAF50.toInt() else 0xFFEF5350.toInt())

        btnStart.visibility = if (svc) View.GONE else View.VISIBLE
        btnStop.visibility = if (svc) View.VISIBLE else View.GONE
    }

    private fun isAccessibility(): Boolean {
        return try {
            if (Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED) == 0) return false
            val svcList = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            svcList.contains("$packageName/${TapService::class.java.name}")
        } catch (_: Exception) { false }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun mw() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun sp(h: Int) = View(this).also { it.layoutParams = LinearLayout.LayoutParams(1, h) }
}
