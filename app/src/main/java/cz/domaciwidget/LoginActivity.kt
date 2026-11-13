package cz.domaciwidget

import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Přihlášení přímo na stránce Xiaomi (WebView). Captchu i ověření e-mailem řeší Xiaomi sám,
 * aplikace si po přihlášení jen převezme přihlašovací cookie (passToken).
 */
class LoginActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var info: TextView
    private var region = "de"

    @Volatile
    private var busy = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        region = intent?.getStringExtra("region") ?: "de"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        info = TextView(this)
        info.textSize = 14f
        info.setPadding(dp(12), dp(8), dp(12), dp(8))
        info.text = "Přihlaste se Xiaomi účtem. Po dokončení (stránka „ok“) se přihlášení dokončí samo."
        root.addView(info)

        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val done = Button(this)
        done.text = "Hotovo – dokončit přihlášení"
        done.setOnClickListener { harvest() }
        root.addView(done)

        setContentView(root)

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.removeAllCookies(null)
        cm.setAcceptThirdPartyCookies(web, true)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url?.toString() ?: ""
                return !(u.startsWith("http://") || u.startsWith("https://"))
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null && url.startsWith("https://sts.api.io.mi.com/sts")) harvest()
            }
        }
        web.loadUrl("https://account.xiaomi.com/pass/serviceLogin?sid=xiaomiio&_locale=cs_CZ")
    }

    private fun harvest() {
        if (busy) return
        busy = true
        info.text = "Dokončuji přihlášení…"

        val cm = CookieManager.getInstance()
        cm.flush()
        val raw = listOf(
            "https://account.xiaomi.com",
            "https://sts.api.io.mi.com",
            "https://api.io.mi.com"
        ).joinToString("; ") { cm.getCookie(it) ?: "" }

        val map = HashMap<String, String>()
        for (part in raw.split(";")) {
            val t = part.trim()
            val i = t.indexOf('=')
            if (i > 0) map[t.substring(0, i)] = t.substring(i + 1)
        }
        val pass = map["passToken"] ?: ""
        val uid = map["userId"] ?: ""
        val dev = map["deviceId"] ?: Prefs.deviceId(this)

        if (pass.isEmpty() || uid.isEmpty()) {
            info.text = "Přihlášení ještě není dokončené (chybí passToken). " +
                    "Dokončete přihlášení na stránce a zkuste to znovu. " +
                    "Nalezené cookies: " + map.keys.joinToString(", ")
            busy = false
            return
        }

        Thread {
            try {
                val agent = Prefs.agentId(this)
                val session = MiCloud(region, dev, agent).loginWithPassToken(uid, pass, dev)
                Prefs.savePassLogin(this, region, uid, pass, dev)
                Prefs.saveSession(this, session)
                val devices = MiCloud(region).listDevicesWithRooms(session)
                Prefs.saveDevices(this, devices)
                runOnUiThread {
                    Toast.makeText(this, "Přihlášeno, nalezeno zařízení: ${devices.size}", Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    info.text = "Chyba: " + (e.message ?: e.javaClass.simpleName)
                    busy = false
                }
            }
        }.start()
    }
}
