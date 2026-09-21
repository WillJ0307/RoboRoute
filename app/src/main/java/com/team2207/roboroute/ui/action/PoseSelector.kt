package com.team2207.roboroute.ui.action

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team2207.roboroute.R
import com.team2207.roboroute.serial.RobotPoseManager
import com.team2207.roboroute.ui.components.FullScreenImage
import com.team2207.roboroute.ui.theme.BlueAlliance
import com.team2207.roboroute.ui.theme.RedAlliance
import com.team2207.roboroute.ui.theme.returnPrimaryColor
import com.team2207.roboroute.ui.theme.returnSecondaryColor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// FRC Field Dimensions (Meters)
const val FIELD_WIDTH_METERS = 16.541
const val FIELD_HEIGHT_METERS = 8.211

@Composable
fun PoseSelectorMainView(
    onBack: () -> Unit,
    onConfirm: (Double, Double, Double) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    initialX: Double = 0.0,
    initialY: Double = 0.0,
    initialR: Double = 0.0, // RADIANS
    robotWidthMeter: Double = 0.6,
    robotLengthMeter: Double = 0.6,
) {
    var robotOffset by remember { mutableStateOf(Offset.Zero) }
    var robotRotation by remember { mutableStateOf(initialR.toFloat()) }
    var isInitialized by remember { mutableStateOf(false) }
    // Guards against a fast double-tap popping the back stack more than once.
    var isNavigating by remember { mutableStateOf(false) }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            val containerWidth = maxWidth
            val containerHeight = maxHeight

            val painter = painterResource(id = R.drawable.field_2026)
            val imgWidth = painter.intrinsicSize.width
            val imgHeight = painter.intrinsicSize.height

            val scale = min(containerWidth.value / imgWidth, containerHeight.value / imgHeight)
            val fitWidthDp = (imgWidth * scale).dp
            val fitHeightDp = (imgHeight * scale).dp

            if (!isInitialized) {
                // Mapping: Bottom-Right is (0,0). Left is +Y, Up is +X.
                val xPx = -(initialX / FIELD_WIDTH_METERS * fitHeightDp.value)
                val yPx = -(initialY / FIELD_HEIGHT_METERS * fitWidthDp.value)
                robotOffset = Offset(yPx.toFloat(), xPx.toFloat())
                isInitialized = true
            }

            FullScreenImage(modifier = Modifier.fillMaxSize())

            IconButton(
                onClick = {
                    if (!isNavigating) {
                        isNavigating = true
                        onBack()
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = returnPrimaryColor(),
                        contentColor = returnSecondaryColor(),
                    ),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            IconButton(
                onClick = {
                    if (!isNavigating) {
                        isNavigating = true
                        val frcX = (-robotOffset.y.dp.value / fitHeightDp.value) * FIELD_WIDTH_METERS
                        val frcY = (-robotOffset.x.dp.value / fitWidthDp.value) * FIELD_HEIGHT_METERS
                        onConfirm(frcX, frcY, robotRotation.toDouble())
                        onBack()
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = returnPrimaryColor(),
                        contentColor = returnSecondaryColor(),
                    ),
            ) {
                Icon(Icons.Default.Check, contentDescription = "Confirm")
            }

            // Field Container
            Box(
                modifier =
                    Modifier
                        .size(fitWidthDp, fitHeightDp)
                        .align(Alignment.Center),
            ) {
                val robotWidthDp = (robotWidthMeter / FIELD_HEIGHT_METERS * fitWidthDp.value).dp
                val robotLengthDp = (robotLengthMeter / FIELD_WIDTH_METERS * fitHeightDp.value).dp

                RobotPoseEdit(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset {
                                IntOffset(
                                    (robotOffset.x.dp.toPx() + (robotWidthDp.toPx() / 2)).roundToInt(),
                                    (robotOffset.y.dp.toPx() + (robotLengthDp.toPx() / 2)).roundToInt(),
                                )
                            },
                    rotation = robotRotation,
                    onMove = { delta ->
                        robotOffset += delta
                    },
                    onRotate = { newRotation ->
                        robotRotation = newRotation
                    },
                    robotWidth = robotWidthDp,
                    robotLength = robotLengthDp,
                )
            }

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
            ) {
                val frcX = (-robotOffset.y.dp.value / fitHeightDp.value) * FIELD_WIDTH_METERS
                val frcY = (-robotOffset.x.dp.value / fitWidthDp.value) * FIELD_HEIGHT_METERS

                Text(text = "X: ${"%.2f".format(frcX)}m", color = Color.Red, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = "Y: ${"%.2f".format(frcY)}m", color = Color.Green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = "R: ${"%.2f".format(robotRotation)} rad", color = Color.Blue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RobotVisual(
    modifier: Modifier = Modifier,
    showControls: Boolean = false,
    robotWidth: Dp = 60.dp,
    robotLength: Dp = 60.dp,
) {
    val extensionLength = 40.dp
    val dotSize = 16.dp
    val strokeWidth = 2.dp
    val isRedAlliance by RobotPoseManager.isRedAlliance.collectAsState()
    val primaryColor = if (isRedAlliance) RedAlliance else BlueAlliance

    // Total height of drawing including control extension if shown
    val drawingHeight = if (showControls) (dotSize / 2) + extensionLength + robotLength else robotLength

    Canvas(modifier = modifier.size(width = robotWidth, height = drawingHeight)) {
        val centerX = size.width / 2f
        val robotWidthPx = robotWidth.toPx()
        val robotLengthPx = robotLength.toPx()
        val strokeWidthPx = strokeWidth.toPx()
        val extensionPx = extensionLength.toPx()
        val dotRadiusPx = (dotSize / 2f).toPx()

        val squareTopY = if (showControls) dotRadiusPx + extensionPx else 0f
        val centerY = squareTopY + (robotLengthPx / 2f)

        // Draw robot body
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(centerX - robotWidthPx / 2f, squareTopY),
            size = Size(robotWidthPx, robotLengthPx),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(width = strokeWidthPx),
        )

        // Draw Front Arrow - Tips at the front edge
        val arrowPath =
            Path().apply {
                val arrowWidth = robotWidthPx * 0.5f
                val arrowHeight = robotLengthPx * 0.3f

                // Front edge is squareTopY
                moveTo(centerX, squareTopY) // Tip at very front
                lineTo(centerX - arrowWidth / 2f, squareTopY + arrowHeight)
                lineTo(centerX + arrowWidth / 2f, squareTopY + arrowHeight)
                close()
            }
        drawPath(path = arrowPath, color = primaryColor.copy(alpha = 0.7f))

        if (showControls) {
            // Line from center to rotation dot
            drawLine(
                color = primaryColor,
                start = Offset(centerX, centerY),
                end = Offset(centerX, dotRadiusPx),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun RobotPoseEdit(
    rotation: Float, // RADIANS
    modifier: Modifier = Modifier,
    onMove: (Offset) -> Unit,
    onRotate: (Float) -> Unit,
    robotWidth: Dp = 60.dp,
    robotLength: Dp = 60.dp,
) {
    val extensionLength = 40.dp
    val dotSize = 16.dp
    val primaryColor = returnPrimaryColor()

    val visualHeight = (dotSize / 2) + extensionLength + robotLength
    val centerOffsetY = (dotSize / 2) + extensionLength + (robotLength / 2)

    var localRotation by remember { mutableStateOf(rotation) }
    var isRotating by remember { mutableStateOf(false) }

    LaunchedEffect(rotation) {
        if (!isRotating) {
            localRotation = rotation
        }
    }

    Box(
        modifier =
            modifier
                .size(width = robotWidth, height = visualHeight)
                .offset(x = -robotWidth / 2, y = -centerOffsetY), // Center it on its logical center point
    ) {
        // Rotated Body & Line
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = Math.toDegrees(localRotation.toDouble()).toFloat()
                        val yPivot = centerOffsetY.toPx() / size.height
                        transformOrigin = TransformOrigin(0.5f, yPivot)
                    },
        ) {
            RobotVisual(
                modifier = Modifier.fillMaxSize(),
                showControls = true,
                robotWidth = robotWidth,
                robotLength = robotLength,
            )
        }

        // MOVE dot (invisible touch target at center)
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = centerOffsetY - (dotSize / 2))
                    .size(dotSize * 2) // Larger touch area
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onMove(dragAmount)
                        }
                    },
        ) {
            // Visual Move Dot
            Box(
                modifier =
                    Modifier
                        .size(dotSize)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(primaryColor),
            )
        }

        // ROTATE dot (Positioned in a circle around center)
        val angleRadForHandle = localRotation.toDouble() - (Math.PI / 2.0)
        val radiusPx = extensionLength + (robotLength / 2)
        val dotOffsetX = (radiusPx.value * cos(angleRadForHandle)).dp
        val dotOffsetY = (radiusPx.value * sin(angleRadForHandle)).dp

        // Pivot is at centerOffsetY.
        // Relative to TopCenter of Box(robotWidth, visualHeight):
        // Center is (centerX, centerOffsetY)
        val finalDotX = dotOffsetX
        val finalDotY = centerOffsetY + dotOffsetY - (dotSize / 2)

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = finalDotX, y = finalDotY)
                    .size(dotSize * 2) // Larger touch area
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isRotating = true },
                            onDragEnd = { isRotating = false },
                            onDragCancel = { isRotating = false },
                        ) { change, _ ->
                            change.consume()
                            val localCenterX = size.width / 2f
                            val localCenterY = centerOffsetY.toPx()
                            val currentPos = change.position
                            val currentVector = Offset(currentPos.x - localCenterX, currentPos.y - localCenterY)
                            val angle = atan2(currentVector.y.toDouble(), currentVector.x.toDouble()).toFloat()
                            localRotation = angle + (Math.PI.toFloat() / 2f)
                            onRotate(localRotation)
                        }
                    },
        ) {
            Box(
                modifier =
                    Modifier
                        .size(dotSize)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(primaryColor),
            )
        }
    }
}
