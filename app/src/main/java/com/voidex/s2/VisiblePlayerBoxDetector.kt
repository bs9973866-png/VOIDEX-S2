package com.voidex.s2

import android.graphics.RectF
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.max
import kotlin.math.min

/**
 * Converts ML Kit pose landmarks into a screen-space person candidate.
 * A candidate is emitted only when a coherent set of body landmarks is present.
 * Pose detection is not guaranteed to recognize game-rendered characters.
 */
object VisiblePlayerBoxDetector {
    fun detect(pose: Pose, imageWidth: Int, imageHeight: Int): List<RectF> {
        val landmarks = pose.allPoseLandmarks.filter { it.inFrameLikelihood >= 0.50f }
        if (landmarks.size < 7) return emptyList()

        val hasUpperBody = landmarks.any {
            it.landmarkType == PoseLandmark.LEFT_SHOULDER ||
            it.landmarkType == PoseLandmark.RIGHT_SHOULDER
        }
        val hasCore = landmarks.any {
            it.landmarkType == PoseLandmark.LEFT_HIP ||
            it.landmarkType == PoseLandmark.RIGHT_HIP ||
            it.landmarkType == PoseLandmark.LEFT_KNEE ||
            it.landmarkType == PoseLandmark.RIGHT_KNEE
        }
        if (!hasUpperBody || !hasCore) return emptyList()

        var left = imageWidth.toFloat()
        var top = imageHeight.toFloat()
        var right = 0f
        var bottom = 0f
        landmarks.forEach { landmark ->
            left = min(left, landmark.position.x)
            top = min(top, landmark.position.y)
            right = max(right, landmark.position.x)
            bottom = max(bottom, landmark.position.y)
        }

        val width = right - left
        val height = bottom - top
        if (width < imageWidth * 0.018f || height < imageHeight * 0.045f) return emptyList()
        if (width > imageWidth * 0.70f || height > imageHeight * 0.90f) return emptyList()
        if (height / width.coerceAtLeast(1f) < 0.45f) return emptyList()

        val padX = width * 0.28f
        val padTop = height * 0.12f
        val padBottom = height * 0.10f
        return listOf(
            RectF(
                (left - padX).coerceAtLeast(0f),
                (top - padTop).coerceAtLeast(0f),
                (right + padX).coerceAtMost(imageWidth.toFloat()),
                (bottom + padBottom).coerceAtMost(imageHeight.toFloat())
            )
        )
    }
}
