package com.mstsc.client.util

/**
 * 设备标识格式：IP或域名:端口
 * 示例：123.45.67.89:3389、rdp.example.com:3389
 */
private val DEVICE_ID_PATTERN = Regex("^[^:]+:\\d{1,5}$")

fun isValidDeviceId(input: String?): Boolean {
    if (input.isNullOrBlank()) return false
    return DEVICE_ID_PATTERN.matches(input.trim())
}

fun requireNonBlank(value: String?, fieldName: String): String {
    val v = value?.trim()
    require(!v.isNullOrBlank()) { "$fieldName 为必填" }
    return v
}
