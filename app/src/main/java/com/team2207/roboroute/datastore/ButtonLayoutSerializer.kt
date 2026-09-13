package com.team2207.roboroute.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object ButtonLayoutSerializer : Serializer<ButtonLayout> {
    override val defaultValue: ButtonLayout = ButtonLayout.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ButtonLayout {
        try {
            return ButtonLayout.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(
        t: ButtonLayout,
        output: OutputStream,
    ) {
        t.writeTo(output)
    }
}
