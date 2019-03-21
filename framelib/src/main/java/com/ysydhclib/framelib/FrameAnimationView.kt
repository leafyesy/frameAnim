package com.ysydhclib.framelib

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.Bitmap.Config
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.ImageView
import android.widget.ImageView.ScaleType

import java.io.IOException

/**
 * 帧动画视图，可以播放逐帧动画,
 * 支持动画的暂停，停止，播放，播放延迟，定位播放到指定帧，
 * 及播放到帧监听器，帧监听器可以用来对播放到指定帧做一些其他处理。
 */
class FrameAnimationView : View, OnGestureListener, OnTouchListener {

    //private val TAG = FrameAnimationView::class.java.simpleName

    private val scaleTypeArray by lazy {
        arrayOf(ScaleType.FIT_CENTER, ScaleType.CENTER, ScaleType.FIT_XY)
    }
    private val gesture: GestureDetector by lazy {
        GestureDetector(context, this).apply {
            setIsLongpressEnabled(true)
        }
    }

    private val paint: Paint by lazy {
        Paint().apply {
            isAntiAlias = true
        }
    }

    private val pfd: PaintFlagsDrawFilter by lazy {
        PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    }

    private var bitmapConfig: Config? = null

    private val frameMatrix: Matrix by lazy { Matrix() }

    private var frameWidth: Int = 0
    private var frameHeight: Int = 0

//    internal var imageCache: ImageCache? = null

    private var scaleType = ScaleType.FIT_CENTER

    private var bitmap: Bitmap? = null

    private var frameCount = -1

    private var formatFilePath: String = ""

    private var bitmapIndex = 0

    private var frameThread: Thread? = null

    private var animPlayState: FramePlayState? = null

    private var isRepeat = true

    private var framePlayListener: FramePlayListener? = null

    private var toFrame = -1

    private var preFrame = -1

    private var delayTime = 0

    private var playEnable = true

    private var releaseThread = false

    private var speedHand = 30

    private var preTime: Long = 0

    private var autoPlay = true

    /**
     * 就近定位
     */
    var isAgentSearch = false
        private set
    private var addFrame = true

    private var fingerGestureEnable = true

    private var foreUpdateUI = false

    var currentFrame: Int
        get() = this.bitmapIndex
        set(currentFrame) {
            if (currentFrame < 0 || currentFrame >= frameCount || currentFrame == bitmapIndex) return
            bitmapIndex = currentFrame
            updateUI()
        }

    private val isPlaying: Boolean
        get() = animPlayState != null && (animPlayState == FramePlayState.PLAYING || animPlayState == FramePlayState.LOCATING)

    private val isPlayEnd: Boolean
        get() = !isRepeat && bitmapIndex == frameCount - 1

    private val frameRunnable = Runnable {
        while (true) {
            if (releaseThread) return@Runnable
            if (animPlayState == null) continue
            if (animPlayState == FramePlayState.PAUSE) continue
            if (animPlayState == FramePlayState.LOCATING) {
                if (bitmapIndex == toFrame) {
                    continue
                }
            }
            try {
                if (delayTime != 0) {
                    Thread.sleep(delayTime.toLong())
                    delayTime = 0
                }
                Thread.sleep(frameDuration.toLong())
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            if (addFrame) {
                bitmapIndexAdd()
            } else {
                bitmapIndexDel()
            }
            updateUI()
        }
    }

    enum class FramePlayState {
        PLAYING, PAUSE, LOCATING
    }

    /**
     * 帧动画监听器
     */
    interface FramePlayListener {
        fun framePlay(FrameAnimationView: FrameAnimationView, frameIndex: Int, framePlayState: FramePlayState?)
    }

    constructor(
        context: Context
    ) : super(context, null, 0) {
        init()
    }

    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyle: Int
    ) : super(context, attrs, defStyle) {
        initAttrs(attrs)
        init()
    }

    constructor(
        context: Context,
        attrs: AttributeSet
    ) : super(context, attrs) {
        initAttrs(attrs)
        init()
    }

    /**
     * @param context
     * @param formatFilePath 图片资源所在asset格式化文件路径，如image_%d.jpg
     * @param frameDuration  帧播放间隔时间
     * @param autoPlay       是否开启线程使帧动画可以支持自动播放
     */
    constructor(
        context: Context,
        formatFilePath: String,
        frameDuration: Int,
        autoPlay: Boolean = true
    ) : super(context) {
        this.formatFilePath = formatFilePath
        FrameAnimationView.frameDuration = frameDuration
        this.autoPlay = autoPlay
        init()
    }


    private fun initAttrs(attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.FrameAnimationView).apply {
            formatFilePath = getString(R.styleable.FrameAnimationView_formatFilePath) ?: ""
            scaleType = scaleTypeArray[getInt(R.styleable.FrameAnimationView_scaleType, 0)]
            frameCount = getInt(R.styleable.FrameAnimationView_frameCount, frameCount)
            frameDuration = getInt(R.styleable.FrameAnimationView_frameDuration, frameDuration)
            speedHand = getInt(R.styleable.FrameAnimationView_speedHand, speedHand)
            autoPlay = getBoolean(R.styleable.FrameAnimationView_autoPlay, autoPlay)
            isAgentSearch = getBoolean(R.styleable.FrameAnimationView_agentSearch, false)
        }
        ta.recycle()
    }

    fun setBitmapConfig(config: Config) {
        this.bitmapConfig = config
    }

    fun setScaleType(scaleType: ScaleType) {
        this.scaleType = scaleType
        foreUpdateUI = true
        postInvalidate()
    }

    /**
     * 关闭线程，回收图片资源
     */
    fun recycle() {
        releaseThread = true
        frameThread?.interrupt()
        bitmap?.let {
            if (it.isRecycled) {
                it.recycle()
            }
        }
        frameThread = null
        bitmap = null
        System.gc()
    }

    private fun init() {
        if (frameCount == -1) {
            try {
                val list = context.assets.list(FrameImageUtils.getFolderName(formatFilePath))
                if (list != null) {
                    frameCount = list.size
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        this.isLongClickable = true
        this.setOnTouchListener(this)
        startFrameThread()
        post {
            if (autoPlay) {
                play()
            } else {
                pause()
            }
        }
    }

    fun setAgentSerchEnable(agentSearch: Boolean) {
        this.isAgentSearch = agentSearch
    }

    fun setFramePlayState(animPlayState: FramePlayState) {
        this.animPlayState = animPlayState
    }

    fun setOnFramePlayListener(framePlayListener: FramePlayListener) {
        this.framePlayListener = framePlayListener
    }

    fun setPlayEnable(playEnable: Boolean) {
        this.playEnable = playEnable
    }

    /**
     * 是否可通过手指进行左右播放控制
     */
    fun setFingerGestureEnable(enable: Boolean) {
        this.fingerGestureEnable = enable
    }

    /**
     * 设定每帧播放时间
     */
    fun setFrameDuration(duration: Int) {
        frameDuration = duration
    }

    fun play(delayTime: Int = 0) {
        if (!playEnable || isPlaying) return
        animPlayState = FramePlayState.PLAYING
        this.delayTime = delayTime

        if (isPlayEnd) {
            bitmapIndex = 0
        }
        toFrame = -1
    }

    fun playToFrame(toFrame: Int) {
        if (toFrame < 0 || toFrame > frameCount - 1 || isPlaying) return
        if (bitmapIndex == toFrame) {
            this.animPlayState = FramePlayState.LOCATING
            updateUI()
            return
        }
        addFrame = addFrameFlag(toFrame)
        isRepeat = true
        this.toFrame = toFrame
        this.animPlayState = FramePlayState.LOCATING
    }

    private fun addFrameFlag(toFrame: Int): Boolean {
        val addCount: Int
        val delCount: Int
        if (bitmapIndex > toFrame) {
            addCount = frameCount - 1 - bitmapIndex + toFrame
            delCount = bitmapIndex - toFrame
        } else {
            addCount = toFrame - bitmapIndex
            delCount = bitmapIndex + frameCount - 1 - toFrame
        }
        return addCount <= delCount
    }

    fun playToFrame(toFrame: Int, delayTime: Int) {
        this.delayTime = delayTime
        playToFrame(toFrame)
    }

    fun pause() {
        animPlayState = FramePlayState.PAUSE
    }

    fun stop() {
        animPlayState = FramePlayState.PAUSE
        bitmapIndex = 0
        updateUI()
    }

    /**
     * 开启帧动画线程
     */
    private fun startFrameThread() {
        if (!releaseThread && frameThread != null) return
        releaseThread = false
        frameThread = Thread(frameRunnable).apply { start() }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return gesture.onTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return true
    }

    override fun onDown(e: MotionEvent): Boolean {
        return false
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false
    }

    override fun onScroll(
        e1: MotionEvent, e2: MotionEvent, distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (!fingerGestureEnable || !playEnable || isPlaying) return false

        if (Math.abs(distanceY) > Math.abs(distanceX) * 2) return false

        if (System.currentTimeMillis() - preTime < speedHand) {
            return true
        } else {
            preTime = System.currentTimeMillis()
        }

        if (distanceX < 0) {
            if (!isRepeat && bitmapIndex == frameCount - 1) return false
            bitmapIndex = ++bitmapIndex % frameCount
        } else {
            if (!isRepeat && bitmapIndex == 0) return false
            bitmapIndexDel()
        }
        updateUI()
        return true
    }

    private fun updateUI() {
        postInvalidate()
    }

    override fun onLongPress(e: MotionEvent) {}

    override fun onFling(
        e1: MotionEvent, e2: MotionEvent, velocityX: Float,
        velocityY: Float
    ): Boolean {
        return false
    }

    private fun bitmapIndexAdd() {
        if (isPlayEnd && !isRepeat) {
            animPlayState = FramePlayState.PAUSE
            return
        }
        bitmapIndex = ++bitmapIndex % frameCount
    }

    private fun bitmapIndexDel() {
        if (bitmapIndex == 0 && !isRepeat) {
            return
        }
        bitmapIndex = if (bitmapIndex - 1 < 0) frameCount - 1 else bitmapIndex - 1
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (frameWidth == 0) {
            frameWidth = w
        }
        if (frameHeight == 0) {
            frameHeight = h
        }
        super.onSizeChanged(w, h, oldw, oldh)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        try {
            if (foreUpdateUI || bitmapIndex != preFrame || bitmap == null) {
                foreUpdateUI = false
                bitmap?.recycle()
                bitmap = null
                val imagePath = String.format(formatFilePath, bitmapIndex)
                bitmap = FrameImageUtils.getAssetBitmap(
                    context, imagePath, 1,
                    if (bitmapConfig != null) bitmapConfig else Config.RGB_565
                )
                resetMatrix(bitmap)
                preFrame = bitmapIndex
            }
            bitmap?.let {
                canvas.apply {
                    save()
                    drawFilter = pfd
                    drawBitmap(it, frameMatrix, paint)
                    restore()
                }
                fireFramePlayListener()
            }
        } catch (ooe: OutOfMemoryError) {
            ooe.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun fireFramePlayListener() {
        framePlayListener?.let {
            it.framePlay(this, bitmapIndex, animPlayState)
            if (bitmapIndex == toFrame && animPlayState == FramePlayState.LOCATING) {
                pause()
            }
        }
    }

    private fun resetMatrix(bmp: Bitmap?) {
        bmp?.let {
            when (scaleType) {
                ImageView.ScaleType.FIT_XY ->
                    frameMatrix.setScale(
                        this.width.toFloat() / bmp.width.toFloat(),
                        this.height.toFloat() / bmp.height.toFloat()
                    )
                ImageView.ScaleType.FIT_CENTER ->
                    fixFitCenter(bmp)
                ImageView.ScaleType.CENTER ->
                    if (bmp.height <= height && bmp.width <= width) {
                        frameMatrix.setTranslate(
                            this.width.toFloat() / 2 - bmp.width.toFloat() / 2,
                            this.height.toFloat() / 2 - bmp.height.toFloat() / 2
                        )
                    } else {
                        fixFitCenter(bmp)
                    }
                else ->
                    frameMatrix.setScale(
                        this.width.toFloat() / bmp.width.toFloat(),
                        this.height.toFloat() / bmp.height.toFloat()
                    )
            }
        }
    }

    private fun fixFitCenter(bmp: Bitmap) {
        val bmpRatioWH = bmp.width.toFloat() / bmp.height.toFloat()
        val viewRatioWH = width.toFloat() / height.toFloat()
        val showWidth: Float
        val showHeight: Float
        if (bmpRatioWH >= viewRatioWH) {
            showWidth = width.toFloat()
            showHeight = showWidth / bmpRatioWH
        } else {
            showHeight = height.toFloat()
            showWidth = showHeight * bmpRatioWH
        }
        val scale = showWidth / bmp.width.toFloat()
        frameMatrix.setScale(scale, scale)
        frameMatrix.postTranslate((width - showWidth) / 2.0f, (height - showHeight) / 2.0f)
    }

    companion object {
        private var frameDuration = 20
    }
}

