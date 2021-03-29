package com.example.merothemeterrobot

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

class MainActivity() : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService

    lateinit var imageTaker : ImageTaker
    lateinit var imageView: ImageView
    lateinit var viewFinder : PreviewView
    lateinit var ocrText : TextView

    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "BROADCAST_SHOW_LAST_PHOTO" -> showImage( intent.getStringExtra("imgName") )
                "BROADCAST_CAMERA_FREE_AGAIN" -> startCamera()
                "BROADCAST_TEXT_RECOGNIZED" -> showText( intent.getStringExtra("recognizedText") )
            }
        }
    }

    private fun showText(recognizedText: String?) {
        ocrText.text = recognizedText
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView  = findViewById<ImageView>(R.id.imageView)

        viewFinder = findViewById(R.id.viewFinder)

        ocrText = findViewById(R.id.recognizedText)

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

        imageTaker = ImageTaker.getInstance()

        val filter = IntentFilter()
        filter.addAction("BROADCAST_SHOW_LAST_PHOTO")
        filter.addAction("BROADCAST_CAMERA_FREE_AGAIN")
        filter.addAction("BROADCAST_TEXT_RECOGNIZED")
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter )

        // get reference to button
        val testButton = findViewById<Button>(R.id.testButton)
        // set on-click listener
        /*testButton.setOnClickListener {
            // your code to perform when the user clicks on the button
            dispatchTakePictureIntent()
        }*/
        testButton.setOnClickListener { takePhoto() }
        val startButton = findViewById<Button>(R.id.startButton)
        startButton.setOnClickListener(View.OnClickListener {
            ForegroundService.startService(this, "Foreground Service is running...")
        })

        // get reference to button
        val stopButton = findViewById<Button>(R.id.stopButton)
        stopButton.setOnClickListener(View.OnClickListener {
            ForegroundService.stopService(this)
        })

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider /*createSurfaceProvider()*/)
                    }

            imageCapture = ImageCapture.Builder()
                    .build()

            /*val imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                            Log.d(TAG, "Average luminosity: $luma")
                        })
                    }*/

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture /*, imageAnalyzer*/)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        imageTaker.takePhoto(this, imageCapture!!, false)
    }

    private fun showImage(imagePath: String) {
        // show image in image view
        val myBitmap = BitmapFactory.decodeFile(imagePath)
        imageView.setImageBitmap(myBitmap)
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.output_dir)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    val REQUEST_IMAGE_CAPTURE = 1

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        } catch (e: ActivityNotFoundException) {
            // display error state to the user
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as Bitmap

            imageView.setImageBitmap(imageBitmap)
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val TAG = "MeRoMainActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        public var imageCapture: ImageCapture? = null
        public lateinit var outputDirectory: File
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    }
}