package com.example.visionbridge.gesture

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.sqrt

/**
 * Non-intrusive Compose Modifier that detects two-finger double-taps.
 *
 * Characteristics:
 * - Uses PointerEventPass.Initial to inspect multi-touch events without consuming
 *   single-pointer touches, ensuring all buttons, cards, scrolls, camera viewports,
 *   and TalkBack accessibility gestures operate without interference.
 * - Detects: Tap 1 (2 fingers down -> up within 350ms) -> Interval (<400ms) -> Tap 2 (2 fingers down -> up within 350ms).
 * - Ignores single taps, long presses, scrolls, and 3+ finger gestures.
 */
fun Modifier.twoFingerDoubleTapGesture(
    onGesture: () -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        var firstTapDownTime = 0L
        var firstTapUpTime = 0L
        var firstTapMidpoint = Offset.Zero
        var waitingForSecondTap = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val changes = event.changes
            val now = System.currentTimeMillis()

            // If waiting for second tap but window expired, reset state
            if (waitingForSecondTap && (now - firstTapUpTime > MAX_DOUBLE_TAP_INTERVAL_MS)) {
                waitingForSecondTap = false
                firstTapDownTime = 0L
                firstTapUpTime = 0L
            }

            val pressedPointers = changes.filter { it.pressed }

            if (pressedPointers.size == 2) {
                val p1 = pressedPointers[0]
                val p2 = pressedPointers[1]
                val midpoint = Offset(
                    (p1.position.x + p2.position.x) / 2f,
                    (p1.position.y + p2.position.y) / 2f
                )

                if (!waitingForSecondTap) {
                    // Tap 1 Down: Record start time and midpoint
                    if (firstTapDownTime == 0L) {
                        firstTapDownTime = now
                        firstTapMidpoint = midpoint
                    }
                } else {
                    // Tap 2 Down: Check spatial proximity to first tap
                    val tap2DownTime = now
                    val dx = midpoint.x - firstTapMidpoint.x
                    val dy = midpoint.y - firstTapMidpoint.y
                    val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                    if (dist < MAX_TAP_DISTANCE_PX) {
                        // Wait for tap 2 release
                        var tap2Finished = false
                        while (!tap2Finished) {
                            val nextEvent = awaitPointerEvent(PointerEventPass.Initial)
                            val remainingPressed = nextEvent.changes.filter { it.pressed }
                            val tap2Duration = System.currentTimeMillis() - tap2DownTime

                            if (remainingPressed.isEmpty() || remainingPressed.size < 2) {
                                tap2Finished = true
                                if (tap2Duration <= MAX_TAP_DURATION_MS) {
                                    // Successfully recognized two-finger double-tap!
                                    waitingForSecondTap = false
                                    firstTapDownTime = 0L
                                    firstTapUpTime = 0L
                                    onGesture()
                                }
                            } else if (tap2Duration > MAX_TAP_DURATION_MS) {
                                // Tap 2 held too long (e.g. two-finger drag/pinch), reject
                                tap2Finished = true
                                waitingForSecondTap = false
                                firstTapDownTime = 0L
                            }
                        }
                    } else {
                        waitingForSecondTap = false
                        firstTapDownTime = 0L
                    }
                }
            } else if (pressedPointers.isEmpty() && firstTapDownTime != 0L && !waitingForSecondTap) {
                // Both pointers released after Tap 1 Down
                val tap1Duration = now - firstTapDownTime
                if (tap1Duration <= MAX_TAP_DURATION_MS) {
                    firstTapUpTime = now
                    waitingForSecondTap = true
                }
                firstTapDownTime = 0L
            } else if (pressedPointers.size > 2) {
                // 3+ fingers: cancel gesture tracking
                firstTapDownTime = 0L
                waitingForSecondTap = false
            }
        }
    }
}

private const val MAX_TAP_DURATION_MS = 350L
private const val MAX_DOUBLE_TAP_INTERVAL_MS = 400L
private const val MAX_TAP_DISTANCE_PX = 200f
