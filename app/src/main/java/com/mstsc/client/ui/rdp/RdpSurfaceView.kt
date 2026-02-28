package com.mstsc.client.ui.rdp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.InputDevice
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ScaleGestureDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    var onDiagnostics: ((String) -> Unit)? = null

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
    /** 本手势中是否已发送过左键按下（用于拖拽选中）；双指时绝不发左键，避免滚动误选中 */
    private var sentMouseDownThisGesture = false
    /** 本手势是否出现过双指（双指滚动时不发任何左键事件） */
    private var multiTouchGesture = false

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        drawRemoteFrame()
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        drawRemoteFrame()
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    /** 当前远程帧缓冲尺寸，用于触屏坐标换算到远程桌面坐标 */
    private var fbWidth = 0
    private var fbHeight = 0

    /** 远程桌面画面 Bitmap，由 FreeRDP 回调更新，在此绘制到 Surface */
    @Volatile
    private var remoteBitmap: Bitmap? = null
    @Volatile
    private var displayScale: Float = 1.0f
    @Volatile
    private var renderLeft = 0f
    @Volatile
    private var renderTop = 0f
    @Volatile
    private var renderWidth = 0f
    @Volatile
    private var renderHeight = 0f
    @Volatile
    private var connectStartedAtMs: Long = 0L
    @Volatile
    private var firstFrameAtMs: Long = 0L
    @Volatile
    private var frameCount: Int = 0

    /** 由 RDP 引擎在连接成功后设置远程桌面分辨率，用于触屏坐标换算 */
    fun setRemoteSize(w: Int, h: Int) {
        fbWidth = w.coerceAtLeast(1)
        fbHeight = h.coerceAtLeast(1)
    }

    /** 设置远程画面 Bitmap（由 OnGraphicsResize 等调用），必须在主线程 */
    fun setRemoteBitmap(bitmap: Bitmap?) {
        remoteBitmap = bitmap
        if (bitmap != null) drawRemoteFrame()
    }

    /** 引擎在收到并应用图像更新后调用，用于首帧与帧计数诊断 */
    fun onFrameUpdated(x: Int, y: Int, w: Int, h: Int) {
        val now = System.currentTimeMillis()
        frameCount += 1
        if (firstFrameAtMs == 0L) {
            firstFrameAtMs = now
            val cost = (firstFrameAtMs - connectStartedAtMs).coerceAtLeast(0)
            onDiagnostics?.invoke("已收到首帧，耗时 ${cost}ms，区域 ${x},${y} ${w}x${h}")
        }
    }

    /** 将当前 remoteBitmap 绘制到 Surface（主线程调用，供 OnGraphicsUpdate 回调后刷新） */
    fun drawRemoteFrame() {
        val bmp = remoteBitmap ?: return
        val surface = holder.surface
        if (!surface.isValid) return
        val canvas = surface.lockCanvas(null) ?: return
        try {
            canvas.drawColor(Color.BLACK)
            val vw = width.coerceAtLeast(1)
            val vh = height.coerceAtLeast(1)
            val src = Rect(0, 0, bmp.width, bmp.height)
            val dw = vw * displayScale
            val dh = vh * displayScale
            val left = (vw - dw) / 2f
            val top = (vh - dh) / 2f
            renderLeft = left
            renderTop = top
            renderWidth = dw
            renderHeight = dh
            val dst = RectF(left, top, left + dw, top + dh)
            canvas.drawBitmap(bmp, src, dst, null)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    /** 全屏/缩小显示切换：默认全屏，点击后缩小到 80% */
    fun toggleDisplayScale(): Boolean {
        displayScale = if (displayScale >= 0.99f) 0.8f else 1.0f
        drawRemoteFrame()
        return displayScale < 0.99f
    }

    /**
     * 连接：仅使用 Windows 自带 RDP 直连，无需在 Windows 上安装任何软件。
     * 需先按文档编译 FreeRDP 原生库并放入 jniLibs，或从 CI/Releases 下载预编译包。
     */
    fun connect() {
        scope.launch {
            connectStartedAtMs = System.currentTimeMillis()
            firstFrameAtMs = 0L
            frameCount = 0
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
            // 视图初次创建时 width/height 可能为 0，给出屏幕分辨率保底，避免请求 0x0 导致黑屏
            val w = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val h = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            val ok = withContext(Dispatchers.IO) {
                eng.connect(host, port, plainUsername, domain, password, w, h)
            }
            if (ok) {
                onConnectionStateChanged?.invoke(RdpSessionActivity.ConnectionState.Connected, null)
                onDiagnostics?.invoke("RDP连接成功，等待首帧...")
                scope.launch {
                    delay(3500)
                    if (engine === eng && firstFrameAtMs == 0L) {
                        val waitMs = System.currentTimeMillis() - connectStartedAtMs
                        onDiagnostics?.invoke(
                            "首帧超时：连接后 ${waitMs}ms 仍未收到图像。frameCount=$frameCount, lastError=${eng.lastError ?: "none"}"
                        )
                    }
                }
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
        val rw = renderWidth.takeIf { it > 1f } ?: width.toFloat().coerceAtLeast(1f)
        val rh = renderHeight.takeIf { it > 1f } ?: height.toFloat().coerceAtLeast(1f)
        val rx = renderLeft
        val ry = renderTop
        val nx = ((x - rx) / rw).coerceIn(0f, 1f)
        val ny = ((y - ry) / rh).coerceIn(0f, 1f)
        val fx = (nx * (fbWidth - 1)).toInt()
        val fy = (ny * (fbHeight - 1)).toInt()
        return (fx.coerceIn(0, fbWidth - 1) to fy.coerceIn(0, fbHeight - 1))
    }

    fun disconnect() {
        engine?.disconnect()
        engine = null
        remoteBitmap = null
        firstFrameAtMs = 0L
        frameCount = 0
    }

    /** 连接态下键盘事件统一走远端映射（硬键盘/部分系统按键） */
    fun handleKeyboardEvent(event: KeyEvent): Boolean {
        val eng = engine ?: return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> eng.sendKeyEvent(event.keyCode, true)
            KeyEvent.ACTION_UP -> eng.sendKeyEvent(event.keyCode, false)
            KeyEvent.ACTION_MULTIPLE -> {
                val text = event.characters
                if (text.isNullOrEmpty()) false else sendUnicodeText(text)
            }
            else -> false
        }
    }

    /** 软键盘文本输入映射：按 Unicode 逐字发送到远端 */
    fun sendUnicodeText(text: String): Boolean {
        val eng = engine ?: return false
        var sent = false
        for (ch in text) {
            val code = ch.code
            val downOk = eng.sendUnicodeKey(code, true)
            val upOk = eng.sendUnicodeKey(code, false)
            sent = sent || (downOk && upOk)
        }
        return sent
    }

    fun reconnect() {
        disconnect()
        connect()
    }

    private fun createEngine(): RdpEngine? {
        return try {
            // 触发 LibFreeRDP 类加载，其 static 块会加载 freerdp-android 等 .so
            Class.forName("com.freerdp.freerdpcore.services.LibFreeRDP")
            if (com.freerdp.freerdpcore.services.LibFreeRDP.isLoaded()) {
                FreerdpEngine(context, this)
            } else {
                null
            }
        } catch (e: Throwable) {
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
        if (event.pointerCount > 1) {
            multiTouchGesture = true
            return true
        }

        val (fx, fy) = viewToFb(event.x.toInt(), event.y.toInt())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastDownX = event.x
                lastDownY = event.y
                lastMoveX = event.x
                lastMoveY = event.y
                isDragging = false
                sentMouseDownThisGesture = false
                multiTouchGesture = false
                engine?.sendMouseMove(fx, fy)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) multiTouchGesture = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastMoveX
                val dy = event.y - lastMoveY
                if (dx * dx + dy * dy > 36) isDragging = true
                if (event.pointerCount == 1 && !multiTouchGesture) {
                    if (isDragging && !sentMouseDownThisGesture) {
                        engine?.sendMouseDown(fx, fy, 1)
                        sentMouseDownThisGesture = true
                    }
                    engine?.sendMouseMove(fx, fy)
                }
                lastMoveX = event.x
                lastMoveY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!multiTouchGesture) {
                    if (sentMouseDownThisGesture) {
                        engine?.sendMouseUp(fx, fy, 1)
                    } else if (!isDragging && (event.eventTime - event.downTime) < 300) {
                        engine?.sendMouseClick(fx, fy, 1)
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (sentMouseDownThisGesture) engine?.sendMouseUp(fx, fy, 1)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // 外接鼠标滚轮/悬停移动事件映射
        if ((event.source and InputDevice.SOURCE_CLASS_POINTER) != 0) {
            when (event.actionMasked) {
                MotionEvent.ACTION_SCROLL -> {
                    val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                    if (vScroll != 0f) {
                        val (fx, fy) = viewToFb(event.x.toInt(), event.y.toInt())
                        engine?.sendMouseWheel(fx, fy, if (vScroll > 0) 1 else -1)
                        return true
                    }
                }
                MotionEvent.ACTION_HOVER_MOVE -> {
                    val (fx, fy) = viewToFb(event.x.toInt(), event.y.toInt())
                    engine?.sendMouseMove(fx, fy)
                    return true
                }
            }
        }
        return super.onGenericMotionEvent(event)
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
        fun sendUnicodeKey(keyCode: Int, down: Boolean): Boolean
        val lastError: String?
    }
}
