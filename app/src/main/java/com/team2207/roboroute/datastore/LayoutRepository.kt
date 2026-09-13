package com.team2207.roboroute.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

val Context.layoutDataStore: DataStore<ButtonLayout> by dataStore(
    fileName = "layout_data.pb",
    serializer = ButtonLayoutSerializer,
)

class LayoutRepository(
    private val context: Context,
) {
    val layoutFlow: Flow<ButtonLayout> =
        context.layoutDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(ButtonLayout.getDefaultInstance())
                } else {
                    throw exception
                }
            }

    suspend fun updateLayout(update: (ButtonLayout.Builder) -> Unit) {
        context.layoutDataStore.updateData { currentLayout ->
            val builder = currentLayout.toBuilder()
            update(builder)
            builder.build()
        }
    }

    suspend fun replaceAll(layout: ButtonLayout) {
        context.layoutDataStore.updateData { layout }
    }
}
