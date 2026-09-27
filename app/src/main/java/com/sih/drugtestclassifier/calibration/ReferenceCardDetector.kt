package com.sih.drugtestclassifier.calibration

import android.graphics.Bitmap
import android.graphics.Color
import com.sih.drugtestclassifier.color.ColorSpace
import com.sih.drugtestclassifier.models.CardDetectionFailure
import com.sih.drugtestclassifier.models.CardDetectionResult
import com.sih.drugtestclassifier.models.RgbColor
import kotlin.math.floor

/**
 * Finds the reference colour card in a captured frame. Ported 1:1 from
 * lib/calibration/reference_card_detector.dart — same coarse-grid /
 * flood-fill white-patch search, same constants, same DFS-via-stack
 * traversal order (the Dart version used `queue.removeLast()`, i.e. a
 * stack, not a FIFO queue — replicated here with an ArrayDeque used as a
 * stack) so results match exactly.
 *
 * Fails closed: if it's not confident, returns notFound rather than
 * guessing, because a wrong calibration is worse than no calibration.
 */
class ReferenceCardDetector(
    private val gridSize: Int = 100,
    private val minWhiteValue: Double = 0.55,
    private val maxWhiteSaturation: Double = 0.30,
    private val minBlobFractionOfFrame: Double = 0.01,
) {

    fun detect(image: Bitmap): CardDetectionResult {
        val gw = gridSize
        val gh = gridSize
        val cellW = image.width.toDouble() / gw
        val cellH = image.height.toDouble() / gh

        // 1-2: build a boolean grid of "looks like the white patch" cells.
        val mask = Array(gh) { BooleanArray(gw) }
        for (gy in 0 until gh) {
            for (gx in 0 until gw) {
                val px = floor((gx + 0.5) * cellW).toInt().coerceIn(0, image.width - 1)
                val py = floor((gy + 0.5) * cellH).toInt().coerceIn(0, image.height - 1)
                val pixel = image.getPixel(px, py)
                val rgb = RgbColor(
                    Color.red(pixel).toDouble(),
                    Color.green(pixel).toDouble(),
                    Color.blue(pixel).toDouble(),
                )
                val (_, s, v) = ColorSpace.rgbToHsv(rgb)
                mask[gy][gx] = v >= minWhiteValue && s <= maxWhiteSaturation
            }
        }

        // 3: flood-fill (DFS via explicit stack) to find the largest connected blob.
        val visited = Array(gh) { BooleanArray(gw) }
        var bestSize = 0
        var bestMinX = 0
        var bestMaxX = 0
        var bestMinY = 0
        var bestMaxY = 0

        val deltas = arrayOf(
            intArrayOf(1, 0),
            intArrayOf(-1, 0),
            intArrayOf(0, 1),
            intArrayOf(0, -1),
        )

        for (gy in 0 until gh) {
            for (gx in 0 until gw) {
                if (!mask[gy][gx] || visited[gy][gx]) continue

                var size = 0
                var minX = gx
                var maxX = gx
                var minY = gy
                var maxY = gy
                val stack = ArrayDeque<IntArray>()
                stack.addLast(intArrayOf(gx, gy))
                visited[gy][gx] = true

                while (stack.isNotEmpty()) {
                    val cell = stack.removeLast()
                    val cx = cell[0]
                    val cy = cell[1]
                    size++
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy

                    for (d in deltas) {
                        val nx = cx + d[0]
                        val ny = cy + d[1]
                        if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue
                        if (visited[ny][nx] || !mask[ny][nx]) continue
                        visited[ny][nx] = true
                        stack.addLast(intArrayOf(nx, ny))
                    }
                }

                if (size > bestSize) {
                    bestSize = size
                    bestMinX = minX
                    bestMaxX = maxX
                    bestMinY = minY
                    bestMaxY = maxY
                }
            }
        }

        if (bestSize == 0) {
            return CardDetectionResult.notFound(CardDetectionFailure.NOT_FOUND)
        }

        val blobFraction = bestSize.toDouble() / (gw * gh)
        if (blobFraction < minBlobFractionOfFrame) {
            return CardDetectionResult.notFound(CardDetectionFailure.TOO_SMALL)
        }

        val left = Math.round((bestMinX * cellW)).toInt()
        val top = Math.round((bestMinY * cellH)).toInt()
        val width = Math.round(((bestMaxX - bestMinX + 1) * cellW)).toInt()
        val height = Math.round(((bestMaxY - bestMinY + 1) * cellH)).toInt()

        return CardDetectionResult.found(left, top, width, height)
    }
}
