package com.dieselalert.app

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.car.app.connection.CarConnection
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Observer
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

class TripService : Service() {

    companion object {
        const val ACTION_STOP = "com.dieselalert.app.STOP"
        const val ACTION_CAR_ON = "com.dieselalert.app.CAR_ON"
        const val ACTION_CAR_OFF = "com.dieselalert.app.CAR_OFF"
        const val EXTRA_SRC = "src"
        const val ID_ONGOING = 1
        const val ID_SUMMARY = 2
        const val ID_ALERT = 3
        private const val TICK_MS = 30_000L
        private const val CAR_OFF_MS = 3 * 60_000L
        private const val IDLE_END_MS = 10 * 60_000L
        private const val RESUME_MAX_MS = 20 * 60_000L

        /** Lo lee la pantalla principal para mostrar el estado del recorrido. */
        @Volatile var active = false
        @Volatile var liveKm = 0.0
    }

    private val store by lazy { Store(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var running = false
    private var startMs = 0L
    private var distM = 0.0
    private var last: Location? = null
    private var lastPt: Location? = null
    private val pts = ArrayList<Pair<Double, Double>>()
    private var lastMoveMs = 0L
    private var seenCar = false
    private var aaOn = false
    private var btOn = false
    private var carGoneSince = 0L
    private var carConn: CarConnection? = null

    private fun carPresent() = aaOn || btOn

    private fun markCar() {
        if (carPresent()) {
            seenCar = true
            carGoneSince = 0L
        } else if (seenCar && carGoneSince == 0L) {
            carGoneSince = System.currentTimeMillis()
        }
    }

    private val carObs = Observer<Int> { type ->
        val on = type == CarConnection.CONNECTION_TYPE_PROJECTION
        if (on != aaOn) Diag.log(this, if (on) "Android Auto conectado" else "Android Auto desconectado")
        aaOn = on
        markCar()
    }

    private val locCb = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) {
            for (l in r.locations) onLoc(l)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.currentTimeMillis()
            updateOngoing()
            TripEnd.checkLow(this@TripService, distM / 1000.0)
            Diag.saveProgress(this@TripService, startMs, distM, encodeRoute())
            val carGone = seenCar && !carPresent() && carGoneSince > 0 && now - carGoneSince >= CAR_OFF_MS
            val idleLong = !seenCar && now - lastMoveMs >= IDLE_END_MS
            if (carGone || idleLong) {
                Diag.log(this@TripService, if (carGone) "Fin: el vehículo se desconectó" else "Fin: sin movimiento")
                finish()
            } else handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val act = intent?.action
        val src = intent?.getStringExtra(EXTRA_SRC) ?: "desconocido"
        when (act) {
            ACTION_STOP -> {
                if (running) finish() else stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CAR_OFF -> {
                if (running) {
                    btOn = false
                    markCar()
                    Diag.log(this, "Bluetooth del vehículo desconectado")
                } else stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CAR_ON -> {
                if (running) {
                    btOn = true
                    seenCar = true
                    carGoneSince = 0L
                    return START_NOT_STICKY
                }
                btOn = true
            }
        }
        if (!running) startTrip(src, act == ACTION_CAR_ON)
        return START_NOT_STICKY
    }

    private fun startTrip(src: String, byBt: Boolean) {
        Notifs.ensureChannels(this)
        try {
            ServiceCompat.startForeground(
                this, ID_ONGOING,
                Notifs.build(this, Notifs.CH_TRIP, "Registrando recorrido", "Iniciando…", true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: Exception) {
            Diag.log(this, "ERROR al iniciar servicio: ${e.javaClass.simpleName} ${e.message ?: ""}")
            stopSelf()
            return
        }
        // ¿Hay un recorrido interrumpido muy reciente? Se continúa; si es viejo se guarda aparte.
        val now = System.currentTimeMillis()
        if (Diag.hasProgress(this) && now - Diag.pSave(this) <= RESUME_MAX_MS) {
            startMs = Diag.pStart(this)
            distM = Diag.pDist(this)
            pts.clear()
            pts.addAll(parsePts(Diag.pRoute(this)))
            Diag.log(this, "Recorrido continuado (${f1(distM / 1000.0)} km)")
        } else {
            if (Diag.hasProgress(this)) TripEnd.recover(this)
            startMs = now
            distM = 0.0
            pts.clear()
        }
        running = true
        active = true
        liveKm = distM / 1000.0
        last = null
        lastPt = null
        lastMoveMs = now
        seenCar = byBt
        aaOn = false
        carGoneSince = 0L
        Diag.saveProgress(this, startMs, distM, encodeRoute())
        Diag.log(this, "Recorrido iniciado por: $src")

        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateDistanceMeters(0f)
            .build()
        try {
            fused.requestLocationUpdates(req, locCb, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Diag.log(this, "ERROR: sin permiso de ubicación (¿falta \"Permitir todo el tiempo\"?)")
            running = false
            active = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val cc = CarConnection(this)
        carConn = cc
        cc.type.observeForever(carObs)
        handler.postDelayed(tick, TICK_MS)
    }

    private fun addPoint(l: Location, force: Boolean = false) {
        val p = lastPt
        if (p == null || force || p.distanceTo(l) >= 30f) {
            pts.add(Pair(l.latitude, l.longitude))
            lastPt = l
        }
    }

    private fun onLoc(l: Location) {
        if (l.hasAccuracy() && l.accuracy > 30f) return
        val now = System.currentTimeMillis()
        val p = last
        if (p == null) {
            last = l
            lastMoveMs = now
            addPoint(l)
            return
        }
        val dt = (l.time - p.time) / 1000.0
        if (dt <= 0) return
        val d = p.distanceTo(l).toDouble()
        val v = d / dt
        // Con el vehículo detenido el GPS "baila" unos metros: solo cuenta si hay velocidad real.
        val moving = if (l.hasSpeed()) l.speed >= 1.2f else d >= 12
        if (v > 50) {
            last = l
        } else if (moving && d >= 4) {
            distM += d
            last = l
            lastMoveMs = now
            liveKm = distM / 1000.0
            addPoint(l)
        } else if (!moving) {
            // no se mueve: se actualiza la referencia sin sumar distancia
            if (dt > 20) last = l
        }
    }

    private fun encodeRoute(): String {
        if (pts.size < 2) return ""
        val max = 120
        val list = if (pts.size <= max) pts.toList()
        else List(max) { i -> pts[(i.toLong() * (pts.size - 1) / (max - 1)).toInt()] }
        return list.joinToString(";") { String.format(Locale.US, "%.5f,%.5f", it.first, it.second) }
    }

    private fun updateOngoing() {
        val km = distM / 1000.0
        val liters = km / store.kmPerL()
        Notifs.post(
            this, ID_ONGOING, "Registrando recorrido",
            "${f1(km)} km · ≈ ${f1(liters)} L", Notifs.CH_TRIP, true
        )
    }

    private fun finish() {
        if (!running) {
            stopSelf()
            return
        }
        running = false
        active = false
        handler.removeCallbacksAndMessages(null)
        try { fused.removeLocationUpdates(locCb) } catch (_: Exception) {}
        try { carConn?.type?.removeObserver(carObs) } catch (_: Exception) {}
        val km = distM / 1000.0
        last?.let { addPoint(it, true) }
        val saved = TripEnd.commit(this, startMs, System.currentTimeMillis(), km, encodeRoute())
        if (!saved) Diag.log(this, "Recorrido descartado (${f1(km)} km, menos de 0.3 km)")
        Diag.clearProgress(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) finish()
        active = false
        super.onDestroy()
    }
}
