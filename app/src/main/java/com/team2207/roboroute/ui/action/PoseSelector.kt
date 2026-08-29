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
    initialR: Double = 0.0
) {
    var robotOffset by remember { mutableStateOf(Offset.Zero) }
    var robotRotation by remember { mutableStateOf(initialR.toFloat()) }
    var isInitialized by remember { mutableStateOf(false) }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                val yOffset = -(initialX / FIELD_WIDTH_METERS * fitHeightDp.value)
                val xOffset = -(initialY / FIELD_HEIGHT_METERS * fitWidthDp.value)
                robotOffset = Offset(xOffset.toFloat(), yOffset.toFloat())
                isInitialized = true
            }

            FullScreenImage(modifier = Modifier.fillMaxSize())

            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = returnPrimaryColor(),
                    contentColor = returnSecondaryColor()
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            IconButton(
                onClick = { 
                    val frcX = (-robotOffset.y.dp.value / fitHeightDp.value) * FIELD_WIDTH_METERS
                    val frcY = (-robotOffset.x.dp.value / fitWidthDp.value) * FIELD_HEIGHT_METERS
                    onConfirm(frcX, frcY, robotRotation.toDouble())
                    onBack() 
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = returnPrimaryColor(),
                    contentColor = returnSecondaryColor()
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = "Confirm")
            }

            Box(
                modifier = Modifier
                    .size(fitWidthDp, fitHeightDp)
                    .align(Alignment.Center)
            ) {
                RobotPoseEdit(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset {
                            IntOffset(
                                robotOffset.x.roundToInt(),
                                robotOffset.y.roundToInt()
                            )
                        },
                    rotation = robotRotation,
                    onMove = { delta ->
                        robotOffset += delta
                    },
                    onRotate = { newRotation ->
                        robotRotation = newRotation
                    }
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                val frcX = (-robotOffset.y.dp.value / fitHeightDp.value) * FIELD_WIDTH_METERS
                val frcY = (-robotOffset.x.dp.value / fitWidthDp.value) * FIELD_HEIGHT_METERS
                
                Text(text = "X: ${"%.2f".format(frcX)}m", color = Color.Red, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = "Y: ${"%.2f".format(frcY)}m", color = Color.Green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = "R: ${robotRotation.roundToInt()}°", color = Color.Blue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RobotVisual(
    modifier: Modifier = Modifier,
    showControls: Boolean = false,
    robotWidth: Dp = 60.dp,
    robotLength: Dp = 60.dp
) {
    val extensionLength = 40.dp
    val dotSize = 16.dp
    val strokeWidth = 2.dp
    val primaryColor = returnPrimaryColor()

    val totalHeight = if (showControls) (dotSize / 2) + extensionLength + robotLength else robotLength

    Canvas(modifier = modifier.size(width = robotWidth, height = totalHeight)) {
        val centerX = size.width / 2f
        val robotWidthPx = robotWidth.toPx()
        val robotLengthPx = robotLength.toPx()
        val strokeWidthPx = strokeWidth.toPx()
        val extensionPx = extensionLength.toPx()
        val dotRadiusPx = (dotSize / 2f).toPx()

        val squareTopY = if (showControls) dotRadiusPx + extensionPx else 0f
        val centerY = squareTopY + (robotLengthPx / 2f)

        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(centerX - robotWidthPx / 2f, squareTopY),
            size = Size(robotWidthPx, robotLengthPx),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(width = strokeWidthPx)
        )

        if (showControls) {
            drawLine(
                color = primaryColor,
                start = Offset(centerX, centerY),
                end = Offset(centerX, dotRadiusPx),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun RobotPoseEdit(
    rotation: Float,
    modifier: Modifier = Modifier,
    onMove: (Offset) -> Unit,
    onRotate: (Float) -> Unit,
    robotWidth: Dp = 60.dp,
    robotLength: Dp = 60.dp
) {
    val extensionLength = 40.dp
    val dotSize = 16.dp

    val primaryColor = returnPrimaryColor()
    val visualTotalHeight = (dotSize / 2) + extensionLength + robotLength
    val rotationCenterOffsetFromTop = (dotSize / 2) + extensionLength + (robotLength / 2)

    var localRotation by remember { mutableStateOf(rotation) }
    var isRotating by remember { mutableStateOf(false) }

    LaunchedEffect(rotation) {
        if (!isRotating) {
            localRotation = rotation
        }
    }

    Box(
        modifier = modifier
            .size(width = robotWidth, height = visualTotalHeight)
            .offset(x = robotWidth / 2, y = robotLength / 2) // Center correction relative to bottom-end
    ) {
        RobotVisual(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = localRotation
                    val yPivot = rotationCenterOffsetFromTop.toPx() / size.height
                    transformOrigin = TransformOrigin(0.5f, yPivot)
                },
            showControls = true,
            robotWidth = robotWidth,
            robotLength = robotLength
        )

        // Center Dot - MOVE
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = extensionLength + (robotLength / 2) - (dotSize / 2))
                .size(dotSize)
                .clip(CircleShape)
                .background(primaryColor)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onMove(dragAmount)
                    }
                }
        )

        // Top Dot - ROTATE
        val angleRad = Math.toRadians(localRotation.toDouble() - 90.0)
        val radiusPx = extensionLength + (robotLength / 2)
        val dotOffsetX = (radiusPx.value * cos(angleRad)).dp
        val dotOffsetY = (radiusPx.value * sin(angleRad)).dp
        val finalDotOffsetX = dotOffsetX
        val finalDotOffsetY = (extensionLength + robotLength / 2) + dotOffsetY - (dotSize / 2)

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = finalDotOffsetX, y = finalDotOffsetY)
                .size(dotSize)
                .clip(CircleShape)
                .background(primaryColor)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isRotating = true },
                        onDragEnd = { isRotating = false },
                        onDragCancel = { isRotating = false }
                    ) { change, _ ->
                        change.consume()
                        val localCenterX = size.width / 2f
                        val localCenterY = (dotSize.toPx() / 2f) + extensionLength.toPx() + (robotLength.toPx() / 2f)
                        val currentPos = change.position
                        val currentVector = Offset(currentPos.x - localCenterX, currentPos.y - localCenterY)
                        val angle = Math.toDegrees(atan2(currentVector.y.toDouble(), currentVector.x.toDouble())).toFloat()
                        localRotation = angle + 90f
                        onRotate(localRotation)
                    }
                }
        )
    }
}
