package com.team2207.roboroute.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.team2207.roboroute.datastore.Action as ProtoAction
import com.team2207.roboroute.datastore.ActionType as ProtoActionType
import com.team2207.roboroute.datastore.Pose2d as ProtoPose2d
import com.team2207.roboroute.ui.action.Action as UIAction
import com.team2207.roboroute.ui.action.ActionCreationScreen
import com.team2207.roboroute.ui.theme.returnPrimaryColor
import com.team2207.roboroute.ui.theme.returnSecondaryColor

@Composable
fun SettingsView(
    onBack: () -> Unit,
    onEditPose: () -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val appData by viewModel.appData.collectAsState()
    val context = LocalContext.current
    
    var draftPose by remember { mutableStateOf<UIAction.PoseSelection?>(null) }
    var editingAction by remember { mutableStateOf<UIAction?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportData(context, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 30.dp, top = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = returnPrimaryColor(),
                        contentColor = returnSecondaryColor()
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate Back"
                    )
                }

                Text(
                    text = "Settings",
                    modifier = Modifier.padding(start = 16.dp),
                    color = returnPrimaryColor(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 70.dp)
                    .padding(horizontal = 16.dp)
            ) {
                RefreshInterval()
                
                NewActionSheetAndButtonToCreate(
                    onSaveAction = { uiAction ->
                        val protoAction = ProtoAction.newBuilder().apply {
                            name = when (uiAction) {
                                is UIAction.NTAction -> uiAction.name
                                is UIAction.PathPlanner -> uiAction.name
                                is UIAction.PoseSelection -> uiAction.name
                            }
                            when (uiAction) {
                                is UIAction.NTAction -> {
                                    actionType = ProtoActionType.NT
                                    ntKey = uiAction.ntRoute
                                    ntData = uiAction.data
                                }
                                is UIAction.PathPlanner -> {
                                    actionType = ProtoActionType.PATHPLANNER
                                    pathName = uiAction.pathName
                                }
                                is UIAction.PoseSelection -> {
                                    actionType = ProtoActionType.POSE
                                    pose = ProtoPose2d.newBuilder().apply {
                                        x = uiAction.x
                                        y = uiAction.y
                                        rotation = uiAction.r
                                    }.build()
                                }
                            }
                            // Use existing ID if editing, otherwise generate new
                            id = if (uiAction.actionId != 0) uiAction.actionId else (appData.actionsList.maxOfOrNull { it.id } ?: 0) + 1
                        }.build()
                        viewModel.saveAction(protoAction)
                        draftPose = null
                        editingAction = null
                    },
                    onEditPose = onEditPose,
                    showSheetInitial = showSheet,
                    onSheetVisibilityChange = { 
                        showSheet = it
                        if (!it) {
                            editingAction = null
                            draftPose = null
                        }
                    },
                    currentPose = draftPose,
                    initialAction = editingAction
                )

                Spacer(modifier = Modifier.padding(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { exportLauncher.launch("roboroute_backup.json") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonColors(containerColor = returnPrimaryColor(), contentColor = returnSecondaryColor(), disabledContainerColor = Color.Gray, disabledContentColor = Color.White)
                    ) {
                        Text("Export JSON")
                    }
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonColors(containerColor = returnPrimaryColor(), contentColor = returnSecondaryColor(), disabledContainerColor = Color.Gray, disabledContentColor = Color.White)
                    ) {
                        Text("Import JSON")
                    }
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
                                        ProtoActionType.NT -> "NT: ${action.ntKey}"
                                        ProtoActionType.PATHPLANNER -> "Path: ${action.pathName}"
                                        ProtoActionType.POSE -> "Pose Selection: (${action.pose.x.toInt()}, ${action.pose.y.toInt()}, ${action.pose.rotation.toInt()}°)"
                                        else -> "Unknown"
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                // Map Proto to UI Action for editing
                                editingAction = when (action.actionType) {
                                    ProtoActionType.NT -> UIAction.NTAction(action.id, action.name, action.ntKey, action.ntData)
                                    ProtoActionType.PATHPLANNER -> UIAction.PathPlanner(action.id, action.name, action.pathName)
                                    ProtoActionType.POSE -> UIAction.PoseSelection(action.id, action.name, action.pose.x, action.pose.y, action.pose.rotation)
                                    else -> null
                                }
                                showSheet = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RefreshInterval() {
    var refreshInterval by remember { mutableStateOf("") }

    OutlinedTextField(
        value = refreshInterval,
        onValueChange = { refreshInterval = it },
        label = { Text("Refresh Interval")},
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewActionSheetAndButtonToCreate(
    onSaveAction: (UIAction) -> Unit,
    onEditPose: () -> Unit,
    showSheetInitial: Boolean = false,
    onSheetVisibilityChange: (Boolean) -> Unit = {},
    currentPose: UIAction.PoseSelection? = null,
    initialAction: UIAction? = null
) {
    var showBottomSheet by remember(showSheetInitial) { mutableStateOf(showSheetInitial) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Sync local sheet state with provided initial visibility
    LaunchedEffect(showSheetInitial) {
        showBottomSheet = showSheetInitial
    }

    Column {
        Button(
            onClick = { 
                showBottomSheet = true
                onSheetVisibilityChange(true)
            },
            colors = ButtonColors(
                containerColor = returnPrimaryColor(),
                contentColor = returnSecondaryColor(),
                disabledContentColor = returnSecondaryColor(),
                disabledContainerColor = Color.LightGray
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Configure New Action")
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showBottomSheet = false
                    onSheetVisibilityChange(false)
                },
                sheetState = sheetState
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
                    onEditPose = onEditPose,
                    currentPose = currentPose,
                    initialAction = initialAction
                )
            }
        }
    }
}
