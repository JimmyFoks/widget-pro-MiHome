package cz.domaciwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SensorService(private val ctx: Context) {

    private fun region(): String = Prefs.region(ctx)

    private fun login(): Session {
        val s = MiCloud(region(), Prefs.deviceId(ctx), Prefs.agentId(ctx)).login(Prefs.user(ctx), Prefs.passHash(ctx))
        Prefs.saveSession(ctx, s)
        return s
    }

    /** Zkusí uložený token; když vyprší, jednou se znovu přihlásí. */
    private fun <T> withSession(block: (MiCloud, Session) -> T): T {
        val cloud = MiCloud(region())
        val first = Prefs.session(ctx)
        if (first != null) {
            try {
                return block(cloud, first)
            } catch (e: MiCloudException) {
                if (!e.auth) throw e
            }
        }
        val fresh = login()
        return block(cloud, fresh)
    }

    fun listDevices(): List<Device> = withSession { c, s -> c.listDevices(s) }

    private fun defaultMap(): Map<String, IntArray> = mapOf(
        "t" to intArrayOf(2, 1),
        "h" to intArrayOf(2, 2),
        "b" to intArrayOf(3, 1)
    )

    private fun mapToJson(map: Map<String, IntArray>): String {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, JSONArray().put(v[0]).put(v[1]))
        return o.toString()
    }

    private fun jsonToMap(json: String): Map<String, IntArray> {
        val o = JSONObject(json)
        val out = HashMap<String, IntArray>()
        for (k in listOf("t", "h", "b")) {
            val a = o.optJSONArray(k) ?: continue
            out[k] = intArrayOf(a.getInt(0), a.getInt(1))
        }
        return out
    }

    private fun propMap(cloud: MiCloud, did: String): Map<String, IntArray> {
        val cached = Prefs.specMap(ctx, did)
        if (cached != null) {
            try {
                val m = jsonToMap(cached)
                if (m.containsKey("t")) return m
            } catch (e: Exception) {
            }
        }
        val dev = Prefs.readDevices(ctx).firstOrNull { it.did == did }
        var map: Map<String, IntArray> = emptyMap()
        if (dev != null && dev.specType.isNotEmpty()) {
            try {
                map = cloud.fetchSpecProps(dev.specType)
            } catch (e: Exception) {
            }
        }
        if (map.containsKey("t")) {
            Prefs.saveSpecMap(ctx, did, mapToJson(map))
            return map
        }
        return defaultMap()
    }

    fun fetchReading(did: String): Reading = withSession { cloud, s ->
        val map = propMap(cloud, did)
        val params = JSONArray()
        for ((_, v) in map) {
            params.put(JSONObject().put("did", did).put("siid", v[0]).put("piid", v[1]))
        }
        val res = cloud.call(
            s, "/miotspec/prop/get",
            JSONObject().put("params", params).put("datasource", 1)
        )
        val arr = res.optJSONArray("result")
            ?: throw MiCloudException("Cloud nevrátil data: " + res.toString().take(200))

        var t: Double? = null
        var h: Double? = null
        var b: Int? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optInt("code", 0) != 0 || !o.has("value")) continue
            val siid = o.optInt("siid")
            val piid = o.optInt("piid")
            val key = map.entries.firstOrNull { it.value[0] == siid && it.value[1] == piid }?.key ?: continue
            val value = o.optDouble("value")
            if (key == "t") t = value
            if (key == "h") h = value
            if (key == "b") b = value.toInt()
        }
        if (t == null && h == null) {
            throw MiCloudException("Žádné hodnoty z cloudu: " + arr.toString().take(300))
        }
        Reading(t, h, b, System.currentTimeMillis())
    }
}
