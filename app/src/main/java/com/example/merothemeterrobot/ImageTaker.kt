package com.example.merothemeterrobot

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ImageTaker {

    companion object {
        private lateinit var instance : ImageTaker
        private var imageCapture: ImageCapture? = null

        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        const val TAG = "CameraXBasic"

        public fun getInstance(): ImageTaker {
            instance = ImageTaker()
            return instance
        }

        /*public fun setImageCapture( imageCapture: ImageCapture ) {
            ImageTaker.imageCapture = imageCapture
        }*/

    }

    private var selectedBitmap: Bitmap? = null

    public fun takePhoto(context: Context, imageCapture: ImageCapture, freeCamera: Boolean) {

        // Create time-stamped output file to hold the image
        val photoFile = File(
                MainActivity.outputDirectory,
                SimpleDateFormat(ImageTaker.FILENAME_FORMAT, Locale.US
                ).format(System.currentTimeMillis()) + ".jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        //imageCapture.flashMode = ImageCapture.FLASH_MODE_ON
        imageCapture.takePicture(
                outputOptions, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e(ImageTaker.TAG, "Photo capture failed: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                val msg = "Photo capture succeeded: $savedUri"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                Log.d(ImageTaker.TAG, msg)

                // show image in image view
                val intent = Intent(context, MainActivity::class.java)
                        .setAction("BROADCAST_SHOW_LAST_PHOTO")
                        .putExtra("imgName", photoFile.absolutePath)
                LocalBroadcastManager.getInstance(context)
                        .sendBroadcast(intent)

                if (freeCamera) {
                    val intent = Intent(context, MainActivity::class.java)
                        .setAction("BROADCAST_CAMERA_FREE_AGAIN")
                    LocalBroadcastManager.getInstance(context)
                        .sendBroadcast(intent)
                }

                recognizeText(context, photoFile)
            }
        })
    }

    private fun recognizeText(context: Context, file: File) {
        //val selectedImage = BitmapFactory.decodeFile(file.absolutePath)
        val inputImage : InputImage = InputImage.fromFilePath(context, file.toUri())
        val recognizer : TextRecognizer = TextRecognition.getClient()
        recognizer.process(inputImage)
            .addOnSuccessListener {
                // show image in image view
                val intent = Intent(context, MainActivity::class.java)
                    .setAction("BROADCAST_TEXT_RECOGNIZED")
                    .putExtra("recognizedText", it.text)
                LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(intent)
                Log.d(ImageTaker.TAG, "text recognition successful")
            }
            .addOnFailureListener {
                Log.d(ImageTaker.TAG, "text recognition failed")
            }
    }
}