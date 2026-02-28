package com.mstsc.client.ui.rdp

import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import com.freerdp.freerdpcore.application.GlobalApp
import com.freerdp.freerdpcore.application.SessionState
import com.freerdp.freerdpcore.services.LibFreeRDP
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.lang.StringBuilder

/**
 * RDP 引擎实现：通过 FreeRDP JNI 直连 Windows 远程桌面。
 * 连接结果通过 LibFreeRDP.EventListener 回调；画面通过 SessionState + UIEventListener 回写到 View。
 */
internal class FreerdpEngine(
    private val context: Context,
    private val view: RdpSurfaceView
) : RdpSurfaceView.RdpEngine {
    private companion object {
        const val PTRFLAGS_DOWN = 0x8000
        const val PTRFLAGS_MOVE = 0x0800
        const val PTRFLAGS_LBUTTON = 0x1000
        const val PTRFLAGS_RBUTTON = 0x2000
        const val PTRFLAGS_WHEEL = 0x0200
        const val PTRFLAGS_WHEEL_NEGATIVE = 0x0100
    }

    @Volatile
    private var inst: Long = 0L

    private val connectionResult = AtomicBoolean(false)
    private val connectionError = AtomicReference<String?>(null)
    @Volatile
    private var connectThread: Thread? = null

    @Volatile
    private var session: SessionState? = null

    override fun connect(
        host: String,
        port: Int,
        username: String,
        domain: String?,
        password: String,
        width: Int,
        height: Int
    ): Boolean {
        if (inst != 0L) return false
        inst = LibFreeRDP.newInstance(context)
        if (inst == 0L) return false

        connectionResult.set(false)
        connectionError.set(null)
        val latch = CountDownLatch(1)

        LibFreeRDP.setEventListener(object : LibFreeRDP.EventListener {
            override fun OnPreConnect(instance: Long) {}
            override fun OnConnectionSuccess(instance: Long) {
                connectionResult.set(true)
                connectionError.set(null)
                latch.countDown()
            }
            override fun OnConnectionFailure(instance: Long) {
                connectionResult.set(false)
                connectionError.set(LibFreeRDP.getLastErrorString(instance) ?: "Connection failed")
                latch.countDown()
            }
            override fun OnDisconnecting(instance: Long) {}
            override fun OnDisconnected(instance: Long) {}
        })

        if (!LibFreeRDP.setConnectionInfoFromParams(context, inst, host, port, username, domain, password, width, height)) {
            connectionError.set("setConnectionInfo failed")
            latch.countDown()
            LibFreeRDP.freeInstance(inst)
            inst = 0L
            return false
        }

        // 注册 Session + UIEventListener，以便 native 的 OnGraphicsUpdate/OnGraphicsResize 能回写画面
        val sessionState = SessionState(inst)
        val initialBitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        sessionState.setSurface(android.graphics.drawable.BitmapDrawable(context.resources, initialBitmap))
        sessionState.setUIEventListener(object : LibFreeRDP.UIEventListener {
            override fun OnSettingsChanged(w: Int, h: Int, bpp: Int) {}
            override fun OnAuthenticate(username: StringBuilder, domain: StringBuilder, password: StringBuilder) = false
            override fun OnGatewayAuthenticate(username: StringBuilder, domain: StringBuilder, password: StringBuilder) = false
            override fun OnVerifiyCertificateEx(host: String, port: Long, commonName: String, subject: String, issuer: String, fingerprint: String, flags: Long) = 0
            override fun OnVerifyChangedCertificateEx(host: String, port: Long, commonName: String, subject: String, issuer: String, fingerprint: String, oldSubject: String, oldIssuer: String, oldFingerprint: String, flags: Long) = 0
            override fun OnGraphicsUpdate(x: Int, y: Int, w: Int, h: Int) {
                val s = session ?: return
                val bmp = s.surface?.bitmap ?: return
                val currentInst = inst
                if (currentInst == 0L) return
                val ok = LibFreeRDP.updateGraphics(currentInst, bmp, x, y, w, h)
                if (!ok) {
                    connectionError.set("图像更新失败（updateGraphics=false）")
                    return
                }
                view.post {
                    view.onFrameUpdated(x, y, w, h)
                    view.drawRemoteFrame()
                }
            }
            override fun OnGraphicsResize(w: Int, h: Int, bpp: Int) {
                val newBitmap = if (bpp > 16) {
                    Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                } else {
                    Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.RGB_565)
                }
                session?.setSurface(android.graphics.drawable.BitmapDrawable(context.resources, newBitmap))
                view.post {
                    view.setRemoteBitmap(newBitmap)
                    view.setRemoteSize(w, h)
                }
            }
            override fun OnRemoteClipboardChanged(data: String) {}
        })
        session = sessionState
        GlobalApp.registerSession(inst, sessionState)

        // 关键：connect 需要在独立线程中常驻，维持 FreeRDP 会话事件循环
        val runner = Thread {
            val ok = LibFreeRDP.connect(inst)
            if (!ok && !connectionResult.get()) {
                connectionError.compareAndSet(null, LibFreeRDP.getLastErrorString(inst) ?: "connect returned false")
                latch.countDown()
            }
        }
        connectThread = runner
        runner.start()
        latch.await(30, TimeUnit.SECONDS)

        if (!connectionResult.get()) {
            GlobalApp.unregisterSession(inst)
            session = null
            LibFreeRDP.disconnect(inst)
            try {
                connectThread?.join(1000)
            } catch (_: InterruptedException) {
            }
            connectThread = null
            LibFreeRDP.freeInstance(inst)
            inst = 0L
            return false
        }
        view.setRemoteBitmap(initialBitmap)
        view.setRemoteSize(width, height)
        return true
    }

    override fun disconnect() {
        val i = inst
        if (i != 0L) {
            inst = 0L
            session = null
            GlobalApp.unregisterSession(i)
            LibFreeRDP.disconnect(i)
            try {
                connectThread?.join(1000)
            } catch (_: InterruptedException) {
            }
            connectThread = null
            LibFreeRDP.freeInstance(i)
        }
    }

    override fun sendMouseMove(x: Int, y: Int) {
        if (inst != 0L) {
            val ok = LibFreeRDP.sendCursorEvent(inst, x, y, PTRFLAGS_MOVE)
            if (!ok) connectionError.set("鼠标移动事件发送失败")
        }
    }

    override fun sendMouseDown(x: Int, y: Int, button: Int) {
        if (inst != 0L) {
            val btn = if (button == 2) PTRFLAGS_RBUTTON else PTRFLAGS_LBUTTON
            val ok = LibFreeRDP.sendCursorEvent(inst, x, y, btn or PTRFLAGS_DOWN)
            if (!ok) connectionError.set("鼠标按下事件发送失败")
        }
    }

    override fun sendMouseUp(x: Int, y: Int, button: Int) {
        if (inst != 0L) {
            val btn = if (button == 2) PTRFLAGS_RBUTTON else PTRFLAGS_LBUTTON
            val ok = LibFreeRDP.sendCursorEvent(inst, x, y, btn)
            if (!ok) connectionError.set("鼠标抬起事件发送失败")
        }
    }

    override fun sendMouseClick(x: Int, y: Int, button: Int) {
        sendMouseDown(x, y, button)
        sendMouseUp(x, y, button)
    }

    override fun sendMouseWheel(x: Int, y: Int, delta: Int) {
        // 按 FreeRDP Android Mouse.java：上滚 0x0078，下滚 0x0088|NEGATIVE
        if (inst != 0L) {
            val flags = if (delta < 0) {
                PTRFLAGS_WHEEL or PTRFLAGS_WHEEL_NEGATIVE or 0x0088
            } else {
                PTRFLAGS_WHEEL or 0x0078
            }
            val ok = LibFreeRDP.sendCursorEvent(inst, x, y, flags)
            if (!ok) connectionError.set("鼠标滚轮事件发送失败")
        }
    }

    override fun sendKeyEvent(keyCode: Int, down: Boolean): Boolean {
        if (inst == 0L) return false
        val vk = toWindowsVk(keyCode)
        return LibFreeRDP.sendKeyEvent(inst, vk, down)
    }

    override fun sendUnicodeKey(keyCode: Int, down: Boolean): Boolean {
        if (inst == 0L) return false
        return LibFreeRDP.sendUnicodeKeyEvent(inst, keyCode, down)
    }

    override val lastError: String?
        get() = connectionError.get() ?: (if (inst != 0L) LibFreeRDP.getLastErrorString(inst) else null)

    private fun toWindowsVk(androidKeyCode: Int): Int {
        if (androidKeyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            return 0x41 + (androidKeyCode - KeyEvent.KEYCODE_A) // A..Z
        }
        if (androidKeyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            return 0x30 + (androidKeyCode - KeyEvent.KEYCODE_0) // 0..9
        }
        if (androidKeyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
            return 0x60 + (androidKeyCode - KeyEvent.KEYCODE_NUMPAD_0) // NUMPAD0..9
        }
        return when (androidKeyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> 0x0D
            KeyEvent.KEYCODE_DEL -> 0x08 // Backspace
            KeyEvent.KEYCODE_BACK -> 0x1B // ESC
            KeyEvent.KEYCODE_TAB -> 0x09
            KeyEvent.KEYCODE_SPACE -> 0x20
            KeyEvent.KEYCODE_ESCAPE -> 0x1B
            KeyEvent.KEYCODE_FORWARD_DEL -> 0x2E
            KeyEvent.KEYCODE_INSERT -> 0x2D
            KeyEvent.KEYCODE_MOVE_HOME -> 0x24
            KeyEvent.KEYCODE_MOVE_END -> 0x23
            KeyEvent.KEYCODE_PAGE_UP -> 0x21
            KeyEvent.KEYCODE_PAGE_DOWN -> 0x22
            KeyEvent.KEYCODE_MINUS -> 0xBD
            KeyEvent.KEYCODE_EQUALS -> 0xBB
            KeyEvent.KEYCODE_LEFT_BRACKET -> 0xDB
            KeyEvent.KEYCODE_RIGHT_BRACKET -> 0xDD
            KeyEvent.KEYCODE_BACKSLASH -> 0xDC
            KeyEvent.KEYCODE_SEMICOLON -> 0xBA
            KeyEvent.KEYCODE_APOSTROPHE -> 0xDE
            KeyEvent.KEYCODE_COMMA -> 0xBC
            KeyEvent.KEYCODE_PERIOD -> 0xBE
            KeyEvent.KEYCODE_SLASH -> 0xBF
            KeyEvent.KEYCODE_GRAVE -> 0xC0
            KeyEvent.KEYCODE_DPAD_LEFT -> 0x25
            KeyEvent.KEYCODE_DPAD_UP -> 0x26
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0x27
            KeyEvent.KEYCODE_DPAD_DOWN -> 0x28
            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> 0x6F
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> 0x6A
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> 0x6D
            KeyEvent.KEYCODE_NUMPAD_ADD -> 0x6B
            KeyEvent.KEYCODE_NUMPAD_DOT -> 0x6E
            KeyEvent.KEYCODE_CTRL_LEFT -> 0xA2
            KeyEvent.KEYCODE_CTRL_RIGHT -> 0xA3
            KeyEvent.KEYCODE_SHIFT_LEFT -> 0xA0
            KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xA1
            KeyEvent.KEYCODE_ALT_LEFT -> 0xA4
            KeyEvent.KEYCODE_ALT_RIGHT -> 0xA5
            KeyEvent.KEYCODE_META_LEFT -> 0x5B
            KeyEvent.KEYCODE_META_RIGHT -> 0x5C
            KeyEvent.KEYCODE_MENU -> 0x5D
            KeyEvent.KEYCODE_CAPS_LOCK -> 0x14
            KeyEvent.KEYCODE_SCROLL_LOCK -> 0x91
            KeyEvent.KEYCODE_BREAK -> 0x13
            KeyEvent.KEYCODE_SYSRQ -> 0x2C
            KeyEvent.KEYCODE_F1 -> 0x70
            KeyEvent.KEYCODE_F2 -> 0x71
            KeyEvent.KEYCODE_F3 -> 0x72
            KeyEvent.KEYCODE_F4 -> 0x73
            KeyEvent.KEYCODE_F5 -> 0x74
            KeyEvent.KEYCODE_F6 -> 0x75
            KeyEvent.KEYCODE_F7 -> 0x76
            KeyEvent.KEYCODE_F8 -> 0x77
            KeyEvent.KEYCODE_F9 -> 0x78
            KeyEvent.KEYCODE_F10 -> 0x79
            KeyEvent.KEYCODE_F11 -> 0x7A
            KeyEvent.KEYCODE_F12 -> 0x7B
            else -> androidKeyCode
        }
    }
}
