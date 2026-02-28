package com.mstsc.client.ui.rdp

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mstsc.client.R
import com.mstsc.client.databinding.ActivityRdpSessionBinding
import kotlinx.coroutines.launch

/**
 * RDP 会话页：公网直连连接逻辑、连接状态反馈；
 * 触屏映射为鼠标事件、虚拟键盘输入同步至 Windows 端。
 * 平板适配：全屏 + 横竖屏、触控区域与键盘区域比例见 RdpSurfaceView。
 */
class RdpSessionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRdpSessionBinding
    private var connectionState: ConnectionState = ConnectionState.Idle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRdpSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID).orEmpty()
        val username = intent.getStringExtra(EXTRA_USERNAME).orEmpty()
        val password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()

        if (deviceId.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "参数不完整", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.toolbarSession.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnDisconnect.setOnClickListener { disconnectAndFinish() }
        binding.btnKeyboard.setOnClickListener { toggleKeyboard() }

        binding.rdpSurface.host = parseHost(deviceId)
        binding.rdpSurface.port = parsePort(deviceId)
        binding.rdpSurface.username = username
        binding.rdpSurface.password = password
        binding.rdpSurface.domain = parseDomain(username)
        binding.rdpSurface.plainUsername = parsePlainUsername(username)
        binding.rdpSurface.onConnectionStateChanged = { state, message ->
            runOnUiThread {
                connectionState = state
                updateConnectionUi(state, message)
            }
        }

        lifecycleScope.launch {
            setState(ConnectionState.Connecting, null)
            binding.rdpSurface.connect()
        }
    }

    private fun updateConnectionUi(state: ConnectionState, message: String?) {
        when (state) {
            ConnectionState.Connecting -> {
                binding.statusText.text = getString(R.string.connecting)
                binding.statusText.visibility = View.VISIBLE
                binding.progressBar.visibility = View.VISIBLE
            }
            ConnectionState.Connected -> {
                binding.statusText.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
            }
            ConnectionState.Failed -> {
                binding.statusText.text = getString(R.string.connect_failed) + (message?.let { ": $it" } ?: "")
                binding.statusText.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
                AlertDialog.Builder(this)
                    .setMessage(binding.statusText.text)
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }
            else -> { }
        }
    }

    private fun setState(state: ConnectionState, message: String?) {
        connectionState = state
        updateConnectionUi(state, message)
    }

    private fun disconnectAndFinish() {
        binding.rdpSurface.disconnect()
        finish()
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (binding.rdpSurface.hasFocus()) {
            imm?.showSoftInput(binding.rdpSurface, InputMethodManager.SHOW_IMPLICIT)
        } else {
            binding.rdpSurface.requestFocus()
            imm?.showSoftInput(binding.rdpSurface, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onBackPressed() {
        if (connectionState == ConnectionState.Connected) {
            AlertDialog.Builder(this)
                .setMessage("确定断开连接？")
                .setPositiveButton(R.string.yes) { _, _ -> disconnectAndFinish() }
                .setNegativeButton(R.string.no, null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    private fun parseHost(deviceId: String): String {
        val i = deviceId.lastIndexOf(':')
        return if (i > 0) deviceId.substring(0, i).trim() else deviceId.trim()
    }

    private fun parsePort(deviceId: String): Int {
        val i = deviceId.lastIndexOf(':')
        if (i <= 0) return 3389
        return deviceId.substring(i + 1).trim().toIntOrNull() ?: 3389
    }

    private fun parseDomain(username: String): String? {
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

    private fun parsePlainUsername(username: String): String {
        if (username.contains("\\")) {
            val parts = username.split("\\", limit = 2)
            return parts.getOrNull(1) ?: username
        }
        if (username.contains("@")) return username.substringBefore("@")
        return username
    }

    enum class ConnectionState { Idle, Connecting, Connected, Failed }

    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
    }
}
