package com.spikeai.app.ui.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.spikeai.app.blocking.FocusController
import com.spikeai.app.localization.tr
import com.spikeai.app.ui.theme.SpikeTheme
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/**
 * Sweat-mode unlock screen.
 *
 * Uses CameraX + ML Kit Pose Detection to count push-ups (or squats) via a simple
 * angle-based state machine. Each completed rep grants 60 seconds of app unlock
 * time. The Claim button hands the earned time to [FocusController] and closes.
 *
 * ML Kit setup is guarded so the screen degrades gracefully (rep counting simply
 * stays at 0) if the model/runtime is unavailable on the device.
 *
 * @param onClaim invoked after a successful claim so the host can dismiss the sheet.
 */
@Composable
fun SweatUnlock(onClaim: () -> Unit) {
    val colors = SpikeTheme.colors
    val context = LocalContext.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    LaunchedEffect(Unit) {
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var reps by remember { mutableIntStateOf(0) }
    // Latest skeleton + coaching cue, updated on the main thread from the analyzer.
    var poseFrame by remember { mutableStateOf<PoseFrame?>(null) }
    var feedback by remember { mutableStateOf(RepFeedback.NO_BODY) }
    val focusState by FocusController.state.collectAsStateWithLifecycle()
    var nowElapsedMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowElapsedMs = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }

    // Cap and centre the content so the camera preview and controls stay a sensible
    // size on tablets / large screens instead of stretching across the whole sheet.
    val config = LocalConfiguration.current
    // Preview height: the natural 3:4 shape of the content column, but never more than
    // 42% of the screen, so the claim button and coaching cue are reachable without
    // scrolling on the tall, narrow phones where this sheet is tightest.
    val contentWidthDp = minOf(config.screenWidthDp - 40, 480 - 40)
    val previewHeight = minOf(contentWidthDp * 4f / 3f, config.screenHeightDp * 0.42f).dp

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            // The app is edge-to-edge, so this sheet draws *underneath* the system
            // navigation bar and has to inset itself — every other full-height sheet and
            // screen here does the same (see TaskSheetContent).
            //
            // Without it the only thing holding the claim button clear of the navigation
            // bar was the fixed bottom padding below, which is enough for gesture
            // navigation's ~24dp hint bar but not for a 3-button navigation bar at ~48dp.
            // On 3-button devices the bar covered the lower half of the button, so it
            // rendered as a bare gradient strip with its vertically-centred label hidden
            // below the cut — the button looked like it had lost its text.
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            // Breathing room *above* the navigation bar. Was 28dp while this had to
            // double as the inset itself; the real inset now handles that.
            .padding(bottom = 16.dp)
    ) {
        // Everything above the claim button lives in its own scrolling region, and the
        // button is pinned below it. `fill = false` lets the sheet keep wrapping short
        // content, but once the copy and preview would overflow, this region is capped
        // instead of pushing the button past the bottom of the sheet — which is how the
        // button used to render as a bare gradient strip with its label cut off, leaving
        // no way to tell it was the control that banks your earned minutes.
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = tr("sweat_title"),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Box(Modifier.height(4.dp))
            Text(
                text = tr("sweat_subtitle"),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )

            Box(Modifier.height(16.dp))

            // ── Camera preview + live rep overlay ─────────────────────────────
            // A 3:4 preview is 1.33× the content width, which on a tall, narrow phone
            // fills the sheet on its own. Cap it against the screen height so the
            // coaching cue and the claim button always keep their room. The preview
            // and the skeleton overlay both scale FILL_CENTER, so a shorter box simply
            // crops rather than distorting or misaligning the joints.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
                    .clip(RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.TopCenter
            ) {
                if (hasCamera) {
                    PoseCameraView(
                        onRep = { reps++ },
                        onPose = { poseFrame = it },
                        onFeedback = { feedback = it },
                    )
                    // Live skeleton drawn on top of the camera feed (motion tracking).
                    PoseOverlay(
                        frame = poseFrame,
                        boneColor = colors.cyan,
                        jointColor = colors.teal,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("sweat_camera_needed"),
                            color = colors.textMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Big live rep counter pinned at the top of the preview.
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$reps",
                        color = colors.teal,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tr("sweat_reps"),
                        color = colors.cyan,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Live form coaching cue ────────────────────────────────────────
            if (hasCamera) {
                Box(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.card)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tr(feedback.stringKey),
                        color = if (feedback == RepFeedback.GOOD) colors.green else colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
                Box(Modifier.height(8.dp))
                val remainingMinutes = remainingUnlockMinutes(focusState.unlockUntilMillis, nowElapsedMs)
                Text(
                    text = if (remainingMinutes > 0) {
                        tr("sweat_access_remaining", remainingMinutes)
                    } else {
                        tr("sweat_access_ready", reps)
                    },
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Box(Modifier.height(20.dp))

        // ── Claim button ──────────────────────────────────────────────────────
        // Each completed rep is worth exactly one minute of access.
        val minutes = reps
        val enabled = reps > 0
        val shape = RoundedCornerShape(14.dp)
        Button(
            onClick = {
                FocusController.grantTemporaryUnlock(context, minutes * 60)
                onClaim()
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                // A minimum, not a fixed height: at large font scales a hard 54dp
                // clips the button's own label.
                .heightIn(min = 54.dp)
                .clip(shape)
                .then(
                    if (enabled) {
                        Modifier.background(Brush.linearGradient(listOf(colors.teal, colors.cyan)))
                    } else {
                        Modifier
                    }
                ),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = colors.cardBorder,
                disabledContentColor = colors.textMuted
            )
        ) {
            Text(
                text = if (enabled) tr("sweat_claim", minutes) else tr("sweat_do_one"),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
      }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CameraX + ML Kit pose analysis
// ─────────────────────────────────────────────────────────────────────────────

@AndroidxOptIn(ExperimentalGetImage::class)
@Composable
private fun PoseCameraView(
    onRep: () -> Unit,
    onPose: (PoseFrame?) -> Unit,
    onFeedback: (RepFeedback) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Single-thread executor for image analysis; closed on dispose.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Build the detector once; null if ML Kit is unavailable (graceful degrade).
    val detector = remember {
        runCatching {
            val options = AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build()
            PoseDetection.getClient(options)
        }.getOrNull()
    }

    // Rep state machine — survives recomposition.
    val repCounter = remember { RepCounter() }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxWidth()
    )

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            runCatching {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || detector == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val input = InputImage.fromMediaImage(mediaImage, rotation)
                    // ML Kit returns landmarks in the upright (rotation-applied) frame,
                    // so swap width/height for 90°/270° rotations.
                    val upright = rotation == 90 || rotation == 270
                    val srcW = (if (upright) imageProxy.height else imageProxy.width).toFloat()
                    val srcH = (if (upright) imageProxy.width else imageProxy.height).toFloat()
                    val frameTimeMs = System.currentTimeMillis()
                    detector.process(input)
                        .addOnSuccessListener { pose ->
                            val repCompleted = repCounter.update(pose, srcW, srcH, frameTimeMs)
                            // Front camera shows a mirrored preview, so mirror the skeleton too.
                            val frame = buildPoseFrame(pose, srcW, srcH, mirror = true)
                            val cue = repCounter.feedback
                            // Hop back to main thread for all UI mutation.
                            previewView.post {
                                if (repCompleted) onRep()
                                onPose(frame)
                                onFeedback(cue)
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }

                // Front camera is most natural for self-recorded exercise.
                val selector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            }.onFailure { Log.w("SweatUnlock", "Camera bind failed", it) }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            runCatching { detector?.close() }
            analysisExecutor.shutdown()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Skeleton overlay (motion tracking)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * An immutable snapshot of the joints we draw, in the upright source-image
 * coordinate space. Built off the analysis thread and handed to Compose so the
 * overlay never touches mutable ML Kit objects.
 */
internal data class PoseFrame(
    val points: Map<Int, Offset>,
    val srcWidth: Float,
    val srcHeight: Float,
    val mirror: Boolean,
)

/** Landmark pairs that form the drawn skeleton. */
private val BONES: List<Pair<Int, Int>> = listOf(
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
    PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
    PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
    PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
    PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
    PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
)

/** Landmarks collected for the overlay; matches everything referenced by [BONES]. */
private val OVERLAY_LANDMARKS: List<Int> = listOf(
    PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
    PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
    PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
    PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE,
)

/** Extracts the confident overlay landmarks into a thread-safe [PoseFrame]. */
private fun buildPoseFrame(pose: Pose, srcW: Float, srcH: Float, mirror: Boolean): PoseFrame? {
    if (srcW <= 0f || srcH <= 0f) return null
    val points = HashMap<Int, Offset>(OVERLAY_LANDMARKS.size)
    for (type in OVERLAY_LANDMARKS) {
        val lm = pose.getPoseLandmark(type) ?: continue
        if (lm.inFrameLikelihood < OVERLAY_MIN_CONF) continue
        points[type] = Offset(lm.position.x, lm.position.y)
    }
    if (points.isEmpty()) return null
    return PoseFrame(points, srcW, srcH, mirror)
}

private const val OVERLAY_MIN_CONF = 0.5f

/**
 * Draws the detected skeleton over the camera preview, mapping source-image
 * coordinates to view coordinates with the same FILL_CENTER (center-crop) math
 * [PreviewView] uses, mirroring horizontally for the front camera.
 */
@Composable
private fun PoseOverlay(
    frame: PoseFrame?,
    boneColor: Color,
    jointColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val f = frame ?: return@Canvas
        val scale = max(size.width / f.srcWidth, size.height / f.srcHeight)
        val dx = (size.width - f.srcWidth * scale) / 2f
        val dy = (size.height - f.srcHeight * scale) / 2f

        fun project(p: Offset): Offset {
            val x = p.x * scale + dx
            val y = p.y * scale + dy
            return Offset(if (f.mirror) size.width - x else x, y)
        }

        val mapped = f.points.mapValues { project(it.value) }

        for ((a, b) in BONES) {
            val pa = mapped[a] ?: continue
            val pb = mapped[b] ?: continue
            drawLine(
                color = boneColor,
                start = pa,
                end = pb,
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
        }
        for (p in mapped.values) {
            drawCircle(color = jointColor, radius = 9f, center = p)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Live form coaching cue
// ─────────────────────────────────────────────────────────────────────────────

/** Coaching cue surfaced under the camera; [stringKey] resolves via [tr]. */
internal enum class RepFeedback(val stringKey: String) {
    NO_BODY("sweat_fb_frame"),
    TURN_SIDEWAYS("sweat_fb_sideways"),
    KEEP_STRAIGHT("sweat_fb_straight"),
    GO_LOWER("sweat_fb_lower"),
    PUSH_UP("sweat_fb_push"),
    GOOD("sweat_fb_good"),
}

internal fun remainingUnlockMinutes(unlockUntilMillis: Long, nowElapsedMs: Long): Int {
    val remainingMs = unlockUntilMillis - nowElapsedMs
    if (remainingMs <= 0L) return 0
    return ((remainingMs + 59_999L) / 60_000L).toInt()
}

// ─────────────────────────────────────────────────────────────────────────────
// Rep-counting state machine
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Counts push-ups and stand-ups (squats) from ML Kit pose frames.
 *
 * Accuracy is built from several layers:
 *  - **Exercise-specific routing** — push-ups are evaluated from arm extension
 *    and plank form from any camera angle, while vertical torso frames count
 *    stand-ups. Ambiguous frames do not feed stand-ups.
 *  - **Exercise-specific form checks** — push-ups need a straight plank while
 *    stand-ups need both hip and knee flexion, not just a moving knee.
 *  - **Confirmed endpoints** — a full down → up cycle must remain at each endpoint
 *    for multiple frames. Brief landmark dropouts are tolerated without accepting
 *    uncertain movement.
 *  - **Bilateral agreement and debounce** — contradictory left/right estimates and
 *    impossibly fast reps are rejected.
 *
 * All readings require a confidence floor, so a partially-visible body simply
 * counts nothing rather than counting wrong.
 */
private class RepCounter {
    private val orientation = StableOrientationDetector()
    private val pushUp = PushUpDetector()
    // Viewpoint-robust backup: counts the body's vertical down→up travel even when
    // foreshortening hides the elbow swing (phone low and in front of the athlete).
    private val verticalRep = AdaptiveVerticalRepCounter()
    private val standUp = AthleteRepDetectors.standUp()

    // Per-joint low-pass filters tame ML Kit's frame-to-frame jitter for squats.
    private val kneeFilter = AngleSmoother()
    private val hipFilter = AngleSmoother()

    // Reject any rep that lands implausibly soon after the previous one.
    private val minRepIntervalMs = 600L
    private var lastRepMs = 0L

    /** Latest coaching cue, read by the UI after each [update]. */
    var feedback: RepFeedback = RepFeedback.NO_BODY
        private set

    /** Returns true exactly once when a full, valid rep completes. */
    fun update(pose: com.google.mlkit.vision.pose.Pose, srcW: Float, srcH: Float, nowMs: Long): Boolean {
        val torsoOrientation = orientation.update(torsoOrientation(pose))
        val frame = pushUpFrame(pose, srcW, srcH)
        val pushUpUpdate = pushUp.update(frame, nowMs)
        // Feed the vertical counter the torso centre (normalized image y), but only
        // when the shoulders and hips are reasonably confident, so noise can't drift it.
        val torsoY = frame?.let { f ->
            val conf = minOf(
                f.leftShoulder.confidence, f.rightShoulder.confidence,
                f.leftHip.confidence, f.rightHip.confidence,
            )
            if (conf >= 0.5f) {
                (f.leftShoulder.y + f.rightShoulder.y + f.leftHip.y + f.rightHip.y) / 4f
            } else {
                null
            }
        }
        val verticalRepCompleted = verticalRep.update(torsoY, nowMs)
        val knee = kneeFilter.update(averagedAngle(
            pose,
            PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE,
        ))
        val hip = hipFilter.update(averagedAngle(
            pose,
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE,
        ))
        var completed = pushUpUpdate.countedRep || verticalRepCompleted
        if (torsoOrientation == TorsoOrientation.VERTICAL && knee != null && hip != null) {
            if (standUp.update(MovementAngles(kneeAngle = knee, hipAngle = hip), nowMs)) {
                completed = true
            }
        } else {
            standUp.missingFrame()
        }

        feedback = computeFeedback(torsoOrientation, pushUpUpdate, verticalRep.inDownPhase, knee, hip)

        if (completed) {
            if (nowMs - lastRepMs < minRepIntervalMs) return false
            lastRepMs = nowMs
            feedback = RepFeedback.GOOD
            return true
        }
        return false
    }

    /**
     * Turns the current angles + detector phase into a single actionable cue.
     * Push-ups are coached whenever the arm and plank evidence is available;
     * this keeps front, side, and oblique camera angles usable.
     */
    private fun computeFeedback(
        torso: TorsoOrientation?,
        pushUpUpdate: PushUpUpdate,
        verticalDownPhase: Boolean,
        knee: Float?,
        hip: Float?,
    ): RepFeedback = when {
        // Squat / stand-up coaching takes priority when clearly upright.
        torso == TorsoOrientation.VERTICAL && knee != null && hip != null -> RepFeedback.GOOD
        // While the body is on its way down, always prompt the return — even if the
        // elbow-based guidance can't see the bend from this angle.
        verticalDownPhase -> RepFeedback.PUSH_UP
        else -> when (pushUpUpdate.guidance) {
            PushUpGuidance.FRAME_BODY -> RepFeedback.NO_BODY
            PushUpGuidance.TURN_SIDEWAYS -> RepFeedback.TURN_SIDEWAYS
            PushUpGuidance.GO_LOWER -> RepFeedback.GO_LOWER
            PushUpGuidance.PUSH_UP -> RepFeedback.PUSH_UP
            PushUpGuidance.GOOD -> RepFeedback.GOOD
        }
    }

    private fun pushUpFrame(
        pose: com.google.mlkit.vision.pose.Pose,
        srcW: Float,
        srcH: Float,
    ): PushUpPoseFrame? {
        if (srcW <= 0f || srcH <= 0f) return null
        fun lm(type: Int): NormalizedPoseLandmark? {
            val point = pose.getPoseLandmark(type) ?: return null
            return NormalizedPoseLandmark(
                x = point.position.x / srcW,
                y = point.position.y / srcH,
                confidence = point.inFrameLikelihood,
            )
        }
        return PushUpPoseFrame(
            leftShoulder = lm(PoseLandmark.LEFT_SHOULDER) ?: return null,
            rightShoulder = lm(PoseLandmark.RIGHT_SHOULDER) ?: return null,
            leftElbow = lm(PoseLandmark.LEFT_ELBOW) ?: return null,
            rightElbow = lm(PoseLandmark.RIGHT_ELBOW) ?: return null,
            leftWrist = lm(PoseLandmark.LEFT_WRIST) ?: return null,
            rightWrist = lm(PoseLandmark.RIGHT_WRIST) ?: return null,
            leftHip = lm(PoseLandmark.LEFT_HIP) ?: return null,
            rightHip = lm(PoseLandmark.RIGHT_HIP) ?: return null,
        )
    }

    /**
     * Torso orientation from the shoulder-midpoint → hip-midpoint vector. The
     * dead zone intentionally classifies diagonal frames as unknown.
     */
    private fun torsoOrientation(pose: com.google.mlkit.vision.pose.Pose): TorsoOrientation? {
        val shoulder = midpoint(pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            ?: return null
        val hip = midpoint(pose, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
            ?: return null
        val dx = abs(hip.first - shoulder.first)
        val dy = abs(hip.second - shoulder.second)
        val fromVertical = Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()
        return when {
            fromVertical <= 35f -> TorsoOrientation.VERTICAL
            fromVertical >= 55f -> TorsoOrientation.HORIZONTAL
            else -> null
        }
    }

    /** Midpoint (x, y) of two confident landmarks, or null if either is unreliable. */
    private fun midpoint(
        pose: com.google.mlkit.vision.pose.Pose,
        a: Int, b: Int,
    ): Pair<Float, Float>? {
        val la = pose.getPoseLandmark(a) ?: return null
        val lb = pose.getPoseLandmark(b) ?: return null
        if (la.inFrameLikelihood < MIN_CONF || lb.inFrameLikelihood < MIN_CONF) return null
        return Pair((la.position.x + lb.position.x) / 2f, (la.position.y + lb.position.y) / 2f)
    }

    /**
     * Average of the left and right joint angles; falls back to whichever is valid.
     * Contradictory sides are rejected instead of being averaged into a false endpoint.
     */
    private fun averagedAngle(
        pose: com.google.mlkit.vision.pose.Pose,
        aL: Int, bL: Int, cL: Int,
        aR: Int, bR: Int, cR: Int,
    ): Float? {
        val left = jointAngle(pose, aL, bL, cL)
        val right = jointAngle(pose, aR, bR, cR)
        return combinedAngle(left, right)
    }

    private fun combinedAngle(left: Float?, right: Float?): Float? {
        return when {
            left != null && right != null && abs(left - right) <= MAX_SIDE_DISAGREEMENT -> {
                (left + right) / 2f
            }
            left != null && right != null -> null
            else -> left ?: right
        }
    }

    /**
     * Angle (degrees) at landmark [b] formed by segments b→a and b→c.
     * Returns null if any landmark is missing or low-confidence.
     */
    private fun jointAngle(
        pose: com.google.mlkit.vision.pose.Pose,
        a: Int, b: Int, c: Int,
    ): Float? {
        val la = pose.getPoseLandmark(a) ?: return null
        val lb = pose.getPoseLandmark(b) ?: return null
        val lc = pose.getPoseLandmark(c) ?: return null
        if (la.inFrameLikelihood < MIN_CONF ||
            lb.inFrameLikelihood < MIN_CONF ||
            lc.inFrameLikelihood < MIN_CONF
        ) return null

        val pa = la.position
        val pb = lb.position
        val pc = lc.position

        val angAbc = Math.toDegrees(
            (atan2((pc.y - pb.y).toDouble(), (pc.x - pb.x).toDouble()) -
                atan2((pa.y - pb.y).toDouble(), (pa.x - pb.x).toDouble())).toDouble()
        ).toFloat()

        var normalized = abs(angAbc)
        if (normalized > 180f) normalized = 360f - normalized
        return normalized
    }

    private companion object {
        // Landmarks below this in-frame likelihood are ignored entirely.
        const val MIN_CONF = 0.65f
        const val MAX_SIDE_DISAGREEMENT = 35f
    }
}

/**
 * Holds an orientation only after consecutive matching frames. Unknown or
 * changing orientation never feeds either movement detector.
 */
internal class StableOrientationDetector(
    private val confirmationFrames: Int = 3,
) {
    private var candidate: TorsoOrientation? = null
    private var candidateFrames = 0
    private var stable: TorsoOrientation? = null

    fun update(reading: TorsoOrientation?): TorsoOrientation? {
        if (reading == null) {
            candidate = null
            candidateFrames = 0
            return null
        }
        if (reading == stable) return stable
        if (reading == candidate) {
            candidateFrames++
        } else {
            candidate = reading
            candidateFrames = 1
        }
        if (candidateFrames >= confirmationFrames) {
            stable = reading
            candidate = null
            candidateFrames = 0
            return stable
        }
        return null
    }
}

internal enum class TorsoOrientation { HORIZONTAL, VERTICAL }

/**
 * Exponential moving-average filter for a single joint angle. A null reading
 * (joint lost) is passed through untouched and does not corrupt the running
 * average — the next valid reading resumes smoothing from the last value.
 */
internal class AngleSmoother(private val alpha: Float = 0.5f) {
    private var value: Float? = null

    fun update(reading: Float?): Float? {
        // Joint lost this frame: report null so the rep detector counts it as a
        // missing frame, but keep the stored value to resume smoothing from.
        if (reading == null) return null
        val prev = value
        value = if (prev == null) reading else prev + alpha * (reading - prev)
        return value
    }
}

internal data class MovementAngles(
    val kneeAngle: Float = Float.NaN,
    val hipAngle: Float = Float.NaN,
)

internal object AthleteRepDetectors {
    fun standUp() = MovementRepDetector(
        isDown = { it.kneeAngle <= 115f && it.hipAngle <= 140f },
        isUp = { it.kneeAngle >= 165f && it.hipAngle >= 155f },
    )
}

/**
 * Confirms both endpoints over multiple frames, with a bounded time window.
 * A few missing frames are tolerated because ML Kit can briefly lose a joint.
 */
internal class MovementRepDetector(
    private val isDown: (MovementAngles) -> Boolean,
    private val isUp: (MovementAngles) -> Boolean,
    private val confirmationFrames: Int = 2,
    private val maxMissingFrames: Int = 4,
    private val minMovementMs: Long = 250L,
    private val maxMovementMs: Long = 8_000L,
) {
    private var downFrames = 0
    private var upFrames = 0
    private var missingFrames = 0
    private var downSinceMs = 0L
    private var isDownConfirmed = false

    /** True once the down endpoint is confirmed and we're waiting for the up endpoint. */
    val inDownPhase: Boolean get() = isDownConfirmed

    fun update(angles: MovementAngles, nowMs: Long): Boolean {
        missingFrames = 0
        if (!isDownConfirmed) {
            downFrames = if (isDown(angles)) downFrames + 1 else 0
            if (downFrames >= confirmationFrames) {
                isDownConfirmed = true
                downSinceMs = nowMs
                upFrames = 0
            }
            return false
        }

        if (nowMs - downSinceMs > maxMovementMs) {
            reset()
            return false
        }
        upFrames = if (isUp(angles)) upFrames + 1 else 0
        if (upFrames < confirmationFrames) return false

        val durationMs = nowMs - downSinceMs
        reset()
        return durationMs >= minMovementMs
    }

    fun missingFrame() {
        missingFrames++
        if (missingFrames > maxMissingFrames) reset()
    }

    private fun reset() {
        downFrames = 0
        upFrames = 0
        missingFrames = 0
        downSinceMs = 0L
        isDownConfirmed = false
    }
}
