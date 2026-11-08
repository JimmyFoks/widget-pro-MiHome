package cz.domaciwidget

import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class MiCloudException(
    message: String,
    val verifyUrl: String? = null,
    val auth: Boolean = false,
    val captchaImage: ByteArray? = null
) : Exception(message)

private class MemoryCookieJar : CookieJar {
    private val store = ArrayList<Cookie>()

    @Synchronized
    fun add(c: Cookie) {
        store.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
        store.add(c)
    }

    @Synchronized
    fun find(name: String): String? = store.lastOrNull { it.name == name }?.value

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) add(c)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = store.filter { it.matches(url) }
}

private fun randomString(n: Int, alphabet: String): String {
    val r = SecureRandom()
    val sb = StringBuilder()
    for (i in 0 until n) sb.append(alphabet[r.nextInt(alphabet.length)])
    return sb.toString()
}

/**
 * Minimální klient pro neoficiální Xiaomi cloud (stejný princip jako
 * Xiaomi-cloud-tokens-extractor / Home Assistant). Přihlášení jménem a heslem,
 * šifrované (RC4) podepsané volání API.
 */
class MiCloud(private val region: String) {

    private val agent = "Android-7.1.1-1.0.0-ONEPLUS A3010-136-" +
            randomString(13, "ABCDE") + " APP/xiaomi.smarthome APPV/62830"
    private val deviceId = randomString(6, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
    private val jar = MemoryCookieJar()

    @Volatile
    private var capturedToken: String? = null

    private val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(Interceptor { chain ->
            val resp = chain.proceed(chain.request())
            for (h in resp.headers("Set-Cookie")) {
                if (h.startsWith("serviceToken=")) {
                    val v = h.substringAfter("serviceToken=").substringBefore(";")
                    if (v.isNotEmpty() && v != "EXPIRED") capturedToken = v
                }
            }
            resp
        })
        .build()

    private val apiHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun cookie(name: String, value: String, domain: String): Cookie =
        Cookie.Builder().name(name).value(value).domain(domain).path("/").build()

    private fun parseJson(text: String): JSONObject {
        val t = text.replace("&&&START&&&", "").trim()
        try {
            return JSONObject(t)
        } catch (e: Exception) {
            throw MiCloudException("Neočekávaná odpověď Xiaomi serveru: " + t.take(120))
        }
    }

    // ------------------------------------------------------------------ přihlášení

    private var pendingUser: String = ""
    private var pendingHash: String = ""
    private var pendingSign: String = ""
    private var pendingCallback: String = ""
    private var pendingQs: String = ""

    fun login(user: String, passMd5Upper: String): Session {
        for (domain in listOf("xiaomi.com", "mi.com")) {
            jar.add(cookie("sdkVersion", "accountsdk-18.8.15", domain))
            jar.add(cookie("deviceId", deviceId, domain))
        }
        jar.add(cookie("userId", user, "xiaomi.com"))

        // krok 1
        val req1 = Request.Builder()
            .url("https://account.xiaomi.com/pass/serviceLogin?sid=xiaomiio&_json=true")
            .header("User-Agent", agent)
            .build()
        val j1 = parseJson(http.newCall(req1).execute().use { it.body?.string().orEmpty() })
        val sign = j1.optString("_sign")
        if (sign.isEmpty()) throw MiCloudException("Přihlášení: server nevrátil podpis (_sign).")

        pendingUser = user
        pendingHash = passMd5Upper
        pendingSign = sign
        pendingCallback = j1.optString("callback").ifEmpty { "https://sts.api.io.mi.com/sts" }
        pendingQs = j1.optString("qs").ifEmpty { "%3Fsid%3Dxiaomiio%26_json%3Dtrue" }

        return authenticate(null)
    }

    /** Dokončí přihlášení po opsání captchy (stejná relace jako login). */
    fun submitCaptcha(code: String): Session = authenticate(code.trim())

    private fun fetchCaptcha(url: String): ByteArray {
        val full = if (url.startsWith("/")) "https://account.xiaomi.com$url" else url
        val req = Request.Builder().url(full).header("User-Agent", agent).build()
        return http.newCall(req).execute().use { it.body?.bytes() ?: ByteArray(0) }
    }

    private fun authenticate(captCode: String?): Session {
        // krok 2
        val fb = FormBody.Builder()
            .add("sid", "xiaomiio")
            .add("hash", pendingHash)
            .add("callback", pendingCallback)
            .add("qs", pendingQs)
            .add("user", pendingUser)
            .add("_sign", pendingSign)
            .add("_json", "true")
        if (!captCode.isNullOrEmpty()) fb.add("captCode", captCode)
        val req2 = Request.Builder()
            .url("https://account.xiaomi.com/pass/serviceLoginAuth2")
            .header("User-Agent", agent)
            .post(fb.build())
            .build()
        val j2 = parseJson(http.newCall(req2).execute().use { it.body?.string().orEmpty() })

        val ssecurity = j2.optString("ssecurity")
        if (ssecurity.length <= 4) {
            var notif = j2.optString("notificationUrl")
            if (notif.isNotEmpty()) {
                if (notif.startsWith("/")) notif = "https://account.xiaomi.com$notif"
                throw MiCloudException(
                    "Xiaomi vyžaduje dodatečné ověření. Otevřete odkaz, ověřte se " +
                            "a pak klepněte na Přihlásit znovu.",
                    verifyUrl = notif
                )
            }
            val captcha = j2.optString("captchaUrl")
            if (captcha.isNotEmpty()) {
                val img = try {
                    fetchCaptcha(captcha)
                } catch (e: Exception) {
                    ByteArray(0)
                }
                if (img.isEmpty()) {
                    throw MiCloudException("Xiaomi chce captchu, ale obrázek se nepodařilo stáhnout. Zkuste to znovu.")
                }
                val first = captCode.isNullOrEmpty()
                throw MiCloudException(
                    if (first) "Xiaomi chce ověřit, že nejste robot. Opište kód z obrázku."
                    else "Kód nesedí nebo vypršel. Opište nový kód z obrázku.",
                    captchaImage = img
                )
            }
            val desc = j2.optString("desc")
            throw MiCloudException("Přihlášení selhalo: $desc (kód ${j2.optInt("code")})")
        }

        // krok 3
        val loc = j2.optString("location")
        if (loc.isEmpty()) throw MiCloudException("Přihlášení: chybí adresa pro získání tokenu.")
        val req3 = Request.Builder().url(loc).header("User-Agent", agent).build()
        http.newCall(req3).execute().use { it.body?.string() }

        val token = capturedToken ?: jar.find("serviceToken")
            ?: throw MiCloudException("Přihlášení: nepodařilo se získat serviceToken.")
        var uid = j2.optString("userId")
        if (uid.isEmpty()) uid = jar.find("userId") ?: pendingUser
        return Session(uid, ssecurity, token)
    }

    // ------------------------------------------------------------------ API

    private fun baseUrl(): String =
        "https://" + (if (region == "cn") "" else "$region.") + "api.io.mi.com/app"

    private fun timezoneString(): String {
        val tz = TimeZone.getDefault()
        val offMin = tz.getOffset(System.currentTimeMillis()) / 60000
        val sign = if (offMin >= 0) "+" else "-"
        val a = Math.abs(offMin)
        return "GMT%s%02d:%02d".format(sign, a / 60, a % 60)
    }

    private fun cookieHeader(s: Session): String {
        val tz = TimeZone.getDefault()
        val dst = tz.inDaylightTime(Date())
        return listOf(
            "userId=${s.userId}",
            "yetAnotherServiceToken=${s.serviceToken}",
            "serviceToken=${s.serviceToken}",
            "locale=en_GB",
            "timezone=${timezoneString()}",
            "is_daylight=${if (dst) 1 else 0}",
            "dst_offset=${if (dst) 3600000 else 0}",
            "channel=MI_APP_STORE"
        ).joinToString("; ")
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(12)
        val rnd = ByteArray(8)
        SecureRandom().nextBytes(rnd)
        System.arraycopy(rnd, 0, bytes, 0, 8)
        val minutes = (System.currentTimeMillis() / 60000L).toInt()
        bytes[8] = (minutes shr 24).toByte()
        bytes[9] = (minutes shr 16).toByte()
        bytes[10] = (minutes shr 8).toByte()
        bytes[11] = minutes.toByte()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun signedNonce(ssecurity: String, nonce: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(Base64.decode(ssecurity, Base64.DEFAULT))
        md.update(Base64.decode(nonce, Base64.DEFAULT))
        return Base64.encodeToString(md.digest(), Base64.NO_WRAP)
    }

    private fun encSignature(path: String, signedNonce: String, params: Map<String, String>): String {
        val parts = ArrayList<String>()
        parts.add("POST")
        parts.add(path)
        for ((k, v) in params) parts.add("$k=$v")
        parts.add(signedNonce)
        val digest = MessageDigest.getInstance("SHA-1").digest(parts.joinToString("&").toByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xff)) and 0xff
            val t = s[i]; s[i] = s[j]; s[j] = t
        }
        var x = 0
        var y = 0
        fun next(): Int {
            x = (x + 1) and 0xff
            y = (y + s[x]) and 0xff
            val t = s[x]; s[x] = s[y]; s[y] = t
            return s[(s[x] + s[y]) and 0xff]
        }
        for (n in 0 until 1024) next()
        val out = ByteArray(data.size)
        for (n in data.indices) out[n] = (data[n].toInt() xor next()).toByte()
        return out
    }

    /** Podepsané a šifrované volání API. [path] např. "/home/device_list". */
    fun call(s: Session, path: String, data: JSONObject): JSONObject {
        val nonce = generateNonce()
        val signed = signedNonce(s.ssecurity, nonce)
        val key = Base64.decode(signed, Base64.DEFAULT)

        val params = LinkedHashMap<String, String>()
        params["data"] = data.toString()
        params["rc4_hash__"] = encSignature(path, signed, params)

        val enc = LinkedHashMap<String, String>()
        for ((k, v) in params) {
            enc[k] = Base64.encodeToString(rc4(key, v.toByteArray()), Base64.NO_WRAP)
        }
        enc["signature"] = encSignature(path, signed, enc)
        enc["ssecurity"] = s.ssecurity
        enc["_nonce"] = nonce

        val bodyBuilder = FormBody.Builder()
        for ((k, v) in enc) bodyBuilder.add(k, v)

        val req = Request.Builder()
            .url(baseUrl() + path)
            .header("User-Agent", agent)
            .header("Accept-Encoding", "identity")
            .header("x-xiaomi-protocal-flag-cli", "PROTOCAL-HTTP2")
            .header("MIOT-ENCRYPT-ALGORITHM", "ENCRYPT-RC4")
            .header("Cookie", cookieHeader(s))
            .post(bodyBuilder.build())
            .build()

        val (httpCode, text) = apiHttp.newCall(req).execute().use { Pair(it.code, it.body?.string().orEmpty().trim()) }
        if (httpCode !in 200..299) {
            throw MiCloudException("HTTP $httpCode od Xiaomi API", auth = (httpCode == 401 || httpCode == 403))
        }

        val json: JSONObject = try {
            val plain = rc4(key, Base64.decode(text, Base64.DEFAULT))
            JSONObject(String(plain))
        } catch (e: Exception) {
            throw MiCloudException("Odpověď API nelze přečíst (vypršel token?)", auth = true)
        }

        val code = json.optInt("code", -1)
        if (code != 0) {
            val msg = json.optString("message")
            throw MiCloudException(
                "Xiaomi API: kód $code $msg",
                auth = (code == 3 || msg.contains("auth", ignoreCase = true))
            )
        }
        return json
    }

    fun listDevices(s: Session): List<Device> {
        val data = JSONObject()
            .put("getVirtualModel", true)
            .put("getHuamiDevices", 1)
            .put("get_split_device", false)
            .put("support_smart_home", true)
        val res = call(s, "/home/device_list", data)
        val list = res.optJSONObject("result")?.optJSONArray("list") ?: return emptyList()
        val out = ArrayList<Device>()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            out.add(Device(o.optString("did"), o.optString("name"), o.optString("model"), o.optString("spec_type")))
        }
        return out
    }

    /** Z miot-spec stáhne čísla vlastností (siid/piid) pro teplotu, vlhkost a baterii. */
    fun fetchSpecProps(specType: String): Map<String, IntArray> {
        val url = "https://miot-spec.org/miot-spec-v2/instance".toHttpUrl().newBuilder()
            .addQueryParameter("type", specType)
            .build()
        val req = Request.Builder().url(url).header("User-Agent", agent).build()
        val text = apiHttp.newCall(req).execute().use { it.body?.string().orEmpty() }
        val json = JSONObject(text)
        val out = HashMap<String, IntArray>()
        val services = json.optJSONArray("services") ?: return out
        for (i in 0 until services.length()) {
            val sv = services.getJSONObject(i)
            val siid = sv.optInt("iid")
            val props = sv.optJSONArray("properties") ?: continue
            for (k in 0 until props.length()) {
                val p = props.getJSONObject(k)
                val name = p.optString("type").split(":").getOrNull(3) ?: continue
                val key = when (name) {
                    "temperature" -> "t"
                    "relative-humidity" -> "h"
                    "battery-level" -> "b"
                    else -> continue
                }
                if (!out.containsKey(key)) out[key] = intArrayOf(siid, p.optInt("iid"))
            }
        }
        return out
    }
}
