package cz.domaciwidget

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.security.MessageDigest

class MainActivity : Activity() {

    private lateinit var regionEt: EditText
    private lateinit var userEt: EditText
    private lateinit var passEt: EditText
    private lateinit var status: TextView
    private lateinit var diag: TextView
    private lateinit var verifyBtn: Button
    private lateinit var captchaImg: ImageView
    private lateinit var captchaEt: EditText
    private lateinit var captchaBtn: Button
    private var verifyUrl: String? = null

    private var pendingCloud: MiCloud? = null
    private var pendingRegion = "de"
    private var pendingUser = ""
    private var pendingHash = ""

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
                "1) Přihlaste se stejným Xiaomi účtem, který používáte ve Vevs/MiHome.\n" +
                        "2) Na plochu přidejte widget „Domácí widget“ (4×2) a vyberte senzory.", 14f
            )
        )

        regionEt = EditText(this)
        regionEt.hint = "Region serveru (de, cn, us, ru, sg, i2)"
        regionEt.setText(Prefs.region(this))
        regionEt.setSingleLine()
        root.addView(regionEt)

        userEt = EditText(this)
        userEt.hint = "E-mail / ID Xiaomi účtu"
        userEt.setText(Prefs.user(this))
        userEt.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        userEt.setSingleLine()
        root.addView(userEt)

        passEt = EditText(this)
        passEt.hint = "Heslo"
        passEt.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        passEt.setSingleLine()
        root.addView(passEt)

        val loginBtn = Button(this)
        loginBtn.text = "Přihlásit"
        loginBtn.setOnClickListener { doLogin() }
        root.addView(loginBtn)

        status = label("", 14f)
        root.addView(status)

        captchaImg = ImageView(this)
        captchaImg.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(90))
        captchaImg.scaleType = ImageView.ScaleType.FIT_START
        captchaImg.visibility = View.GONE
        root.addView(captchaImg)

        captchaEt = EditText(this)
        captchaEt.hint = "Kód z obrázku"
        captchaEt.inputType = InputType.TYPE_CLASS_TEXT
        captchaEt.setSingleLine()
        captchaEt.visibility = View.GONE
        root.addView(captchaEt)

        captchaBtn = Button(this)
        captchaBtn.text = "Odeslat kód"
        captchaBtn.visibility = View.GONE
        captchaBtn.setOnClickListener { doCaptcha() }
        root.addView(captchaBtn)

        verifyBtn = Button(this)
        verifyBtn.text = "Otevřít ověření Xiaomi"
        verifyBtn.visibility = View.GONE
        verifyBtn.setOnClickListener {
            val u = verifyUrl
            if (u != null) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        }
        root.addView(verifyBtn)

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
        if (Prefs.hasCredentials(this)) {
            status.text = "Uložený účet: ${Prefs.user(this)} (region ${Prefs.region(this)})"
        }
        diag.text = "Poslední obnova dat: " + Prefs.status(this)
    }

    private fun md5Upper(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02X".format(it) }

    private fun hideCaptcha() {
        captchaImg.visibility = View.GONE
        captchaEt.visibility = View.GONE
        captchaBtn.visibility = View.GONE
    }

    private fun showError(e: MiCloudException) {
        status.text = e.message
        verifyUrl = e.verifyUrl
        verifyBtn.visibility = if (e.verifyUrl != null) View.VISIBLE else View.GONE
        val img = e.captchaImage
        if (img != null && img.isNotEmpty()) {
            val bmp = BitmapFactory.decodeByteArray(img, 0, img.size)
            if (bmp != null) {
                captchaImg.setImageBitmap(bmp)
                captchaImg.visibility = View.VISIBLE
                captchaEt.setText("")
                captchaEt.visibility = View.VISIBLE
                captchaBtn.visibility = View.VISIBLE
                return
            }
        }
        hideCaptcha()
    }

    private fun completeLogin(region: String, user: String, hash: String, session: Session) {
        Prefs.saveLogin(this, region, user, hash)
        Prefs.saveSession(this, session)
        val devices = MiCloud(region).listDevices(session)
        Prefs.saveDevices(this, devices)
        runOnUiThread {
            hideCaptcha()
            verifyBtn.visibility = View.GONE
            passEt.setText("")
            status.text = "Přihlášeno. Nalezeno zařízení: ${devices.size}.\n" +
                    "Nyní přidejte widget 4×2 na plochu."
        }
    }

    private fun doLogin() {
        val region = regionEt.text.toString().trim().lowercase().ifEmpty { "de" }
        val user = userEt.text.toString().trim()
        val pass = passEt.text.toString()
        if (user.isEmpty() || pass.isEmpty()) {
            status.text = "Vyplňte e-mail a heslo."
            return
        }
        status.text = "Přihlašuji…"
        verifyBtn.visibility = View.GONE
        hideCaptcha()

        Thread {
            try {
                val hash = md5Upper(pass)
                val cloud = MiCloud(region, Prefs.deviceId(this), Prefs.agentId(this))
                pendingCloud = cloud
                pendingRegion = region
                pendingUser = user
                pendingHash = hash
                val session = cloud.login(user, hash)
                completeLogin(region, user, hash, session)
            } catch (e: MiCloudException) {
                runOnUiThread { showError(e) }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Chyba: " + (e.message ?: e.javaClass.simpleName) }
            }
        }.start()
    }

    private fun doCaptcha() {
        val cloud = pendingCloud ?: return
        val code = captchaEt.text.toString().trim()
        if (code.isEmpty()) {
            status.text = "Opište kód z obrázku."
            return
        }
        status.text = "Ověřuji kód…"

        Thread {
            try {
                val session = cloud.submitCaptcha(code)
                completeLogin(pendingRegion, pendingUser, pendingHash, session)
            } catch (e: MiCloudException) {
                runOnUiThread { showError(e) }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Chyba: " + (e.message ?: e.javaClass.simpleName) }
            }
        }.start()
    }
}
