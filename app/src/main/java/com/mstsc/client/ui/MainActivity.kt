package com.mstsc.client.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mstsc.client.R
import com.mstsc.client.data.AppDatabase
import com.mstsc.client.data.DeviceRepository
import com.mstsc.client.data.RdpDevice
import com.mstsc.client.databinding.ActivityMainBinding
import com.mstsc.client.util.isValidDeviceId
import com.mstsc.client.ui.rdp.RdpSessionActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 主页面：仅一个主界面，核心模块为「设备管理列表」。
 * 支持添加设备、连接、编辑、删除；平板适配通过 res/values-sw600dp 与 Fragment 可选扩展。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: DeviceRepository
    private lateinit var adapter: DeviceListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = DeviceRepository(AppDatabase.getInstance(this).rdpDeviceDao())
        adapter = DeviceListAdapter(
            onConnect = { startRdpSession(it) },
            onEdit = { showDeviceForm(it) },
            onDelete = { confirmDelete(it) }
        )

        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = adapter

        binding.fabAdd.setOnClickListener { showDeviceForm(null) }

        lifecycleScope.launch {
            repository.devicesFlow().collectLatest { adapter.submitList(it) }
        }
    }

    private fun showDeviceForm(device: RdpDevice?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_device_form, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (device == null) getString(R.string.add_device) else getString(R.string.edit))
            .setView(dialogView)
            .create()

        val tilDeviceId = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_device_id)
        val etDeviceId = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_device_id)
        val tilUsername = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_username)
        val etUsername = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_username)
        val tilPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_password)
        val etPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_password)

        device?.let {
            etDeviceId.setText(it.deviceId)
            etUsername.setText(it.username)
            etPassword.setText(it.password)
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save).setOnClickListener {
            val deviceId = etDeviceId.text?.toString()?.trim().orEmpty()
            val username = etUsername.text?.toString()?.trim().orEmpty()
            val password = etPassword.text?.toString().orEmpty()

            tilDeviceId.error = null
            tilUsername.error = null
            tilPassword.error = null

            if (!isValidDeviceId(deviceId)) {
                tilDeviceId.error = getString(R.string.invalid_device_id)
                return@setOnClickListener
            }
            if (username.isBlank()) {
                tilUsername.error = getString(R.string.required_field)
                return@setOnClickListener
            }
            if (password.isBlank()) {
                tilPassword.error = getString(R.string.required_field)
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    if (device == null) {
                        repository.add(RdpDevice(deviceId = deviceId, username = username, password = password))
                        Toast.makeText(this@MainActivity, R.string.add_device, Toast.LENGTH_SHORT).show()
                    } else {
                        repository.update(device.copy(deviceId = deviceId, username = username, password = password))
                        Toast.makeText(this@MainActivity, R.string.edit, Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(device: RdpDevice) {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch {
                    repository.delete(device)
                    Toast.makeText(this@MainActivity, R.string.delete, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    /**
     * 启动 RDP 会话：公网直连，参数与 mstsc 对齐。
     */
    private fun startRdpSession(device: RdpDevice) {
        val intent = Intent(this, RdpSessionActivity::class.java).apply {
            putExtra(RdpSessionActivity.EXTRA_DEVICE_ID, device.deviceId)
            putExtra(RdpSessionActivity.EXTRA_USERNAME, device.username)
            putExtra(RdpSessionActivity.EXTRA_PASSWORD, device.password)
        }
        startActivity(intent)
    }
}
