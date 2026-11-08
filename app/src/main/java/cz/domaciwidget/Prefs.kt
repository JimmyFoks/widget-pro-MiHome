package cz.domaciwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("domaci_widget", Context.MODE_PRIVATE)

    fun deviceId(ctx: Context): String {
        val p = sp(ctx)
        val cur = p.getString("device_id", null)
        if (cur != null) return cur
        val n = newDeviceId()
        p.edit().putString("device_id", n).apply()
        return n
    }

    fun agentId(ctx: Context): String {
        val p = sp(ctx)
        val cur = p.getString("agent_id", null)
        if (cur != null) return cur
        val n = newAgentId()
        p.edit().putString("agent_id", n).apply()
        return n
    }

    fun region(ctx: Context): String = sp(ctx).getString("region", "de") ?: "de"
    fun user(ctx: Context): String = sp(ctx).getString("user", "") ?: ""
    fun passHash(ctx: Context): String = sp(ctx).getString("pass_hash", "") ?: ""
    fun hasCredentials(ctx: Context): Boolean = user(ctx).isNotEmpty() && passHash(ctx).isNotEmpty()

    fun saveLogin(ctx: Context, region: String, user: String, passHash: String) {
        sp(ctx).edit()
            .putString("region", region)
            .putString("user", user)
            .putString("pass_hash", passHash)
            .apply()
    }

    fun session(ctx: Context): Session? {
        val p = sp(ctx)
        val uid = p.getString("s_uid", null)
        val sec = p.getString("s_sec", null)
        val tok = p.getString("s_tok", null)
        return if (uid != null && sec != null && tok != null) Session(uid, sec, tok) else null
    }

    fun saveSession(ctx: Context, s: Session) {
        sp(ctx).edit()
            .putString("s_uid", s.userId)
            .putString("s_sec", s.ssecurity)
            .putString("s_tok", s.serviceToken)
            .apply()
    }

    // --- widgety ---
    fun widgetDid(ctx: Context, id: Int): String? = sp(ctx).getString("w_did_$id", null)
    fun widgetName(ctx: Context, id: Int): String? = sp(ctx).getString("w_name_$id", null)

    fun setWidget(ctx: Context, id: Int, did: String, name: String) {
        sp(ctx).edit().putString("w_did_$id", did).putString("w_name_$id", name).apply()
    }

    fun widgetDid2(ctx: Context, id: Int): String? = sp(ctx).getString("w_did2_$id", null)
    fun widgetName2(ctx: Context, id: Int): String? = sp(ctx).getString("w_name2_$id", null)

    fun setWidget2(ctx: Context, id: Int, did: String, name: String) {
        sp(ctx).edit().putString("w_did2_$id", did).putString("w_name2_$id", name).apply()
    }

    fun clearWidget2(ctx: Context, id: Int) {
        sp(ctx).edit().remove("w_did2_$id").remove("w_name2_$id").apply()
    }

    fun removeWidget(ctx: Context, id: Int) {
        sp(ctx).edit()
            .remove("w_did_$id").remove("w_name_$id")
            .remove("w_did2_$id").remove("w_name2_$id")
            .apply()
    }

    // --- zařízení ---
    fun saveDevices(ctx: Context, list: List<Device>) {
        val arr = JSONArray()
        for (d in list) {
            arr.put(
                JSONObject()
                    .put("did", d.did)
                    .put("name", d.name)
                    .put("model", d.model)
                    .put("spec", d.specType)
            )
        }
        sp(ctx).edit().putString("devices", arr.toString()).apply()
    }

    fun readDevices(ctx: Context): List<Device> {
        val s = sp(ctx).getString("devices", null) ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Device(o.optString("did"), o.optString("name"), o.optString("model"), o.optString("spec"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- naměřené hodnoty ---
    fun saveReading(ctx: Context, did: String, r: Reading) {
        val o = JSONObject()
        r.temp?.let { o.put("t", it) }
        r.hum?.let { o.put("h", it) }
        r.bat?.let { o.put("b", it) }
        o.put("ts", r.ts)
        sp(ctx).edit().putString("r_$did", o.toString()).apply()
    }

    fun reading(ctx: Context, did: String): Reading? {
        val s = sp(ctx).getString("r_$did", null) ?: return null
        return try {
            val o = JSONObject(s)
            Reading(
                if (o.has("t")) o.getDouble("t") else null,
                if (o.has("h")) o.getDouble("h") else null,
                if (o.has("b")) o.getInt("b") else null,
                o.optLong("ts", 0L)
            )
        } catch (e: Exception) {
            null
        }
    }

    // --- mapa vlastností (siid/piid) ---
    fun specMap(ctx: Context, did: String): String? = sp(ctx).getString("spec_$did", null)
    fun saveSpecMap(ctx: Context, did: String, json: String) {
        sp(ctx).edit().putString("spec_$did", json).apply()
    }

    // --- poslední stav (diagnostika) ---
    fun status(ctx: Context): String = sp(ctx).getString("status", "zatím neproběhla") ?: ""
    fun setStatus(ctx: Context, s: String) {
        sp(ctx).edit().putString("status", s).apply()
    }
}
