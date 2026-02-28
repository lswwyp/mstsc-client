package com.mstsc.client.ui.rdp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ScaleGestureDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RDP 渲染与输入视图：触屏映射为鼠标事件（点击、双击、拖拽、滚轮），
 * 键盘输入（含快捷键）通过 RdpEngine 同步至 Windows 端。
 *
 * 平板适配要点：
 * - 使用 ScaleGestureDetector 区分单指点击与双指缩放，避免误触；
 * - 大屏下触控区域全屏，工具栏可折叠（由 Activity 控制）。
 */
class RdpSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    var host: String = ""
    var port: Int = 3389
    var username: String = ""
    var password: String = ""
    var domain: String? = null
    var plainUsername: String = ""

    var onConnectionStateChanged: ((RdpSessionActivity.ConnectionState, String?) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var engine: RdpEngine? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 48f
    }
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val (fx, fy) = viewToFb(detector.focusX.toInt(), detector.focusY.toInt())
            engine?.sendMouseWheel(fx, fy, -detector.scaleFactor.toInt().coerceIn(-1, 1))
            return true
        }
    })

    private var lastDownX = 0f
    private var lastDownY = 0f
    private var lastMoveX = 0f
    private var lastMoveY = 0f
    private var isDragging = false

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        disconnect()
    }

    /** 当前远程帧缓冲尺寸，用于触屏坐标换算到远程桌面坐标 */
    private var fbWidth = 0
    private var fbHeight = 0

    /**
     * 连接：仅使用 Windows 自带 RDP 直连，无需在 Windows 上安装任何软件。
     * 需先按文档编译 FreeRDP 原生库并放入 jniLibs，或从 CI/Releases 下载预编译包。
     */
    fun connect() {
        scope.launch {
            val eng = createEngine()
            engine = eng
            if (eng == null) {
                onConnectionStateChanged?.invoke(
                    RdpSessionActivity.ConnectionState.Failed,
                    "请按 README 或「RDP 直连说明」编译并放入 RDP 原生库（jniLibs）后重新打包。直连仅用 Windows 自带远程桌面，无需在 Windows 安装任何软件。"
                )
                drawPlaceholder("请先放入 RDP 原生库\n见 README / RDP直连说明")
                return@launch
            }
            val w = width
            val h = height
            val ok = withContext(Dispatchers.IO) {
                eng.connect(host, port, plainUsername, domain, password, w, h)
            }
            if (ok) {
                onConnectionStateChanged?.invoke(RdpSessionActivity.ConnectionState.Connected, null)
            } else {
                onConnectionStateChanged?.invoke(
                    RdpSessionActivity.ConnectionState.Failed,
                    eng.lastError ?: "连接失败"
                )
                drawPlaceholder("连接失败: ${eng.lastError}")
            }
        }
    }

    /** 将视图坐标转换为远程桌面坐标（用于鼠标事件） */
    private fun viewToFb(x: Int, y: Int): Pair<Int, Int> {
        if (fbWidth <= 0 || fbHeight <= 0) return x to y
        val vw = width.coerceAtLeast(1)
        val vh = height.coerceAtLeast(1)
        val fx = (x * fbWidth) / vw
        val fy = (y * fbHeight) / vh
        return (fx.coerceIn(0, fbWidth - 1) to fy.coerceIn(0, fbHeight - 1))
    }

    fun disconnect() {
        engine?.disconnect()
        engine = null
    }

    private fun createEngine(): RdpEngine? {
        return try {
            // 若已集成 FreeRDP 原生库，可在此加载并返回 FreeRdpEngine
            // System.loadLibrary("freerdp-android2")
            // FreeRdpEngine(this)
            null
        } catch (e: UnsatisfiedLinkError) {
            null
        }
    }

    private fun drawPlaceholder(text: String) {
        holder.surface.let { surface ->
            if (!surface.isValid) return
            val canvas = surface.lockCanvas(null) ?: return
            try {
                canvas.drawColor(Color.BLACK)
                val lines = text.split("\n")
                var y = height / 2f - (lines.size * 30)
                lines.forEach { line ->
                    canvas.drawText(line, width / 2f - paint.measureText(line) / 2f, y, paint)
                    y += 56f
                }
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1) return true

        val (fx, fy) = viewToFb(event.x.toInt(), event.y.toInt())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastDownX = event.x
                lastDownY = event.y
                lastMoveX = event.x
                lastMoveY = event.y
                isDragging = false
                engine?.sendMouseDown(fx, fy, 1)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastMoveX
                val dy = event.y - lastMoveY
                if (dx * dx + dy * dy > 36) isDragging = true
                lastMoveX = event.x
                lastMoveY = event.y
                engine?.sendMouseMove(fx, fy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && (event.eventTime - event.downTime) < 300) {
                    engine?.sendMouseClick(fx, fy, 1)
                }
                engine?.sendMouseUp(fx, fy, 1)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                engine?.sendMouseUp(fx, fy, 1)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (engine?.sendKeyEvent(keyCode, true) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (engine?.sendKeyEvent(keyCode, false) == true) return true
        return super.onKeyUp(keyCode, event)
    }

    /**
     * RDP 引擎抽象：连接、断开、鼠标与键盘事件。真实实现依赖 FreeRDP JNI。
     */
    interface RdpEngine {
        fun connect(host: String, port: Int, username: String, domain: String?, password: String, width: Int, height: Int): Boolean
        fun disconnect()
        fun sendMouseMove(x: Int, y: Int)
        fun sendMouseDown(x: Int, y: Int, button: Int)
        fun sendMouseUp(x: Int, y: Int, button: Int)
        fun sendMouseClick(x: Int, y: Int, button: Int)
        fun sendMouseWheel(x: Int, y: Int, delta: Int)
        fun sendKeyEvent(keyCode: Int, down: Boolean): Boolean
        val lastError: String?
    }
}
