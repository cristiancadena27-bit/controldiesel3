package com.dieselalert.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

const val L_PER_GAL = 3.78541

fun f1(x: Double): String = "%.1f".format(Locale.US, x)
fun f2(x: Double): String = "%.2f".format(Locale.US, x)
fun money(x: Double): String = "$" + "%.2f".format(Locale.US, x)

fun parsePts(s: String): List<Pair<Double, Double>> {
    if (s.isBlank()) return emptyList()
    return s.split(';').mapNotNull {
        val a = it.split(',')
        val la = a.getOrNull(0)?.toDoubleOrNull()
        val lo = a.getOrNull(1)?.toDoubleOrNull()
        if (la != null && lo != null) Pair(la, lo) else null
    }
}

data class Fill(
    val ts: Long,
    val liters: Double,
    val cost: Double,
    val full: Boolean,
    val dashOdo: Double?,
    val gpsKm: Double
) {
    fun toJson(): JSONObject = JSONObject()
        .put("ts", ts).put("l", liters).put("c", cost).put("f", full).put("gk", gpsKm)
        .also { if (dashOdo != null) it.put("odo", dashOdo) }
}

data class Trip(
    val start: Long,
    val end: Long,
    val km: Double,
    val liters: Double,
    val cost: Double,
    val pts: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("s", start).put("e", end).put("km", km).put("l", liters).put("c", cost)
        .also { if (pts.isNotEmpty()) it.put("p", pts) }
}

data class MItem(
    val id: String,
    val name: String,
    val intervalKm: Double,
    val lastKm: Double,   // km del tablero cuando se hizo; -1 si no hay dato
    val lastTs: Long,
    val level: Int        // 0 nada avisado, 1 aviso previo enviado, 2 vencido enviado
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("n", name).put("i", intervalKm).put("l", lastKm).put("t", lastTs).put("v", level)
}

class Store(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("diesel", Context.MODE_PRIVATE)

    private fun getD(k: String, d: Double): Double =
        if (sp.contains(k)) java.lang.Double.longBitsToDouble(sp.getLong(k, 0L)) else d

    private fun putD(k: String, v: Double) {
        sp.edit().putLong(k, java.lang.Double.doubleToRawLongBits(v)).apply()
    }

    private fun getS(k: String): String = sp.getString(k, "") ?: ""
    private fun putS(k: String, v: String) {
        sp.edit().putString(k, v).apply()
    }

    var capacityL: Double
        get() = getD("cap", 76.0)
        set(v) = putD("cap", v)

    var baseKmPerL: Double
        get() = getD("base", 9.0)
        set(v) = putD("base", v)

    var lowPct: Double
        get() = getD("low", 20.0)
        set(v) = putD("low", v)

    var tankL: Double
        get() = getD("tank", -1.0)
        set(v) = putD("tank", v)

    var totalKm: Double
        get() = getD("km", 0.0)
        set(v) = putD("km", v)

    var pricePerL: Double
        get() = getD("price", 0.0)
        set(v) = putD("price", v)

    var pricePerGal: Double
        get() = getD("pgal", 0.0)
        set(v) = putD("pgal", v)

    var alerted: Boolean
        get() = sp.getBoolean("alerted", false)
        set(v) = sp.edit().putBoolean("alerted", v).apply()

    // ---- avisos a otros celulares (ntfy)
    var relayOn: Boolean
        get() = sp.getBoolean("rOn", false)
        set(v) = sp.edit().putBoolean("rOn", v).apply()

    var relayTrips: Boolean
        get() = sp.getBoolean("rTrips", true)
        set(v) = sp.edit().putBoolean("rTrips", v).apply()

    var relayAlerts: Boolean
        get() = sp.getBoolean("rAlerts", true)
        set(v) = sp.edit().putBoolean("rAlerts", v).apply()

    var relayServer: String
        get() = getS("rSrv").ifBlank { "https://ntfy.sh" }
        set(v) = putS("rSrv", v)

    var relayTopic: String
        get() = getS("rTopic")
        set(v) = putS("rTopic", v)

    var relayStatus: String
        get() = getS("rStatus")
        set(v) = putS("rStatus", v)

    var relayQueue: String
        get() = sp.getString("rQueue", "[]") ?: "[]"
        set(v) = putS("rQueue", v)

    // ---- detección automática
    var btAddr: String
        get() = getS("btAddr")
        set(v) = putS("btAddr", v)

    var btName: String
        get() = getS("btName")
        set(v) = putS("btName", v)

    // ---- mantenimiento
    var warnKm: Double
        get() = getD("warnKm", 500.0)
        set(v) = putD("warnKm", v)

    private var dashAnchor: Double
        get() = getD("dashA", -1.0)
        set(v) = putD("dashA", v)

    private var gpsAnchor: Double
        get() = getD("gpsA", 0.0)
        set(v) = putD("gpsA", v)

    /** Km estimados del tablero: el último valor que escribiste + lo que el GPS contó desde entonces. */
    fun dashKm(): Double? = if (dashAnchor < 0) null else dashAnchor + (totalKm - gpsAnchor)

    fun setDashKm(km: Double) {
        dashAnchor = km
        gpsAnchor = totalKm
    }

    fun maintItems(): List<MItem> {
        val raw = sp.getString("maint", null)
        if (raw == null) {
            val def = listOf(
                MItem("aceite", "Cambio de aceite", 5000.0, -1.0, 0L, 0),
                MItem("f_aceite", "Filtro de aceite", 5000.0, -1.0, 0L, 0),
                MItem("f_aire", "Filtro de aire", 15000.0, -1.0, 0L, 0),
                MItem("f_comb", "Filtro de combustible", 10000.0, -1.0, 0L, 0),
                MItem("balanceo", "Balanceo", 10000.0, -1.0, 0L, 0),
                MItem("alineacion", "Alineación", 10000.0, -1.0, 0L, 0)
            )
            saveMaint(def)
            return def
        }
        val a = JSONArray(raw)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            MItem(o.getString("id"), o.getString("n"), o.getDouble("i"), o.getDouble("l"), o.getLong("t"), o.getInt("v"))
        }
    }

    fun saveMaint(list: List<MItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("maint", arr.toString()).apply()
    }

    /** Km que faltan para el próximo cambio; null si falta algún dato. Negativo = vencido. */
    fun maintRemaining(m: MItem): Double? {
        val d = dashKm() ?: return null
        if (m.lastKm < 0) return null
        return m.lastKm + m.intervalKm - d
    }

    var vehName: String
        get() = getS("vn")
        set(v) = putS("vn", v)

    var vehModel: String
        get() = getS("vm")
        set(v) = putS("vm", v)

    var vehYear: String
        get() = getS("vy")
        set(v) = putS("vy", v)

    var vehPlate: String
        get() = getS("vp")
        set(v) = putS("vp", v)

    var vehNotes: String
        get() = getS("vnotes")
        set(v) = putS("vnotes", v)

    /** Precio por litro: el de la última carga; si no hay, el que el usuario puso en Ajustes. */
    fun priceL(): Double = if (pricePerL > 0) pricePerL else pricePerGal / L_PER_GAL

    fun tripCost(t: Trip): Double = if (t.cost > 0) t.cost else t.liters * priceL()

    fun fills(): List<Fill> {
        val a = JSONArray(sp.getString("fills", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Fill(
                o.getLong("ts"), o.getDouble("l"), o.getDouble("c"), o.getBoolean("f"),
                if (o.has("odo")) o.getDouble("odo") else null, o.getDouble("gk")
            )
        }
    }

    fun trips(): List<Trip> {
        val a = JSONArray(sp.getString("trips", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Trip(
                o.getLong("s"), o.getLong("e"), o.getDouble("km"), o.getDouble("l"), o.getDouble("c"),
                if (o.has("p")) o.getString("p") else ""
            )
        }
    }

    fun addFill(liters: Double, cost: Double, full: Boolean, dashOdo: Double?) {
        val list = (fills() + Fill(System.currentTimeMillis(), liters, cost, full, dashOdo, totalKm)).takeLast(200)
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("fills", arr.toString()).apply()
        if (dashOdo != null && dashOdo > 0) setDashKm(dashOdo)
        tankL = if (full) capacityL else minOf(capacityL, (if (tankL < 0) 0.0 else tankL) + liters)
        pricePerL = cost / liters
        alerted = false
    }

    fun addTrip(t: Trip) {
        val list = (trips() + t).takeLast(300)
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("trips", arr.toString()).apply()
        totalKm = totalKm + t.km
        if (tankL >= 0) tankL = maxOf(0.0, tankL - t.liters)
    }

    fun setLevelFraction(frac: Double) {
        tankL = capacityL * frac.coerceIn(0.0, 1.0)
        alerted = false
    }

    fun kmSinceLastFill(): Double = totalKm - (fills().lastOrNull()?.gpsKm ?: 0.0)

    fun kmPerL(): Double {
        val f = fills().filter { it.full }.sortedBy { it.ts }
        if (f.size >= 2) {
            val a = f[f.size - 2]
            val b = f.last()
            val km = if (a.dashOdo != null && b.dashOdo != null) b.dashOdo - a.dashOdo else b.gpsKm - a.gpsKm
            if (km >= 50 && b.liters > 0) {
                val v = km / b.liters
                if (v in 3.0..25.0) return v
            }
        }
        return baseKmPerL
    }
}
