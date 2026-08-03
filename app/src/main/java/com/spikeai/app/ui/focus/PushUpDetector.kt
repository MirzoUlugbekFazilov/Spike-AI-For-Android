package com.spikeai.app.ui.focus

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal enum class PushUpState {
    READY,
    DOWN,
    UP,
    COUNT_REP,
}

internal enum class PushUpGuidance {
    FRAME_BODY,
    TURN_SIDEWAYS,
    GO_LOWER,
    PUSH_UP,
    GOOD,
}

internal data class NormalizedPoseLandmark(
    val x: Float,
    val y: Float,
    val confidence: Float = 1f,
)

internal data class PushUpPoseFrame(
    val leftShoulder: NormalizedPoseLandmark,
    val rightShoulder: NormalizedPoseLandmark,
    val leftElbow: NormalizedPoseLandmark,
    val rightElbow: NormalizedPoseLandmark,
    val leftWrist: NormalizedPoseLandmark,
    val rightWrist: NormalizedPoseLandmark,
    val leftHip: NormalizedPoseLandmark,
    val rightHip: NormalizedPoseLandmark,
)

internal data class PushUpReading(
    val averageElbowAngle: Float,
    val shoulderHipMidlineLength: Float,
    val torsoCenterY: Float,
    val sideFacingScore: Float,
    val armCompression: Float,
)

internal data class PushUpUpdate(
    val countedRep: Boolean,
    val state: PushUpState,
    val guidance: PushUpGuidance,
    val averageElbowAngle: Float?,
)

internal class PushUpDetector(
    private val config: Config = Config(),
) {
    data class Config(
        // Lower confidence floor + larger allowed per-frame motion keep real, fast
        // reps in low light from being silently dropped (which reads as 0 reps).
        val confidenceThreshold: Float = 0.6f,
        val smoothingWindowFrames: Int = 5,
        val maxLandmarkJump: Float = 0.4f,
        val downElbowAngle: Float = 90f,
        val upElbowAngle: Float = 160f,
        val liveDownElbowAngle: Float = 128f,
        val liveUpElbowAngle: Float = 142f,
        val endpointHoldMs: Long = 150L,
        val minRepMs: Long = 500L,
        val cooldownMs: Long = 200L,
        val minElbowRange: Float = 28f,
        val minBodyDisplacement: Float = 0.01f,
        val minArmCompressionChange: Float = 0.03f,
        val frontFacingMaxScore: Float = 0.95f,
    )

    private val smoother = LandmarkMovingAverage(config.smoothingWindowFrames)
    private var state = PushUpState.READY
    private var upHoldStartMs: Long? = null
    private var downHoldStartMs: Long? = null
    private var repStartMs = 0L
    private var cooldownUntilMs = 0L
    private var upTorsoY = Float.NaN
    private var maxTorsoDisplacementDuringRep = 0f
    private var minElbowDuringRep = Float.POSITIVE_INFINITY
    private var maxElbowDuringRep = Float.NEGATIVE_INFINITY
    private var minCompressionDuringRep = Float.POSITIVE_INFINITY
    private var maxCompressionDuringRep = Float.NEGATIVE_INFINITY
    private var lastReading: PushUpReading? = null
    private var lostFrames = 0

    val currentState: PushUpState
        get() = state

    fun update(frame: PushUpPoseFrame?, nowMs: Long): PushUpUpdate {
        val reading = frame
            ?.takeIf { it.hasRequiredConfidence(config.confidenceThreshold) }
            ?.let { smoother.update(it, config.maxLandmarkJump) }
            ?.let { toReading(it) }

        if (reading == null) {
            lostFrames++
            upHoldStartMs = null
            downHoldStartMs = null
            return PushUpUpdate(false, state, PushUpGuidance.FRAME_BODY, null)
        }
        lostFrames = 0
        lastReading = reading

        if (nowMs < cooldownUntilMs) {
            return PushUpUpdate(false, state, guidanceFor(reading), reading.averageElbowAngle)
        }

        minElbowDuringRep = min(minElbowDuringRep, reading.averageElbowAngle)
        maxElbowDuringRep = max(maxElbowDuringRep, reading.averageElbowAngle)
        minCompressionDuringRep = min(minCompressionDuringRep, reading.armCompression)
        maxCompressionDuringRep = max(maxCompressionDuringRep, reading.armCompression)
        maxTorsoDisplacementDuringRep = max(
            maxTorsoDisplacementDuringRep,
            torsoDisplacementFromUp(reading),
        )

        val isUp = isUpPosition(reading)
        val isDown = isDownPosition(reading)

        when (state) {
            PushUpState.READY -> {
                if (heldUp(isUp, nowMs)) {
                    state = PushUpState.UP
                    repStartMs = nowMs
                    upTorsoY = reading.torsoCenterY
                    minElbowDuringRep = reading.averageElbowAngle
                    maxElbowDuringRep = reading.averageElbowAngle
                    minCompressionDuringRep = reading.armCompression
                    maxCompressionDuringRep = reading.armCompression
                }
                downHoldStartMs = null
            }

            PushUpState.UP -> {
                maxElbowDuringRep = max(maxElbowDuringRep, reading.averageElbowAngle)
                if (heldDown(isDown, nowMs)) {
                    if (bodyMovedEnough()) {
                        state = PushUpState.DOWN
                    }
                }
                if (!isDown) downHoldStartMs = null
            }

            PushUpState.DOWN -> {
                minElbowDuringRep = min(minElbowDuringRep, reading.averageElbowAngle)
                if (heldUp(isUp, nowMs)) {
                    val valid = nowMs - repStartMs >= config.minRepMs &&
                        elbowMovedEnough() &&
                        armCompressedEnough() &&
                        bodyMovedEnough()
                    if (valid) {
                        state = PushUpState.COUNT_REP
                        cooldownUntilMs = nowMs + config.cooldownMs
                        val counted = PushUpUpdate(
                            countedRep = true,
                            state = state,
                            guidance = PushUpGuidance.GOOD,
                            averageElbowAngle = reading.averageElbowAngle,
                        )
                        resetForNextRep(keepReady = true, reading = reading, nowMs = nowMs)
                        return counted
                    }
                }
                if (!isUp) upHoldStartMs = null
            }

            PushUpState.COUNT_REP -> {
                resetForNextRep(keepReady = true, reading = reading, nowMs = nowMs)
            }
        }

        return PushUpUpdate(false, state, guidanceFor(reading), reading.averageElbowAngle)
    }

    fun reset() {
        state = PushUpState.READY
        upHoldStartMs = null
        downHoldStartMs = null
        repStartMs = 0L
        cooldownUntilMs = 0L
        upTorsoY = Float.NaN
        maxTorsoDisplacementDuringRep = 0f
        minElbowDuringRep = Float.POSITIVE_INFINITY
        maxElbowDuringRep = Float.NEGATIVE_INFINITY
        minCompressionDuringRep = Float.POSITIVE_INFINITY
        maxCompressionDuringRep = Float.NEGATIVE_INFINITY
        lostFrames = 0
        smoother.clear()
        lastReading = null
    }

    private fun heldUp(condition: Boolean, nowMs: Long): Boolean {
        if (!condition) {
            upHoldStartMs = null
            return false
        }
        if (upHoldStartMs == null) upHoldStartMs = nowMs
        return nowMs - (upHoldStartMs ?: nowMs) >= config.endpointHoldMs
    }

    private fun heldDown(condition: Boolean, nowMs: Long): Boolean {
        if (!condition) {
            downHoldStartMs = null
            return false
        }
        if (downHoldStartMs == null) downHoldStartMs = nowMs
        return nowMs - (downHoldStartMs ?: nowMs) >= config.endpointHoldMs
    }

    private fun bodyMovedEnough(): Boolean {
        val reading = lastReading ?: return false
        if (!upTorsoY.isFinite()) return false
        val normalizedThreshold = config.minBodyDisplacement * reading.shoulderHipMidlineLength
        return maxTorsoDisplacementDuringRep >= normalizedThreshold
    }

    private fun torsoDisplacementFromUp(reading: PushUpReading): Float {
        if (!upTorsoY.isFinite()) return 0f
        return abs(reading.torsoCenterY - upTorsoY)
    }

    private fun elbowMovedEnough(): Boolean =
        maxElbowDuringRep.isFinite() &&
            minElbowDuringRep.isFinite() &&
            maxElbowDuringRep - minElbowDuringRep >= config.minElbowRange

    private fun armCompressedEnough(): Boolean =
        maxCompressionDuringRep.isFinite() &&
            minCompressionDuringRep.isFinite() &&
            maxCompressionDuringRep - minCompressionDuringRep >= config.minArmCompressionChange

    private fun guidanceFor(reading: PushUpReading): PushUpGuidance = when {
        reading.sideFacingScore > config.frontFacingMaxScore -> PushUpGuidance.TURN_SIDEWAYS
        state == PushUpState.DOWN -> PushUpGuidance.PUSH_UP
        isUpPosition(reading) -> PushUpGuidance.GO_LOWER
        else -> PushUpGuidance.GOOD
    }

    private fun isUpPosition(reading: PushUpReading): Boolean =
        reading.averageElbowAngle > config.upElbowAngle ||
            reading.averageElbowAngle > config.liveUpElbowAngle

    private fun isDownPosition(reading: PushUpReading): Boolean =
        reading.averageElbowAngle < config.downElbowAngle ||
            reading.averageElbowAngle < config.liveDownElbowAngle

    private fun resetEndpointsOnly() {
        upHoldStartMs = null
        downHoldStartMs = null
    }

    private fun resetForNextRep(keepReady: Boolean, reading: PushUpReading, nowMs: Long) {
        state = if (keepReady) PushUpState.UP else PushUpState.READY
        upHoldStartMs = nowMs
        downHoldStartMs = null
        repStartMs = nowMs
        upTorsoY = reading.torsoCenterY
        maxTorsoDisplacementDuringRep = 0f
        minElbowDuringRep = reading.averageElbowAngle
        maxElbowDuringRep = reading.averageElbowAngle
        minCompressionDuringRep = reading.armCompression
        maxCompressionDuringRep = reading.armCompression
    }

    private fun toReading(frame: PushUpPoseFrame): PushUpReading {
        val leftElbow = angle(frame.leftShoulder, frame.leftElbow, frame.leftWrist)
        val rightElbow = angle(frame.rightShoulder, frame.rightElbow, frame.rightWrist)
        val averageElbow = (leftElbow + rightElbow) / 2f
        val shoulderMid = midpoint(frame.leftShoulder, frame.rightShoulder)
        val hipMid = midpoint(frame.leftHip, frame.rightHip)
        val torsoLength = max(distance(shoulderMid, hipMid), 0.001f)
        val torsoCenterY = (shoulderMid.y + hipMid.y) / 2f
        val shoulderWidth = distance(frame.leftShoulder, frame.rightShoulder)
        val hipWidth = distance(frame.leftHip, frame.rightHip)
        val sideFacingScore = min(shoulderWidth, hipWidth) / torsoLength
        val armCompression = (
            armCompression(frame.leftShoulder, frame.leftElbow, frame.leftWrist, torsoLength) +
                armCompression(frame.rightShoulder, frame.rightElbow, frame.rightWrist, torsoLength)
            ) / 2f
        return PushUpReading(
            averageElbowAngle = averageElbow,
            shoulderHipMidlineLength = torsoLength,
            torsoCenterY = torsoCenterY,
            sideFacingScore = sideFacingScore,
            armCompression = armCompression,
        )
    }

    private fun armCompression(
        shoulder: NormalizedPoseLandmark,
        elbow: NormalizedPoseLandmark,
        wrist: NormalizedPoseLandmark,
        torsoLength: Float,
    ): Float {
        val upperArm = distance(shoulder, elbow)
        val forearm = distance(elbow, wrist)
        val shoulderToWrist = distance(shoulder, wrist)
        val limbLength = max(upperArm + forearm, torsoLength * 0.35f)
        return (1f - shoulderToWrist / limbLength).coerceIn(0f, 1f)
    }
}

/**
 * Viewpoint-robust rep counter driven purely by the vertical travel of the body
 * in the frame. Where elbow angles barely change (e.g. a phone placed low and in
 * front, so the push-up happens mostly *toward* the camera), the torso still
 * clearly moves down and back up in the image — this counter keys off exactly
 * that.
 *
 * It is fully self-calibrating: a decaying min/max envelope tracks the person's
 * own range of motion, so it adapts to any camera distance or angle without fixed
 * pixel thresholds. A rep is counted on a full down→up excursion that:
 *  - spans at least [minAmplitude] of the frame height (rejects small jitter),
 *  - reaches deep toward the bottom and returns near the top (hysteresis), and
 *  - takes at least [minRepMs] (rejects impossibly fast twitches).
 *
 * The signal convention is image-space y (0 = top of frame, 1 = bottom): going
 * *down* in a push-up increases y.
 */
internal class AdaptiveVerticalRepCounter(
    private val alpha: Float = 0.4f,
    private val envelopeDecay: Float = 0.02f,
    private val minAmplitude: Float = 0.035f,
    private val enterFraction: Float = 0.6f,
    private val exitFraction: Float = 0.4f,
    private val minRepMs: Long = 450L,
    private val maxRepMs: Long = 8_000L,
) {
    private var smoothed = Float.NaN
    private var hi = Float.NaN
    private var lo = Float.NaN
    private var isDown = false
    private var downStartMs = 0L

    /** True once a downward excursion is confirmed and we're awaiting the return. */
    val inDownPhase: Boolean get() = isDown

    fun reset() {
        smoothed = Float.NaN
        hi = Float.NaN
        lo = Float.NaN
        isDown = false
        downStartMs = 0L
    }

    /** Returns true exactly once per completed down→up vertical excursion. */
    fun update(y: Float?, nowMs: Long): Boolean {
        if (y == null || y.isNaN()) return false

        smoothed = if (smoothed.isNaN()) y else smoothed + alpha * (y - smoothed)
        hi = if (hi.isNaN() || smoothed > hi) smoothed else hi + envelopeDecay * (smoothed - hi)
        lo = if (lo.isNaN() || smoothed < lo) smoothed else lo + envelopeDecay * (smoothed - lo)

        val range = hi - lo
        if (range < minAmplitude) return false

        val downThreshold = lo + enterFraction * range
        val upThreshold = lo + exitFraction * range

        if (!isDown) {
            if (smoothed >= downThreshold) {
                isDown = true
                downStartMs = nowMs
            }
            return false
        }

        if (nowMs - downStartMs > maxRepMs) {
            isDown = false
            return false
        }
        if (smoothed <= upThreshold) {
            val durationMs = nowMs - downStartMs
            isDown = false
            return durationMs >= minRepMs
        }
        return false
    }
}

internal class LandmarkMovingAverage(private val windowSize: Int) {
    private val frames = ArrayDeque<PushUpPoseFrame>()
    private var previousAverage: PushUpPoseFrame? = null

    fun update(frame: PushUpPoseFrame, maxJump: Float): PushUpPoseFrame? {
        val previous = previousAverage
        if (previous != null && frame.maxDistanceFrom(previous) > maxJump) {
            return null
        }
        frames.addLast(frame)
        while (frames.size > windowSize.coerceAtLeast(1)) frames.removeFirst()
        val averaged = frames.averageFrame()
        previousAverage = averaged
        return averaged
    }

    fun clear() {
        frames.clear()
        previousAverage = null
    }
}

internal fun angle(a: NormalizedPoseLandmark, b: NormalizedPoseLandmark, c: NormalizedPoseLandmark): Float {
    val radians = atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble()) -
        atan2((a.y - b.y).toDouble(), (a.x - b.x).toDouble())
    var degrees = abs(Math.toDegrees(radians).toFloat())
    if (degrees > 180f) degrees = 360f - degrees
    return degrees
}

private fun PushUpPoseFrame.hasRequiredConfidence(threshold: Float): Boolean =
    landmarks().all { it.confidence >= threshold }

private fun PushUpPoseFrame.maxDistanceFrom(other: PushUpPoseFrame): Float =
    landmarks().zip(other.landmarks()).maxOf { (a, b) -> distance(a, b) }

private fun ArrayDeque<PushUpPoseFrame>.averageFrame(): PushUpPoseFrame {
    fun avg(selector: (PushUpPoseFrame) -> NormalizedPoseLandmark) = NormalizedPoseLandmark(
        x = sumOf { selector(it).x.toDouble() }.toFloat() / size,
        y = sumOf { selector(it).y.toDouble() }.toFloat() / size,
        confidence = sumOf { selector(it).confidence.toDouble() }.toFloat() / size,
    )
    return PushUpPoseFrame(
        leftShoulder = avg { it.leftShoulder },
        rightShoulder = avg { it.rightShoulder },
        leftElbow = avg { it.leftElbow },
        rightElbow = avg { it.rightElbow },
        leftWrist = avg { it.leftWrist },
        rightWrist = avg { it.rightWrist },
        leftHip = avg { it.leftHip },
        rightHip = avg { it.rightHip },
    )
}

private fun PushUpPoseFrame.landmarks(): List<NormalizedPoseLandmark> = listOf(
    leftShoulder,
    rightShoulder,
    leftElbow,
    rightElbow,
    leftWrist,
    rightWrist,
    leftHip,
    rightHip,
)

private fun midpoint(a: NormalizedPoseLandmark, b: NormalizedPoseLandmark): NormalizedPoseLandmark =
    NormalizedPoseLandmark(
        x = (a.x + b.x) / 2f,
        y = (a.y + b.y) / 2f,
        confidence = min(a.confidence, b.confidence),
    )

private fun distance(a: NormalizedPoseLandmark, b: NormalizedPoseLandmark): Float =
    hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
