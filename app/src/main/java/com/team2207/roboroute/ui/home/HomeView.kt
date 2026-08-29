package com.team2207.roboroute.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team2207.roboroute.R
import com.team2207.roboroute.datastore.CustomButton
import com.team2207.roboroute.ui.theme.returnPrimaryColor
import com.team2207.roboroute.ui.theme.returnSecondaryColor
import com.team2207.roboroute.ui.components.FullScreenImage
import kotlin.math.roundToInt
import kotlin.math.min

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.team2207.roboroute.ui.action.RobotVisual
import com.team2207.roboroute.ui.action.FIELD_WIDTH_METERS
import com.team2207.roboroute.ui.action.FIELD_HEIGHT_METERS

@Composable
fun MainView(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val isEditing by viewModel.isEditing.collectAsState()
    val layout by viewModel.layout.collectAsState()
    val appData by viewModel.appData.collectAsState()
    val livePose by viewModel.livePose.collectAsState()
    val isPoseValid by viewModel.isPoseValid.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val maxWidthPx = constraints.maxWidth.toFloat()
            val maxHeightPx = constraints.maxHeight.toFloat()
            
            val painter = painterResource(id = R.drawable.field_2026)
            val imgSize = painter.intrinsicSize
            val scale = min(maxWidthPx / imgSize.width, maxHeightPx / imgSize.height)
            val density = LocalDensity.current.density
            val fitWidthDp = (imgSize.width * scale / density).dp
            val fitHeightDp = (imgSize.height * scale / density).dp

            FullScreenImage(
                modifier = Modifier.fillMaxSize()
            )

            // Field-relative container
            Box(
                modifier = Modifier
                    .size(fitWidthDp, fitHeightDp)
                    .align(Alignment.Center)
            ) {
                // Live Robot Pose
                if (isPoseValid) {
                    val xMeter = livePose.x
                    val yMeter = livePose.y
                    
                    // Conversion: X is depth (length), Y is width.
                    // (0,0) is bottom-right.
                    // UP increases X -> Negative Y Screen Offset
                    // LEFT increases Y -> Negative X Screen Offset
                    val xOffsetDp = -(xMeter / FIELD_WIDTH_METERS * fitHeightDp.value).dp
                    val yOffsetDp = -(yMeter / FIELD_HEIGHT_METERS * fitWidthDp.value).dp
                    
                    // Robot Dimensions in DP (scaled relative to field image)
                    // Let's say robot_width and robot_length are in meters.
                    // We need to scale them to DP.
                    val robotWidthDp = (appData.robotWidth / FIELD_HEIGHT_METERS * fitWidthDp.value).dp
                    val robotLengthDp = (appData.robotLength / FIELD_WIDTH_METERS * fitHeightDp.value).dp

                    RobotVisual(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(yOffsetDp + (robotWidthDp / 2), xOffsetDp + (robotLengthDp / 2))
                            .graphicsLayer {
                                rotationZ = livePose.rotation.toFloat()
                            },
                        showControls = false,
                        robotWidth = robotWidthDp,
                        robotLength = robotLengthDp
                    )
                }

                // Layout Buttons
                layout.buttonsList.forEach { button ->
                    androidx.compose.runtime.key(button.id) {
                        CircularButton(
                            button = button,
                            isEditing = isEditing,
                            onUpdate = { x, y, r, actionId ->
                                viewModel.updateButton(button.id, x, y, r, actionId)
                            },
                            onDelete = { viewModel.deleteButton(button.id) },
                            actions = appData.actionsList,
                            maxWidth = maxWidthPx,
                            maxHeight = maxHeightPx
                        )
                    }
                }
            }

            if (isEditing) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.setEditing(false) },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = returnPrimaryColor(),
                            contentColor = returnSecondaryColor()
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done")
                    }
                    IconButton(
                        onClick = { viewModel.addButton() },
                        modifier = Modifier.padding(top = 8.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = returnPrimaryColor(),
                            contentColor = returnSecondaryColor()
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Button")
                    }
                }
            } else {
                MenuButton(
                    onSettingsClick = onNavigateToSettings,
                    onEditLayoutClick = { viewModel.setEditing(true) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 30.dp)
                )
            }
        }
    }

    if (!isPoseValid && !isEditing) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Robot Position Not Found") },
            text = { Text("Can't find robot position. Please check your NetworkTables path and serial connection.") },
            confirmButton = {
                TextButton(onClick = onNavigateToSettings) {
                    Text("Check Settings")
                }
            }
        )
    }
}

@Composable
fun CircularButton(
    button: CustomButton,
    isEditing: Boolean,
    onUpdate: (Float, Float, Float, Int) -> Unit,
    onDelete: () -> Unit,
    actions: List<com.team2207.roboroute.datastore.Action>,
    maxWidth: Float,
    maxHeight: Float
) {
    var showActionDialog by remember { mutableStateOf(false) }
    val assignedAction = actions.find { it.id == button.actionId }
    val density = LocalDensity.current

    var localX by remember { mutableStateOf(button.x) }
    var localY by remember { mutableStateOf(button.y) }
    var localRadius by remember { mutableStateOf(button.radius) }
    var isInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(button.x, button.y, button.radius, isInteracting) {
        if (!isInteracting) {
            localX = button.x
            localY = button.y
            localRadius = button.radius
        }
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (localX * maxWidth - localRadius).roundToInt(),
                    (localY * maxHeight - localRadius).roundToInt()
                )
            }
            .size(with(density) { (localRadius * 2).toDp() })
            .clip(CircleShape)
            .background(
                if (isEditing) returnPrimaryColor().copy(alpha = 0.5f)
                else returnPrimaryColor()
            )
            .border(2.dp, returnSecondaryColor(), CircleShape)
            .pointerInput(button.id, isEditing) {
                if (isEditing) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        isInteracting = true
                        localRadius = (localRadius * zoom).coerceIn(40f, 400f)
                        localX = (localX + pan.x / maxWidth).coerceIn(0f, 1f)
                        localY = (localY + pan.y / maxHeight).coerceIn(0f, 1f)
                        onUpdate(localX, localY, localRadius, button.actionId)
                    }
                }
            }
            .clickable {
                if (isEditing) {
                    showActionDialog = true
                } else {
                    println("Executing action: ${assignedAction?.name}")
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = assignedAction?.name ?: if (isEditing) "+" else "",
            color = Color.White,
            fontSize = with(density) { (localRadius / 3).toSp() },
            fontWeight = FontWeight.Bold
        )
    }

    if (showActionDialog && isEditing) {
        AlertDialog(
            onDismissRequest = { showActionDialog = false },
            title = { Text("Configure Button") },
            text = {
                Column {
                    Text("Select Action:")
                    actions.forEach { action ->
                        ListItem(
                            headlineContent = { Text(action.name) },
                            modifier = Modifier.clickable {
                                onUpdate(button.x, button.y, button.radius, action.id)
                                showActionDialog = false
                            }
                        )
                    }
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Delete Button", color = Color.Red)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MenuButton(
    onSettingsClick: () -> Unit,
    onEditLayoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true }, colors = IconButtonDefaults.iconButtonColors(
                containerColor = returnPrimaryColor(),
                contentColor = returnSecondaryColor()
            )
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu"
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TextButton(
                onClick = {
                    expanded = false
                    onEditLayoutClick()
                }
            ) {
                Text("Edit layout", color = returnPrimaryColor())
            }
            TextButton(
                onClick = {
                    expanded = false
                    onSettingsClick()
                }
            ) {
                Text("Settings", color = returnPrimaryColor())
            }
        }
    }
}
