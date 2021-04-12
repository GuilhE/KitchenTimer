package com.github.guilhe.compose.kitchentimer.ui

import android.graphics.Camera
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import java.io.Serializable
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

// Camera coordinate system
//     +y
//      y
//      y
//      z  x  x +x
//    z
// +z

private const val SECONDS = 60
private const val SECONDS_LEFT = SECONDS * 1000L //*60
private const val GLOBE_RADIUS_FACTOR = 1.05f
private const val FIELD_OF_VIEW_FACTOR = .7f
private const val TWO_PI = Math.PI.toFloat() * 2
private const val DEFAULT_PHI = Math.PI.toFloat() / 1.7f
private const val STROKE_TRANSFORMATION_OFFSET = 35f //considering phi value of π/1.7
private const val NUMBER_TRANSFORMATION_OFFSET = 30f //considering phi value of π/1.7
private const val DEFAULT_ALPHA_Y_THRESHOLD = 30f
private const val BIG_STROKE_WIDTH = 10f
private const val BIG_STROKE_HEIGHT = 50f
private const val SMALL_STROKE_WIDTH = BIG_STROKE_WIDTH / 2
private const val SMALL_STROKE_HEIGHT = BIG_STROKE_HEIGHT / 2
private const val BG_COLOR = 0xFFD51E27
private const val ARC_COLOR = 0xFF000000
private const val SEC_COLOR = 0xFFFFFFFF
private const val START_SHIFT = 0.25f

private data class SecondInfo(val value: Int, val phi: Float, val theta: Float) : Serializable
private data class Info2D(val projectedX: Float, val projectedY: Float, val projectedScale: Float) : Serializable
data class TimerColors(
    val background: Color = Color(BG_COLOR),
    val stroke: Color = Color(SEC_COLOR),
    val number: Color = stroke,
    val tracker: Color = Color(SEC_COLOR),
    val arc: Color = Color(ARC_COLOR),
)

fun defaultColors() = TimerColors()

@Composable
fun KitchenTimer(
    modifier: Modifier,
    secondsInMillis: Long,
    colors: TimerColors = defaultColors(),
    isSettingTime: Boolean,
    onTick: (millisLeft: Long) -> Unit,
    debugMode: Boolean = false
) {
    var job: Job? = null
    val textPaint = Paint().apply {
        isAntiAlias = true
        color = colors.number.toArgb()
        textSize = 40f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val secs = rememberSaveable {
        (0 until SECONDS).map {
            SecondInfo(
                //Phi (vertical position): The polar angle (between -90° and 90°) [0 ; π]
                //Theta (y rotation): The azimuth angle (between 0 and 360°) [0 ; 2π]
                value = it,
                phi = DEFAULT_PHI,
                theta = normalize(it.toFloat(), 0f, SECONDS.toFloat(), 0f, TWO_PI)
            )
        }
    }
    val scope = rememberCoroutineScope()

    var lastMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var millisLeft by rememberSaveable { mutableStateOf(SECONDS_LEFT) }
    var progress by rememberSaveable { mutableStateOf(0f) }  //[0 ; 1] [0 ; 2Pi]

    lastMillis = System.currentTimeMillis()
    if (isSettingTime) {
        job?.cancel()
        millisLeft = secondsInMillis
        progress = normalize(millisLeft.toFloat(), 0f, SECONDS_LEFT.toFloat(), 0f, 1f)
    } else {
        DisposableEffect(key1 = 0) {
            job = scope.launch(context = Dispatchers.Unconfined) {
                while (true) {
                    yield()
                    withContext(Dispatchers.Main) {
                        val current = System.currentTimeMillis()
                        millisLeft = (millisLeft - (current - lastMillis)).coerceAtLeast(0)
                        lastMillis = current
                        progress = normalize(millisLeft.toFloat(), 0f, SECONDS_LEFT.toFloat(), 0f, 1f)
                        if (millisLeft <= 0L) {
                            job?.cancel()
                        }
                        onTick.invoke(millisLeft)
                    }
                }
            }
            onDispose { job?.cancel() }
        }
    }

    Canvas(modifier) {
        drawBackground(this, colors)
        secs.forEach {
            drawSeconds(this, it, (START_SHIFT + progress) * TWO_PI, textPaint, colors)
        }
        drawForeground(this)
        if (debugMode) {
            drawDebugLines(this)
        }
    }
}

private fun drawBackground(drawScope: DrawScope, colors: TimerColors) {
    with(drawScope) {
        //Draw background shape to simulate a sphere
        drawCircle(
            color = colors.background,
            radius = size.minDimension / 2.0f,
            center = size.center
        )

        //Draw lines to simulate point of separation (2 half spheres).
        //Considering phi = π/1.7 we have height = 170 and offset.y = size.center.y - 120
        val stroke = Stroke((2.5).dp.toPx())
        val arcSize = Size(width = size.minDimension - 3f, 170f)
        val offset = Offset(size.center.x - arcSize.width / 2, size.center.y - 120f)
        drawArc(color = colors.arc, 0f, 180f, false, offset, arcSize, 1f, stroke, blendMode = BlendMode.Overlay)
        drawArc(colors.arc, 0f, 180f, false, offset.copy(y = offset.y + 7), arcSize, .4f, stroke, blendMode = BlendMode.Overlay)

        // Draw triangle (time pointer)
        drawPath(color = colors.tracker, alpha = 0.8f, path = Path().apply {
            moveTo(size.center.x, size.center.y + 70)
            lineTo(size.center.x - 25, size.center.y + 115)
            lineTo(size.center.x + 25, size.center.y + 115)
        })
    }
}

private fun drawForeground(drawScope: DrawScope) {
    with(drawScope) {
        drawCircle(
//            brush = Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = .5f), Color.Black, Color.Black)),
            brush = Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = .5f), Color.Black, Color.Black, Color.Black)),
            radius = size.minDimension / 2.0f,
            center = size.center,
            blendMode = BlendMode.Overlay
        )
    }
}

private fun drawDebugLines(drawScope: DrawScope) {
    with(drawScope) {
        //Green for canvas limits
        drawLine(Color.Green, Offset(0f, 0f), Offset(0f, size.height), 4f)
        drawLine(Color.Green, Offset(0f, 0f), Offset(size.width, 0f), 4f)
        drawLine(Color.Green, Offset(size.width, 0f), Offset(size.width, size.height), 4f)
        drawLine(Color.Green, Offset(0f, size.height), Offset(size.width, size.height), 4f)

        //Yellow for canvas horizontal and vertical center
        drawLine(Color.Yellow, Offset(0f, size.center.y), Offset(size.width, size.center.y), 4f)
        drawLine(Color.Yellow, Offset(size.center.x, 0f), Offset(size.center.x, size.height), 4f)

        //Cyan for alpha horizontal thresholds
        drawLine(
            Color.Cyan,
            Offset(0f, drawScope.size.center.y - DEFAULT_ALPHA_Y_THRESHOLD),
            Offset(size.width, drawScope.size.center.y - DEFAULT_ALPHA_Y_THRESHOLD),
            4f
        )

        //Gray for number transformations offset
        drawLine(
            Color.Gray,
            Offset(size.center.x - size.minDimension / 2 + NUMBER_TRANSFORMATION_OFFSET, 0f),
            Offset(size.center.x - size.minDimension / 2 + NUMBER_TRANSFORMATION_OFFSET, size.height),
            4f
        )
        drawLine(
            Color.Gray,
            Offset(size.center.x + size.minDimension / 2 - NUMBER_TRANSFORMATION_OFFSET, 0f),
            Offset(size.center.x + size.minDimension / 2 - NUMBER_TRANSFORMATION_OFFSET, size.height),
            4f
        )

        //LightGray for stroke transformations offset
        drawLine(
            Color.LightGray,
            Offset(size.center.x - size.minDimension / 2 + STROKE_TRANSFORMATION_OFFSET, 0f),
            Offset(size.center.x - size.minDimension / 2 + STROKE_TRANSFORMATION_OFFSET, size.height),
            4f
        )
        drawLine(
            Color.LightGray,
            Offset(size.center.x + size.minDimension / 2 - STROKE_TRANSFORMATION_OFFSET, 0f),
            Offset(size.center.x + size.minDimension / 2 - STROKE_TRANSFORMATION_OFFSET, size.height),
            4f
        )
    }
}

private fun drawSeconds(drawScope: DrawScope, second: SecondInfo, rotationY: Float, textPaint: Paint, colors: TimerColors) {
    val info2d = map3Dto2D(drawScope, second.phi, second.theta, rotationY, (drawScope.size.minDimension * GLOBE_RADIUS_FACTOR))

    // Shift to "center" considering phi = π/1.7
    val finalY = info2d.projectedY + round(drawScope.size.minDimension / 9)
    val alphaThreshold = drawScope.size.center.y - DEFAULT_ALPHA_Y_THRESHOLD
    val strokeAlpha = if (finalY < alphaThreshold) {
        normalize(finalY, alphaThreshold - 20f, alphaThreshold, 0f, 0.8f)
    } else 0.8f

    val isM5 = second.value % 5 == 0
    val isM10 = second.value % 10 == 0
    drawSecondStroke(drawScope, info2d.projectedX, finalY, isM5, strokeAlpha, colors)
    if (isM10) {
        drawSecondNumber(drawScope, info2d.projectedX, finalY, second.value, textPaint, strokeAlpha)
    }
}

private fun drawSecondStroke(drawScope: DrawScope, x: Float, y: Float, isBig: Boolean, alpha: Float = 1f, colors: TimerColors) {
    val matrix = android.graphics.Matrix()
    val camera = Camera()
    val minuteStrokeWith = if (isBig) BIG_STROKE_WIDTH else SMALL_STROKE_WIDTH

    drawScope.drawIntoCanvas { canvas ->
        applyStrokeTransformations(x, drawScope.size, camera)
        camera.getMatrix(matrix)
        matrix.preTranslate(-x, -y)
        matrix.postTranslate(x, y)

        canvas.save()
        canvas.nativeCanvas.concat(matrix)
        drawScope.drawLine(
            color = colors.stroke,
            start = Offset(x, y),
            end = Offset(x, y - if (isBig) BIG_STROKE_HEIGHT else SMALL_STROKE_HEIGHT),
            strokeWidth = minuteStrokeWith,
            alpha = alpha
        )
        canvas.restore()
    }
}

private fun drawSecondNumber(drawScope: DrawScope, x: Float, y: Float, number: Int, paint: Paint, alpha: Float = 1f) {
    val matrix = android.graphics.Matrix()
    val camera = Camera()
    val textY = y - BIG_STROKE_HEIGHT - 10f

    drawScope.drawIntoCanvas { canvas ->
        applyNumberTransformations(x, drawScope.size, camera)
        camera.getMatrix(matrix)
        matrix.preTranslate(-x, -textY)
        matrix.postTranslate(x, textY)

        canvas.save()
        canvas.nativeCanvas.concat(matrix)
        canvas.nativeCanvas.drawText("$number", x, textY, paint.apply { this.alpha = (alpha * 255).toInt() })
        canvas.restore()
    }
}

private fun applyStrokeTransformations(projectedX: Float, canvasDim: Size, camera: Camera) {
    val canvasCenterX = canvasDim.center.x
    val canvasStartX = canvasCenterX - canvasDim.minDimension / 2
    val canvasEndX = canvasCenterX + canvasDim.minDimension / 2
    val canvasStartOffsetX = canvasStartX + STROKE_TRANSFORMATION_OFFSET
    val canvasEndOffsetX = canvasEndX - STROKE_TRANSFORMATION_OFFSET
    if (projectedX < canvasCenterX) {
        camera.rotate(
            0f,
            normalize(projectedX, canvasStartX, canvasCenterX, 80f, 0f),
            normalize(projectedX, canvasStartX, canvasStartOffsetX, -35f, 0f)
        )
    } else {
        camera.rotate(
            0f,
            normalize(projectedX, canvasCenterX, canvasEndX, 0f, -80f),
            normalize(projectedX, canvasEndOffsetX, canvasEndX, 0f, 35f)
        )
    }
}

private fun applyNumberTransformations(projectedX: Float, canvasDim: Size, camera: Camera) {
    val canvasCenterX = canvasDim.center.x
    val canvasStartX = canvasCenterX - canvasDim.minDimension / 2
    val canvasEndX = canvasCenterX + canvasDim.minDimension / 2
    val canvasStartOffsetX = canvasStartX + NUMBER_TRANSFORMATION_OFFSET
    val canvasEndOffsetX = canvasEndX - NUMBER_TRANSFORMATION_OFFSET
    if (projectedX < canvasCenterX) {
        camera.translate(
            normalize(projectedX, canvasStartX, canvasStartOffsetX, 10f, 0f),
            normalize(projectedX, canvasStartX, canvasStartOffsetX, -10f, 0f),
            0f
        )
        camera.rotateY(normalize(projectedX, canvasStartX, canvasCenterX, -90f, 0f))
        camera.rotateX(normalize(projectedX, canvasStartOffsetX, canvasCenterX, 10f, 0f))
        camera.rotateZ(normalize(projectedX, canvasStartOffsetX, canvasCenterX, -5f, 0f))
    } else {
        camera.translate(
            normalize(projectedX, canvasEndOffsetX, canvasEndX, 0f, -10f),
            normalize(projectedX, canvasEndOffsetX, canvasEndX, 0f, -10f),
            0f
        )
        camera.rotateY(normalize(projectedX, canvasCenterX, canvasEndX, 0f, 90f))
        camera.rotateX(normalize(projectedX, canvasCenterX, canvasEndOffsetX, 0f, 10f))
        camera.rotateZ(normalize(projectedX, canvasCenterX, canvasEndOffsetX, 0f, 5f))
    }
}

private fun map3Dto2D(drawScope: DrawScope, phi: Float, theta: Float, rotationY: Float, radius: Float): Info2D {
    // Calculate coordinates in 3D plane.
    val x = radius * sin(phi) * cos(theta)
    val y = radius * cos(phi)
    val z = radius * sin(phi) * sin(theta) - radius

    // Rotate 3D coordinates about the y-axis.
    val rotatedX = cos(rotationY) * x + sin(rotationY) * (z + radius)
    val rotatedZ = -sin(rotationY) * x + cos(rotationY) * (z + radius) - radius

    // Project the rotated 3D coordinates onto the 2D canvas.
    val fieldOfView = drawScope.size.minDimension * FIELD_OF_VIEW_FACTOR
    val projectedScale = fieldOfView / (fieldOfView - rotatedZ)
    val projectedX: Float = ((rotatedX * projectedScale) + drawScope.size.width / 2f)
    val projectedY: Float = ((y * projectedScale) + drawScope.size.height / 2f)

    return Info2D(projectedX, projectedY, projectedScale)
}

fun normalize(value: Float, fromMin: Float, fromMax: Float, toMin: Float, toMax: Float): Float {
    //[min,max] to [a,b] >>> f(x) = (b - a) (x - min) / (max - min) + a
    val v = when {
        value > fromMax -> fromMax
        value < fromMin -> fromMin
        else -> value
    }
    return (toMax - toMin) * (v - fromMin) / (fromMax - fromMin) + toMin
}