package com.mstsc.client.data

import kotlinx.coroutines.flow.Flow

/**
 * 设备仓库：对外提供设备的增删改查，供 UI 层使用。
 */
class DeviceRepository(private val dao: RdpDeviceDao) {

    fun devicesFlow(): Flow<List<RdpDevice>> = dao.getAllFlow()

    suspend fun getAll(): List<RdpDevice> = dao.getAll()

    suspend fun getById(id: Long): RdpDevice? = dao.getById(id)

    suspend fun add(device: RdpDevice): Long = dao.insert(device)

    suspend fun update(device: RdpDevice) = dao.update(device)

    suspend fun delete(device: RdpDevice) = dao.delete(device)
}
