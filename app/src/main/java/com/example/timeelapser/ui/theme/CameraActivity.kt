package com.example.timeelapser.ui.theme

// In your MainActivity.kt or your camera-related class

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.timeelapser.R

class CameraActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var camera: Camera
    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder

    private val cameraPermissionCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        surfaceView = findViewById(R.id.surfaceView)
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)

        if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), cameraPermissionCode)
        } else {
            startCameraPreview()
        }
    }

    private fun startCameraPreview() {
        try {
            camera = Camera.open()
            setCameraDisplayOrientation()
            setCameraZoom()  // Add this line
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