package com.example.timeelapser.ui.theme

// In your MainActivity.kt or your camera-related class

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.example.timeelapser.R
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class CameraActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var timeStampvid: String
    private lateinit var camera: Camera
    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder
    private lateinit var grayImageView: ImageView

    private lateinit var rgba: Mat
    private lateinit var gray: Mat

    private lateinit var logTextView: TextView

    private lateinit var ffmpeg: com.arthenica.mobileffmpeg.FFmpeg // Declare private variable

    private val cameraPermissionCode = 101

    private var pictureNumber = 0

    private var lastTriggerTime: Long = 0
    private var triggeravailable: Boolean = false
    private val debounceTime1: Long = 1000 // Set your desired debounce time 1 in milliseconds
    private val debounceTime2: Long = 8000 // Set your desired debounce time 2 in milliseconds
    private var videoprocongoing: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        System.loadLibrary("opencv_java4")
        Log.d("OPENCV", "OpenCV loaded successful: ${OpenCVLoader.initDebug()}")

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_camera)

        grayImageView = findViewById(R.id.grayImageView)

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize log TextView
        logTextView = findViewById(R.id.logTextView)

        timeStampvid = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())

        surfaceView = findViewById(R.id.surfaceView)
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraPermissionCode)
        } else {
            startCameraPreview()
        }

    }

    private fun appendLogMessage(message: String) {
        runOnUiThread {
            logTextView.append("$message\n")

            // Scroll to the bottom to show the latest log messages
            val scrollAmount = logTextView.layout.getLineTop(logTextView.lineCount) - logTextView.height
            if (scrollAmount > 0) {
                logTextView.scrollTo(0, scrollAmount)
            }
        }
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                capturePicture()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                videoprocongoing = true
                compileVideo()
                return true
            }
            else -> return super.onKeyDown(keyCode, event)
        }
    }

    private fun compileVideo() {
        GlobalScope.launch(Dispatchers.IO) {
            val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val imagesDir = File(dcimDir, "TimeElaps")

            val videoOutputPath = createOutputVideoPath()

            val imagePattern = "${imagesDir}/timeelaps_${timeStampvid}_%04d.jpg"

            // Get the list of image files
            val imageFiles = mutableListOf<String>()
            var i = 0
            while (true) {
                val imagePath = String.format(imagePattern, i)
                val file = File(imagePath)
                if (!file.exists()) {
                    println("File not found: $imagePath")
                    break
                }
                imageFiles.add(imagePath)
                i++
            }

            // Print the list of image files (for debugging)
            println("Image files:")
            imageFiles.forEach { println(it) }

            val ffmpegCommand = mutableListOf<String>().apply {
                add("-framerate")
                add("15")
                add("-pattern_type")
                add("sequence")
                add("-start_number")
                add("-0001")
                add("-i")
                add(imagePattern)
                add("-c:v")
                add("libx264")
                add("-r")
                add("25")
                add("-pix_fmt")
                add("yuv420p")
                // Specify the output video path
                add(videoOutputPath)
            }

            // Convert the ffmpegCommand array to a string
            val commandString = ffmpegCommand.joinToString(" ")

            // Print the command to the log
            println("FFmpeg Command: $commandString")

            // Execute FFmpeg command using withContext
            val rc = withContext(Dispatchers.Default) {
                Config.enableStatisticsCallback { statistics ->
                    // You can handle FFmpeg statistics here
                    println("Statistics: $statistics")
                }

                FFmpeg.execute(ffmpegCommand.toTypedArray())
            }

            launch(Dispatchers.Main) {
                if (rc == Config.RETURN_CODE_SUCCESS) {
                    // Video compilation successful
                    println("Video compilation successful")
                    timeStampvid = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
                    videoprocongoing = false
                } else {
                    // Handle failure
                    println("Video compilation failed, exit code: $rc")
                    videoprocongoing = false
                }
            }
        }
    }


    private fun createOutputVideoPath(): String {
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val outputVideoDir = File(dcimDir, "TimeElapsVideo")
        if (!outputVideoDir.exists()) {
            outputVideoDir.mkdirs()
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(outputVideoDir, "timeelaps_video_$timeStampvid.mp4").absolutePath
    }
    private fun capturePicture() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                camera.takePicture(null, null) { data, _ ->
                    savePicture(data)
                    // Remove the increment here, it's done in savePicture now
                    camera.startPreview()
                }
            } catch (e: Exception) {
                Log.e("CameraActivity", "Error capturing picture: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun savePictureAsync(data: ByteArray): File? = withContext(Dispatchers.IO) {
        try {
            val pictureFile = getOutputMediaFile()
            pictureFile?.let {
                val fos = FileOutputStream(it)
                fos.write(data)
                fos.close()
                Log.d("CameraActivity", "Picture saved: ${it.absolutePath}")
                pictureNumber++
            }
            pictureFile
        } catch (e: Exception) {
            Log.e("CameraActivity", "Error saving picture: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun savePicture(data: ByteArray) {
        GlobalScope.launch(Dispatchers.Main) {
            savePictureAsync(data)?.let {
                // This block runs on the main thread and can be used for any UI updates after the picture is saved.
            }
        }
    }

    private fun getOutputMediaFile(): File? {
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val mediaStorageDir = File(dcimDir, "TimeElaps")

        if (!mediaStorageDir.exists()) {
            mediaStorageDir.mkdirs()
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        //val fileName = "timeelaps_${timeStamp}.jpg"
        val fileName = "timeelaps_${timeStampvid}_${String.format("%04d", pictureNumber)}.jpg"
        val mediaFile = File(mediaStorageDir, fileName)

        return mediaFile
    }


    private fun startCameraPreview() {
        try {
            camera = Camera.open()
            configureCameraPreviewSize()
            setCameraDisplayOrientation()
            setCameraZoom()

            val parameters = camera.parameters

            // Set preview format to NV21
            parameters.previewFormat = ImageFormat.NV21

            // Check supported preview formats
            val supportedPreviewFormats = parameters.supportedPreviewFormats
            Log.d("CameraActivity", "Supported Preview Formats: ${supportedPreviewFormats.joinToString()}")

            // Check if NV21 is supported
            if (supportedPreviewFormats.contains(ImageFormat.NV21)) {
                Log.d("CameraActivity", "NV21 Preview Format is supported")
            } else {
                Log.e("CameraActivity", "NV21 Preview Format is not supported")
                // Handle the case where NV21 is not supported, or choose another format if needed
            }

            // Check supported focus modes
            val supportedFocusModes = parameters.supportedFocusModes
            Log.d("CameraActivity", "Supported Focus Modes: ${supportedFocusModes.joinToString()}")

            // Enable autofocus if supported
            if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                Log.d("CameraActivity", "Autofocus enabled")
            } else {
                Log.d("CameraActivity", "Autofocus not supported")
            }

            // Set touch focus listener on the SurfaceView
            surfaceView.setOnTouchListener { _, event ->
                handleTouchFocus(event)
                true
            }

            camera.parameters = parameters

            camera.setPreviewDisplay(surfaceHolder)
            camera.startPreview()
        } catch (e: Exception) {
            Log.e("CameraActivity", "Error starting camera preview: ${e.message}")
            e.printStackTrace()
        }
    }



    private fun setCameraDisplayOrientation() {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info)

        val rotation = windowManager.defaultDisplay.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360  // compensate for the mirror effect
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360
        }

        camera.setDisplayOrientation(result)
    }



    private fun stopCameraPreview() {
        try {
            camera.stopPreview()
            camera.release()
        } catch (e: Exception) {
            Log.e("CameraActivity", "Error stopping camera preview: ${e.message}")
            e.printStackTrace()
        }
    }  // Other methods...

    private fun setCameraZoom() {
        val parameters = camera.parameters

        // Set focus mode to autofocus
        if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
        }

        // Adjust the zoom level (0 to max zoom)
        val maxZoom = parameters.maxZoom
        if (parameters.isZoomSupported) {
            val desiredZoom = 0  // Adjust this value as needed
            if (desiredZoom >= 0 && desiredZoom <= maxZoom) {
                parameters.zoom = desiredZoom
            }
        }

        camera.parameters = parameters
    }

    private fun handleTouchFocus(event: MotionEvent) {
        try {
            val rect = calculateFocusRect(event.x, event.y)

            // Check if focus areas are supported
            val parameters = camera.parameters
            if (parameters.maxNumFocusAreas > 0) {
                val focusAreas = mutableListOf<Camera.Area>()
                focusAreas.add(Camera.Area(rect, 1000)) // 1000 is the weight, you can adjust it

                parameters.focusAreas = focusAreas
                parameters.meteringAreas = focusAreas
                camera.parameters = parameters

                // Call auto focus to apply the changes
                camera.autoFocus { _, camera ->
                    // You can handle the result of auto focus if needed
                }
                var eventx = event.x
                var eventy = event.y
                //appendLogMessage("Set focus point X:${eventx} Y:${eventy}")
            }
        } catch (e: Exception) {
            Log.e("CameraActivity", "Error handling touch focus: ${e.message}")
            //appendLogMessage("Error handling touch focus: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun calculateFocusRect(x: Float, y: Float): Rect {
        // Convert touch coordinates to camera coordinates
        val matrix = Matrix()
        surfaceView.matrix.invert(matrix)
        val touchPoint = floatArrayOf(x, y)
        matrix.mapPoints(touchPoint)

        // Calculate focus area rectangle
        val halfFocusAreaSize = 50 // You can adjust this size as needed
        val focusArea = Rect(
            clamp(touchPoint[0].toInt() - halfFocusAreaSize, 0, surfaceView.width - 1),
            clamp(touchPoint[1].toInt() - halfFocusAreaSize, 0, surfaceView.height - 1),
            clamp(touchPoint[0].toInt() + halfFocusAreaSize, 0, surfaceView.width - 1),
            clamp(touchPoint[1].toInt() + halfFocusAreaSize, 0, surfaceView.height - 1)
        )

        return focusArea
    }

    private fun clamp(value: Int, min: Int, max: Int): Int {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }


    private fun configureCameraPreviewSize() {
        val parameters = camera.parameters

        // Get a list of supported preview sizes
        val supportedSizes = parameters.supportedPreviewSizes

        // Choose the desired preview size (you can modify this based on your requirements)
        val desiredWidth = 1080
        val desiredHeight = 1080

        // Find the closest supported size to the desired size
        val bestSize = findBestPreviewSize(supportedSizes, desiredWidth, desiredHeight)

        // Set the chosen preview size
        bestSize?.let {
            parameters.setPreviewSize(it.width, it.height)
            camera.parameters = parameters
        }
    }

    private fun findBestPreviewSize(sizes: List<Camera.Size>, desiredWidth: Int, desiredHeight: Int): Camera.Size? {
        var bestSize: Camera.Size? = null
        var minDiff = Int.MAX_VALUE



        for (size in sizes) {
            val diff = Math.abs(size.width - desiredWidth) + Math.abs(size.height - desiredHeight)

            if (diff < minDiff) {
                bestSize = size
                minDiff = diff
            }
            var sizestrinw = size.width
            var sizestrinh = size.height
            Log.d("SIZESTRING","W: ${sizestrinw} x H:${sizestrinh}")
        }

        return bestSize
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Implementation for surfaceChanged
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("CameraActivity", "Surface created")
        startCameraPreview()

        // Initialize OpenCV matrices
        rgba = Mat()
        gray = Mat()

        // Set the preview callback
        camera.setPreviewCallback(PreviewCallbackImpl())
    }

    // Inner class or object implementing Camera.PreviewCallback
    private inner class PreviewCallbackImpl : Camera.PreviewCallback {
        override fun onPreviewFrame(data: ByteArray, camera: Camera?) {
            // Process the frame data with OpenCV
            processDataWithOpenCV(data)
        }
    }


    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("CameraActivity", "Surface destroyed")
        stopCameraPreview()

        // Release OpenCV matrices
        rgba.release()
        gray.release()
    }

    private fun processDataWithOpenCV(data: ByteArray) {
        try {
            // Convert the frame data to OpenCV Mat
            //val yuvImage = Mat(Size(surfaceView.width.toDouble(), surfaceView.height.toDouble()), CvType.CV_8UC1)
            //yuvImage.put(0, 0, data)
            val yuvImage = YuvImage(
                data,
                camera.parameters.previewFormat,
                camera.parameters.previewSize.width,
                camera.parameters.previewSize.height,
               null
            )

            // convert image to bitmap
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, camera.parameters.previewSize.width, camera.parameters.previewSize.height), 50, out)
            val imageBytes = out.toByteArray()
            val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

             // Analyze the grayscale image for brightness or color contrast change
            if (isBedArrived(image)) {
                // Trigger capture or perform other actions
                capturePicture()
            }
        } catch (e: Exception) {
            Log.e("CameraActivity", "Error processing frame with OpenCV: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun isBedArrived(grayImage: Bitmap): Boolean {
        // Implement your logic to detect the arrival of the bed
        // Example: Check for a significant change in brightness or color contrast
        // You might need to experiment with threshold values based on your specific setup
        // Return true if the bed has arrived, otherwise false



        Utils.bitmapToMat(grayImage, rgba)

        // Split the channels
        val channels = mutableListOf<Mat>()
        Core.split(rgba, channels)

        // Access the first channel
        gray = channels[0]

        // Sample logic: Calculate the average intensity of the bottom part of the image
        val numRows = gray.rows()
        val numCols = gray.cols()
        val bottomPart = gray.submat(0, numRows, numCols-10, numCols)
        val averageIntensity = Core.mean(bottomPart).`val`[0]

        // Print the average intensity in the log
        //Log.d("CameraActivity", "Average Intensity: $averageIntensity")
        appendLogMessage("I: $averageIntensity, R: $numRows, C: $numCols")

        // Display the gray image in grayImageView
        updateGrayImage(gray)

        val currentTime = System.currentTimeMillis()

        // You can experiment with threshold values based on your specific setup
        val YOUR_THRESHOLD = 50
        var isTriggered = false

        if ((averageIntensity < YOUR_THRESHOLD) && (currentTime - lastTriggerTime >= debounceTime2) && (videoprocongoing == false)) {
            triggeravailable = true
            lastTriggerTime = currentTime
        }

        if ((triggeravailable == true) && (currentTime - lastTriggerTime >= debounceTime1)) {
            triggeravailable = false
            isTriggered = true
        }

        return isTriggered
    }

    private fun updateGrayImage(grayImage: Mat) {
        // Convert the OpenCV Mat to a Bitmap
        val grayBitmap = Bitmap.createBitmap(grayImage.cols(), grayImage.rows(), Bitmap.Config.ARGB_8888)

        Core.transpose(grayImage, grayImage)

        // Flip the transposed matrix (change the second parameter to flip horizontally or vertically)
        Core.flip(grayImage, grayImage, 1)
        Utils.matToBitmap(grayImage, grayBitmap)

        // Display the Bitmap in grayImageView
        runOnUiThread {
            grayImageView.setImageBitmap(grayBitmap)
        }
    }

}
