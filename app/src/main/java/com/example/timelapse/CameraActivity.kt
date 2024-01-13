package com.example.timelapse

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
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.example.timeelapser.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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


class CameraActivity : AppCompatActivity(), SurfaceHolder.Callback, BeepListener {

    private lateinit var timeStampvid: String
    private lateinit var camera: Camera

    private lateinit var surfaceHolder: SurfaceHolder

    private lateinit var rgba: Mat
    private lateinit var gray: Mat

    private lateinit var surfaceView: SurfaceView

    private lateinit var LogTextView: TextView

    private lateinit var PictureImageView: ImageView
    private lateinit var IntensityTextView: TextView
    private lateinit var ThresholdPlusButton: Button
    private lateinit var ThresholdMinusButton: Button
    private lateinit var ThresholdEditText: EditText

    private lateinit var StartCaptureButton: Button
    private lateinit var StopCaptureButton: Button
    private lateinit var StatusCaptureTextView: TextView
    private lateinit var VorgangnameTextView: TextView

    private lateinit var GenerateVideoButton: Button
    private lateinit var StatusVideoTextView: TextView

    private lateinit var Debounce1TextTextView: TextView
    private lateinit var Debounce1ValueEditText: EditText
    private lateinit var Debounce2TextTextView: TextView
    private lateinit var Debounce2ValueEditText: EditText


    private lateinit var ffmpeg: FFmpeg // Declare private variable

    private lateinit var beepDetector: BeepDetector

    private var Threshold = 50

    private val cameraPermissionCode = 101

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }


    private var pictureNumber = 1
    private var beepnumber = 0

    private var lastTriggerTime: Long = 0
    private var triggeravailable: Boolean = false
    private var debounceTime1: Long = 100 // Set your desired debounce time 1 in milliseconds
    private var debounceTime2: Long = 3000 // Set your desired debounce time 2 in milliseconds
    private var videoprocongoing: Boolean = false
    private var capturestarted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        System.loadLibrary("opencv_java4")
        Log.d("OPENCV", "OpenCV loaded successful: ${OpenCVLoader.initDebug()}")

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_camera)

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        surfaceView = findViewById(R.id.surfaceView)
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)

        PictureImageView = findViewById(R.id.PictureView)

        LogTextView = findViewById(R.id.LogTextView)
        // Set TextView to be scrollable
        LogTextView.movementMethod = ScrollingMovementMethod.getInstance()


        ThresholdEditText = findViewById(R.id.Threshold)
        ThresholdEditText.setText("${Threshold}")

        IntensityTextView = findViewById(R.id.Intensity)
        ThresholdPlusButton = findViewById(R.id.ThresholdPlus)
        ThresholdPlusButton.setOnClickListener {
            Threshold++
            ThresholdEditText.setText("${Threshold}")
        }
        ThresholdMinusButton = findViewById(R.id.ThresholdMinus)
        ThresholdMinusButton.setOnClickListener {
            Threshold--
            ThresholdEditText.setText("${Threshold}")
        }

        StartCaptureButton = findViewById(R.id.StartCapture)
        StartCaptureButton.setOnClickListener {
            capturestarted = true
            StatusCaptureTextView.setText("Capture on going")
        }
        StopCaptureButton = findViewById(R.id.StopCapture)
        StopCaptureButton.setOnClickListener {
            capturestarted = false
            StatusCaptureTextView.setText("Capture stopped")
        }
        StatusCaptureTextView = findViewById(R.id.StatusCapture)
        VorgangnameTextView = findViewById(R.id.Vorgangname)

        GenerateVideoButton = findViewById(R.id.GenerateVideo)
        GenerateVideoButton.setOnClickListener {
            capturestarted = false
            videoprocongoing = true
            StatusCaptureTextView.setText("Capture stopped")
            StatusVideoTextView.setText("Compiling video")
            compileVideo()
        }

        StatusVideoTextView = findViewById(R.id.StatusVideo)
        StatusVideoTextView.setText("Video generation not running")



        Debounce1ValueEditText = findViewById(R.id.Debounce1Value)
        Debounce1ValueEditText.setText(debounceTime1.toString())
        Debounce1ValueEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence?, start: Int, count: Int, after: Int) {
                // This method is called to notify you that the characters within `charSequence` are about to be replaced with new text
            }

            override fun onTextChanged(charSequence: CharSequence?, start: Int, before: Int, count: Int) {
                // This method is called to notify you that somewhere within `charSequence`, the characters have been replaced by new text
            }
            override fun afterTextChanged(editable: Editable?) {
                val numberAsString: String = editable.toString()
                if (numberAsString.isNotBlank()) {
                    debounceTime1 = numberAsString.toLong()
                }
            }
        })

        Debounce2ValueEditText = findViewById(R.id.Debounce2Value)
        Debounce2ValueEditText.setText(debounceTime2.toString())
        Debounce2ValueEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence?, start: Int, count: Int, after: Int) {
                // This method is called to notify you that the characters within `charSequence` are about to be replaced with new text
            }

            override fun onTextChanged(charSequence: CharSequence?, start: Int, before: Int, count: Int) {
                // This method is called to notify you that somewhere within `charSequence`, the characters have been replaced by new text
            }
            override fun afterTextChanged(editable: Editable?) {
                val numberAsString: String = editable.toString()
                if (numberAsString.isNotBlank()) {
                    debounceTime2 = numberAsString.toLong()
                }
            }
        })

        timeStampvid = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        VorgangnameTextView.setText(timeStampvid)
        pictureNumber = 1

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraPermissionCode)
        } else {
            startCameraPreview()
        }

        // Create an instance of BeepDetector
        beepDetector = BeepDetector(this)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            startBeepDetection()
        }

    }

    private fun startBeepDetection() {
        beepDetector.startListening()
    }

    private fun stopBeepDetection() {
        beepDetector.stopListening()
    }

    // Implement BeepListener interface method
    override fun onBeepDetected() {
        // Handle beep detection
        // This method will be called when a beep is detected
        appendLogMessage("Beep detected number $beepnumber")
        beepnumber++
        // Analyze the grayscale image for brightness or color contrast change
        if (isBeepDetected()) {
            // Trigger capture or perform other actions
            if (capturestarted == true) {
                capturePicture()
            }
        }
    }


    private fun appendLogMessage(message: String) {
        runOnUiThread {
            LogTextView.append("$message\n")

            // Scroll to the bottom to show the latest log messages
            val scrollAmount =
                LogTextView.layout.getLineTop(LogTextView.lineCount) - LogTextView.height
            if (scrollAmount > 0) {
                LogTextView.scrollTo(0, scrollAmount)
            }
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
                add("-vf")
                add("transpose=1")
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
            appendLogMessage("FFmpeg Command: $commandString")

            // Execute FFmpeg command using withContext
            val rc = withContext(Dispatchers.Default) {
                Config.enableStatisticsCallback { statistics ->
                    // You can handle FFmpeg statistics here
                    appendLogMessage("Statistics: $statistics")
                }

                FFmpeg.execute(ffmpegCommand.toTypedArray())
            }

            launch(Dispatchers.Main) {
                if (rc == Config.RETURN_CODE_SUCCESS) {
                    // Video compilation successful
                    println("Video compilation successful")
                    StatusVideoTextView.setText("Video compilation successful")
                    timeStampvid =
                        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
                    VorgangnameTextView.setText(timeStampvid)
                    pictureNumber = 1
                    videoprocongoing = false
                } else {
                    // Handle failure
                    println("Video compilation failed, exit code: $rc")
                    StatusVideoTextView.setText("Video compilation failed, exit code: $rc")
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
                    val originalBitmap = BitmapFactory.decodeByteArray(data, 0, data.size)

                    // Rotate the Bitmap by 90 degrees clockwise
                    val matrix = Matrix()
                    matrix.postRotate(90f)
                    val rotatedBitmap = Bitmap.createBitmap(
                        originalBitmap,
                        0,
                        0,
                        originalBitmap.width,
                        originalBitmap.height,
                        matrix,
                        true
                    )

                    // Update the UI on the main thread
                    runOnUiThread {
                        // Assuming you have an ImageView with the id "imageView" in your layout
                        val imageView: ImageView = findViewById(R.id.PictureView)

                        // Set the rotated Bitmap in the ImageView
                        imageView.setImageBitmap(rotatedBitmap)
                    }
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

    private fun getOutputMediaFile(): File {
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
            Log.d(
                "CameraActivity",
                "Supported Preview Formats: ${supportedPreviewFormats.joinToString()}"
            )

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
            // Cancel any ongoing autofocus
            camera.cancelAutoFocus()

            // Calculate focus area based on touch coordinates
            val focusRect = calculateFocusRect(event.x, event.y)

            // Get current camera parameters
            val parameters = camera.parameters

            // Set focus mode to auto if it's not already
            if (parameters.focusMode == Camera.Parameters.FOCUS_MODE_AUTO) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
            }

            // Check if focus areas are supported
            if (parameters.maxNumFocusAreas > 0) {
                val focusAreas = mutableListOf<Camera.Area>()
                focusAreas.add(Camera.Area(focusRect, 1000))
                parameters.focusAreas = focusAreas
            }

            // Apply parameters and start preview
            camera.cancelAutoFocus()
            camera.parameters = parameters
            camera.startPreview()

            // Start autofocus with callback
            camera.autoFocus { success, camera ->
                if (!camera.parameters.focusMode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    val newParameters = camera.parameters
                    newParameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE

                    // Clear focus areas if they were set
                    if (newParameters.maxNumFocusAreas > 0) {
                        newParameters.focusAreas = null
                    }

                    // Apply new parameters and start preview
                    camera.parameters = newParameters
                    camera.startPreview()
                }
            }
            var eventx = event.x
            var eventy = event.y
            appendLogMessage("Set focus point X:${eventx} Y:${eventy}")
        } catch (e: Exception) {
            appendLogMessage("Error handling touch focus: ${e.message}")
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

    private fun findBestPreviewSize(
        sizes: List<Camera.Size>,
        desiredWidth: Int,
        desiredHeight: Int
    ): Camera.Size? {
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
            Log.d("SIZESTRING", "W: ${sizestrinw} x H:${sizestrinh}")
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
            if (videoprocongoing == false) {
                // Process the frame data with OpenCV
                processDataWithOpenCV(data)
            }
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
            yuvImage.compressToJpeg(
                Rect(
                    0,
                    0,
                    camera.parameters.previewSize.width,
                    camera.parameters.previewSize.height
                ), 50, out
            )
            val imageBytes = out.toByteArray()
            val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            // Analyze the grayscale image for brightness or color contrast change
            if (isBedArrived(image)) {
                // Trigger capture or perform other actions
                if (capturestarted == true) {
                    capturePicture()
                }
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
        val bottomPart = gray.submat(0, numRows, numCols - 10, numCols)
        val averageIntensity = Core.mean(bottomPart).`val`[0]

        IntensityTextView.setText("${averageIntensity}")

        val currentTime = System.currentTimeMillis()


        var isTriggered = false

        if ((averageIntensity < Threshold) && (currentTime - lastTriggerTime >= debounceTime2) && (videoprocongoing == false)) {
            triggeravailable = true
            lastTriggerTime = currentTime
        }

        if ((triggeravailable == true) && (currentTime - lastTriggerTime >= debounceTime1)) {
            triggeravailable = false
            isTriggered = true
        }

        return isTriggered
    }

    private fun isBeepDetected(): Boolean {

        val currentTime = System.currentTimeMillis()

        var isTriggered = false

        if ((currentTime - lastTriggerTime >= debounceTime2) && (videoprocongoing == false)) {
            triggeravailable = true
            lastTriggerTime = currentTime
        }

        if ((triggeravailable == true) && (currentTime - lastTriggerTime >= debounceTime1)) {
            triggeravailable = false
            isTriggered = true
        }

        return isTriggered
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, proceed with your audio processing logic
                    appendLogMessage("Record audio permission granted")
                    startBeepDetection()

                } else {
                    appendLogMessage("Record audio permission denied")
                }
            }
        }
    }


}
