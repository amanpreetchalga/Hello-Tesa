package com.example.hellotesa

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.IOException

/**
 * This activity allows users to upload an image, select a color, and apply that color to a specific area
 * of the image based on touch input. The image processing is handled using OpenCV, and the activity
 * supports flood fill operations to change the color of a continuous region while preserving texture.
 */
class FloodFillActivity : AppCompatActivity() {

    private var uploadedImage: Bitmap? = null
    private lateinit var customImageView: CustomImageView
    private var selectedColor: Scalar = Scalar(0.0, 0.0, 255.0) // Default to red
    private var lastFilledPoint: Point? = null

    /**
     * Initializes the activity, sets up the UI components, and configures touch listeners.
     * Loads OpenCV library for image processing.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     *                           this Bundle contains the data it most recently supplied. Otherwise, it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flood_fill)

        customImageView = findViewById(R.id.imageView2)

        val selectImageButton = findViewById<Button>(R.id.btnOpenGallery)
        selectImageButton.setOnClickListener { openGallery() }

        // Define the buttons and their onClick listeners
        val buttons = listOf(
            R.id.button01, R.id.button02, R.id.button03, R.id.button04, R.id.button05, R.id.button06,
            R.id.button1, R.id.button2, R.id.button3, R.id.button4, R.id.button5,
            R.id.button6, R.id.button7, R.id.button8, R.id.button9, R.id.button10,
            R.id.button11, R.id.button12, R.id.button13, R.id.button14, R.id.button15,
            R.id.button16, R.id.button17, R.id.button18, R.id.button19
        )

        for (buttonId in buttons) {
            findViewById<Button>(buttonId).setOnClickListener { updateSelectedColorFromButton(findViewById(buttonId)) }
        }

        customImageView.setOnImageTouchListener(object : CustomImageView.OnImageTouchListener {
            override fun onImageTouch(x: Int, y: Int) {
                uploadedImage?.let {
                    val scaledPoint = mapTouchToImageCoordinates(customImageView, it, x, y)
                    val resultBitmap = processImage(it, scaledPoint, selectedColor)
                    lastFilledPoint = scaledPoint
                    customImageView.setBitmap(resultBitmap)
                }
            }
        })

        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
            println("Error loading OpenCV")
        }
    }

    /**
     * Opens the gallery app to allow the user to select an image for editing.
     * The selected image URI is returned in the onActivityResult callback.
     */
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, 101)
    }

    /**
     * Updates the selected color based on the color of the button that was clicked.
     * The selected color is then applied to any previously filled region.
     *
     * @param button The button whose background color will be used as the selected color.
     */
    private fun updateSelectedColorFromButton(button: Button) {
        val buttonColor = (button.background as ColorDrawable).color
        selectedColor = Scalar(
            (buttonColor shr 16 and 0xFF).toDouble(),
            (buttonColor shr 8 and 0xFF).toDouble(),
            (buttonColor and 0xFF).toDouble()
        )
        recolorFilledPixels()
    }

    /**
     * Recolors the previously filled region with the newly selected color. This method is called
     * whenever the user changes the selected color after a region has already been filled.
     */
    private fun recolorFilledPixels() {
        uploadedImage?.let { bitmap ->
            lastFilledPoint?.let { point ->
                val resultBitmap = processImage(bitmap, point, selectedColor)
                customImageView.setBitmap(resultBitmap)
            }
        }
    }

    /**
     * Handles the result from the image gallery selection. Loads the selected image into a Bitmap
     * and displays it in the CustomImageView.
     *
     * @param requestCode The integer request code originally supplied to startActivityForResult(),
     *                    allowing you to identify who this result came from.
     * @param resultCode  The integer result code returned by the child activity through its setResult().
     * @param data        An Intent, which can return result data to the caller.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK && data != null && data.data != null) {
            val imageUri: Uri? = data.data
            try {
                imageUri?.let {
                    val options = BitmapFactory.Options()
                    options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
                    uploadedImage = BitmapFactory.decodeStream(contentResolver.openInputStream(it), null, options)
                    uploadedImage = uploadedImage?.copy(Bitmap.Config.ARGB_8888, true) // Convert to mutable

                    customImageView.setBitmap(uploadedImage)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Calculates an appropriate inSampleSize value for BitmapFactory.Options to ensure that the
     * image is loaded at a manageable size, reducing memory usage.
     *
     * @param options   The BitmapFactory.Options object with the outWidth and outHeight set.
     * @param reqWidth  The requested width for the image.
     * @param reqHeight The requested height for the image.
     * @return The calculated inSampleSize value to be used in BitmapFactory.Options.
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Maps the touch coordinates from the image view's coordinate system to the actual bitmap's
     * coordinate system, accounting for any scaling or padding applied to the image.
     *
     * @param imageView The CustomImageView displaying the image.
     * @param bitmap    The Bitmap being displayed in the imageView.
     * @param x         The x-coordinate of the touch event in the imageView.
     * @param y         The y-coordinate of the touch event in the imageView.
     * @return A Point object representing the corresponding coordinates on the bitmap.
     */
    private fun mapTouchToImageCoordinates(imageView: CustomImageView, bitmap: Bitmap, x: Int, y: Int): Point {
        val imageViewWidth = imageView.width.toFloat()
        val imageViewHeight = imageView.height.toFloat()
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (imageViewWidth / imageWidth < imageViewHeight / imageHeight) {
            scale = imageViewWidth / imageWidth
            offsetX = 0f
            offsetY = (imageViewHeight - imageHeight * scale) / 2f
        } else {
            scale = imageViewHeight / imageHeight
            offsetX = (imageViewWidth - imageWidth * scale) / 2f
            offsetY = 0f
        }

        val scaledX = ((x - offsetX) / scale).toInt()
        val scaledY = ((y - offsetY) / scale).toInt()

        return Point(scaledX.toDouble(), scaledY.toDouble())
    }

    /**
     * Processes the image to apply the selected color to the region around the specified point.
     * This includes performing a flood fill operation and blending the result with the original image.
     *
     * @param bitmap The original image as a Bitmap.
     * @param point  The Point where the flood fill operation should start.
     * @param color  The color to be applied to the flood-filled region.
     * @return A Bitmap with the flood-filled region recolored.
     */
    private fun processImage(bitmap: Bitmap, point: Point, color: Scalar): Bitmap {
        // Convert Bitmap to Mat
        val src = Mat()
        val bitmap32 = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        org.opencv.android.Utils.bitmapToMat(bitmap32, src)

        // Convert to 3 channels (BGR)
        val bgr = Mat()
        Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR)

        // Create a mask for the flood fill operation
        val mask = Mat(bgr.rows() + 2, bgr.cols() + 2, CvType.CV_8UC1, Scalar(0.0))

        // Increase the tolerance for color variations
        val lowerDiff = Scalar(40.0, 40.0, 40.0)
        val upperDiff = Scalar(40.0, 40.0, 40.0)

        // Perform flood fill on a copy of the original image to keep the edges intact
        val floodFilled = bgr.clone()
        Imgproc.floodFill(floodFilled, mask, point, color, null, lowerDiff, upperDiff, Imgproc.FLOODFILL_FIXED_RANGE)

        // Convert the flood-filled image back to 4 channels (BGRA) to match the original
        Imgproc.cvtColor(floodFilled, floodFilled, Imgproc.COLOR_BGR2BGRA)

        // Blend the flood-filled image with the original image
        Core.addWeighted(floodFilled, 0.5, src, 0.5, 0.0, src)

        // Convert the processed Mat back to Bitmap
        val resultBitmap = Bitmap.createBitmap(src.cols(), src.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(src, resultBitmap)

        return resultBitmap
    }
}
