package com.example.timelapse

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.abs
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.CvType.CV_32FC1
import org.opencv.core.Core

class BeepDetector(private val listener: CameraActivity) {

    private var isListening = false
    private lateinit var audioRecord: AudioRecord
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val beepFrequency = 4450 // Define your desired beep frequency

    // Frequenzen Ta Da Melody: 3525 4450 4700

    fun startListening() {
        if (!isListening) {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord.startRecording()
            isListening = true

            Thread {
                detectBeep()
            }.start()
        }
    }

    fun stopListening() {
        if (isListening) {
            isListening = false
            audioRecord.stop()
            audioRecord.release()
        }
    }

    private fun detectBeep() {
        val buffer = ShortArray(bufferSize)

        while (isListening) {
            audioRecord.read(buffer, 0, bufferSize)

            // Perform frequency analysis on the audio data
            val frequency = calculateFrequency(buffer)

            // Check if the detected frequency matches the beep frequency
            if (isBeep(frequency)) {
                listener.onBeepDetected()
            }
        }
    }

    private fun calculateFrequency(buffer: ShortArray): Int {
        // Convert buffer to Mat for OpenCV processing
        if (buffer.isNotEmpty()) {
            val matBuffer = Mat(1, buffer.size, CV_32FC1)
            matBuffer.put(0, 0, buffer.map { it.toFloat() }.toFloatArray())

            // Log matBuffer for debugging
            Log.d("BeepDetector", "matBuffer: $matBuffer")

            // Apply FFT
            val complexInput = Mat()
            val complexOutput = Mat()
            Core.merge(arrayListOf(matBuffer, Mat.zeros(matBuffer.size(), CV_32FC1)), complexInput)
            Core.dft(complexInput, complexOutput)

            // Calculate magnitude spectrum
            Core.magnitude(
                complexOutput.submat(Rect(0, 0, matBuffer.cols(), matBuffer.rows())),
                complexOutput.submat(Rect(0, 0, matBuffer.cols(), matBuffer.rows())),
                matBuffer
            )

            // Ensure matBuffer is single-channel
            val singleChannelMat = Mat()
            Core.extractChannel(matBuffer, singleChannelMat, 0)

            // Log magnitude spectrum for debugging
            Log.d("BeepDetector", "Magnitude Spectrum: $singleChannelMat")

            // Find peak frequency
            if (!singleChannelMat.empty()) {
                try {
                    val minMaxResult = Core.minMaxLoc(singleChannelMat)
                    val peakIndex = minMaxResult.maxLoc.x.toInt()

                    // Calculate frequency in Hertz
                    val frequency = peakIndex * sampleRate / buffer.size

                    // Log frequency for debugging
                    Log.d("BeepDetector", "Detected Frequency: $frequency Hz")

                    return frequency
                } catch (e: Exception) {
                    // Handle the case where minMaxLoc fails
                    Log.e("BeepDetector", "Error in minMaxLoc: ${e.message}")
                    return -1
                }
            } else {
                // Handle the case where matBuffer is empty
                Log.e("BeepDetector", "matBuffer is empty")
                return -1
            }
        } else {
            // Handle the case where buffer is empty
            Log.e("BeepDetector", "Buffer is empty")
            return -1
        }
    }




    private fun isBeep(detectedFrequency: Int): Boolean {
        // Adjust this threshold based on your specific use case
        val frequencyThreshold = 10
        return abs(beepFrequency - detectedFrequency) < frequencyThreshold
    }
}

interface BeepListener {
    fun onBeepDetected()
}
