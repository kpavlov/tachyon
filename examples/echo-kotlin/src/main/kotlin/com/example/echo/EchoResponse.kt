package com.example.echo

import kotlinx.serialization.SerialName
import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.Schema

@Schema
@SerialName("EchoResponse")
internal data class EchoResponse(
    @Description("Response message")
    val reply: String,
)
