package com.mstsc.client.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RdpDeviceDao {
    @Query("SELECT * FROM rdp_devices ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<RdpDevice>>

    @Query("SELECT * FROM rdp_devices ORDER BY createdAt DESC")
    suspend fun getAll(): List<RdpDevice>

    @Query("SELECT * FROM rdp_devices WHERE id = :id")
    suspend fun getById(id: Long): RdpDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: RdpDevice): Long

    @Update
    suspend fun update(device: RdpDevice)

    @Delete
    suspend fun delete(device: RdpDevice)
}
