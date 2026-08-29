package com.team2207.roboroute.ui.action

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team2207.roboroute.ui.theme.returnPrimaryColor
import com.team2207.roboroute.ui.theme.returnSecondaryColor
import kotlin.math.roundToInt

sealed class Action {
    data class PathPlanner(var id: Int = 0, var name: String, var pathName: String) : Action()
    data class PoseSelection(var id: Int = 0, var name: String, var x: Double = 0.0, var y: Double = 0.0, var r: Double = 0.0) : Action()
    
    val actionId: Int
        get() = when (this) {
            is PathPlanner -> id
            is PoseSelection -> id
        }
}

enum class ActionType { PATH_PLANNER, POSE_SELECTION }

@Composable
fun ActionCreationScreen(
    onDismiss: () -> Unit,
    onSaveAction: (Action) -> Unit,
    onEditPose: () -> Unit,
    currentPose: Action.PoseSelection? = null,
    initialAction: Action? = null
) {
    // 1. Text field input states, initialized from initialAction if available
    var nameInput by remember { mutableStateOf(initialAction?.let { 
        when(it) {
            is Action.PathPlanner -> it.name
            is Action.PoseSelection -> it.name
        }
    } ?: "") }
    
    var ntRouteInput by remember { mutableStateOf(initialAction?.let {
        when(it) {
            is Action.PathPlanner -> it.pathName
            else -> ""
        }
    } ?: "") }

    // 2. Track which tab is currently selected
    var selectedTab by remember { 
        mutableStateOf(
            when {
                currentPose != null -> ActionType.POSE_SELECTION
                initialAction is Action.PathPlanner -> ActionType.PATH_PLANNER
                initialAction is Action.PoseSelection -> ActionType.POSE_SELECTION
                else -> ActionType.PATH_PLANNER
            }
        )
    }

    // Update state if currentPose arrives (from returning from PoseSelector)
    LaunchedEffect(currentPose) {
        if (currentPose != null) {
            selectedTab = ActionType.POSE_SELECTION
        }
    }

    val primaryAccent = returnPrimaryColor()
    val secondaryAccent = returnSecondaryColor()

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val createdAction = when (selectedTab) {
                            ActionType.PATH_PLANNER -> Action.PathPlanner(
                                id = initialAction?.actionId ?: 0,
                                name = nameInput.trim(),
                                pathName = ntRouteInput.trim()
                            )
                            ActionType.POSE_SELECTION -> {
                                currentPose?.copy(id = initialAction?.actionId ?: 0, name = nameInput.trim()) 
                                    ?: (initialAction as? Action.PoseSelection)?.copy(name = nameInput.trim())
                                    ?: Action.PoseSelection(id = initialAction?.actionId ?: 0, name = nameInput.trim())
                            }
                        }
                        onSaveAction(createdAction)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Save", fontSize = 16.sp, color = Color.White)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color.LightGray),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Cancel", fontSize = 16.sp, color = Color.Black)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (initialAction == null) "New Action" else "Edit Action", fontSize = 28.sp)
                IconButton(onClick = onDismiss) {
                    Text("✕", fontSize = 20.sp)
                }
            }

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryAccent)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                ActionType.entries.forEachIndexed { index, type ->
                    val isSelected = selectedTab == type
                    val shape = when (index) {
                        0 -> RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                        1 -> RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                        else -> RoundedCornerShape(0.dp)
                    }

                    Button(
                        onClick = { selectedTab = type },
                        shape = shape,
                        border = BorderStroke(1.dp, if (isSelected) primaryAccent else Color.LightGray),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) secondaryAccent else Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text(
                            text = when (type) {
                                ActionType.PATH_PLANNER -> "PathPlanner"
                                ActionType.POSE_SELECTION -> "Pose Selection"
                            },
                            color = if (isSelected) primaryAccent else Color.Black,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            when (selectedTab) {
                ActionType.PATH_PLANNER -> {
                    OutlinedTextField(
                        value = ntRouteInput,
                        onValueChange = { ntRouteInput = it },
                        label = { Text("Path Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryAccent)
                    )
                }
                ActionType.POSE_SELECTION -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val displayPose = currentPose ?: (initialAction as? Action.PoseSelection)
                        if (displayPose != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "X: ${"%.2f".format(displayPose.x)}m", color = Color.Red, fontWeight = FontWeight.Bold)
                                Text(text = "Y: ${"%.2f".format(displayPose.y)}m", color = Color.Green, fontWeight = FontWeight.Bold)
                                Text(text = "R: ${"%.2f".format(displayPose.r)} rad", color = Color.Blue, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Button(
                            onClick = onEditPose,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (displayPose == null) "Select Pose" else "Edit Pose", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
