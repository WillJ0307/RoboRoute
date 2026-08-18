package com.team2207.roboroute.ui.action

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team2207.roboroute.ui.theme.returnPrimaryColor
import com.team2207.roboroute.ui.theme.returnSecondaryColor

sealed class Action {
    data class NTAction(var name: String, var ntRoute: String, var data: String) : Action()
    data class PathPlanner(var name: String, var pathName: String) : Action()
    data class PoseSelection(var name: String, var x: Double = 0.0, var y: Double = 0.0, var r: Double = 0.0) : Action()
}

enum class ActionType { NT_ACTION, PATH_PLANNER, POSE_SELECTION }

@Composable
fun ActionNameBox(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Name of Your Action") }
    )
}

@Composable
fun ActionRouteBox(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Route of Your Action") }
    )
}

@Composable
fun ActionDataBox(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Data of Your Action") }
    )
}

@Composable
fun ActionCreationScreen(onDismiss: () -> Unit, onSaveAction: (Action) -> Unit) {
    // 1. Text field input states
    var nameInput by remember { mutableStateOf("") }
    var ntRouteInput by remember { mutableStateOf("") }
    var dataInput by remember { mutableStateOf("") }

    // 2. Track which tab is currently selected
    var selectedTab by remember { mutableStateOf(ActionType.NT_ACTION) }

    // Color definitions pulled from your custom functions
    val primaryAccent = returnPrimaryColor()     // e.g., Purple color in your photo
    val secondaryAccent = returnSecondaryColor() // e.g., Soft gray or light purple border

    Scaffold(
        bottomBar = {
            // Bottom Save/Cancel Actions Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        // Dynamically build object depending on active type
                        val createdAction = when (selectedTab) {
                            ActionType.NT_ACTION -> Action.NTAction(
                                name = nameInput.trim(),
                                ntRoute = ntRouteInput.trim(),
                                data = dataInput.trim()
                            )
                            ActionType.PATH_PLANNER -> Action.PathPlanner(
                                name = nameInput.trim(),
                                pathName = ntRouteInput.trim() // Shared box acts as pathName
                            )
                            ActionType.POSE_SELECTION -> Action.PoseSelection(
                                name = nameInput.trim() // Takes only name for now
                            )
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
            // Top Header Line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Action", fontSize = 28.sp)
                IconButton(onClick = onDismiss) {
                    Text("✕", fontSize = 20.sp) // Simple text close button placeholder
                }
            }

            // Text Box 1: Universal Action Name (Always Visible)
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryAccent)
            )

            // Segmented Tab Row Selection Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                ActionType.entries.forEachIndexed { index, type ->
                    val isSelected = selectedTab == type
                    val shape = when (index) {
                        0 -> RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                        2 -> RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
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
                                ActionType.NT_ACTION -> "NT Action"
                                ActionType.PATH_PLANNER -> "PathPlanner"
                                ActionType.POSE_SELECTION -> "Pose Selection"
                            },
                            color = if (isSelected) primaryAccent else Color.Black,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Conditional Inputs Block based on Current Selection State
            when (selectedTab) {
                ActionType.NT_ACTION -> {
                    // Text Box 2: NT Route
                    OutlinedTextField(
                        value = ntRouteInput,
                        onValueChange = { ntRouteInput = it },
                        label = { Text("NT Route") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryAccent)
                    )
                    // Text Box 3: Data
                    OutlinedTextField(
                        value = dataInput,
                        onValueChange = { dataInput = it },
                        label = { Text("Data") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryAccent)
                    )
                }
                ActionType.PATH_PLANNER -> {
                    // Reuses Text Box 2 explicitly for path name entry
                    OutlinedTextField(
                        value = ntRouteInput,
                        onValueChange = { ntRouteInput = it },
                        label = { Text("Path Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryAccent)
                    )
                }
                ActionType.POSE_SELECTION -> {
                    // Pose Selection takes just Name for now, rendering no extra boxes.
                }
            }
        }
    }
}
