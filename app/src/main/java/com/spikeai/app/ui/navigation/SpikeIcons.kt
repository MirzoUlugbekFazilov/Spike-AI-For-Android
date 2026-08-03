package com.spikeai.app.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom navigation icons hand-built to mirror the iOS Spike AI tab bar
 * SF Symbols, so the Android bottom bar matches the iOS look:
 *   • [Home]     → house.fill
 *   • [Progress] → chart.line.uptrend.xyaxis
 *   • [Focus]    → shield.lefthalf.filled
 *   • [Profile]  → person.crop.circle
 *
 * Drawn on a 24×24 viewport. Fill/stroke colours are placeholders — the
 * [androidx.compose.material3.Icon] composable re-tints the whole vector,
 * so colour here is irrelevant; only the shape matters.
 */
object SpikeIcons {

    private val ink = SolidColor(Color(0xFF000000))

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    /** Filled house with overhanging eaves and an arched doorway (house.fill). */
    val Home: ImageVector = icon("SpikeHome") {
        path(fill = ink) {
            moveTo(12f, 2.9f)
            curveTo(11.6f, 2.9f, 11.2f, 3.05f, 10.85f, 3.32f)
            lineTo(2.9f, 9.7f)
            curveTo(2.55f, 9.98f, 2.5f, 10.3f, 2.75f, 10.62f)
            curveTo(3.0f, 10.94f, 3.4f, 10.95f, 3.75f, 10.72f)
            lineTo(4.0f, 10.52f)
            lineTo(4.0f, 19.6f)
            curveTo(4.0f, 20.4f, 4.6f, 21.0f, 5.4f, 21.0f)
            lineTo(9.2f, 21.0f)
            lineTo(9.2f, 15.2f)
            curveTo(9.2f, 14.2f, 10.0f, 13.4f, 11.0f, 13.4f)
            lineTo(13.0f, 13.4f)
            curveTo(14.0f, 13.4f, 14.8f, 14.2f, 14.8f, 15.2f)
            lineTo(14.8f, 21.0f)
            lineTo(18.6f, 21.0f)
            curveTo(19.4f, 21.0f, 20.0f, 20.4f, 20.0f, 19.6f)
            lineTo(20.0f, 10.52f)
            lineTo(20.25f, 10.72f)
            curveTo(20.6f, 10.95f, 21.0f, 10.94f, 21.25f, 10.62f)
            curveTo(21.5f, 10.3f, 21.45f, 9.98f, 21.1f, 9.7f)
            lineTo(13.15f, 3.32f)
            curveTo(12.8f, 3.05f, 12.4f, 2.9f, 12f, 2.9f)
            close()
        }
    }

    /** L-shaped axis with a rising zig-zag line and arrowhead (chart.line.uptrend.xyaxis). */
    val Progress: ImageVector = icon("SpikeProgress") {
        // axis
        path(
            stroke = ink,
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 3.4f)
            lineTo(4f, 20f)
            lineTo(20.6f, 20f)
        }
        // rising trend line + arrowhead
        path(
            stroke = ink,
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6.6f, 14.8f)
            lineTo(9.9f, 11.3f)
            lineTo(12.4f, 13.3f)
            lineTo(18f, 7f)
            // arrowhead
            moveTo(14.4f, 7f)
            lineTo(18f, 7f)
            lineTo(18f, 10.6f)
        }
    }

    /** Shield outline with the left half filled (shield.lefthalf.filled). */
    val Focus: ImageVector = icon("SpikeFocus") {
        // left-half fill (centre spine down the left contour)
        path(fill = ink) {
            moveTo(12f, 2.6f)
            curveTo(12f, 2.6f, 6.5f, 4.6f, 5.2f, 5.05f)
            curveTo(4.7f, 5.2f, 4.4f, 5.6f, 4.4f, 6.1f)
            lineTo(4.4f, 11.2f)
            curveTo(4.4f, 15.9f, 7.6f, 19.6f, 12f, 21.3f)
            lineTo(12f, 2.6f)
            close()
        }
        // full shield outline
        path(
            stroke = ink,
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 2.6f)
            curveTo(12f, 2.6f, 6.5f, 4.6f, 5.2f, 5.05f)
            curveTo(4.7f, 5.2f, 4.4f, 5.6f, 4.4f, 6.1f)
            lineTo(4.4f, 11.2f)
            curveTo(4.4f, 15.9f, 7.6f, 19.6f, 12f, 21.3f)
            curveTo(16.4f, 19.6f, 19.6f, 15.9f, 19.6f, 11.2f)
            lineTo(19.6f, 6.1f)
            curveTo(19.6f, 5.6f, 19.3f, 5.2f, 18.8f, 5.05f)
            curveTo(17.5f, 4.6f, 12f, 2.6f, 12f, 2.6f)
            close()
        }
    }

    /** Three rounded vertical bars of increasing height — bar-chart Progress glyph. */
    val ProgressBars: ImageVector = icon("SpikeProgressBars") {
        path(
            stroke = ink,
            strokeLineWidth = 3.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6.5f, 18.4f); lineTo(6.5f, 13f)
            moveTo(12f, 18.4f); lineTo(12f, 9f)
            moveTo(17.5f, 18.4f); lineTo(17.5f, 5f)
        }
    }

    /** Solid filled circle — avatar-style Profile glyph (tinted at the call site). */
    val ProfileDot: ImageVector = icon("SpikeProfileDot") {
        path(fill = ink) {
            moveTo(12f, 4.5f)
            curveTo(16.14f, 4.5f, 19.5f, 7.86f, 19.5f, 12f)
            curveTo(19.5f, 16.14f, 16.14f, 19.5f, 12f, 19.5f)
            curveTo(7.86f, 19.5f, 4.5f, 16.14f, 4.5f, 12f)
            curveTo(4.5f, 7.86f, 7.86f, 4.5f, 12f, 4.5f)
            close()
        }
    }

    /** Person bust inside a ring (person.crop.circle). */
    val Profile: ImageVector = icon("SpikeProfile") {
        // outer ring
        path(
            stroke = ink,
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 2.8f)
            curveTo(17.08f, 2.8f, 21.2f, 6.92f, 21.2f, 12f)
            curveTo(21.2f, 17.08f, 17.08f, 21.2f, 12f, 21.2f)
            curveTo(6.92f, 21.2f, 2.8f, 17.08f, 2.8f, 12f)
            curveTo(2.8f, 6.92f, 6.92f, 2.8f, 12f, 2.8f)
            close()
        }
        // head
        path(fill = ink) {
            moveTo(12f, 6.8f)
            curveTo(13.55f, 6.8f, 14.8f, 8.05f, 14.8f, 9.6f)
            curveTo(14.8f, 11.15f, 13.55f, 12.4f, 12f, 12.4f)
            curveTo(10.45f, 12.4f, 9.2f, 11.15f, 9.2f, 9.6f)
            curveTo(9.2f, 8.05f, 10.45f, 6.8f, 12f, 6.8f)
            close()
        }
        // shoulders (hug the bottom of the ring)
        path(fill = ink) {
            moveTo(6.4f, 18.2f)
            curveTo(6.9f, 15.6f, 9.2f, 13.8f, 12f, 13.8f)
            curveTo(14.8f, 13.8f, 17.1f, 15.6f, 17.6f, 18.2f)
            curveTo(16.1f, 19.8f, 14.2f, 20.6f, 12f, 20.6f)
            curveTo(9.8f, 20.6f, 7.9f, 19.8f, 6.4f, 18.2f)
            close()
        }
    }
}
