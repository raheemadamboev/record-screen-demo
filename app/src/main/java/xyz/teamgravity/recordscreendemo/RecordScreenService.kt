package xyz.teamgravity.recordscreendemo

import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.provider.MediaStore
import androidx.core.content.contentValuesOf
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File
import java.io.FileInputStream

class RecordScreenService : LifecycleService() {

    companion object {
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        const val EXTRA_CONFIG = "RecordScreenService_extraConfig"

        private const val VIDEO_FRAME_RATE = 30
        private const val VIDEO_BIT_RATE_KILOBITS = 512
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private val mediaRecorder: MediaRecorder by lazy {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(applicationContext) else MediaRecorder()
    }
    private val cachedFile: File by lazy { File(cacheDir, "cachedFile.mp4") }
    private val mediaProjectionManager: MediaProjectionManager? by lazy { getSystemService() }
    private val notification: RecordScreenNotification by lazy { RecordScreenNotification(application) }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (Action.fromName(intent?.action)) {
            Action.StartRecording -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(notification.id(), notification.notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(notification.id(), notification.notification())
                }

                _running.value = true
                startRecording(intent)
            }

            Action.StopRecording -> stopRecording()
            null -> Unit
        }

        return START_STICKY
    }

    private fun getWindowsSize(): Pair<Int, Int> {
        val calculator = WindowMetricsCalculator.getOrCreate()
        val metrics = calculator.computeMaximumWindowMetrics(applicationContext)
        return metrics.bounds.width() to metrics.bounds.height()
    }

    private fun getScaledDimensions(
        maxWidth: Int,
        maxHeight: Int,
        scaleFactor: Float = 0.8F
    ): Pair<Int, Int> {
        val aspectRatio = maxWidth / maxHeight.toFloat()

        var newWidth = (maxWidth * scaleFactor).toInt()
        var newHeight = (newWidth / aspectRatio).toInt()

        val scaledHeight = (maxHeight * scaleFactor).toInt()
        if (newHeight > scaledHeight) {
            newWidth = (scaledHeight * scaleFactor).toInt()
            newHeight = scaledHeight
        }

        return newWidth to newHeight
    }

    private fun initializeRecorder() {
        val (width, height) = getWindowsSize()
        val (scaledWidth, scaledHeight) = getScaledDimensions(
            maxWidth = width,
            maxHeight = height
        )

        with(mediaRecorder) {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(cachedFile)
            setVideoSize(scaledWidth, scaledHeight)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(VIDEO_BIT_RATE_KILOBITS * 1_000)
            setVideoFrameRate(VIDEO_FRAME_RATE)
            prepare()
        }
    }

    private fun createVirtualDisplay(): VirtualDisplay? {
        val (width, height) = getWindowsSize()
        return mediaProjection?.createVirtualDisplay(
            "Screen",
            width,
            height,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder.surface,
            null,
            null
        )
    }

    private fun startRecording(intent: Intent?) {
        val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_CONFIG, Config::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_CONFIG)
        } ?: return

        mediaProjection = mediaProjectionManager?.getMediaProjection(config.resultCode, config.data)
        mediaProjection?.registerCallback(mediaProjectionCallback, null)

        initializeRecorder()
        mediaRecorder.start()

        virtualDisplay = createVirtualDisplay()
    }

    private fun stopRecording() {
        mediaRecorder.stop()
        mediaProjection?.stop()
        mediaRecorder.reset()
    }

    private fun releaseResources() {
        mediaRecorder.release()
        virtualDisplay?.release()
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection = null
    }

    private suspend fun stopService() {
        _running.emit(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun saveCachedFileToGallery() {
        withContext(Dispatchers.IO) {
            val values = contentValuesOf(
                MediaStore.Video.Media.DISPLAY_NAME to "video_${System.currentTimeMillis()}.mp4",
                MediaStore.Video.Media.RELATIVE_PATH to "Movies/Recordings2"
            )
            val collection =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

            contentResolver.insert(collection, values)?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(cachedFile).use { input ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // MISC
    ///////////////////////////////////////////////////////////////////////////

    private val mediaProjectionCallback: MediaProjection.Callback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            lifecycleScope.launch {
                releaseResources()
                saveCachedFileToGallery()
                stopService()
            }
        }
    }

    enum class Action {
        StartRecording,
        StopRecording;

        companion object {
            fun fromName(name: String?): Action? {
                if (name == null) return null
                return entries.firstOrNull { it.name == name }
            }
        }
    }

    @Parcelize
    data class Config(
        val resultCode: Int,
        val data: Intent
    ) : Parcelable
}