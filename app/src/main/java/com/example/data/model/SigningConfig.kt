package com.example.data.model

import java.io.Serializable

data class SigningConfig(
    val isAutoSigning: Boolean = true,
    val v1Enabled: Boolean = true,
    val v2Enabled: Boolean = true,
    val v3Enabled: Boolean = true,
    val v4Enabled: Boolean = false,
    val useCustomKeystore: Boolean = false,
    val customKeystorePath: String? = null,
    val keystorePassword: String = "",
    val keyAlias: String = "",
    val keyPassword: String = ""
) : Serializable
