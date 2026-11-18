package com.dieselalert.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Textos de los avisos (se usan en las notificaciones, en "Compartir" y en el envío a otros celulares). */
object Msg {
    private fun name(s: Store): String = s.vehName.ifBlank { "Mi vehículo" }

    fun levelLine(s: Store): String? {
        val t = s.tankL
        if (t < 0) return null
        val kpl = s.kmPerL()
        return "Nivel restante: ≈ ${f1(t)} L de ${f1(s.capacityL)} L (≈ ${(t * kpl).toInt()} km de autonomía)"
    }

    fun trip(s: Store, t: Trip): String {
        val df = SimpleDateFormat("d MMM yyyy HH:mm", Locale("es"))
        val cost = s.tripCost(t)
        val lines = mutableListOf(
            name(s),
            "Recorrido del ${df.format(Date(t.start))}",
            "Distancia: ${f1(t.km)} km",
            "Diésel gastado: ≈ ${f1(t.liters)} L (${f1(t.liters / L_PER_GAL)} gal)"
        )
        if (cost > 0) lines.add("Costo: ≈ ${money(cost)}")
        levelLine(s)?.let { lines.add(it) }
        return lines.joinToString("\n")
    }

    fun lowFuel(s: Store): String = listOfNotNull("${name(s)}: diésel por terminarse", levelLine(s)).joinToString("\n")

    fun maint(s: Store, m: MItem, remaining: Double): String {
        val what = if (remaining <= 0) "¡${m.name} vencido hace ${fmtKm(-remaining)} km!"
        else "${m.name}: faltan ${fmtKm(remaining)} km"
        return "${name(s)}\n$what"
    }

    fun fmtKm(x: Double): String = "%,d".format(Locale.US, x.toLong()).replace(',', ' ')
}
