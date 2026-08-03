package com.spikeai.app.ui.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AthleteRepDetectorTest {
    @Test
    fun pushUpCountsOnlyUpDownUpWithHeldEndpoints() {
        val detector = PushUpDetector()

        var t = 0L
        repeat(5) {
            assertFalse(detector.update(pushUpFrame(elbow = 168f, torsoY = 0.40f), t).countedRep)
            t += 80L
        }
        repeat(8) {
            assertFalse(detector.update(pushUpFrame(elbow = 82f, torsoY = 0.55f), t).countedRep)
            t += 120L
        }
        var counted = false
        repeat(8) {
            counted = detector.update(pushUpFrame(elbow = 168f, torsoY = 0.40f), t).countedRep || counted
            t += 120L
        }
        assertTrue(counted)
    }

    @Test
    fun pushUpRejectsDownDownAndIncompleteMovements() {
        val detector = PushUpDetector()

        repeat(8) { i ->
            assertFalse(detector.update(pushUpFrame(elbow = 82f, torsoY = 0.55f), i * 120L).countedRep)
        }

        detector.reset()
        repeat(5) { i -> detector.update(pushUpFrame(elbow = 168f, torsoY = 0.40f), i * 120L) }
        repeat(5) { i -> detector.update(pushUpFrame(elbow = 82f, torsoY = 0.55f), 600L + i * 120L) }
        repeat(5) { i ->
            assertFalse(detector.update(pushUpFrame(elbow = 145f, torsoY = 0.44f), 1_300L + i * 150L).countedRep)
        }
    }

    @Test
    fun pushUpCountsRealCameraProjectedAngles() {
        val detector = PushUpDetector()

        var t = 0L
        repeat(5) {
            detector.update(pushUpFrame(elbow = 150f, torsoY = 0.40f), t)
            t += 100L
        }
        repeat(8) {
            detector.update(pushUpFrame(elbow = 118f, torsoY = 0.49f), t)
            t += 120L
        }
        var counted = false
        repeat(8) {
            counted = detector.update(pushUpFrame(elbow = 150f, torsoY = 0.40f), t).countedRep || counted
            t += 120L
        }

        assertTrue(counted)
    }

    @Test
    fun pushUpRejectsImplausiblyFastRep() {
        val detector = PushUpDetector()

        val fastDetector = PushUpDetector(PushUpDetector.Config(smoothingWindowFrames = 1))
        fastDetector.update(pushUpFrame(elbow = 168f, torsoY = 0.40f), 0L)
        fastDetector.update(pushUpFrame(elbow = 168f, torsoY = 0.40f), 160L)
        fastDetector.update(pushUpFrame(elbow = 82f, torsoY = 0.55f), 220L)
        fastDetector.update(pushUpFrame(elbow = 82f, torsoY = 0.55f), 380L)
        fastDetector.update(pushUpFrame(elbow = 168f, torsoY = 0.40f), 430L)
        assertFalse(fastDetector.update(pushUpFrame(elbow = 168f, torsoY = 0.40f), 500L).countedRep)
    }

    @Test
    fun pushUpPausesWhenLandmarksAreLowConfidence() {
        val detector = PushUpDetector()

        repeat(5) { i -> detector.update(pushUpFrame(elbow = 168f, torsoY = 0.40f), i * 120L) }
        val lowConfidence = detector.update(pushUpFrame(elbow = 82f, torsoY = 0.55f, confidence = 0.55f), 500L)

        assertFalse(lowConfidence.countedRep)
        assertSame(PushUpGuidance.FRAME_BODY, lowConfidence.guidance)
    }

    @Test
    fun pushUpRejectsArmOnlyCheatWhenTorsoIsStationary() {
        val detector = PushUpDetector()

        repeat(5) { i -> detector.update(pushUpFrame(elbow = 168f, torsoY = 0.40f), i * 120L) }
        repeat(5) { i -> detector.update(pushUpFrame(elbow = 82f, torsoY = 0.405f), 700L + i * 120L) }
        repeat(5) { i ->
            assertFalse(detector.update(pushUpFrame(elbow = 168f, torsoY = 0.40f), 1_400L + i * 120L).countedRep)
        }
    }

    @Test
    fun pushUpGivesFrontFacingCameraGuidanceWithoutCrashing() {
        val detector = PushUpDetector()

        val update = detector.update(pushUpFrame(elbow = 168f, torsoY = 0.40f, bodyWidth = 0.36f), 0L)

        assertFalse(update.countedRep)
        assertSame(PushUpGuidance.TURN_SIDEWAYS, update.guidance)
    }

    @Test
    fun movingAverageSuppressesSingleFrameJitter() {
        val smoother = LandmarkMovingAverage(windowSize = 5)

        val stable = pushUpFrame(elbow = 168f, torsoY = 0.40f)
        assertNotNull(smoother.update(stable, maxJump = 0.18f))
        assertNotNull(smoother.update(stable, maxJump = 0.18f))
        assertNull(smoother.update(pushUpFrame(elbow = 82f, torsoY = 0.80f), maxJump = 0.18f))
    }

    // ── Viewpoint-robust vertical counter (phone low & in front) ──────────────

    @Test
    fun verticalCounterCountsForeshortenedPushUpWhereElbowBarelyMoves() {
        val c = AdaptiveVerticalRepCounter()
        var count = 0
        var t = 0L
        repeat(5) { c.update(0.40f, t); t += 100 }     // settle at top
        repeat(6) { c.update(0.52f, t); t += 100 }     // lower the body
        repeat(8) { if (c.update(0.40f, t)) count++; t += 100 }  // press back up
        assertEquals(1, count)
    }

    @Test
    fun verticalCounterCountsThreeConsecutiveReps() {
        val c = AdaptiveVerticalRepCounter()
        var count = 0
        var t = 0L
        repeat(5) { c.update(0.40f, t); t += 100 }
        repeat(3) {
            repeat(6) { c.update(0.52f, t); t += 100 }
            repeat(6) { if (c.update(0.40f, t)) count++; t += 100 }
        }
        assertEquals(3, count)
    }

    @Test
    fun verticalCounterIgnoresSmallJitter() {
        val c = AdaptiveVerticalRepCounter()
        var count = 0
        var t = 0L
        repeat(40) {
            val y = if (it % 2 == 0) 0.40f else 0.42f   // ~0.02 amplitude, below floor
            if (c.update(y, t)) count++
            t += 100
        }
        assertEquals(0, count)
    }

    @Test
    fun verticalCounterRejectsImplausiblyFastExcursion() {
        val c = AdaptiveVerticalRepCounter()
        var count = 0
        var t = 0L
        repeat(3) { c.update(0.40f, t); t += 50 }
        repeat(3) { c.update(0.52f, t); t += 50 }
        repeat(4) { if (c.update(0.40f, t)) count++; t += 50 }
        assertEquals(0, count)
    }

    @Test
    fun verticalCounterDoesNotCountDescentWithoutReturn() {
        val c = AdaptiveVerticalRepCounter()
        var count = 0
        var t = 0L
        repeat(5) { c.update(0.40f, t); t += 100 }
        repeat(12) { if (c.update(0.52f, t)) count++; t += 100 }
        assertEquals(0, count)
    }

    @Test
    fun standUpRequiresHipFlexionAsWellAsKneeFlexion() {
        val detector = AthleteRepDetectors.standUp()

        assertFalse(detector.update(standUp(knee = 100f, hip = 165f), 0L))
        assertFalse(detector.update(standUp(knee = 100f, hip = 165f), 100L))
        assertFalse(detector.update(standUp(knee = 170f, hip = 170f), 400L))
        assertFalse(detector.update(standUp(knee = 170f, hip = 170f), 500L))
    }

    @Test
    fun briefLandmarkDropoutDoesNotLoseValidSquatRep() {
        val detector = AthleteRepDetectors.standUp()

        assertFalse(detector.update(standUp(knee = 100f, hip = 120f), 0L))
        assertFalse(detector.update(standUp(knee = 100f, hip = 120f), 100L))
        repeat(4) { detector.missingFrame() }
        assertFalse(detector.update(standUp(knee = 170f, hip = 170f), 400L))
        assertTrue(detector.update(standUp(knee = 170f, hip = 170f), 500L))
    }

    @Test
    fun extendedLandmarkDropoutLosesInProgressSquatRep() {
        val detector = AthleteRepDetectors.standUp()

        assertFalse(detector.update(standUp(knee = 100f, hip = 120f), 0L))
        assertFalse(detector.update(standUp(knee = 100f, hip = 120f), 100L))
        repeat(5) { detector.missingFrame() }
        assertFalse(detector.update(standUp(knee = 170f, hip = 170f), 400L))
        assertFalse(detector.update(standUp(knee = 170f, hip = 170f), 500L))
    }

    @Test
    fun orientationRequiresThreeMatchingFramesAndRejectsUnknown() {
        val detector = StableOrientationDetector()

        assertNull(detector.update(TorsoOrientation.VERTICAL))
        assertNull(detector.update(null))
        assertNull(detector.update(TorsoOrientation.VERTICAL))
        assertNull(detector.update(TorsoOrientation.VERTICAL))
        assertSame(TorsoOrientation.VERTICAL, detector.update(TorsoOrientation.VERTICAL))
    }

    @Test
    fun angleSmootherIgnoresNullWithoutLosingItsValue() {
        val smoother = AngleSmoother(alpha = 0.5f)

        assertNull(smoother.update(null))
        assertEquals(90f, smoother.update(90f)!!, 0.001f)
        assertEquals(135f, smoother.update(180f)!!, 0.001f)
        assertNull(smoother.update(null))
        assertEquals(157.5f, smoother.update(180f)!!, 0.001f)
    }

    private fun pushUpFrame(
        elbow: Float,
        torsoY: Float,
        bodyWidth: Float = 0.08f,
        confidence: Float = 1f,
    ): PushUpPoseFrame {
        val shoulderY = torsoY - 0.18f
        val hipY = torsoY + 0.18f
        val shoulderX = 0.50f
        val leftShoulder = lm(shoulderX - bodyWidth / 2f, shoulderY, confidence)
        val rightShoulder = lm(shoulderX + bodyWidth / 2f, shoulderY, confidence)
        val leftHip = lm(shoulderX - bodyWidth / 2f, hipY, confidence)
        val rightHip = lm(shoulderX + bodyWidth / 2f, hipY, confidence)
        val leftElbow = armElbow(leftShoulder, elbow, side = -1f, confidence)
        val rightElbow = armElbow(rightShoulder, elbow, side = 1f, confidence)
        val leftWrist = armWrist(leftShoulder, leftElbow, elbow, side = -1f, confidence)
        val rightWrist = armWrist(rightShoulder, rightElbow, elbow, side = 1f, confidence)
        return PushUpPoseFrame(
            leftShoulder = leftShoulder,
            rightShoulder = rightShoulder,
            leftElbow = leftElbow,
            rightElbow = rightElbow,
            leftWrist = leftWrist,
            rightWrist = rightWrist,
            leftHip = leftHip,
            rightHip = rightHip,
        )
    }

    private fun armElbow(
        shoulder: NormalizedPoseLandmark,
        elbowAngle: Float,
        side: Float,
        confidence: Float,
    ): NormalizedPoseLandmark {
        val upperArm = 0.12f
        val bend = ((180f - elbowAngle) / 90f).coerceIn(0f, 1f)
        return lm(
            x = shoulder.x + side * 0.02f,
            y = shoulder.y + upperArm + bend * 0.02f,
            confidence = confidence,
        )
    }

    private fun armWrist(
        shoulder: NormalizedPoseLandmark,
        elbow: NormalizedPoseLandmark,
        elbowAngle: Float,
        side: Float,
        confidence: Float,
    ): NormalizedPoseLandmark {
        val forearm = 0.12f
        val upperX = shoulder.x - elbow.x
        val upperY = shoulder.y - elbow.y
        val upperLen = kotlin.math.hypot(upperX.toDouble(), upperY.toDouble()).toFloat()
        val ux = upperX / upperLen
        val uy = upperY / upperLen
        val theta = Math.toRadians((side * elbowAngle).toDouble())
        val cos = kotlin.math.cos(theta).toFloat()
        val sin = kotlin.math.sin(theta).toFloat()
        val wx = ux * cos - uy * sin
        val wy = ux * sin + uy * cos
        return lm(
            x = elbow.x + wx * forearm,
            y = elbow.y + wy * forearm,
            confidence = confidence,
        )
    }

    private fun lm(x: Float, y: Float, confidence: Float = 1f) =
        NormalizedPoseLandmark(x = x, y = y, confidence = confidence)

    private fun standUp(knee: Float, hip: Float) =
        MovementAngles(kneeAngle = knee, hipAngle = hip)
}
