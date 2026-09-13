package com.team2207.roboroute.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object AppDataSerializer : Serializer<AppData> {
    override val defaultValue: AppData = AppData.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): AppData {
        try {
            return AppData.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(
        t: AppData,
        output: OutputStream,
    ) = t.writeTo(output)
}
