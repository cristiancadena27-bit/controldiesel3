package com.dieselalert.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Imagen del recorrido dibujada con los puntos GPS (sin mapa de fondo, funciona sin internet). */
@Composable
fun RouteMap(
    pts: List<Pair<Double, Double>>,
    label: String,
    km: Double,
    liters: Double,
    cost: Double,
    height: Int = 220
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF214A78), Color(0xFF0E2236))))
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 30.dp)) {
            val g = 6
            for (i in 1 until g) {
                val x = size.width * i / g
                val y = size.height * i / g
                drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, size.height), 2f)
                drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), 2f)
            }
            if (pts.size >= 2) {
                val minLat = pts.minOf { it.first }
                val maxLat = pts.maxOf { it.first }
                val minLng = pts.minOf { it.second }
                val maxLng = pts.maxOf { it.second }
                val kx = cos(Math.toRadians((minLat + maxLat) / 2))
                val w = max((maxLng - minLng) * kx, 1e-6)
                val h = max(maxLat - minLat, 1e-6)
                val scale = min(size.width / w, size.height / h)
                val offX = (size.width - w * scale) / 2
                val offY = (size.height - h * scale) / 2
                fun pr(p: Pair<Double, Double>): Offset = Offset(
                    ((p.second - minLng) * kx * scale + offX).toFloat(),
                    (size.height - ((p.first - minLat) * scale + offY)).toFloat()
                )
                val path = Path()
                pts.forEachIndexed { i, p ->
                    val o = pr(p)
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(
                    path, Color(0xFF08131F),
                    style = Stroke(width = 20f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path, Color(0xFF38BDF8),
                    style = Stroke(width = 11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawCircle(color = Color.White, radius = 18f, center = pr(pts.first()))
                drawCircle(color = Color(0xFF1FA971), radius = 12f, center = pr(pts.first()))
                drawCircle(color = Color.White, radius = 18f, center = pr(pts.last()))
                drawCircle(color = Color(0xFFE53935), radius = 12f, center = pr(pts.last()))
            }
        }
        if (pts.size < 2) {
            Text(
                "Sin trazado para este recorrido",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(
            label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text("${f1(km)} km", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            val costTxt = if (cost > 0) " · ≈ ${money(cost)}" else ""
            Text(
                "≈ ${f1(liters)} L de diésel$costTxt",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp
            )
        }
    }
}
