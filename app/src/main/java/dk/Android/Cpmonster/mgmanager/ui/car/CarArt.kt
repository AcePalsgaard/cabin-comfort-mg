package dk.Android.Cpmonster.mgmanager.ui.car

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dk.Android.Cpmonster.mgmanager.data.db.VehicleState
import dk.Android.Cpmonster.mgmanager.data.model.CarColor
import kotlin.math.hypot

/** Rough body shape, picked from the model name. Our own drawing – no MG artwork. */
enum class BodyStyle(
    val tailTop: Float, val rearPillar: Float, val roof: Float, val frontRoof: Float,
    val windscreenBase: Float, val hood: Float, val belt: Float, val wheelR: Float,
) {
    HATCH(16f, 15f, 8f, 50f, 68f, 20f, 19f, 7f),
    SUV(13f, 10f, 4.5f, 56f, 72f, 16.5f, 17f, 7.4f),
    ESTATE(15f, 9f, 8.5f, 53f, 70f, 19.5f, 19f, 6.8f),
    ROADSTER(19f, 36f, 13f, 48f, 62f, 21f, 20f, 7f);

    companion object {
        fun of(v: VehicleState?): BodyStyle {
            val s = listOfNotNull(v?.model, v?.series).joinToString(" ").uppercase()
            return when {
                "CYBERSTER" in s -> ROADSTER
                "MG5" in s || "MG 5" in s -> ESTATE
                listOf("ZS", "HS", "MARVEL", "S5", "S6", "SUV").any { it in s } -> SUV
                else -> HATCH
            }
        }
    }
}

/** Colour to paint the car: the user's choice, else detected from the API, else silver. */
fun carColor(override: CarColor?, v: VehicleState?): CarColor =
    override ?: CarColor.detect(v?.colorName) ?: CarColor.SILVER

private const val W = 100f
private const val H = 40f
private const val GROUND = 39.5f

/** Side view of the car, facing right. Keeps a 5:2 aspect ratio. */
@Composable
fun CarSilhouette(style: BodyStyle, color: CarColor, modifier: Modifier = Modifier) {
    val paint = Color(color.argb)
    val shapes = remember(style) { Shapes(style) }
    Canvas(
        modifier
            .aspectRatio(W / H)
            .semantics { contentDescription = "${color.label} car" },
    ) {
        val k = size.width / W
        scale(k, k, pivot = Offset.Zero) {
            // Ground shadow
            drawOval(Color.Black.copy(alpha = 0.18f), topLeft = Offset(4f, GROUND - 2f), size = androidx.compose.ui.geometry.Size(92f, 3f))
            // Body with light from above
            drawPath(
                shapes.body,
                Brush.verticalGradient(
                    0f to lerp(paint, Color.White, 0.35f),
                    0.45f to paint,
                    1f to lerp(paint, Color.Black, 0.35f),
                    startY = style.roof, endY = GROUND,
                ),
            )
            // Shoulder crease
            drawLine(
                lerp(paint, Color.White, 0.5f).copy(alpha = 0.7f),
                Offset(8f, style.belt + 4f), Offset(94f, style.hood + 4f), strokeWidth = 0.5f,
            )
            drawPath(shapes.glass, Brush.verticalGradient(listOf(Color(0xFF5A6675), Color(0xFF1C2229)), startY = style.roof, endY = style.belt))
            // B-pillar
            val bx = (style.rearPillar + style.frontRoof) / 2f + 3f
            drawLine(lerp(paint, Color.Black, 0.25f), Offset(bx, style.roof + 2f), Offset(bx + 1f, style.belt), strokeWidth = 1.6f)
            // Lights
            drawRoundRect(Color(0xFFFFF4D6), topLeft = Offset(93f, style.hood + 2.5f), size = androidx.compose.ui.geometry.Size(4.5f, 1.6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.8f))
            drawRoundRect(Color(0xFFD0202E), topLeft = Offset(2.6f, style.tailTop + 2f), size = androidx.compose.ui.geometry.Size(3f, 1.8f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.8f))
            // Wheels
            listOf(shapes.rearWheel, shapes.frontWheel).forEach { cx ->
                val c = Offset(cx, GROUND - style.wheelR)
                drawCircle(Color(0xFF15171A), style.wheelR, c)
                drawCircle(Color(0xFF9AA1A9), style.wheelR * 0.6f, c)
                drawCircle(Color(0xFF3A3F46), style.wheelR * 0.6f, c, style = Stroke(0.6f))
                drawCircle(Color(0xFF3A3F46), style.wheelR * 0.18f, c)
            }
        }
    }
}

private class Shapes(s: BodyStyle) {
    val rearWheel = 21f
    val frontWheel = 79f
    private val sill = GROUND - 3f

    val body: Path = run {
        val outline = rounded(
            listOf(
                Offset(3.5f, sill), Offset(2f, s.tailTop + 3f), Offset(4f, s.tailTop), Offset(s.rearPillar, s.roof + 0.8f),
                Offset((s.rearPillar + s.frontRoof) / 2f, s.roof - 0.6f),
                Offset(s.frontRoof, s.roof + 0.4f), Offset(s.windscreenBase, s.hood),
                Offset(90f, s.hood + 1.8f), Offset(97f, s.hood + 4f), Offset(99f, s.hood + 8f), Offset(98.5f, sill),
            ),
            radius = 4.5f,
        )
        val arches = Path().apply {
            listOf(rearWheel, frontWheel).forEach { cx ->
                val r = s.wheelR + 1.4f
                addOval(Rect(Offset(cx, GROUND - s.wheelR), r))
            }
        }
        Path.combine(PathOperation.Difference, outline, arches)
    }

    val glass: Path = rounded(
        listOf(
            Offset(s.rearPillar + 3.5f, s.roof + 2f), Offset(s.frontRoof - 0.5f, s.roof + 2f),
            Offset(s.windscreenBase - 4f, s.belt), Offset(s.rearPillar + 2f, s.belt),
        ),
        radius = 1.5f,
    )
}

/** Closed polygon with each corner smoothed by a quadratic curve. */
private fun rounded(points: List<Offset>, radius: Float): Path {
    val n = points.size
    fun toward(from: Offset, to: Offset, d: Float): Offset {
        val len = hypot(to.x - from.x, to.y - from.y)
        val t = (d / len).coerceAtMost(0.5f)
        return Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
    }
    return Path().apply {
        for (i in 0 until n) {
            val prev = points[(i - 1 + n) % n]
            val cur = points[i]
            val next = points[(i + 1) % n]
            val a = toward(cur, prev, radius)
            val b = toward(cur, next, radius)
            if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
            quadraticTo(cur.x, cur.y, b.x, b.y)
        }
        close()
    }
}
