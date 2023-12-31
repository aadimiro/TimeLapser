package com.example.timeelapser.ui.theme

// In your MainActivity.kt or your camera-related class

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.timeelapser.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import kotlinx.coroutines.*

class CameraActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var timeStampvid: String
    private lateinit var camera: Camera
    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder

    private lateinit var ffmpeg: com.arthenica.mobileffmpeg.FFmpeg // Declare private variable

    private val cameraPermissionCode = 101

    private var pictureNumber = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                capturePicture()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
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
                add("5")
                add("-i")
                add(imagePattern) // Use explicit file list

                // Add all image files to the input specification
                imageFiles.forEach { add("-i"); add(it) }

                // Add the remaining encoding options
                add("-c:v")
                add("libx264")
                add("-r")
                add("30")
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
                } else {
                    // Handle failure
                    println("Video compilation failed, exit code: $rc")
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
            setCameraDisplayOrientation()
            setCameraZoom()

            val parameters = camera.parameters

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

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Implementation for surfaceChanged
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("CameraActivity", "Surface created")
        startCameraPreview()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("CameraActivity", "Surface destroyed")
        stopCameraPreview()
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
            val desiredZoom = 5  // Adjust this value as needed
            if (desiredZoom >= 0 && desiredZoom <= maxZoom) {
                parameters.zoom = desiredZoom
            }
        }

        camera.parameters = parameters
    }


}