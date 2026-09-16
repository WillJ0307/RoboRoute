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
import com.team2207.roboroute.ui.components.FullScreenImage
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
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

            val density = androidx.compose.ui.platform.LocalDensity.current
            val fitWidthPx = with(density) { fitWidthDp.toPx() }
            val fitHeightPx = with(density) { fitHeightDp.toPx() }

            // FRC convention: X is long axis (~16.5m), Y is short axis (~8.2m).
            // Image convention: Width is long side, Height is short side.
            // Mapping: Bottom-Right is (0,0). Left is +Y (Long), Up is +X (Short).
            // Wait, if "Up is +X (Short)", then the user's "X" is the 8.2m axis.
            // And "Left is +Y (Long)", then the user's "Y" is the 16.5m axis.
            // Let's stick to this consistently.

            // Pixels per meter should be consistent to avoid stretching.
            // We'll use the height as the master scale.
            val pxPerMeter = fitHeightPx / FIELD_HEIGHT_METERS

            if (!isInitialized) {
                // initialX (Short) -> Vertical axis (Height)
                // initialY (Long) -> Horizontal axis (Width)
                val xPx = -(initialX / FIELD_HEIGHT_METERS * fitHeightPx)
                val yPx = -(initialY / FIELD_WIDTH_METERS * fitWidthPx)
                robotOffset = Offset(yPx.toFloat(), xPx.toFloat())
                isInitialized = true
            }

            FullScreenImage(modifier = Modifier.fillMaxSize())

            IconButton(
                onClick = onBack,
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
                    val confirmX = (-robotOffset.y / fitHeightPx) * FIELD_HEIGHT_METERS
                    val confirmY = (-robotOffset.x / fitWidthPx) * FIELD_WIDTH_METERS
                    onConfirm(confirmX, confirmY, robotRotation.toDouble())
                    onBack()
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
                // Use consistent pxPerMeter to keep the robot square
                val robotWidthDp = (robotWidthMeter * pxPerMeter / density.density).dp
                val robotLengthDp = (robotLengthMeter * pxPerMeter / density.density).dp

                RobotPoseEdit(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset {
                                IntOffset(
                                    robotOffset.x.roundToInt(),
                                    robotOffset.y.roundToInt(),
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
                val frcX = (-robotOffset.y / fitHeightPx) * FIELD_HEIGHT_METERS
                val frcY = (-robotOffset.x / fitWidthPx) * FIELD_WIDTH_METERS

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
    val primaryColor = returnPrimaryColor()

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

    // The robot center is the pivot.
    // We need a box large enough to hold the body and the handle at any angle.
    val radiusDp = extensionLength + (robotLength / 2)
    val handleBoxSize = radiusDp * 2 + dotSize * 2

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
                .size(handleBoxSize)
                .offset(x = -handleBoxSize / 2, y = -handleBoxSize / 2),
        contentAlignment = Alignment.Center
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val radiusPx = with(density) { radiusDp.toPx() }
        val dotSizePx = with(density) { dotSize.toPx() }

        // Rotated Body & Line
        Box(
            modifier =
                Modifier
                    .size(width = robotWidth, height = robotLength + extensionLength + dotSize / 2)
                    .graphicsLayer {
                        rotationZ = Math.toDegrees(localRotation.toDouble()).toFloat()
                        // Pivot is at the center of the robot body.
                        // Robot body starts at dotSize/2 + extensionLength.
                        // So center is at dotSize/2 + extensionLength + robotLength/2.
                        val pivotY = (dotSize.toPx() / 2f + extensionLength.toPx() + robotLength.toPx() / 2f)
                        transformOrigin = TransformOrigin(0.5f, pivotY / size.height)
                    }
                    .align(Alignment.Center)
                    // Adjust position so the body center aligns with the handleBox center
                    .offset(y = -(extensionLength / 2 + dotSize / 4))
        ) {
            RobotVisual(
                modifier = Modifier.fillMaxSize(),
                showControls = true,
                robotWidth = robotWidth,
                robotLength = robotLength,
            )
        }

        // MOVE dot (at center)
        Box(
            modifier =
                Modifier
                    .size(dotSize * 2)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onMove(dragAmount)
                        }
                    },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier =
                    Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(primaryColor),
            )
        }

        // ROTATE dot (Calculated position)
        val angleRadForHandle = localRotation.toDouble() - (Math.PI / 2.0)
        val dotOffsetX = (radiusDp.value * cos(angleRadForHandle)).dp
        val dotOffsetY = (radiusDp.value * sin(angleRadForHandle)).dp

        Box(
            modifier =
                Modifier
                    .offset(x = dotOffsetX, y = dotOffsetY)
                    .size(dotSize * 2)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isRotating = true },
                            onDragEnd = { isRotating = false },
                            onDragCancel = { isRotating = false },
                        ) { change, _ ->
                            change.consume()

                            // Get the handle's current position relative to parent center
                            val currentDotX = radiusPx * cos(localRotation - Math.PI / 2.0)
                            val currentDotY = radiusPx * sin(localRotation - Math.PI / 2.0)

                            // The touch 'change.position' is relative to the dot box top-left.
                            // The dot box top-left relative to parent center is (currentDotX - dotSizePx, currentDotY - dotSizePx)
                            val touchX = currentDotX - dotSizePx + change.position.x
                            val touchY = currentDotY - dotSizePx + change.position.y

                            val angle = atan2(touchY, touchX).toFloat()
                            localRotation = angle + (Math.PI.toFloat() / 2f)
                            onRotate(localRotation)
                        }
                    },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier =
                    Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(primaryColor),
            )
        }
    }
}
