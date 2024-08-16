package com.example.hellotesa

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View.OnTouchListener
import androidx.appcompat.widget.AppCompatImageView

/**
 * CustomImageView is an extension of AppCompatImageView that adds custom touch handling
 * and interaction capabilities. It allows setting a bitmap image and handling touch events
 * on the image, triggering a listener for specific coordinates where the user touches the image.
 */
class CustomImageView : AppCompatImageView {
    private var bitmap: Bitmap? = null
    private var onImageTouchListener: OnImageTouchListener? = null

    /**
     * Constructor for initializing CustomImageView with context.
     *
     * @param context The context in which the view is running.
     */
    constructor(context: Context?) : super(context!!) {
        init()
    }

    /**
     * Constructor for initializing CustomImageView with context and attribute set.
     *
     * @param context The context in which the view is running.
     * @param attrs   The attribute set from which to retrieve view attributes.
     */
    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    ) {
        init()
    }

    /**
     * Constructor for initializing CustomImageView with context, attribute set, and default style.
     *
     * @param context      The context in which the view is running.
     * @param attrs        The attribute set from which to retrieve view attributes.
     * @param defStyleAttr The default style to apply to this view.
     */
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!, attrs, defStyleAttr
    ) {
        init()
    }

    /**
     * Initializes the CustomImageView, setting up the touch listener to detect touch events on the image.
     * If a touch event occurs, it triggers the OnImageTouchListener callback with the coordinates of the touch.
     */
    private fun init() {
        setOnTouchListener(OnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (onImageTouchListener != null) {
                    val x = event.x.toInt()
                    val y = event.y.toInt()
                    onImageTouchListener!!.onImageTouch(x, y)
                }
                performClick()
                return@OnTouchListener true
            }
            false
        })
    }

    /**
     * Initializes the CustomImageView, setting up the touch listener to detect touch events on the image.
     * If a touch event occurs, it triggers the OnImageTouchListener callback with the coordinates of the touch.
     */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /**
     * Initializes the CustomImageView, setting up the touch listener to detect touch events on the image.
     * If a touch event occurs, it triggers the OnImageTouchListener callback with the coordinates of the touch.
     */
    fun setBitmap(bitmap: Bitmap?) {
        this.bitmap = bitmap
        setImageBitmap(bitmap)
    }

    /**
     * Registers a listener that will be notified when the user touches the image.
     *
     * @param listener The listener that will be triggered on touch events.
     */
    fun setOnImageTouchListener(listener: OnImageTouchListener?) {
        onImageTouchListener = listener
    }

    /**
     * Interface definition for a callback to be invoked when the image is touched.
     */
    interface OnImageTouchListener {
        fun onImageTouch(x: Int, y: Int)
    }
}

