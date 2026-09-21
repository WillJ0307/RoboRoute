package com.team2207.roboroute.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team2207.roboroute.R
import com.team2207.roboroute.datastore.ActionType
import com.team2207.roboroute.datastore.CustomButton
import com.team2207.roboroute.ui.action.FIELD_HEIGHT_METERS
import com.team2207.roboroute.ui.action.FIELD_WIDTH_METERS
import com.team2207.roboroute.ui.action.RobotVisual
import com.team2207.roboroute.ui.components.FullScreenImage
import com.team2207.roboroute.ui.theme.returnPrimaryColor
import com.team2207.roboroute.ui.theme.returnSecondaryColor
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun MainView(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val isEditing by viewModel.isEditing.collectAsState()
    val layout by viewModel.layout.collectAsState()
    val appData by viewModel.appData.collectAsState()
    val livePose by viewModel.livePose.collectAsState()
    val isPoseValid by viewModel.isPoseValid.collectAsState()
    val isRedAlliance by viewModel.isRedAlliance.collectAsState()
    val isAoaConnected by viewModel.isAoaConnected.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            val maxWidthPx = constraints.maxWidth.toFloat()
            val maxHeightPx = constraints.maxHeight.toFloat()

            val painter = painterResource(id = R.drawable.field_2026)
            val imgSize = painter.intrinsicSize
            val scale = min(maxWidthPx / imgSize.width, maxHeightPx / imgSize.height)
            val density = LocalDensity.current.density
            val fitWidthDp = (imgSize.width * scale / density).dp
            val fitHeightDp = (imgSize.height * scale / density).dp

            val isFlipped = isRedAlliance
            val flipAngle = if (isFlipped) 180f else 0f

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = flipAngle },
            ) {
                FullScreenImage(
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier =
                    Modifier
                        .size(fitWidthDp, fitHeightDp)
                        .align(Alignment.Center),
            ) {
                if (isPoseValid) {
                    val xMeter = if (isFlipped) FIELD_WIDTH_METERS - livePose.x else livePose.x
                    val yMeter = if (isFlipped) FIELD_HEIGHT_METERS - livePose.y else livePose.y

                    val xOffsetDp = -(xMeter / FIELD_WIDTH_METERS * fitHeightDp.value).dp
                    val yOffsetDp = -(yMeter / FIELD_HEIGHT_METERS * fitWidthDp.value).dp

                    val robotWidthDp = (appData.robotWidth / FIELD_HEIGHT_METERS * fitWidthDp.value).dp
                    val robotLengthDp = (appData.robotLength / FIELD_WIDTH_METERS * fitHeightDp.value).dp

                    RobotVisual(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(yOffsetDp + (robotWidthDp / 2), xOffsetDp + (robotLengthDp / 2))
                                .graphicsLayer {
                                    rotationZ =
                                        Math
                                            .toDegrees(
                                                livePose.rotation + (if (isFlipped) Math.PI else 0.0),
                                            ).toFloat()
                                },
                        showControls = false,
                        robotWidth = robotWidthDp,
                        robotLength = robotLengthDp,
                    )
                }

                layout.buttonsList.forEach { button ->
                    androidx.compose.runtime.key(button.id) {
                        CircularButton(
                            button = button,
                            isEditing = isEditing,
                            onUpdate = { x, y, r, actionId ->
                                viewModel.updateButton(button.id, x, y, r, actionId)
                            },
                            onDelete = { viewModel.deleteButton(button.id) },
                            onCreateNewAction = onNavigateToSettings,
                            actions = appData.actionsList,
                            maxWidth = maxWidthPx,
                            maxHeight = maxHeightPx,
                            flipped = isFlipped,
                        )
                    }
                }
            }

            if (isEditing) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                ) {
                    IconButton(
                        onClick = { viewModel.setEditing(false) },
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = returnPrimaryColor(),
                                contentColor = returnSecondaryColor(),
                            ),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done")
                    }
                    IconButton(
                        onClick = { viewModel.addButton() },
                        modifier = Modifier.padding(top = 8.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = returnPrimaryColor(),
                                contentColor = returnSecondaryColor(),
                            ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Button")
                    }
                }
            } else {
                ConnectionIndicator(
                    isConnected = isAoaConnected,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                )
                MenuButton(
                    onSettingsClick = onNavigateToSettings,
                    onEditLayoutClick = { viewModel.setEditing(true) },
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 30.dp),
                )
            }
        }
    }
}

@Composable
fun ConnectionIndicator(
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) Color(0xFF4CAF50) else Color.Red),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = (if (isConnected) "Connected" else "Disconnected"),
            color = returnSecondaryColor(),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun CircularButton(
    button: CustomButton,
    isEditing: Boolean,
    onUpdate: (Float, Float, Float, Int) -> Unit,
    onDelete: () -> Unit,
    onCreateNewAction: () -> Unit,
    actions: List<com.team2207.roboroute.datastore.Action>,
    maxWidth: Float,
    maxHeight: Float,
    flipped: Boolean,
) {
    var showActionDialog by remember { mutableStateOf(false) }
    val assignedAction = actions.find { it.id == button.actionId }
    val currentActionId by rememberUpdatedState(button.actionId)
    val density = LocalDensity.current

    // Stored coords are always in the "unflipped" reference frame. When the field is
    // flipped the button is mirrored onto the screen and mirrored back before saving.
    val toDisplay = { v: Float -> if (flipped) 1f - v else v }
    val toRaw = { v: Float -> if (flipped) 1f - v else v }

    var localX by remember { mutableStateOf(toDisplay(button.x)) }
    var localY by remember { mutableStateOf(toDisplay(button.y)) }
    var localRadius by remember { mutableStateOf(button.radius) }
    var isInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(button.x, button.y, button.radius, isInteracting) {
        if (!isInteracting) {
            localX = toDisplay(button.x)
            localY = toDisplay(button.y)
            localRadius = button.radius
        }
    }

    val primaryColor = returnPrimaryColor()
    val secondaryColor = returnSecondaryColor()

    Box(
        modifier =
            Modifier
                .offset {
                    IntOffset(
                        (localX * maxWidth - localRadius).roundToInt(),
                        (localY * maxHeight - localRadius).roundToInt(),
                    )
                }.size(with(density) { (localRadius * 2).toDp() }),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        if (isEditing) {
                            primaryColor.copy(alpha = 0.5f)
                        } else {
                            primaryColor
                        },
                    ).border(2.dp, secondaryColor, CircleShape)
                    .pointerInput(button.id, isEditing) {
                        if (isEditing) {
                            detectDragGestures(
                                onDragStart = { isInteracting = true },
                                onDragEnd = { isInteracting = false },
                                onDragCancel = { isInteracting = false },
                            ) { change, dragAmount ->
                                change.consume()
                                localX = (localX + dragAmount.x / maxWidth).coerceIn(0f, 1f)
                                localY = (localY + dragAmount.y / maxHeight).coerceIn(0f, 1f)
                                onUpdate(toRaw(localX), toRaw(localY), localRadius, currentActionId)
                            }
                        }
                    }.clickable {
                        if (isEditing) {
                            showActionDialog = true
                        } else {
                            println("Executing action: ${assignedAction?.name}")
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = assignedAction?.name ?: if (isEditing) "+" else "",
                color = Color.White,
                fontSize = with(density) { (localRadius / 3).toSp() },
                fontWeight = FontWeight.Bold,
            )
        }

        if (isEditing) {
            // Resize handles in corners
            listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { alignment ->
                Box(
                    modifier =
                        Modifier
                            .align(alignment)
                            .size(28.dp)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(secondaryColor)
                            .border(1.dp, primaryColor, CircleShape)
                            .pointerInput(button.id, alignment) {
                                detectDragGestures(
                                    onDragStart = { isInteracting = true },
                                    onDragEnd = { isInteracting = false },
                                    onDragCancel = { isInteracting = false },
                                ) { change, dragAmount ->
                                    change.consume()
                                    // Corner-aware resizing
                                    val factorX = if (alignment == Alignment.TopStart || alignment == Alignment.BottomStart) -1 else 1
                                    val factorY = if (alignment == Alignment.TopStart || alignment == Alignment.TopEnd) -1 else 1
                                    val deltaRadius = (dragAmount.x * factorX + dragAmount.y * factorY) / 2f
                                    localRadius = (localRadius + deltaRadius).coerceIn(40f, 600f)
                                    onUpdate(toRaw(localX), toRaw(localY), localRadius, currentActionId)
                                }
                            },
                )
            }
        }
    }

    if (showActionDialog && isEditing) {
        AlertDialog(
            onDismissRequest = { showActionDialog = false },
            title = { Text("Configure Button") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Select Action:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.size(8.dp))

                    if (actions.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Text("No actions available. Create one first!", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(actions) { action ->
                                val actionLabel =
                                    when {
                                        action.name.isNotBlank() -> action.name
                                        action.actionType == ActionType.POSE ->
                                            "Pose Selection: (${"%.2f".format(action.pose.x)}, " +
                                                "${"%.2f".format(action.pose.y)}, ${"%.2f".format(action.pose.rotation)} rad)"
                                        else -> "Unnamed"
                                    }
                                ListItem(
                                    headlineContent = { Text(actionLabel) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier =
                                        Modifier.clickable {
                                            onUpdate(button.x, button.y, button.radius, action.id)
                                            showActionDialog = false
                                        },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.size(16.dp))
                    Button(
                        onClick = {
                            showActionDialog = false
                            onCreateNewAction()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create New Action")
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            onDelete()
                            showActionDialog = false
                        },
                    ) {
                        Text("Delete Button", color = Color.Red)
                    }
                    TextButton(onClick = { showActionDialog = false }) {
                        Text("Close")
                    }
                }
            },
        )
    }
}

@Composable
fun MenuButton(
    onSettingsClick: () -> Unit,
    onEditLayoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = returnPrimaryColor(),
                    contentColor = returnSecondaryColor(),
                ),
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TextButton(
                onClick = {
                    expanded = false
                    onEditLayoutClick()
                },
            ) {
                Text("Edit layout", color = returnPrimaryColor())
            }
            TextButton(
                onClick = {
                    expanded = false
                    onSettingsClick()
                },
            ) {
                Text("Settings", color = returnPrimaryColor())
            }
        }
    }
}
