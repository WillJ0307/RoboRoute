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

@Composable
fun PoseSelectorMainView(
    onBack: () -> Unit,
    onConfirm: (Offset, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var robotOffset by remember { mutableStateOf(Offset.Zero) }
    var robotRotation by remember { mutableStateOf(0f) }

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
            val fitWidth = (imgWidth * scale).dp
            val fitHeight = (imgHeight * scale).dp

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
                    onConfirm(robotOffset, robotRotation)
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
                    .size(fitWidth, fitHeight)
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
                val frcX = (-robotOffset.y / 10f).roundToInt()
                val frcY = (-robotOffset.x / 10f).roundToInt()
                
                Text(text = "X: $frcX", color = Color.Red, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = "Y: $frcY", color = Color.Green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = "R: ${robotRotation.roundToInt()}°", color = Color.Blue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RobotPoseEdit(
    rotation: Float,
    modifier: Modifier = Modifier,
    onMove: (Offset) -> Unit,
    onRotate: (Float) -> Unit
) {
    val squareSize = 60.dp
    val extensionLength = 40.dp
    val dotSize = 16.dp
    val strokeWidth = 2.dp

    val primaryColor = returnPrimaryColor()
    val visualTotalHeight = (dotSize / 2) + extensionLength + squareSize
    val rotationCenterOffsetFromTop = (dotSize / 2) + extensionLength + (squareSize / 2)

    Box(
        modifier = modifier.size(width = squareSize, height = visualTotalHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotation
                    val yPivot = rotationCenterOffsetFromTop.toPx() / size.height
                    transformOrigin = TransformOrigin(0.5f, yPivot)
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val centerX = size.width / 2f
                val squareSizePx = squareSize.toPx()
                val strokeWidthPx = strokeWidth.toPx()
                val dotRadiusPx = (dotSize / 2f).toPx()
                val extensionPx = extensionLength.toPx()

                val lineTopY = dotRadiusPx
                val squareTopY = lineTopY + extensionPx
                val centerY = squareTopY + (squareSizePx / 2f)

                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(centerX - squareSizePx / 2f, squareTopY),
                    size = Size(squareSizePx, squareSizePx),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = strokeWidthPx)
                )

                drawLine(
                    color = primaryColor,
                    start = Offset(centerX, centerY),
                    end = Offset(centerX, lineTopY),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            }
        }

        // Center Dot - MOVE
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = extensionLength + (squareSize / 2) - (dotSize / 2))
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
        val angleRad = Math.toRadians(rotation.toDouble() - 90.0)
        val radiusPx = extensionLength + (squareSize / 2)
        val dotOffsetX = (radiusPx.value * cos(angleRad)).dp
        val dotOffsetY = (radiusPx.value * sin(angleRad)).dp
        val finalDotOffsetX = dotOffsetX
        val finalDotOffsetY = (extensionLength + squareSize / 2) + dotOffsetY - (dotSize / 2)

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = finalDotOffsetX, y = finalDotOffsetY)
                .size(dotSize)
                .clip(CircleShape)
                .background(primaryColor)
                .pointerInput(Unit) { // Use Unit key to prevent snapping
                    detectDragGestures { change, _ ->
                        change.consume()
                        val localCenterX = size.width / 2f
                        val localCenterY = (dotSize.toPx() / 2f) + extensionLength.toPx() + (squareSize.toPx() / 2f)
                        val currentPos = change.position
                        val currentVector = Offset(currentPos.x - localCenterX, currentPos.y - localCenterY)
                        val angle = Math.toDegrees(atan2(currentVector.y.toDouble(), currentVector.x.toDouble())).toFloat()
                        onRotate(angle + 90f)
                    }
                }
        )
    }
}
