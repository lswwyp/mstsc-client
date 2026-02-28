package com.mstsc.client.ui.rdp

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
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
    private var reconnectOnResume = false

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

        binding.btnFloatingDisconnect.setOnClickListener { disconnectAndFinish() }
        setupDraggableDisconnectButton()

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
        binding.rdpSurface.onDiagnostics = { msg ->
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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
                binding.rdpSurface.requestFocus()
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

    override fun onDestroy() {
        // Activity 真正销毁时释放连接；切后台仅 surface 销毁时不会断开，避免返回黑屏
        if (isFinishing) binding.rdpSurface.disconnect()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (connectionState == ConnectionState.Connected) {
            binding.rdpSurface.requestFocus()
            if (reconnectOnResume) {
                reconnectOnResume = false
                lifecycleScope.launch {
                    setState(ConnectionState.Connecting, "前台恢复，正在重连…")
                    binding.rdpSurface.reconnect()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        reconnectOnResume = connectionState == ConnectionState.Connected
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 连接后将键盘事件统一映射到远端（包括 Ctrl/Win 等）
        if (connectionState == ConnectionState.Connected) {
            binding.rdpSurface.requestFocus()
            binding.rdpSurface.handleKeyboardEvent(event)
            // 无论发送是否成功都拦截，避免 Ctrl+Tab/Win 等本地系统快捷键抢占
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setupDraggableDisconnectButton() {
        var downRawX = 0f
        var downRawY = 0f
        var dX = 0f
        var dY = 0f
        var moved = false
        val btn = binding.btnFloatingDisconnect
        btn.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dX = v.x - downRawX
                    dY = v.y - downRawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val parent = v.parent as View
                    val newX = (event.rawX + dX).coerceIn(0f, (parent.width - v.width).toFloat().coerceAtLeast(0f))
                    val newY = (event.rawY + dY).coerceIn(0f, (parent.height - v.height).toFloat().coerceAtLeast(0f))
                    if (kotlin.math.abs(event.rawX - downRawX) > 6f || kotlin.math.abs(event.rawY - downRawY) > 6f) {
                        moved = true
                    }
                    v.x = newX
                    v.y = newY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    override fun onBackPressed() {
        // 全屏会话中返回键也映射远端，退出请用悬浮断开按钮
        if (connectionState == ConnectionState.Connected) return
        super.onBackPressed()
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
