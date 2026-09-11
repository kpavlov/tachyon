package com.example.echo

import kotlinx.serialization.SerialName
import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.Schema

@Schema
@SerialName("EchoRequest")
internal data class EchoRequest(
    @Description("Message to echo")
    val message: String,
)
