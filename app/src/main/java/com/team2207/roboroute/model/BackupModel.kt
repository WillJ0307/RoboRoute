package com.team2207.roboroute.model

data class BackupModel(
    val actions: List<ActionExport>,
    val buttons: List<ButtonExport>
)

data class ActionExport(
    val id: Int,
    val name: String,
    val type: String,
    val pose: PoseExport? = null,
    val pathName: String? = null,
    val ntKey: String? = null,
    val ntData: String? = null
)

data class ButtonExport(
    val id: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val actionId: Int
)

data class PoseExport(
    val x: Double,
    val y: Double,
    val rotation: Double
)
