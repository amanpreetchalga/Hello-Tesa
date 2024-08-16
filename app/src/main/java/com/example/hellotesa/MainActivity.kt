package com.example.hellotesa

import android.Manifest.permission.CAMERA
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * MainActivity class handles the user interface for selecting images from the gallery,
 * running a TensorFlow Lite model for image segmentation, and applying color changes
 * to detected wall segments in the image.
 */
class MainActivity : AppCompatActivity() {
    private val GALLERY_REQUEST_CODE = 101
    private lateinit var imageView: ImageView
    private lateinit var tflite: Interpreter


    /**
     * Initializes the activity, sets up the UI components, requests necessary permissions,
     * and initializes the TensorFlow Lite interpreter.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     *                           this Bundle contains the data it most recently supplied. Otherwise, it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results.all { it.value }
            if (granted) {
                // Permissions granted, proceed with storage access
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                // Permissions denied, handle case (e.g., explain to user)
                Toast.makeText(this, "Storage permissions denied", Toast.LENGTH_SHORT).show()
            }
        }

        // Permission request logic
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requestPermissions.launch(arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED, CAMERA))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions.launch(arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, CAMERA))
        } else {
            requestPermissions.launch(arrayOf(READ_EXTERNAL_STORAGE, CAMERA))
        }

        // Initialize TensorFlow Lite interpreter
        try {
            tflite = Interpreter(loadModelFile(this))
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Error initializing TensorFlow Lite interpreter.")
        }

        val galleryPopButton = findViewById<Button>(R.id.galleryPopButton)
        galleryPopButton.setOnClickListener {
            goToCameraActivity()
        }

        val floodFillBtn = findViewById<Button>(R.id.btnfloodfill)
        floodFillBtn.setOnClickListener{
            startActivity(Intent(this, FloodFillActivity::class.java))
        }
    }

    /**
     * Opens the gallery app to allow the user to select an image.
     */
    private fun goToCameraActivity() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, 101)
    }

    /**
     * Handles the result from the gallery activity when the user selects an image.
     *
     * @param requestCode The request code originally supplied to startActivityForResult().
     * @param resultCode  The result code returned by the child activity through its setResult().
     * @param data        An Intent, which can return result data to the caller.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK) {
            val selectedImageUri = data?.data
            if (selectedImageUri != null) {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedImageUri)
                imageView = findViewById(R.id.imageView)
                imageView.setImageURI(selectedImageUri)
                if (::tflite.isInitialized) {
                    runClassification(bitmap)
                } else {
                    Toast.makeText(this, "TensorFlow Lite interpreter is not initialized", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Runs the TensorFlow Lite model on the provided bitmap to perform image segmentation.
     *
     * @param bitmap The image to be classified.
     */
    private fun runClassification(bitmap: Bitmap) {
        val inputShape = tflite.getInputTensor(0).shape()
        val height = inputShape[1]
        val width = inputShape[2]

        val inputImageBuffer = convertBitmapToByteBuffer(bitmap, width, height)

        val outputShape = tflite.getOutputTensor(0).shape()
        val outputHeight = outputShape[1]
        val outputWidth = outputShape[2]
        val numClasses = outputShape[3]

        val output = Array(1) { Array(outputHeight) { Array(outputWidth) { FloatArray(numClasses) } } }

        tflite.run(inputImageBuffer, output)
        val segmentationBitmap = processOutput(output)

        val imageView = findViewById<ImageView>(R.id.imageView)
        imageView.setImageBitmap(segmentationBitmap)
        Toast.makeText(this, "Segmentation Completed", Toast.LENGTH_SHORT).show()
    }

    /**
     * Converts the given bitmap to a ByteBuffer that can be used as input to the TensorFlow Lite model.
     *
     * @param bitmap The bitmap to be converted.
     * @param width  The target width of the input image.
     * @param height The target height of the input image.
     * @return A ByteBuffer containing the image data in the correct format.
     */
    private fun convertBitmapToByteBuffer(bitmap: Bitmap, width: Int, height: Int): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val numPixels = width * height
        val bytesPerChannel = 4 // 4 bytes per float
        val numChannels = 3 // RGB channels
        val totalBufferSize = numPixels * numChannels * bytesPerChannel

        val byteBuffer = ByteBuffer.allocateDirect(totalBufferSize).apply {
            order(ByteOrder.nativeOrder())
        }

        val intValues = IntArray(numPixels)
        resizedBitmap.getPixels(intValues, 0, width, 0, 0, width, height)
        for (pixelValue in intValues) {
            // Convert pixel to floats and normalize
            byteBuffer.putFloat(((pixelValue shr 16 and 0xFF) / 255.0f)) // Red
            byteBuffer.putFloat(((pixelValue shr 8 and 0xFF) / 255.0f)) // Green
            byteBuffer.putFloat((pixelValue and 0xFF) / 255.0f) // Blue
        }
        return byteBuffer
    }

    /**
     * Processes the output from the TensorFlow Lite model and converts it into a segmentation bitmap.
     *
     * @param output The output array from the TensorFlow Lite model.
     * @return A bitmap representing the segmented image.
     */
    private fun processOutput(output: Array<Array<Array<FloatArray>>>): Bitmap {
        val outputHeight = output[0].size
        val outputWidth = output[0][0].size

        val segmentationBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)

        for (i in 0 until outputHeight) {
            for (j in 0 until outputWidth) {
                val maxClassIdx = output[0][i][j].indices.maxByOrNull { output[0][i][j][it] } ?: 0
                val color = getColorForClass(maxClassIdx)
                segmentationBitmap.setPixel(j, i, color)
            }
        }
        return segmentationBitmap
    }

    /**
     * Returns a color corresponding to the provided class index.
     *
     * @param classIdx The index of the class.
     * @return The color associated with the class.
     */
    private fun getColorForClass(classIdx: Int): Int {
        // Define colors for your classes
        val colors = arrayOf(
            Color.TRANSPARENT, // class 0: background
            Color.RED,         // class 1
            Color.GREEN,       // class 2
        )
        return colors[classIdx % colors.size]
    }

    /**
     * Displays a toast message with the provided text.
     *
     * @param message The message to be displayed.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Loads the TensorFlow Lite model file from the assets folder.
     *
     * @param context The application context.
     * @return A MappedByteBuffer containing the model data.
     * @throws IOException If there is an issue reading the model file.
     */
    @Throws(IOException::class)
    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("deeplabv3.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Runs the TensorFlow Lite model on the provided bitmap and returns the segmentation output.
     *
     * @param bitmap The image to be processed.
     * @return A multi-dimensional array representing the segmentation output.
     */
    private fun runModel(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 257, 257, false)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 257 * 257 * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        val intValues = IntArray(257 * 257)
        resizedBitmap.getPixels(intValues, 0, 257, 0, 0, 257, 257)
        var pixel = 0
        for (i in 0 until 257) {
            for (j in 0 until 257) {
                val v = intValues[pixel++]
                inputBuffer.putFloat(((v shr 16) and 0xFF) / 255.0f)
                inputBuffer.putFloat(((v shr 8) and 0xFF) / 255.0f)
                inputBuffer.putFloat((v and 0xFF) / 255.0f)
            }
        }
        val output = Array(1) { Array(257) { Array(257) { FloatArray(21) } } }
        tflite.run(inputBuffer, output)
        return output
    }

    /**
     * Replaces the color of the detected wall segments in the bitmap with the selected color.
     *
     * @param bitmap           The original image.
     * @param segmentationMask The segmentation mask indicating wall segments.
     * @param selectedColor    The color to apply to the wall segments.
     * @return A bitmap with the wall segments colored with the selected color.
     */
    private fun replaceWallColor(bitmap: Bitmap, segmentationMask: Array<Array<Array<FloatArray>>>, selectedColor: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (isWallSegment(segmentationMask, x, y)) {
                    outputBitmap.setPixel(x, y, selectedColor)
                }
            }
        }
        return outputBitmap
    }

    /**
     * Checks if the pixel at the specified coordinates corresponds to a wall segment.
     *
     * @param segmentationMask The segmentation mask indicating wall segments.
     * @param x                The x-coordinate of the pixel.
     * @param y                The y-coordinate of the pixel.
     * @return True if the pixel corresponds to a wall segment, false otherwise.
     */
    private fun isWallSegment(segmentationMask: Array<Array<Array<FloatArray>>>, x: Int, y: Int): Boolean {
        val wallClassIndex = 15
        return segmentationMask[0][y][x][wallClassIndex] > 0.5
    }
}
