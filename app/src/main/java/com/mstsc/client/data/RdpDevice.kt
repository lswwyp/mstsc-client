package com.mstsc.client.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 设备实体：对应「设备管理列表」中一条记录。
 * 设备标识格式：IP或域名:端口，与 mstsc 公网直连一致。
 */
@Entity(tableName = "rdp_devices")
data class RdpDevice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 设备标识，必填，格式 IP/域名:端口，如 123.45.67.89:3389 */
    val deviceId: String,
    /** Windows 账号，必填，支持域账号（DOMAIN\user 或 user@domain） */
    val username: String,
    /** Windows 密码，必填 */
    val password: String,
    /** 显示名称（可选，列表展示用） */
    val displayName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 解析 host:port，公网直连用 */
    fun host(): String {
        val i = deviceId.lastIndexOf(':')
        return if (i > 0) deviceId.substring(0, i).trim() else deviceId.trim()
    }

    fun port(): Int {
        val i = deviceId.lastIndexOf(':')
        if (i <= 0) return 3389
        return deviceId.substring(i + 1).trim().toIntOrNull() ?: 3389
    }

    /** 解析域账号：DOMAIN\user -> domain=DOMAIN, user=user；user@domain 同理 */
    fun domain(): String? {
        if (username.contains("\\")) {
            val parts = username.split("\\", limit = 2)
            return parts[0].takeIf { it.isNotBlank() }
        }
        if (username.contains("@")) {
            val parts = username.split("@", limit = 2)
            return parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
        return null
    }

    fun plainUsername(): String {
        if (username.contains("\\")) {
            val parts = username.split("\\", limit = 2)
            return parts.getOrNull(1) ?: username
        }
        if (username.contains("@")) {
            return username.substringBefore("@")
        }
        return username
    }
}
