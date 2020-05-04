package com.scwang.wave

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Trace
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import androidx.annotation.ColorRes
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 多重水波纹
 * Created by SCWANG on 2017/12/11.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
open class MultiWaveHeader @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    protected var mPath: Path? = null
    protected var mShape: ShapeType = ShapeType.Rect
    protected var mPaint = Paint()
    protected var mMatrix = Matrix()
    protected var mltWave: MutableList<Wave> = ArrayList()
    protected var mCornerRadius: Float
    protected var mWaveHeight: Int
    protected var mStartColor: Int
    protected var mCloseColor: Int
    protected var mGradientAngle: Int
    var isRunning: Boolean
        protected set
    var isEnableFullScreen: Boolean
    var velocity: Float
    protected var mColorAlpha: Float
    protected var mProgress: Float
    protected var mCurProgress: Float
    protected var mLastTime: Long = 0
    protected var reboundAnimator: ValueAnimator? = null

    init {
        mPaint.isAntiAlias = true
        val ta = context.obtainStyledAttributes(attrs, R.styleable.MultiWaveHeader)
        mWaveHeight = ta.getDimensionPixelOffset(R.styleable.MultiWaveHeader_mwhWaveHeight, Util.dp2px(50f).toInt())
        mStartColor = ta.getColor(R.styleable.MultiWaveHeader_mwhStartColor, -0xfa9330)
        mCloseColor = ta.getColor(R.styleable.MultiWaveHeader_mwhCloseColor, -0xce5002)
        mColorAlpha = ta.getFloat(R.styleable.MultiWaveHeader_mwhColorAlpha, 0.45f)
        velocity = ta.getFloat(R.styleable.MultiWaveHeader_mwhVelocity, 1f)
        mGradientAngle = ta.getInt(R.styleable.MultiWaveHeader_mwhGradientAngle, 45)
        isRunning = ta.getBoolean(R.styleable.MultiWaveHeader_mwhIsRunning, true)
        isEnableFullScreen = ta.getBoolean(R.styleable.MultiWaveHeader_mwhEnableFullScreen, false)
        mCornerRadius = ta.getDimensionPixelOffset(R.styleable.MultiWaveHeader_mwhCornerRadius, Util.dp2px(25f).toInt()).toFloat()
        mShape = ShapeType.values()[ta.getInt(R.styleable.MultiWaveHeader_mwhShape, mShape.ordinal)]
        mCurProgress = ta.getFloat(R.styleable.MultiWaveHeader_mwhProgress, 1f)
        mProgress = mCurProgress
        if (ta.hasValue(R.styleable.MultiWaveHeader_mwhWaves)) {
            tag = ta.getString(R.styleable.MultiWaveHeader_mwhWaves)
        } else if (tag == null) {
            tag = """
                70,25,1.4,1.4,-26
                100,5,1.4,1.2,15
                420,0,1.15,1,-10
                520,10,1.7,1.5,20
                220,0,1,1,-15
                """.trimIndent()
        }
        ta.recycle()
    }

    //    @Override
    //    protected void onFinishInflate() {
    //        super.onFinishInflate();
    //
    //        int count = getChildCount();
    //        if (count > 0) {
    //            for (int i = 0; i < count; i++) {
    //                View child = getChildAt(i);
    //                if (child instanceof Wave) {
    //                    child.setVisibility(GONE);
    //                } else {
    //                    throw new RuntimeException("只能用Wave作为子视图，You can only use Wave as a subview.");
    //                }
    //            }
    //        }
    //    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (mltWave.isEmpty()) {
            updateWavePath()
            updateWavePath(r - l, b - t)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShapePath()
        updateWavePath(w, h)
        updateLinearGradient(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (mltWave.size > 0) {
            mPath?.let {
                canvas.save()
                canvas.clipPath(it)
            }
            val thisView: View = this
            val height = thisView.height
            val thisTime = System.currentTimeMillis()
            for (i in 0 until mltWave.size) {
                mMatrix.reset()
                canvas.save()
                if (isRunning && mLastTime > 0 && mltWave[i].velocity != 0F) {
                    var offsetX = mltWave[i].offsetX - mltWave[i].velocity * velocity * (thisTime - mLastTime) / 1000f
                    if (-mltWave[i].velocity > 0) {
                        offsetX %= (mltWave[i].width / 2).toFloat()
                    } else {
                        while (offsetX < 0) {
                            offsetX += (mltWave[i].width / 2).toFloat()
                        }
                    }
                    mltWave[i].offsetX = offsetX
                    mMatrix.setTranslate(offsetX, (1 - mCurProgress) * height) //mltWave[i].offsetX =
                    canvas.translate(-offsetX, -mltWave[i].offsetY - (1 - mCurProgress) * height)
                } else {
                    mMatrix.setTranslate(mltWave[i].offsetX, (1 - mCurProgress) * height)
                    canvas.translate(-mltWave[i].offsetX, -mltWave[i].offsetY - (1 - mCurProgress) * height)
                }
                mPaint.shader.setLocalMatrix(mMatrix)
                canvas.drawPath(mltWave[i].path, mPaint)
                canvas.restore()
            }
            mLastTime = thisTime
            mPath?.let {
                canvas.restore()
            }
            if (isRunning) {
                ViewCompat.postInvalidateOnAnimation(this)
            }
        }
    }

    private fun updateLinearGradient(width: Int, height: Int) {
        val startColor = ColorUtils.setAlphaComponent(mStartColor, (mColorAlpha * 255).toInt())
        val closeColor = ColorUtils.setAlphaComponent(mCloseColor, (mColorAlpha * 255).toInt())
        val w = width.toDouble()
        val h = height * mCurProgress.toDouble()
        val r = sqrt(w * w + h * h) / 2
        val y = r * sin(2 * Math.PI * mGradientAngle / 360)
        val x = r * cos(2 * Math.PI * mGradientAngle / 360)
        mPaint.shader = LinearGradient((w / 2 - x).toFloat(), (h / 2 - y).toFloat(), (w / 2 + x).toFloat(), (h / 2 + y).toFloat(), startColor, closeColor, Shader.TileMode.CLAMP)
    }

    protected fun updateShapePath() {
        val thisView: View = this
        val w = thisView.width
        val h = thisView.height
        if (w > 0 && h > 0) {
            when (mShape) {
                ShapeType.RoundRect -> {
                    mPath = Path()
                    mPath?.addRoundRect(RectF(0F, 0F, w.toFloat(), h.toFloat()), mCornerRadius, mCornerRadius, Path.Direction.CW)
                }
                ShapeType.Oval -> {
                    mPath = Path()
                    mPath?.addOval(RectF(0F, 0F, w.toFloat(), h.toFloat()), Path.Direction.CW)
                }
                else -> mPath = null
            }
        }
    }

    protected fun updateWavePath() {
        mltWave.clear()
        if (tag is String) {
            var waves = tag.toString().split("\\s+".toRegex())
            if ("-1" == tag) {
                waves = "70,25,1.4,1.4,-26\n100,5,1.4,1.2,15\n420,0,1.15,1,-10\n520,10,1.7,1.5,20\n220,0,1,1,-15".split("\\s+".toRegex())
            } else if ("-2" == tag) {
                waves = "0,0,1,0.5,90\n90,0,1,0.5,90".split("\\s+".toRegex())
            }
            for (wave in waves) {
                val args = wave.split("\\s*,\\s*".toRegex())
                if (args.size == 5) {
                    mltWave.add(Wave(Util.dp2px(args[0].toFloat()), Util.dp2px(args[1].toFloat()), Util.dp2px(args[4].toFloat()), args[2].toFloat(), args[3].toFloat(), mWaveHeight / 2))
                } else {
                    //Silently log
                    Log.e("MultiWaveHeader", "Incorrect Wave String Passed: wave")
                }
            }
        } else {
            val wave = Wave(Util.dp2px(50f), Util.dp2px(0f), Util.dp2px(5f), 1.7f, 2f, mWaveHeight / 2)
            mltWave.add(wave)
        }
    }

    protected fun updateWavePath(w: Int, h: Int) {
        for (wave in mltWave) {
            wave.updateWavePath(w, h, mWaveHeight / 2, isEnableFullScreen, mCurProgress)
        }
    }

    /**
     * 执行回弹动画
     * @param progress 目标值
     * @param interpolator 加速器
     * @param duration 时长
     */
    protected fun animProgress(progress: Float, interpolator: Interpolator?, duration: Int) {
        if (mCurProgress != progress) {
            if (reboundAnimator != null) {
                reboundAnimator!!.cancel()
            }
            reboundAnimator = ValueAnimator.ofFloat(mCurProgress, progress)
            reboundAnimator?.duration = duration.toLong()
            reboundAnimator?.interpolator = interpolator
            reboundAnimator?.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    reboundAnimator = null
                }
            })
            reboundAnimator?.addUpdateListener { animation -> updateProgress(animation.animatedValue as Float) }
            reboundAnimator?.start()
        }
    }

    protected fun updateProgress(progress: Float) {
        val thisView: View = this
        mCurProgress = progress
        updateLinearGradient(thisView.width, thisView.height)
        if (isEnableFullScreen) {
            for (wave in mltWave) {
                wave.updateWavePath(thisView.width, thisView.height, mCurProgress)
            }
        }
        if (!isRunning) {
            invalidate()
        }
    }

    //<editor-fold desc="method api">
    fun setWaves(waves: String?) {
        tag = waves
        if (mLastTime > 0) {
            val thisView: View = this
            updateWavePath()
            updateWavePath(thisView.width, thisView.height)
        }
    }

    var waveHeight: Int
        get() = mWaveHeight
        set(waveHeight) {
            mWaveHeight = Util.dp2px(waveHeight.toFloat()).toInt()
            if (mltWave.isNotEmpty()) {
                val thisView: View = this
                updateWavePath(thisView.width, thisView.height)
            }
        }

    var progress: Float
        get() = mProgress
        set(progress) {
            mProgress = progress
            if (!isRunning) {
                updateProgress(progress)
            } else {
                @Suppress("MemberVisibilityCanBePrivate")
                animProgress(progress, DecelerateInterpolator(), 300)
            }
        }

    fun setProgress(progress: Float, interpolator: Interpolator = DecelerateInterpolator(), duration: Int) {
        mProgress = progress
        animProgress(progress, interpolator, duration)
    }

    var gradientAngle: Int
        get() = mGradientAngle
        set(angle) {
            mGradientAngle = angle
            if (mltWave.isNotEmpty()) {
                val thisView: View = this
                updateLinearGradient(thisView.width, thisView.height)
            }
        }

    var startColor: Int
        get() = mStartColor
        set(color) {
            mStartColor = color
            if (mltWave.isNotEmpty()) {
                val thisView: View = this
                updateLinearGradient(thisView.width, thisView.height)
            }
        }

    fun setStartColorId(@ColorRes colorId: Int) {
        val thisView: View = this
        startColor = Util.getColor(thisView.context, colorId)
    }

    var closeColor: Int
        get() = mCloseColor
        set(color) {
            mCloseColor = color
            if (mltWave.isNotEmpty()) {
                val thisView: View = this
                updateLinearGradient(thisView.width, thisView.height)
            }
        }

    fun setCloseColorId(@ColorRes colorId: Int) {
        val thisView: View = this
        closeColor = Util.getColor(thisView.context, colorId)
    }

    var colorAlpha: Float
        get() = mColorAlpha
        set(alpha) {
            mColorAlpha = alpha
            if (mltWave.isNotEmpty()) {
                val thisView: View = this
                updateLinearGradient(thisView.width, thisView.height)
            }
        }

    fun start() {
        if (!isRunning) {
            isRunning = true
            mLastTime = System.currentTimeMillis()
            invalidate()
        }
    }

    fun stop() {
        isRunning = false
    }

    var shape: ShapeType
        get() = mShape
        set(shape) {
            mShape = shape
            updateShapePath()
        }
    //</editor-fold>
}