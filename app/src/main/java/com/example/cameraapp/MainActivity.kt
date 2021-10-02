package com.example.cameraapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.*
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.cameraapp.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import android.os.Build
import java.lang.Exception


val TAG = MainActivity::class.simpleName
const val REQUEST_CAMERA_CODE = 201
const val REQUEST_EXTERNAL_STORAGE_CODE = 202

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    lateinit var cameraManager: CameraManager
    var isFlashOn = false
    private val mSurfaceTextureListener by lazy {
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                setUpCamera(p1, p2)
                connectCamera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {

            }
        }
    }


    private var ORIENTATIONS = SparseIntArray()
    private var isRecording = false

    private var videoFolder: File? = null
    private var videoFileName = ""

    private var mediaRecorder: MediaRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ORIENTATIONS.append(Surface.ROTATION_0, 0)
        ORIENTATIONS.append(Surface.ROTATION_90, 90)
        ORIENTATIONS.append(Surface.ROTATION_180, 180)
        ORIENTATIONS.append(Surface.ROTATION_270, 270)

        createVideoFolder()
        mediaRecorder = MediaRecorder()
        binding.videoCapture.setOnClickListener {
            if (isRecording) {
                isRecording = false
                binding.videoCapture.text = "Record Stopped"
                mediaRecorder?.stop()
                mediaRecorder?.reset()
                startPreview()
            } else {
                checkStoragePermission()
            }
        }

        binding.flashButton.setOnClickListener {
            binding.flash.performClick()
        }

        binding.flash.setOnClickListener {
            if (isFlashOn) {
                isFlashOn = false
                turnOffFlashLight()
            } else {
                isFlashOn = true
                turnOnFlashLight()
            }
        }
    }

    lateinit var cameraCaptureSession: CameraCaptureSession

    private fun turnOnFlashLight() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                captureRequestBuilder?.set(
                    CaptureRequest.FLASH_MODE,
                    CameraMetadata.FLASH_MODE_TORCH
                )
                cameraCaptureSession.setRepeatingRequest(
                    captureRequestBuilder?.build()!!,
                    null,
                    null
                )
                binding.flashButton.text = "Turn off flash"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun turnOffFlashLight() {
        captureRequestBuilder?.set(
            CaptureRequest.FLASH_MODE,
            CameraMetadata.FLASH_MODE_OFF
        )
        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder?.build()!!, null, null)
        binding.flashButton.text = "Turn on flash"
    }

    private fun setUpMediaRecorder() {
        mediaRecorder?.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder?.setOutputFile(videoFileName)
        mediaRecorder?.setVideoEncodingBitRate(10_000_000)
        mediaRecorder?.setVideoFrameRate(30)
        mediaRecorder?.setVideoSize(videoSize?.width!!, videoSize?.height!!)
        mediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder?.setOrientationHint(totalRotation)
        mediaRecorder?.prepare()
    }


    private fun closeCamera() {
        if (cameraDevice != null) {
            cameraDevice?.close()
            cameraDevice = null
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                isRecording = true
                binding.videoCapture.text = "Record Started"
                createVideoFileName()
                startRecord()
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, "Storage permission not allowed", Toast.LENGTH_SHORT)
                        .show()
                }
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_EXTERNAL_STORAGE_CODE
                )
            }
        } else {
            isRecording = true
            binding.videoCapture.text = "Record Started"
            createVideoFileName()
            startRecord()
        }
    }

    private fun startRecord() {
        startRecording()
        mediaRecorder?.start()
    }

    private fun createVideoFileName(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val preName = "VIDEO_${timeStamp}_"
        val videoF = File.createTempFile(preName, ".mp4", videoFolder)
        videoFileName = videoF.absolutePath
        return videoF
    }

    private fun createVideoFolder() {
        val file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        videoFolder = File(file, "CameraAppVideos")
        if (videoFolder?.exists() == false) {
            videoFolder?.mkdirs()
        }
    }


    private var captureRequestBuilder: CaptureRequest.Builder? = null

    inner class CompareSizeByArea : Comparator<Size> {
        override fun compare(lhs: Size?, rhs: Size?): Int {
            return java.lang.Long.signum(((lhs?.width!! * lhs.height).toLong()) / ((rhs?.width!! * rhs.height).toLong()))
        }

    }


    private fun sensorToDeviceRotation(
        charecteristics: CameraCharacteristics,
        deviceOrientation: Int
    ): Int {
        val sensorOrientation = charecteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
        val deviceOr = ORIENTATIONS.get(deviceOrientation)
        return if (sensorOrientation != null) {
            (sensorOrientation + deviceOr + 360) % 360
        } else
            0
    }


    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (binding.textureView.isAvailable) {
            setUpCamera(binding.textureView.width, binding.textureView.height)
            connectCamera()
        } else {
            binding.textureView.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    private var cameraDevice: CameraDevice? = null
    private val cameraDeviceStateCallback: CameraDevice.StateCallback by lazy {
        object : CameraDevice.StateCallback() {
            override fun onOpened(camDevice: CameraDevice) {
                cameraDevice = camDevice
                if (isRecording) {
                    createVideoFileName()
                    startRecord()
                } else {
                    startPreview()
                }
            }

            override fun onDisconnected(camDevice: CameraDevice) {
                cameraDevice?.close()
                cameraDevice = null
            }

            override fun onError(camDevice: CameraDevice, p1: Int) {
                if (cameraDevice != null) {
                    cameraDevice?.close()
                    cameraDevice = null
                }
            }

        }
    }

    private var cameraID = ""
    private var previewSize: Size? = null
    private var videoSize: Size? = null

    private var totalRotation = 0


    private fun setUpCamera(width: Int, height: Int) {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        for (cameraIds in cameraManager.cameraIdList) {
            val charecteristics = cameraManager.getCameraCharacteristics(cameraIds)
            if (charecteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                continue
            }

            val map = charecteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            val isFlashSupport = charecteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
            print("support.....$isFlashSupport")
            val deviceOrientation = windowManager.defaultDisplay.rotation
            totalRotation = sensorToDeviceRotation(charecteristics, deviceOrientation)
            val swapRotation = totalRotation == 90 || totalRotation == 270
            var rotatedWidth = width
            var rotatedHeight = height
            if (swapRotation) {
                rotatedWidth = height
                rotatedHeight = width
            }

            previewSize = chooseOptimalSize(
                map?.getOutputSizes(SurfaceTexture::class.java),
                rotatedWidth,
                rotatedHeight
            )

            videoSize = chooseOptimalSize(
                map?.getOutputSizes(MediaRecorder::class.java),
                rotatedWidth,
                rotatedHeight
            )
            cameraID = cameraIds
            return
        }
    }


    private fun connectCamera() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                cameraManager.openCamera(cameraID, cameraDeviceStateCallback, handler)
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    Toast.makeText(this, "This App Requires Camera Access", Toast.LENGTH_SHORT)
                        .show()
                }
                requestPermissions(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    ), REQUEST_CAMERA_CODE
                )
            }
        } else {
            cameraManager.openCamera(cameraID, cameraDeviceStateCallback, handler)
        }
    }

    private fun startRecording() {
        setUpMediaRecorder()
        val surfaceTexture = binding.textureView.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(previewSize?.width!!, previewSize?.height!!)
        val surface = Surface(surfaceTexture)
        val recordSurface = mediaRecorder?.surface
        captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        captureRequestBuilder?.addTarget(surface)
        if (recordSurface != null) {
            captureRequestBuilder?.addTarget(recordSurface)
        }
        cameraDevice?.createCaptureSession(mutableListOf(surface, recordSurface), object :
            CameraCaptureSession.StateCallback() {
            override fun onConfigured(p0: CameraCaptureSession) {
                cameraCaptureSession = p0
                cameraCaptureSession.setRepeatingRequest(
                    captureRequestBuilder?.build()!!,
                    null,
                    null
                )
            }

            override fun onConfigureFailed(p0: CameraCaptureSession) {
                Toast.makeText(this@MainActivity, "Unable to show preview", Toast.LENGTH_SHORT)
                    .show()
            }

        }, null)
    }

    private fun startPreview() {
        val surfaceTexture = binding.textureView.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(previewSize?.width!!, previewSize?.height!!)
        val surface = Surface(surfaceTexture)
        captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder?.addTarget(surface)
        cameraDevice?.createCaptureSession(mutableListOf(surface), object :
            CameraCaptureSession.StateCallback() {
            override fun onConfigured(p0: CameraCaptureSession) {
                cameraCaptureSession = p0
                cameraCaptureSession.setRepeatingRequest(
                    captureRequestBuilder?.build()!!,
                    null,
                    handler
                )
            }

            override fun onConfigureFailed(p0: CameraCaptureSession) {
                Toast.makeText(this@MainActivity, "Unable to show preview", Toast.LENGTH_SHORT)
                    .show()
            }

        }, handler)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_CODE -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "You denied the camera permission", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    connectCamera()
                }
                if (grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "You denied the audio permission", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    connectCamera()
                }
            }
            REQUEST_EXTERNAL_STORAGE_CODE -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "You denied the storage permission", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    isRecording = true
                    binding.videoCapture.text = "Record Started"
                    createVideoFileName()
                    startRecord()
                }
            }
        }
    }

    private fun chooseOptimalSize(
        choices: Array<Size>?,
        rotatedWidth: Int,
        rotatedHeight: Int
    ): Size? {
        val bigEnough = ArrayList<Size>()
        for (i in choices?.indices!!) {
            if (choices[i].height == choices[i].width * rotatedHeight / rotatedWidth &&
                choices[i].width >= rotatedWidth && choices[i].height >= rotatedHeight
            ) {
                bigEnough.add(choices[i])
            }
        }

        return if (bigEnough.size > 0) {
            Collections.min(bigEnough, CompareSizeByArea())
        } else {
            choices[0]
        }

    }

    override fun onPause() {
        print("on pause calledddd....................")
        stopBackGroundThread()
        closeCamera()
        super.onPause()
    }


    private var backgroundThread: HandlerThread? = null
    private var handler: Handler? = null


    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraApp")
        backgroundThread?.start()
        handler = backgroundThread?.looper?.let { Handler(it) }
    }

    private fun stopBackGroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        handler = null
    }


}
