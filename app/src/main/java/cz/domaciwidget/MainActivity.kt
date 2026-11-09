package cz.domaciwidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var regionEt: EditText
    private lateinit var status: TextView
    private lateinit var diag: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String, sizeSp: Float): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = sizeSp
        tv.setPadding(0, dp(8), 0, dp(4))
        return tv
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(16))
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(label("Domácí widget – teploměry z Xiaomi / MiHome", 18f))
        root.addView(
            label(
                "1) Klepněte na Přihlásit a přihlaste se Xiaomi účtem v otevřeném okně " +
                        "(captchu i ověření e-mailem vyřeší Xiaomi tam).\n" +
                        "2) Na plochu přidejte widget „Domácí widget“ (4×2) a vyberte senzory.", 14f
            )
        )

        regionEt = EditText(this)
        regionEt.hint = "Region serveru (de, cn, us, ru, sg, i2)"
        regionEt.setText(Prefs.region(this))
        regionEt.inputType = InputType.TYPE_CLASS_TEXT
        regionEt.setSingleLine()
        root.addView(regionEt)

        val loginBtn = Button(this)
        loginBtn.text = "Přihlásit přes Xiaomi"
        loginBtn.setOnClickListener {
            val region = regionEt.text.toString().trim().lowercase().ifEmpty { "de" }
            startActivity(Intent(this, LoginActivity::class.java).putExtra("region", region))
        }
        root.addView(loginBtn)

        status = label("", 14f)
        root.addView(status)

        val refreshBtn = Button(this)
        refreshBtn.text = "Obnovit data teď"
        refreshBtn.setOnClickListener {
            RefreshWorker.enqueueNow(this)
            WidgetRenderer.renderAll(this)
            diag.text = "Obnova spuštěna, za chvíli otevřete aplikaci znovu a uvidíte výsledek."
        }
        root.addView(refreshBtn)

        diag = label("", 13f)
        root.addView(diag)

        root.addView(
            label(
                "Tip: v nastavení telefonu (Aplikace → Domácí widget) povolte automatické " +
                        "spouštění a u baterie zvolte „Bez omezení“, jinak systém obnovu " +
                        "na pozadí zastaví.", 13f
            )
        )
    }

    override fun onResume() {
        super.onResume()
        status.text = if (Prefs.hasCredentials(this)) {
            "Přihlášeno (účet ${Prefs.user(this)}, region ${Prefs.region(this)}). Zařízení: ${Prefs.readDevices(this).size}."
        } else {
            "Zatím nejste přihlášeni."
        }
        diag.text = "Poslední obnova dat: " + Prefs.status(this)
    }
}
