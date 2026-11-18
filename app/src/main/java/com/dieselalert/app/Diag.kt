package com.dieselalert.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro de eventos de la detección automática (últimos 60) para saber qué pasó. */
object Diag {
    private const val MAX = 60

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("diag", Context.MODE_PRIVATE)

    @Synchronized
    fun log(c: Context, msg: String) {
        val s = sp(c)
        val t = SimpleDateFormat("d MMM HH:mm:ss", Locale("es")).format(Date())
        val lines = (s.getString("log", "") ?: "").split('\n').filter { it.isNotBlank() }.toMutableList()
        lines.add("$t  $msg")
        val keep = if (lines.size > MAX) lines.takeLast(MAX) else lines
        s.edit().putString("log", keep.joinToString("\n")).apply()
    }

    fun read(c: Context): String = sp(c).getString("log", "") ?: ""

    fun clear(c: Context) {
        sp(c).edit().remove("log").apply()
    }

    /** Dispositivos Bluetooth emparejados: (nombre, dirección). */
    @SuppressLint("MissingPermission")
    fun bonded(c: Context): List<Pair<String, String>> = try {
        val bm = c.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        (bm.adapter?.bondedDevices ?: emptySet()).map { Pair(it.name ?: it.address, it.address) }
            .sortedBy { it.first.lowercase() }
    } catch (e: Exception) {
        emptyList()
    }

    // ---- avance del recorrido guardado para sobrevivir si el sistema mata el proceso
    fun saveProgress(c: Context, start: Long, distM: Double, route: String) {
        sp(c).edit().putLong("pStart", start).putFloat("pDist", distM.toFloat())
            .putString("pRoute", route).putLong("pSave", System.currentTimeMillis()).apply()
    }

    fun clearProgress(c: Context) {
        sp(c).edit().remove("pStart").remove("pDist").remove("pRoute").remove("pSave").apply()
    }

    fun hasProgress(c: Context) = sp(c).getLong("pStart", 0L) > 0L
    fun pStart(c: Context) = sp(c).getLong("pStart", 0L)
    fun pDist(c: Context) = sp(c).getFloat("pDist", 0f).toDouble()
    fun pRoute(c: Context) = sp(c).getString("pRoute", "") ?: ""
    fun pSave(c: Context) = sp(c).getLong("pSave", 0L)
}

/** Cierra un recorrido: lo guarda, avisa y envía el resumen. Lo usan el servicio y la recuperación. */
object TripEnd {
    fun commit(c: Context, start: Long, end: Long, km: Double, route: String): Boolean {
        val store = Store(c)
        if (km < 0.3) return false
        val kpl = store.kmPerL()
        val liters = km / kpl
        val cost = liters * store.priceL()
        val trip = Trip(start, end, km, liters, cost, route)
        store.addTrip(trip)
        val t = store.tankL
        val rest = if (t >= 0) "\nQuedan ≈ ${f1(t)} L (≈ ${(t * kpl).toInt()} km)" else ""
        val costTxt = if (cost > 0) " · ≈ ${money(cost)}" else ""
        Notifs.post(
            c, TripService.ID_SUMMARY, "Recorrido terminado",
            "${f1(km)} km · ≈ ${f1(liters)} L (${f1(liters / L_PER_GAL)} gal)$costTxt$rest",
            Notifs.CH_INFO
        )
        if (store.relayTrips) Relay.send(c, "Recorrido terminado", Msg.trip(store, trip), 3, "fuelpump")
        checkLow(c, 0.0)
        Maint.check(c)
        Diag.log(c, "Recorrido guardado: ${f1(km)} km")
        return true
    }

    fun checkLow(c: Context, extraKm: Double) {
        val store = Store(c)
        val t = store.tankL
        if (t < 0 || store.alerted) return
        val kpl = store.kmPerL()
        val rem = t - extraKm / kpl
        if (rem <= store.capacityL * store.lowPct / 100.0) {
            store.alerted = true
            val left = maxOf(0.0, rem)
            Notifs.post(
                c, TripService.ID_ALERT, "Diésel por terminarse",
                "Quedan ≈ ${f1(left)} L (≈ ${(left * kpl).toInt()} km de autonomía)",
                Notifs.CH_ALERT
            )
            if (store.relayAlerts) Relay.send(c, "Diésel por terminarse", Msg.lowFuel(store), 4, "warning")
        }
    }

    /** Si quedó un recorrido a medias (proceso muerto) y ya no hay servicio activo, lo guarda. */
    fun recover(c: Context) {
        if (TripService.active || !Diag.hasProgress(c)) return
        val start = Diag.pStart(c)
        val km = Diag.pDist(c) / 1000.0
        val end = maxOf(Diag.pSave(c), start)
        val route = Diag.pRoute(c)
        Diag.clearProgress(c)
        Diag.log(c, "Recorrido interrumpido recuperado")
        commit(c, start, end, km, route)
    }
}
