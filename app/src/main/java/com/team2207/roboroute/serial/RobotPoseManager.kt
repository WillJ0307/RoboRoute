package com.team2207.roboroute.serial

import com.team2207.roboroute.datastore.Pose2d
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object RobotPoseManager {
    private val _livePose = MutableStateFlow(Pose2d.newBuilder().setX(0.0).setY(0.0).setRotation(0.0).build())
    val livePose: StateFlow<Pose2d> = _livePose.asStateFlow()

    private val _isPoseValid = MutableStateFlow(false)
    val isPoseValid: StateFlow<Boolean> = _isPoseValid.asStateFlow()

    private var lastUpdateTime = 0L

    fun updateX(x: Double) {
        updateLastTime()
        _livePose.update { current ->
            current.toBuilder().setX(x).build()
        }
    }

    fun updateY(y: Double) {
        updateLastTime()
        _livePose.update { current ->
            current.toBuilder().setY(y).build()
        }
    }

    fun updateRotation(rotation: Double) {
        updateLastTime()
        _livePose.update { current ->
            current.toBuilder().setRotation(rotation).build()
        }
    }

    fun updateFullPose(x: Double, y: Double, rotation: Double) {
        updateLastTime()
        _livePose.value = Pose2d.newBuilder()
            .setX(x)
            .setY(y)
            .setRotation(rotation)
            .build()
    }

    private fun updateLastTime() {
        lastUpdateTime = System.currentTimeMillis()
        _isPoseValid.value = true
    }

    // Bot stays visible at last known position
    fun checkValidity() {
        // We keep it valid once we've received at least one pose
        // or we could have a "stale" indicator, but user wants it to stay.
        if (lastUpdateTime == 0L) {
            _isPoseValid.value = false
        } else {
            _isPoseValid.value = true
        }
    }
}
