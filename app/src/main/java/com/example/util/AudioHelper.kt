package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileInputStream

private const val TAG = "AudioHelper"

class AudioHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentRecordingFile: File? = null

    fun startRecording(): File? {
        return try {
            val file = File(context.cacheDir, "voice_memo_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = file

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            recorder?.release()
            recorder = null
            null
        }
    }

    fun stopRecording(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            currentRecordingFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            recorder?.release()
            recorder = null
            null
        }
    }

    fun playAudio(audioPathOrData: String, onComplete: () -> Unit) {
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                if (audioPathOrData.startsWith("data:audio") || audioPathOrData.length > 500 && !audioPathOrData.startsWith("/")) {
                    // Base64 audio payload
                    val cleanBase64 = if (audioPathOrData.contains(",")) audioPathOrData.substringAfter(",") else audioPathOrData
                    val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    val tempFile = File.createTempFile("temp_play", ".m4a", context.cacheDir)
                    tempFile.writeBytes(decodedBytes)
                    setDataSource(tempFile.absolutePath)
                } else {
                    setDataSource(audioPathOrData)
                }
                prepare()
                start()
                setOnCompletionListener {
                    stopPlayback()
                    onComplete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio", e)
            onComplete()
        }
    }

    fun stopPlayback() {
        try {
            player?.apply {
                if (isPlaying) stop()
                release()
            }
            player = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping playback: ${e.message}")
        }
    }

    fun fileToBase64(file: File): String {
        val bytes = ByteArray(file.length().toInt())
        val fis = FileInputStream(file)
        fis.read(bytes)
        fis.close()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
