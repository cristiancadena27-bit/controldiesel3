package com.dieselalert.app

import android.Manifest
import android.os.Build
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF0A3A73)
private val Sky = Color(0xFF1E88E5)
private val Green = Color(0xFF1FA971)
private val Amber = Color(0xFFF59E0B)
private val Red = Color(0xFFE53935)

private fun String.num(): Double? = replace(',', '.').trim().toDoubleOrNull()

private fun granted(c: Context, p: String) =
    ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

private fun dateStr(ts: Long): String = SimpleDateFormat("d MMM · HH:mm", Locale("es")).format(Date(ts))

// ---------------------------------------------------------------- componentes

@Composable
fun NumField(label: String, v: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = v,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun TextField2(label: String, v: String, onChange: (String) -> Unit, lines: Int = 1, kb: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = v,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = lines == 1,
        minLines = lines,
        keyboardOptions = KeyboardOptions(keyboardType = kb),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------------------------------------------------------------- Inicio

@Composable
fun HomeScreen(store: Store, tick: Int, onChange: () -> Unit, toast: (String) -> Unit) {
    val ctx = LocalContext.current
    var pulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            pulse++
        }
    }
    @Suppress("UNUSED_VARIABLE") val refresh = tick + pulse

    var showFill by remember { mutableStateOf(false) }
    var showLevel by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    var markDone by remember { mutableStateOf<MItem?>(null) }

    val tank = store.tankL
    val kpl = store.kmPerL()
    val cap = store.capacityL
    val ppl = store.priceL()
    val frac = if (tank < 0) 0.0 else (tank / cap).coerceIn(0.0, 1.0)
    val gauge = when {
        frac > 0.5 -> Green
        frac > store.lowPct / 100.0 -> Amber
        else -> Red
    }
    val title = store.vehName.ifBlank { "Mi vehículo" }
    val sub = listOf(store.vehModel, store.vehYear, store.vehPlate).filter { it.isNotBlank() }.joinToString(" · ")
    val trips = store.trips()

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Tarjeta principal
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Navy, Sky)))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (sub.isNotEmpty()) Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                if (tank < 0) {
                    Text("Registra tu primera carga o ajusta el nivel", color = Color.White, fontSize = 16.sp)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(f1(tank), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                        Text(
                            " L de ${f1(cap)} L",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(frac.toFloat().coerceAtLeast(0.02f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(gauge)
                        )
                    }
                    Text(
                        "Autonomía aproximada: ${(tank * kpl).toInt()} km",
                        color = Color.White, fontSize = 15.sp
                    )
                }
            }
        }

        if (TripService.active) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFDDF5EA))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("● ", color = Green, fontSize = 18.sp)
                Text(
                    "Registrando recorrido: ${f1(TripService.liveKm)} km",
                    color = Color(0xFF0B5A3C), fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Estadísticas
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Consumo", "${f1(kpl)} km/L", Modifier.weight(1f))
            StatTile("Costo por km", if (ppl > 0) money(ppl / kpl) else "—", Modifier.weight(1f))
            StatTile("Precio galón", if (ppl > 0) money(ppl * L_PER_GAL) else "—", Modifier.weight(1f))
        }
        Text(
            "Desde la última carga: ${f1(store.kmSinceLastFill())} km",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
        )
        if (ppl <= 0) {
            Text(
                "Para ver el costo en dólares registra una carga o pon el precio por galón en Ajustes.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
            )
        }

        // Mantenimiento
        val dash = store.dashKm()
        val due = store.maintItems().mapNotNull { m -> store.maintRemaining(m)?.let { Pair(m, it) } }.sortedBy { it.second }
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mantenimiento", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (dash != null) {
                        Text(
                            "Tablero ≈ ${Msg.fmtKm(dash)} km",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (dash == null) {
                    Text(
                        "Escribe el km del tablero en Ajustes > Mantenimiento para ver cuánto falta.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (due.isEmpty()) {
                    Text(
                        "Anota el km del último cambio de cada ítem en Ajustes > Mantenimiento.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                due.take(3).forEach { (m, rem) ->
                    val col = when {
                        rem <= 0 -> Red
                        rem <= store.warnKm -> Amber
                        else -> Green
                    }
                    val txt = if (rem <= 0) "vencido hace ${Msg.fmtKm(-rem)} km" else "faltan ${Msg.fmtKm(rem)} km"
                    val frac = (rem / m.intervalKm).coerceIn(0.0, 1.0).toFloat()
                    Column(Modifier.fillMaxWidth().clickable { markDone = m }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(m.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(txt, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (rem <= 0) Red else Color(0xFF14202E))
                        }
                        Box(
                            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE3EBF5))
                        ) {
                            Box(
                                Modifier.fillMaxWidth(frac.coerceAtLeast(0.03f)).fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp)).background(col)
                            )
                        }
                    }
                }
                if (due.isNotEmpty()) {
                    Text("Toca un ítem para marcarlo como hecho hoy", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // Botones
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { showFill = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber, contentColor = Color(0xFF3A2500)
                )
            ) { Text("⛽ Cargué diésel") }
            FilledTonalButton(onClick = { showLevel = true }, modifier = Modifier.weight(1f)) {
                Text("🎚 Ajustar nivel")
            }
        }
        if (!TripService.active) {
            Button(
                onClick = {
                    try {
                        Launch.startTrip(ctx, "botón manual")
                        toast("▶ Recorrido iniciado")
                    } catch (_: Exception) {
                        toast("No se pudo iniciar. Revisa los permisos en Ajustes")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) { Text("▶ Iniciar recorrido") }
        } else {
            Button(
                onClick = {
                    ctx.startService(Intent(ctx, TripService::class.java).setAction(TripService.ACTION_STOP))
                    toast("■ Recorrido terminado")
                    onChange()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Red)
            ) { Text("■ Terminar recorrido") }
        }

        // Último recorrido con imagen
        val last = trips.lastOrNull()
        if (last != null) {
            Text("Último recorrido", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            RouteMap(parsePts(last.pts), dateStr(last.start), last.km, last.liters, store.tripCost(last), 180)
        }
    }

    if (showFill) {
        FillDialog(
            onDismiss = { showFill = false },
            onSave = { liters, cost, full, odo ->
                store.addFill(liters, cost, full, odo)
                showFill = false
                done = "Carga registrada:\n${f1(liters / L_PER_GAL)} gal (${f1(liters)} L) por ${money(cost)}\n" +
                    "Precio: ${money(cost / (liters / L_PER_GAL))} por galón" +
                    (if (full) "\nTanque lleno" else "")
                onChange()
            }
        )
    }
    if (showLevel) {
        LevelDialog(
            initial = if (store.tankL < 0) 0.5f else (store.tankL / store.capacityL).toFloat().coerceIn(0f, 1f),
            onDismiss = { showLevel = false },
            onSave = {
                store.setLevelFraction(it.toDouble())
                showLevel = false
                toast("✓ Nivel ajustado a ${(it * 100).toInt()} %")
                onChange()
            }
        )
    }
    markDone?.let { m ->
        val d = store.dashKm()
        AlertDialog(
            onDismissRequest = { markDone = null },
            title = { Text(m.name) },
            text = { Text("¿Registrar como hecho hoy" + (if (d != null) " a ${Msg.fmtKm(d)} km?" else "?")) },
            confirmButton = {
                TextButton(onClick = {
                    if (d != null) {
                        store.saveMaint(
                            store.maintItems().map {
                                if (it.id == m.id) it.copy(lastKm = d, lastTs = System.currentTimeMillis(), level = 0) else it
                            }
                        )
                        toast("✓ ${m.name} registrado")
                        onChange()
                    }
                    markDone = null
                }) { Text("Sí, hecho") }
            },
            dismissButton = { TextButton(onClick = { markDone = null }) { Text("Cancelar") } }
        )
    }
    done?.let { msg ->
        AlertDialog(
            onDismissRequest = { done = null },
            title = { Text("✓ Listo") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { done = null }) { Text("OK") } }
        )
    }
}

@Composable
fun FillDialog(onDismiss: () -> Unit, onSave: (Double, Double, Boolean, Double?) -> Unit) {
    var qty by remember { mutableStateOf("") }
    var gal by remember { mutableStateOf(true) }
    var cost by remember { mutableStateOf("") }
    var odo by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    val q = qty.num()
    val c = cost.num()
    val perGal = if (q != null && q > 0 && c != null && c > 0) c / (if (gal) q else q / L_PER_GAL) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar carga") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = gal, onClick = { gal = true }, label = { Text("Galones") })
                    FilterChip(selected = !gal, onClick = { gal = false }, label = { Text("Litros") })
                }
                NumField(if (gal) "Galones cargados" else "Litros cargados", qty) { qty = it }
                NumField("Total pagado ($)", cost) { cost = it }
                NumField("Km del tablero (opcional)", odo) { odo = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = full, onCheckedChange = { full = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Llené el tanque")
                }
                if (perGal != null) {
                    Text("Precio: ${money(perGal)} por galón", color = MaterialTheme.colorScheme.primary)
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (q == null || q <= 0 || c == null || c <= 0) {
                    err = "Escribe la cantidad cargada y el total pagado"
                } else {
                    onSave(q * (if (gal) L_PER_GAL else 1.0), c, full, odo.num())
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun LevelDialog(initial: Float, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nivel según la aguja") },
        text = {
            Column {
                Text("${(v * 100).toInt()} % del tanque")
                Slider(value = v, onValueChange = { v = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v) }) { Text("Aplicar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ---------------------------------------------------------------- Historial

@Composable
fun HistoryScreen(store: Store, tick: Int, toast: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val refresh = tick
    var seg by remember { mutableIntStateOf(0) }
    var openTs by remember { mutableLongStateOf(-1L) }
    val trips = store.trips().reversed()
    val fills = store.fills().reversed()

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Historial", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = seg == 0, onClick = { seg = 0 }, label = { Text("Viajes (${trips.size})") })
            FilterChip(selected = seg == 1, onClick = { seg = 1 }, label = { Text("Cargas (${fills.size})") })
        }
        if (seg == 0) {
            if (trips.isEmpty()) Text("Aún no hay recorridos.")
            else {
                val km = trips.sumOf { it.km }
                val l = trips.sumOf { it.liters }
                val c = trips.sumOf { store.tripCost(it) }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Total registrado", fontSize = 12.sp)
                        Text(
                            "${f1(km)} km · ≈ ${f1(l)} L" + (if (c > 0) " · ≈ ${money(c)}" else ""),
                            fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                    }
                }
            }
            trips.take(60).forEach { t ->
                TripCard(store, t, openTs == t.start, { openTs = if (openTs == t.start) -1L else t.start }, toast)
            }
        } else {
            if (fills.isEmpty()) Text("Aún no hay cargas.")
            fills.forEach { f ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dateStr(f.ts), fontWeight = FontWeight.SemiBold)
                            Text(money(f.cost), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("${f1(f.liters / L_PER_GAL)} gal (${f1(f.liters)} L)")
                        Text(
                            "Precio: ${money(f.cost / (f.liters / L_PER_GAL))} por galón" +
                                (if (f.full) " · tanque lleno" else "") +
                                (f.dashOdo?.let { " · tablero ${it.toInt()} km" } ?: ""),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TripCard(store: Store, t: Trip, expanded: Boolean, onToggle: () -> Unit, toast: (String) -> Unit) {
    val ctx = LocalContext.current
    val cost = store.tripCost(t)
    val msg = Msg.trip(store, t)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(dateStr(t.start), fontWeight = FontWeight.SemiBold)
                    Text(
                        "${f1(t.km)} km · ≈ ${f1(t.liters)} L",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (cost > 0) money(cost) else "—",
                    fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                RouteMap(parsePts(t.pts), dateStr(t.start), t.km, t.liters, cost, 220)
                Button(
                    onClick = {
                        try {
                            ctx.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg),
                                    "Compartir resumen"
                                )
                            )
                        } catch (_: Exception) {
                            toast("No se pudo compartir")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) { Text("Compartir resumen") }
            } else {
                Text("Toca para ver el recorrido", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ---------------------------------------------------------------- Ajustes

@Composable
fun Section(
    icon: String, title: String, subtitle: String, open: Boolean, onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (open) "▴" else "▾", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (open) content()
        }
    }
}

@Composable
fun SettingsScreen(store: Store, tick: Int, onChange: () -> Unit, toast: (String) -> Unit) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val refresh = tick
    var open by remember { mutableIntStateOf(-1) }

    var vn by remember { mutableStateOf(store.vehName) }
    var vm by remember { mutableStateOf(store.vehModel) }
    var vy by remember { mutableStateOf(store.vehYear) }
    var vp by remember { mutableStateOf(store.vehPlate) }
    var vnotes by remember { mutableStateOf(store.vehNotes) }
    var cap by remember { mutableStateOf(f1(store.capacityL)) }
    var base by remember { mutableStateOf(f1(store.baseKmPerL)) }
    var low by remember { mutableStateOf(f1(store.lowPct)) }
    var pgal by remember { mutableStateOf(if (store.pricePerGal > 0) f2(store.pricePerGal) else "") }

    var dashTxt by remember { mutableStateOf(store.dashKm()?.let { "%.0f".format(Locale.US, it) } ?: "") }
    var warn by remember { mutableStateOf("%.0f".format(Locale.US, store.warnKm)) }
    var items by remember { mutableStateOf(store.maintItems()) }
    var editItem by remember { mutableStateOf<MItem?>(null) }
    var editIsNew by remember { mutableStateOf(false) }

    var rOn by remember { mutableStateOf(store.relayOn) }
    var rTrips by remember { mutableStateOf(store.relayTrips) }
    var rAlerts by remember { mutableStateOf(store.relayAlerts) }
    var rServer by remember { mutableStateOf(store.relayServer) }
    var rTopic by remember { mutableStateOf(store.relayTopic) }
    var rStatus by remember { mutableStateOf(store.relayStatus) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            rStatus = store.relayStatus
        }
    }

    fun persist(n: List<MItem>) {
        items = n
        store.saveMaint(n)
        Maint.check(ctx)
        onChange()
    }

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ActivityTracking.register(ctx)
        onChange()
    }
    val bg = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChange() }
    var btName by remember { mutableStateOf(store.btName) }
    var btDlg by remember { mutableStateOf(false) }
    var logTxt by remember { mutableStateOf(Diag.read(ctx)) }
    val btPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) btDlg = true else toast("Sin permiso de Bluetooth no se puede elegir el dispositivo")
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Ajustes", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Section("🚛", "Vehículo", "Nombre, modelo, placa y características", open == 0, { open = if (open == 0) -1 else 0 }) {
            TextField2("Nombre del vehículo", vn, { vn = it })
            TextField2("Marca y modelo", vm, { vm = it })
            TextField2("Año", vy, { vy = it }, kb = KeyboardType.Number)
            TextField2("Placa", vp, { vp = it })
            NumField("Capacidad del tanque (L)", cap) { cap = it }
            TextField2("Características (motor, cabina, notas)", vnotes, { vnotes = it }, lines = 3)
            Button(onClick = {
                store.vehName = vn.trim()
                store.vehModel = vm.trim()
                store.vehYear = vy.trim()
                store.vehPlate = vp.trim()
                store.vehNotes = vnotes.trim()
                cap.num()?.takeIf { it > 0 }?.let { store.capacityL = it }
                toast("✓ Datos del vehículo guardados")
                onChange()
            }) { Text("Guardar vehículo") }
        }

        Section("⛽", "Combustible y alertas", "Rendimiento, precio y aviso de poco diésel", open == 1, { open = if (open == 1) -1 else 1 }) {
            NumField("Rendimiento inicial (km/L)", base) { base = it }
            NumField("Alerta al quedar (% del tanque)", low) { low = it }
            NumField("Precio por galón ($, si aún no registras cargas)", pgal) { pgal = it }
            Button(onClick = {
                base.num()?.takeIf { it > 0 }?.let { store.baseKmPerL = it }
                low.num()?.takeIf { it in 1.0..90.0 }?.let { store.lowPct = it }
                store.pricePerGal = pgal.num()?.takeIf { it > 0 } ?: 0.0
                toast("✓ Ajustes guardados")
                onChange()
            }) { Text("Guardar ajustes") }
        }

        Section("🔧", "Mantenimiento", "Aceite, filtros, balanceo y alineación", open == 2, { open = if (open == 2) -1 else 2 }) {
            Text(
                "La app cuenta los km con el GPS y te avisa cuando falta poco para cada cambio. " +
                    "Escribe el km actual del tablero y, en cada ítem, el km al que lo hiciste la última vez.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NumField("Km actual del tablero", dashTxt) { dashTxt = it }
            NumField("Avisar con (km de anticipación)", warn) { warn = it }
            Button(onClick = {
                val d = dashTxt.num()
                if (d != null && d > 0) store.setDashKm(d)
                warn.num()?.takeIf { it > 0 }?.let { store.warnKm = it }
                Maint.check(ctx)
                toast("✓ Kilometraje guardado")
                onChange()
            }) { Text("Guardar kilometraje") }

            items.forEach { m ->
                val rem = store.maintRemaining(m)
                val info = when {
                    m.lastKm < 0 -> "Sin dato del último cambio"
                    rem == null -> "Falta el km del tablero"
                    rem <= 0 -> "Vencido hace ${Msg.fmtKm(-rem)} km"
                    else -> "Faltan ${Msg.fmtKm(rem)} km"
                }
                val col = when {
                    rem == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    rem <= 0 -> Red
                    rem <= store.warnKm -> Color(0xFFB36B00)
                    else -> Color(0xFF0B7A52)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Cada ${Msg.fmtKm(m.intervalKm)} km" +
                                (if (m.lastKm >= 0) " · último a ${Msg.fmtKm(m.lastKm)} km" else ""),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(info, fontSize = 13.sp, color = col, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = {
                        val d = store.dashKm()
                        if (d == null) toast("Primero escribe el km actual del tablero")
                        else {
                            persist(items.map { if (it.id == m.id) it.copy(lastKm = d, lastTs = System.currentTimeMillis(), level = 0) else it })
                            toast("✓ ${m.name}: registrado a ${Msg.fmtKm(d)} km")
                        }
                    }) { Text("Hecho") }
                    TextButton(onClick = { editIsNew = false; editItem = m }) { Text("Editar") }
                }
            }
            OutlinedButton(onClick = {
                editIsNew = true
                editItem = MItem("x" + System.currentTimeMillis(), "", 5000.0, -1.0, 0L, 0)
            }) { Text("＋ Agregar ítem") }
        }

        Section("📲", "Avisos a otros celulares", "Los mismos avisos llegan a otro teléfono", open == 3, { open = if (open == 3) -1 else 3 }) {
            Text(
                "Este celular envía los avisos por internet con el servicio gratuito ntfy. En el otro celular instala la app " +
                    "ntfy (Play Store) y suscríbete al tema de abajo. No hace falta instalar esta app allá.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = rOn, onCheckedChange = {
                    rOn = it
                    store.relayOn = it
                    if (it && store.relayTopic.isBlank()) {
                        store.relayTopic = Relay.newTopic()
                        rTopic = store.relayTopic
                    }
                    if (it) Relay.flush(ctx)
                })
                Spacer(Modifier.width(8.dp))
                Text("Enviar avisos a otros celulares")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = rTrips, onCheckedChange = { rTrips = it; store.relayTrips = it })
                Spacer(Modifier.width(8.dp))
                Text("Resumen de cada recorrido")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = rAlerts, onCheckedChange = { rAlerts = it; store.relayAlerts = it })
                Spacer(Modifier.width(8.dp))
                Text("Alertas (poco diésel y mantenimiento)")
            }
            if (rTopic.isNotBlank()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFE3EBF5)).padding(12.dp)
                ) {
                    Text("Tema (es como una contraseña, no lo compartas)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(rTopic, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("tema", rTopic))
                        toast("✓ Tema copiado")
                    }) { Text("Copiar") }
                    TextButton(onClick = {
                        val srv = if (rServer.trimEnd('/') == "https://ntfy.sh") "" else "\nServidor: $rServer"
                        val txt = "Para recibir los avisos de mi camioneta:\n1) Instala la app ntfy (Play Store o F-Droid).\n" +
                            "2) Toca + y suscríbete al tema: $rTopic$srv"
                        try {
                            ctx.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt),
                                    "Compartir instrucciones"
                                )
                            )
                        } catch (_: Exception) {
                        }
                    }) { Text("Compartir") }
                    TextButton(onClick = {
                        store.relayTopic = Relay.newTopic()
                        rTopic = store.relayTopic
                        toast("Tema nuevo: vuelve a suscribir el otro celular")
                    }) { Text("Cambiar") }
                }
            }
            TextField2("Servidor", rServer, { rServer = it }, kb = KeyboardType.Uri)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val s = rServer.trim()
                    if (s.startsWith("https://")) {
                        store.relayServer = s
                        toast("✓ Servidor guardado")
                    } else toast("El servidor debe empezar con https://")
                }) { Text("Guardar") }
                OutlinedButton(onClick = {
                    if (!rOn || rTopic.isBlank()) toast("Activa primero los avisos")
                    else {
                        Relay.send(ctx, "Prueba", "Control de diésel: aviso de prueba", 3, "white_check_mark")
                        toast("Enviando prueba…")
                    }
                }) { Text("Enviar prueba") }
            }
            if (rStatus.isNotBlank()) {
                Text(rStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Section("🔐", "Permisos", "Ubicación, actividad, notificaciones y batería", open == 4, { open = if (open == 4) -1 else 4 }) {
            val loc = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            val bgLoc = granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            val act = granted(ctx, Manifest.permission.ACTIVITY_RECOGNITION)
            val notif = granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
            val bat = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(ctx.packageName)
            PermRow("Ubicación", loc)
            PermRow("Actividad física", act)
            PermRow("Notificaciones", notif)
            PermRow("Ubicación todo el tiempo", bgLoc)
            PermRow("Sin optimizar batería", bat)
            if (!(loc && act && notif)) {
                Button(onClick = {
                    perms.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACTIVITY_RECOGNITION,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    )
                }) { Text("Conceder permisos básicos") }
            } else if (!bgLoc) {
                Button(onClick = { bg.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) {
                    Text("Permitir ubicación todo el tiempo")
                }
            }
            if (!bat) {
                OutlinedButton(onClick = {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                }) { Text("Quitar optimización de batería") }
            }
        }

        Section("🚗", "Detección automática", "Bluetooth del vehículo y registro de eventos", open == 5, { open = if (open == 5) -1 else 5 }) {
            val bgLoc2 = granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            val bat2 = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(ctx.packageName)
            Text(
                "El recorrido empieza solo cuando el celular se conecta al Bluetooth del vehículo " +
                    "(el mismo que usa Android Auto) y termina al desconectarse.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PermRow(
                if (btName.isBlank()) "Bluetooth del vehículo: sin elegir" else "Bluetooth del vehículo: $btName",
                btName.isNotBlank()
            )
            PermRow("Ubicación \"todo el tiempo\"", bgLoc2)
            PermRow("Batería sin restricción", bat2)
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 31 && !granted(ctx, Manifest.permission.BLUETOOTH_CONNECT))
                    btPerm.launch(Manifest.permission.BLUETOOTH_CONNECT)
                else btDlg = true
            }) { Text(if (btName.isBlank()) "Elegir Bluetooth del vehículo" else "Cambiar Bluetooth") }
            Text("Registro de eventos", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                if (logTxt.isBlank()) "Aún no hay eventos." else logTxt.split('\n').takeLast(15).reversed().joinToString("\n"),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { logTxt = Diag.read(ctx) }) { Text("Actualizar") }
                OutlinedButton(onClick = { Diag.clear(ctx); logTxt = "" }) { Text("Borrar") }
            }
        }

        Text(
            "Control de diésel 2.2 · los datos se guardan solo en este teléfono",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.outline
        )
    }

    if (btDlg) {
        val devs = remember { Diag.bonded(ctx) }
        AlertDialog(
            onDismissRequest = { btDlg = false },
            title = { Text("Bluetooth del vehículo") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (devs.isEmpty()) Text("No hay dispositivos emparejados. Empareja el celular con el vehículo y vuelve.")
                    devs.forEach { d ->
                        Text(
                            d.first, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                store.btAddr = d.second
                                store.btName = d.first
                                btName = d.first
                                btDlg = false
                                Diag.log(ctx, "Bluetooth elegido: ${d.first}")
                                toast("✓ Bluetooth guardado")
                            }.padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { btDlg = false }) { Text("Cerrar") } }
        )
    }

    editItem?.let { cur ->
        MaintDialog(
            item = cur,
            isNew = editIsNew,
            onDismiss = { editItem = null },
            onSave = { n ->
                persist(if (editIsNew) items + n else items.map { if (it.id == n.id) n else it })
                editItem = null
                toast("✓ Guardado")
            },
            onDelete = {
                persist(items.filter { it.id != cur.id })
                editItem = null
                toast("Ítem eliminado")
            }
        )
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✓" else "✗", color = if (ok) Green else Red, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text(label)
    }
}

@Composable
fun MaintDialog(item: MItem, isNew: Boolean, onDismiss: () -> Unit, onSave: (MItem) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var interval by remember { mutableStateOf("%.0f".format(Locale.US, item.intervalKm)) }
    var last by remember { mutableStateOf(if (item.lastKm >= 0) "%.0f".format(Locale.US, item.lastKm) else "") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nuevo ítem" else "Editar ítem") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField2("Nombre (ej. Cambio de aceite)", name, { name = it })
                NumField("Cada cuántos km", interval) { interval = it }
                NumField("Km del tablero en el último cambio (vacío = sin dato)", last) { last = it }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!isNew) TextButton(onClick = onDelete) { Text("Eliminar este ítem", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val iv = interval.num()
                if (name.isBlank() || iv == null || iv <= 0) {
                    err = "Escribe el nombre y cada cuántos km"
                } else {
                    val l = last.num()
                    onSave(item.copy(name = name.trim(), intervalKm = iv, lastKm = if (l != null && l >= 0) l else -1.0, level = 0))
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
