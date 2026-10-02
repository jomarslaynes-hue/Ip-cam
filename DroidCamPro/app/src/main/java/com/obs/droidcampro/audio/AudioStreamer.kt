package com.obs.droidcampro.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioStreamer {
    private val TAG = "AudioStreamer"
    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    val sampleRate = 48000 // 48kHz broadcast standard
    val audioLevel = MutableStateFlow(0) // 0 - 100 VU
    val isMuted = MutableStateFlow(false)

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording.get()) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = minBufferSize.coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            recordingThread = Thread({
                val buffer = ShortArray(1024)
                while (isRecording.get()) {
                    val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readCount > 0) {
                        // Calculate RMS for VU meter
                        var sum = 0.0
                        for (i in 0 until readCount) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / readCount)
                        val level = ((rms / 32767.0) * 100).toInt().coerceIn(0, 100)

                        if (!isMuted.value) {
                            audioLevel.value = level
                            // Stream PCM or encode to Opus for WebRTC stream
                        } else {
                            audioLevel.value = 0
                        }
                    }
                }
            }, "AudioRecordingThread").apply { start() }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord", e)
        }
    }

    fun toggleMute(): Boolean {
        isMuted.value = !isMuted.value
        return isMuted.value
    }

    fun stop() {
        isRecording.set(false)
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        recordingThread = null
    }
}
