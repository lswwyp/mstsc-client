package com.mstsc.client.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mstsc.client.data.RdpDevice
import com.mstsc.client.databinding.ItemDeviceBinding

/**
 * 设备列表适配器：展示设备标识、账号，并提供连接/编辑/删除操作。
 */
class DeviceListAdapter(
    private val onConnect: (RdpDevice) -> Unit,
    private val onEdit: (RdpDevice) -> Unit,
    private val onDelete: (RdpDevice) -> Unit
) : ListAdapter<RdpDevice, DeviceListAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: RdpDevice) {
            binding.tvDeviceId.text = device.deviceId
            binding.tvUsername.text = device.username
            binding.btnConnect.setOnClickListener { onConnect(device) }
            binding.btnEdit.setOnClickListener { onEdit(device) }
            binding.btnDelete.setOnClickListener { onDelete(device) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<RdpDevice>() {
        override fun areItemsTheSame(a: RdpDevice, b: RdpDevice) = a.id == b.id
        override fun areContentsTheSame(a: RdpDevice, b: RdpDevice) = a == b
    }
}
