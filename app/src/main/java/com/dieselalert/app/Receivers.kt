package com.dieselalert.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

object ActivityTracking {
    @SuppressLint("MissingPermission")
    fun register(ctx: Context) {
        val app = ctx.applicationContext
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Diag.log(app, "Detección de actividad: falta el permiso de Actividad física")
            return
        }
        val t = ActivityTransition.Builder()
            .setActivityType(DetectedActivity.IN_VEHICLE)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build()
        val pi = PendingIntent.getBroadcast(
            app, 0, Intent(app, ActivityReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        ActivityRecognition.getClient(app)
            .requestActivityTransitionUpdates(ActivityTransitionRequest(listOf(t)), pi)
            .addOnSuccessListener { app.getSharedPreferences("diag", Context.MODE_PRIVATE).edit().putBoolean("arOk", true).apply() }
            .addOnFailureListener {
                app.getSharedPreferences("diag", Context.MODE_PRIVATE).edit().putBoolean("arOk", false).apply()
                Diag.log(app, "Detección de actividad NO se pudo registrar: ${it.message}")
            }
    }
}

object Launch {
    fun startTrip(c: Context, src: String, action: String? = null) {
        try {
            val i = Intent(c, TripService::class.java).putExtra(TripService.EXTRA_SRC, src)
            if (action != null) i.action = action
            ContextCompat.startForegroundService(c, i)
        } catch (e: Exception) {
            Diag.log(c, "ERROR no se pudo arrancar ($src): ${e.javaClass.simpleName} ${e.message ?: ""}")
        }
    }
}

class ActivityReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (!ActivityTransitionResult.hasResult(i)) return
        val res = ActivityTransitionResult.extractResult(i) ?: return
        for (e in res.transitionEvents) {
            if (e.activityType == DetectedActivity.IN_VEHICLE &&
                e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
            ) {
                Diag.log(c, "Detectado: entró a un vehículo")
                if (!TripService.active) Launch.startTrip(c, "actividad (en vehículo)")
            }
        }
    }
}

/** Bluetooth del vehículo: al conectarse arranca el recorrido, al desconectarse lo termina. */
class BtReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(c: Context, i: Intent) {
        val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
            i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val addr = dev?.address ?: return
        val store = Store(c)
        if (store.btAddr.isBlank() || !addr.equals(store.btAddr, ignoreCase = true)) return
        when (i.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                Diag.log(c, "Bluetooth del vehículo conectado")
                Launch.startTrip(c, "Bluetooth (${store.btName})", TripService.ACTION_CAR_ON)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                if (TripService.active) Launch.startTrip(c, "Bluetooth", TripService.ACTION_CAR_OFF)
                else Diag.log(c, "Bluetooth del vehículo desconectado")
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Diag.log(c, "Teléfono iniciado o app actualizada")
        ActivityTracking.register(c)
        TripEnd.recover(c)
    }
}
