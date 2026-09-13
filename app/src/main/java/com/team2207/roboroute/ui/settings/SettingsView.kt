package com.team2207.roboroute.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.team2207.roboroute.serial.SerialLogManager
import com.team2207.roboroute.ui.action.ActionCreationScreen
import com.team2207.roboroute.ui.theme.returnPrimaryColor
import com.team2207.roboroute.ui.theme.returnSecondaryColor
import com.team2207.roboroute.datastore.Action as ProtoAction
import com.team2207.roboroute.datastore.ActionType as ProtoActionType
import com.team2207.roboroute.datastore.Pose2d as ProtoPose2d
import com.team2207.roboroute.ui.action.Action as UIAction

@Composable
fun SettingsView(
    onBack: () -> Unit,
    onEditPose: (Double, Double, Double, Double, Double) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val appData by viewModel.appData.collectAsState()
    val context = LocalContext.current

    // Local state to avoid cursor jumping
    var localNtPath by remember(appData.ntPath) { mutableStateOf(appData.ntPath) }
    var localWidth by remember(appData.robotWidth) { mutableStateOf(appData.robotWidth.toString()) }
    var localLength by remember(appData.robotLength) { mutableStateOf(appData.robotLength.toString()) }

    var draftPose by remember { mutableStateOf<UIAction.PoseSelection?>(null) }
    var editingAction by remember { mutableStateOf<UIAction?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var showLogSheet by remember { mutableStateOf(false) }

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            uri?.let { viewModel.exportData(context, it) }
        }

    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { viewModel.importData(context, it) }
        }

    LaunchedEffect(navController.currentBackStackEntry) {
        val savedState = navController.currentBackStackEntry?.savedStateHandle
        val x = savedState?.get<Double>("pose_x")
        val y = savedState?.get<Double>("pose_y")
        val r = savedState?.get<Double>("pose_r")

        if (x != null && y != null && r != null) {
            draftPose = UIAction.PoseSelection(id = editingAction?.actionId ?: 0, name = "", x = x, y = y, r = r)
            showSheet = true
            savedState.remove<Double>("pose_x")
            savedState.remove<Double>("pose_y")
            savedState.remove<Double>("pose_r")
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 30.dp, top = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = returnPrimaryColor(),
                            contentColor = returnSecondaryColor(),
                        ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate Back",
                    )
                }

                Text(
                    text = "Settings",
                    modifier = Modifier.padding(start = 16.dp),
                    color = returnPrimaryColor(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = 70.dp)
                        .padding(horizontal = 16.dp),
            ) {
                OutlinedTextField(
                    value = localNtPath,
                    onValueChange = {
                        localNtPath = it
                        viewModel.updateNtPath(it)
                    },
                    label = { Text("NetworkTables Robot Pose Path") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.padding(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = localWidth,
                        onValueChange = {
                            localWidth = it
                            it.toDoubleOrNull()?.let { w -> viewModel.updateRobotDimensions(w, appData.robotLength) }
                        },
                        label = { Text("Robot Width (m)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = localLength,
                        onValueChange = {
                            localLength = it
                            it.toDoubleOrNull()?.let { l -> viewModel.updateRobotDimensions(appData.robotWidth, l) }
                        },
                        label = { Text("Robot Length (m)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }

                Spacer(modifier = Modifier.padding(8.dp))

                NewActionSheetAndButtonToCreate(
                    onSaveAction = { uiAction ->
                        val protoAction =
                            ProtoAction
                                .newBuilder()
                                .apply {
                                    name =
                                        when (uiAction) {
                                            is UIAction.PathPlanner -> uiAction.name
                                            is UIAction.PoseSelection -> uiAction.name
                                        }
                                    when (uiAction) {
                                        is UIAction.PathPlanner -> {
                                            actionType = ProtoActionType.PATHPLANNER
                                            pathName = uiAction.pathName
                                        }
                                        is UIAction.PoseSelection -> {
                                            actionType = ProtoActionType.POSE
                                            pose =
                                                ProtoPose2d
                                                    .newBuilder()
                                                    .apply {
                                                        x = uiAction.x
                                                        y = uiAction.y
                                                        rotation = uiAction.r
                                                    }.build()
                                        }
                                    }
                                    id =
                                        if (uiAction.actionId !=
                                            0
                                        ) {
                                            uiAction.actionId
                                        } else {
                                            (appData.actionsList.maxOfOrNull { it.id } ?: 0) + 1
                                        }
                                }.build()
                        viewModel.saveAction(protoAction)
                        draftPose = null
                        editingAction = null
                    },
                    onEditPose = { x, y, r -> onEditPose(x, y, r, appData.robotWidth, appData.robotLength) },
                    showSheetInitial = showSheet,
                    onSheetVisibilityChange = {
                        showSheet = it
                        if (!it) {
                            editingAction = null
                            draftPose = null
                        }
                    },
                    currentPose = draftPose,
                    initialAction = editingAction,
                )

                Spacer(modifier = Modifier.padding(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { exportLauncher.launch("roboroute_backup.json") },
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonColors(
                                containerColor = returnPrimaryColor(),
                                contentColor = returnSecondaryColor(),
                                disabledContainerColor = Color.Gray,
                                disabledContentColor = Color.White,
                            ),
                    ) {
                        Text("Export JSON")
                    }
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonColors(
                                containerColor = returnPrimaryColor(),
                                contentColor = returnSecondaryColor(),
                                disabledContainerColor = Color.Gray,
                                disabledContentColor = Color.White,
                            ),
                    ) {
                        Text("Import JSON")
                    }
                }

                Spacer(modifier = Modifier.padding(8.dp))

                Button(
                    onClick = { viewModel.manualSubscribe() },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonColors(
                            containerColor = returnPrimaryColor(),
                            contentColor = returnSecondaryColor(),
                            disabledContainerColor = Color.Gray,
                            disabledContentColor = Color.White,
                        ),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Check Alliance / Re-Subscribe")
                }

                Spacer(modifier = Modifier.padding(4.dp))

                Button(
                    onClick = { showLogSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonColors(
                            containerColor = returnPrimaryColor(),
                            contentColor = returnSecondaryColor(),
                            disabledContainerColor = Color.Gray,
                            disabledContentColor = Color.White,
                        ),
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("View Logs")
                }

                Spacer(modifier = Modifier.padding(8.dp))

                Text("Saved Actions", fontWeight = FontWeight.Bold, color = returnPrimaryColor())
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(appData.actionsList) { action ->
                        ListItem(
                            headlineContent = { Text(action.name) },
                            supportingContent = {
                                Text(
                                    when (action.actionType) {
                                        ProtoActionType.PATHPLANNER -> "Path: ${action.pathName}"
                                        ProtoActionType.POSE -> "Pose Selection: (${"%.2f".format(
                                            action.pose.x,
                                        )}, ${"%.2f".format(action.pose.y)}, ${"%.2f".format(action.pose.rotation)} rad)"
                                        else -> "Legacy/Unknown"
                                    },
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    editingAction =
                                        when (action.actionType) {
                                            ProtoActionType.PATHPLANNER -> UIAction.PathPlanner(action.id, action.name, action.pathName)
                                            ProtoActionType.POSE ->
                                                UIAction.PoseSelection(
                                                    action.id,
                                                    action.name,
                                                    action.pose.x,
                                                    action.pose.y,
                                                    action.pose.rotation,
                                                )
                                            else -> null
                                        }
                                    showSheet = true
                                },
                        )
                    }
                }
            }
        }
    }

    if (showLogSheet) {
        SerialLogSheet(
            onDismiss = { showLogSheet = false },
            onManualSubscribe = { viewModel.manualSubscribe() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerialLogSheet(
    onDismiss: () -> Unit,
    onManualSubscribe: () -> Unit,
) {
    val logs by SerialLogManager.logs.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.8f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AOA / Serial Logs", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = onManualSubscribe, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Manual Subscribe")
                    }
                    Button(onClick = { SerialLogManager.clearLogs() }) {
                        Text("Clear")
                    }
                }
            }
            Spacer(modifier = Modifier.padding(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logs) { log ->
                    Text(
                        text = log,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewActionSheetAndButtonToCreate(
    onSaveAction: (UIAction) -> Unit,
    onEditPose: (Double, Double, Double) -> Unit,
    showSheetInitial: Boolean = false,
    onSheetVisibilityChange: (Boolean) -> Unit = {},
    currentPose: UIAction.PoseSelection? = null,
    initialAction: UIAction? = null,
) {
    var showBottomSheet by remember(showSheetInitial) { mutableStateOf(showSheetInitial) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(showSheetInitial) {
        showBottomSheet = showSheetInitial
    }

    Column {
        Button(
            onClick = {
                showBottomSheet = true
                onSheetVisibilityChange(true)
            },
            colors =
                ButtonColors(
                    containerColor = returnPrimaryColor(),
                    contentColor = returnSecondaryColor(),
                    disabledContentColor = returnSecondaryColor(),
                    disabledContainerColor = Color.LightGray,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Configure New Action")
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                    onSheetVisibilityChange(false)
                },
                sheetState = sheetState,
            ) {
                ActionCreationScreen(
                    onDismiss = {
                        showBottomSheet = false
                        onSheetVisibilityChange(false)
                    },
                    onSaveAction = { action ->
                        onSaveAction(action)
                        showBottomSheet = false
                        onSheetVisibilityChange(false)
                    },
                    onEditPose = {
                        val currentX = currentPose?.x ?: (initialAction as? UIAction.PoseSelection)?.x ?: 0.0
                        val currentY = currentPose?.y ?: (initialAction as? UIAction.PoseSelection)?.y ?: 0.0
                        val currentR = currentPose?.r ?: (initialAction as? UIAction.PoseSelection)?.r ?: 0.0
                        onEditPose(currentX, currentY, currentR)
                    },
                    currentPose = currentPose,
                    initialAction = initialAction,
                )
            }
        }
    }
}
