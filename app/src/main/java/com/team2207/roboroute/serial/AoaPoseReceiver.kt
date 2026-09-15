package com.team2207.roboroute.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.ParcelFileDescriptor
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.team2207.roboroute.datastore.ActionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

object AoaSubscribeTrigger {
    val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun trigger() {
        events.tryEmit(Unit)
    }
}

class AoaPoseReceiver(
    private val context: Context,
    private val repository: ActionRepository,
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var accessory: UsbAccessory? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val scope = CoroutineScope(Dispatchers.IO)
    private val gson = Gson()

    private var currentNtPath: String = ""
    private val seenKeys = mutableSetOf<String>()

    // Pending pose stages are coalesced and published at a fixed rate so a burst of
    // telemetry messages cannot flood the UI with recompositions.
    private val poseLock = Any()
    private var pendingFullPose = false
    private var pendingAxisMask = 0
    private var pendingX = 0.0
    private var pendingY = 0.0
    private var pendingRotation = 0.0

    companion object {
        private const val ACTION_USB_PERMISSION = "com.team2207.roboroute.USB_PERMISSION"
        private const val ALLIANCE_PATH = "FMSInfo/isRedAlliance"
        private const val POSE_PUBLISH_INTERVAL_MS = 33L
        private const val NEWLINE_BYTE = '\n'.code.toByte()
        private const val MAX_LINE_BYTES = 1 shl 22
        private const val AXIS_X = 1
        private const val AXIS_Y = 2
        private const val AXIS_ROTATION = 4
    }

    private val usbReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val usbAccessory: UsbAccessory? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                            }
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            usbAccessory?.let { openAccessory(it) }
                        } else {
                            SerialLogManager.addLog("AOA: USB Permission denied")
                        }
                    }
                }
            }
        }

    fun start() {
        SerialLogManager.addLog("AOA: Receiver instance starting...")
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(usbReceiver, filter)
        }
        findAndConnect()

        // Publish staged pose updates at a fixed rate regardless of incoming message rate.
        scope.launch {
            while (true) {
                publishPendingPose()
                delay(POSE_PUBLISH_INTERVAL_MS)
            }
        }

        // Observe path changes and re-subscribe
        scope.launch {
            repository.appDataFlow
                .map { it.ntPath }
                .distinctUntilChanged()
                .collect { path ->
                    currentNtPath = path
                    SerialLogManager.addLog("AOA: NT Path updated to: $path")
                    if (path.isNotEmpty() && outputStream != null) {
                        subscribe(path)
                    }
                }
        }

        // Listen for manual subscribe triggers
        scope.launch {
            AoaSubscribeTrigger.events.collect {
                if (outputStream != null) {
                    SerialLogManager.addLog("AOA: Manual subscription triggered")
                    if (currentNtPath.isNotEmpty()) subscribe(currentNtPath)
                    subscribe(ALLIANCE_PATH)
                }
            }
        }

        // Periodic Alliance check (every minute)
        scope.launch {
            while (true) {
                delay(60000)
                if (outputStream != null) {
                    SerialLogManager.addLog("AOA: Periodic Alliance check...")
                    subscribe(ALLIANCE_PATH)
                }
            }
        }
    }

    fun handleIntent(intent: Intent) {
        SerialLogManager.addLog("AOA: Handling intent: ${intent.action}")
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED == intent.action) {
            val usbAccessory: UsbAccessory? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                }
            usbAccessory?.let {
                SerialLogManager.addLog("AOA: Accessory from intent: ${it.model}")
                openAccessory(it)
            }
        }
    }

    fun subscribe(path: String) {
        SerialLogManager.addLog("AOA: Attempting to subscribe to: $path")
        if (outputStream == null) {
            SerialLogManager.addLog("AOA: Error - outputStream is null, cannot send SUB")
            return
        }
        // Normalize path: leading slash if not present
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val subscribeMessage = mapOf("subscribe" to listOf(normalizedPath))
        val json = gson.toJson(subscribeMessage) + "\n"
        try {
            val bytes = json.toByteArray()
            outputStream?.write(bytes)
            outputStream?.flush()
            SerialLogManager.addLog("AOA: SENT SUB (${bytes.size} bytes): ${json.trim()}")
        } catch (e: IOException) {
            SerialLogManager.addLog("AOA: Write Error: ${e.message}")
        }
    }

    fun stop() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
        }
        scope.cancel()
        closeAccessory()
    }

    private fun findAndConnect() {
        val accessories = usbManager.accessoryList
        if (accessories.isNullOrEmpty()) {
            SerialLogManager.addLog("AOA: No accessories found in list")
            return
        }

        SerialLogManager.addLog("AOA: Found ${accessories.size} accessories")
        val target = accessories[0]

        if (usbManager.hasPermission(target)) {
            openAccessory(target)
        } else {
            SerialLogManager.addLog("AOA: Requesting permission for ${target.model}")
            val flags = PendingIntent.FLAG_IMMUTABLE
            val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
            usbManager.requestPermission(target, permissionIntent)
        }
    }

    private fun openAccessory(usbAccessory: UsbAccessory) {
        if (accessory != null) {
            SerialLogManager.addLog("AOA: Accessory already open, closing old one first")
            closeAccessory()
        }

        seenKeys.clear()
        SerialLogManager.addLog("AOA: Opening accessory: ${usbAccessory.model}")
        fileDescriptor = usbManager.openAccessory(usbAccessory)
        if (fileDescriptor != null) {
            accessory = usbAccessory
            val fd = fileDescriptor!!.fileDescriptor
            inputStream = FileInputStream(fd)
            outputStream = FileOutputStream(fd)

            startReading()

            SerialLogManager.addLog("AOA: Accessory opened successfully")

            // Initial subscriptions
            scope.launch {
                delay(1000)
                if (currentNtPath.isNotEmpty()) subscribe(currentNtPath)
                subscribe(ALLIANCE_PATH)
            }
        } else {
            SerialLogManager.addLog("AOA: Error - Failed to open accessory descriptor")
        }
    }

    private fun closeAccessory() {
        try {
            fileDescriptor?.close()
        } catch (e: IOException) {
        }
        fileDescriptor = null
        accessory = null
        inputStream = null
        outputStream = null
        SerialLogManager.addLog("AOA: Accessory closed")
    }

    private var lastDataLogTime = 0L

    private fun startReading() {
        scope.launch {
            val chunk = ByteArray(16384)
            val pending = ByteArrayOutputStream(4096)
            try {
                while (true) {
                    val bytesRead = inputStream?.read(chunk) ?: -1
                    if (bytesRead > 0) {
                        var lineStart = 0
                        for (i in 0 until bytesRead) {
                            if (chunk[i] == NEWLINE_BYTE) {
                                pending.write(chunk, lineStart, i - lineStart)
                                val line = String(pending.toByteArray(), StandardCharsets.UTF_8).trim()
                                pending.reset()
                                lineStart = i + 1
                                if (line.isNotEmpty()) {
                                    processLine(line)
                                }
                            }
                        }
                        if (lineStart < bytesRead) {
                            pending.write(chunk, lineStart, bytesRead - lineStart)
                        }

                        // Guard against an unbounded partial frame (e.g. a giant listing or a
                        // malformed stream). Dropping it is safe; a pending list is not useful.
                        if (pending.size() > MAX_LINE_BYTES) {
                            pending.reset()
                        }

                        // Log "Data received" at most once per second to avoid flooding
                        val now = System.currentTimeMillis()
                        if (now - lastDataLogTime > 1000) {
                            SerialLogManager.addLog("AOA: Telemetry data being received...")
                            lastDataLogTime = now
                        }
                    } else if (bytesRead == -1) {
                        SerialLogManager.addLog("AOA: End of stream reached")
                        break
                    }
                }
            } catch (e: IOException) {
                SerialLogManager.addLog("AOA: Read Error: ${e.message}")
            }
        }
    }

    private fun processLine(line: String) {
        // Fast path: only handle value messages. This skips the (possibly huge) topic listing
        // and other protocol messages without building a full Gson tree for them.
        if (!line.startsWith("{") || !line.contains("\"key\"")) {
            return
        }

        try {
            val json = gson.fromJson(line, JsonObject::class.java)
            val key = json.get("key")?.asString ?: return
            val valueElement = json.get("value") ?: return

            if (seenKeys.add(key)) {
                SerialLogManager.addLog("AOA: Received topic: $key")
            }

            // Handle Alliance Color
            if (key.endsWith(ALLIANCE_PATH)) {
                if (valueElement.isJsonPrimitive) {
                    RobotPoseManager.updateAlliance(valueElement.asBoolean)
                }
                return
            }

            if (currentNtPath.isEmpty()) return

            val normalizedPath = if (currentNtPath.startsWith("/")) currentNtPath else "/$currentNtPath"

            // Case 1: Full Pose2d match
            if (key == normalizedPath) {
                if (valueElement.isJsonArray) {
                    val arr = valueElement.asJsonArray
                    if (arr.size() >= 3) {
                        stageFullPose(
                            arr.get(0).asDouble,
                            arr.get(1).asDouble,
                            arr.get(2).asDouble,
                        )
                        return
                    }
                } else if (valueElement.isJsonObject) {
                    val obj = valueElement.asJsonObject
                    val x =
                        obj.get("x")?.asDouble ?: obj
                            .get("translation")
                            ?.asJsonObject
                            ?.get("x")
                            ?.asDouble
                    val y =
                        obj.get("y")?.asDouble ?: obj
                            .get("translation")
                            ?.asJsonObject
                            ?.get("y")
                            ?.asDouble
                    val rotElement = obj.get("rotation") ?: obj.get("rot") ?: obj.get("r")
                    val rotation = rotElement?.let { if (it.isJsonObject) it.asJsonObject.get("value")?.asDouble else it.asDouble }
                    if (x != null && y != null && rotation != null) {
                        stageFullPose(x, y, rotation)
                        return
                    }
                }
            }

            // Case 2: Individual components
            if (key.startsWith(normalizedPath)) {
                val subKey = key.substringAfter(normalizedPath).removePrefix("/")
                if (valueElement.isJsonPrimitive) {
                    val value = valueElement.asDouble
                    when (subKey.uppercase()) {
                        "X" -> stageAxis(AXIS_X, value)
                        "Y" -> stageAxis(AXIS_Y, value)
                        "ROTATION", "R", "ROT" -> stageAxis(AXIS_ROTATION, value)
                    }
                }
            }
        } catch (e: Exception) {
            SerialLogManager.addLog("AOA: Parse Error: ${e.message}")
        }
    }

    private fun stageFullPose(
        x: Double,
        y: Double,
        rotation: Double,
    ) {
        synchronized(poseLock) {
            pendingX = x
            pendingY = y
            pendingRotation = rotation
            pendingFullPose = true
        }
    }

    private fun stageAxis(
        axis: Int,
        value: Double,
    ) {
        synchronized(poseLock) {
            when (axis) {
                AXIS_X -> pendingX = value
                AXIS_Y -> pendingY = value
                AXIS_ROTATION -> pendingRotation = value
            }
            pendingAxisMask = pendingAxisMask or axis
        }
    }

    private fun publishPendingPose() {
        var hasFullPose = false
        var axisMask = 0
        var x = 0.0
        var y = 0.0
        var rotation = 0.0

        synchronized(poseLock) {
            hasFullPose = pendingFullPose
            axisMask = pendingAxisMask
            x = pendingX
            y = pendingY
            rotation = pendingRotation
            pendingFullPose = false
            pendingAxisMask = 0
        }

        if (hasFullPose) {
            RobotPoseManager.updateFullPose(x, y, rotation)
        } else if (axisMask != 0) {
            if (axisMask and AXIS_X != 0) RobotPoseManager.updateX(x)
            if (axisMask and AXIS_Y != 0) RobotPoseManager.updateY(y)
            if (axisMask and AXIS_ROTATION != 0) RobotPoseManager.updateRotation(rotation)
        }
    }
}
