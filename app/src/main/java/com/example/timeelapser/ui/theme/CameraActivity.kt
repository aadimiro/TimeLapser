package com.example.timeelapser.ui.theme

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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_camera)
        grayImageView = findViewById(R.id.grayImageView)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
            println("Image files:")
            imageFiles.forEach { println(it) }
            val ffmpegCommand = mutableListOf<String>().apply {
                add("-framerate")
                add("5")
                add("-i")
                add(imagePattern) // Use explicit file list
                imageFiles.forEach { add("-i"); add(it) }
                add("-c:v")
                add("libx264")
                add("-r")
                add("30")
                add("-pix_fmt")
                add("yuv420p")
                add(videoOutputPath)
            }
            val commandString = ffmpegCommand.joinToString(" ")
            println("FFmpeg Command: $commandString")
            val rc = withContext(Dispatchers.Default) {
                Config.enableStatisticsCallback { statistics ->
                    println("Statistics: $statistics")
                }
                FFmpeg.execute(ffmpegCommand.toTypedArray())
            }
            launch(Dispatchers.Main) {
                if (rc == Config.RETURN_CODE_SUCCESS) {
                    println("Video compilation successful")
                    timeStampvid = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
                    videoprocongoing = false
                } else {
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
            savePicture