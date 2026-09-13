package com.team2207.roboroute.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

val Context.dataStore: DataStore<AppData> by dataStore(
    fileName = "app_data.pb",
    serializer = AppDataSerializer,
)

class ActionRepository(
    private val context: Context,
) {
    val appDataFlow: Flow<AppData> =
        context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(AppData.getDefaultInstance())
                } else {
                    throw exception
                }
            }

    suspend fun addAction(action: Action) {
        context.dataStore.updateData { currentAppData ->
            val builder = currentAppData.toBuilder()
            val existingIndex = builder.actionsList.indexOfFirst { it.id == action.id }
            if (existingIndex != -1) {
                builder.setActions(existingIndex, action)
            } else {
                builder.addActions(action)
            }
            builder.build()
        }
    }

    suspend fun updateNtPath(path: String) {
        context.dataStore.updateData { currentAppData ->
            currentAppData
                .toBuilder()
                .setNtPath(path)
                .build()
        }
    }

    suspend fun updateRobotDimensions(
        width: Double,
        length: Double,
    ) {
        context.dataStore.updateData { currentAppData ->
            currentAppData
                .toBuilder()
                .setRobotWidth(width)
                .setRobotLength(length)
                .build()
        }
    }

    suspend fun replaceAll(appData: AppData) {
        context.dataStore.updateData { appData }
    }
}
