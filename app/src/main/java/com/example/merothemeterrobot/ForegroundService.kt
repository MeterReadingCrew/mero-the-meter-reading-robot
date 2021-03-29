package com.example.merothemeterrobot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.util.*
import java.util.concurrent.ExecutorService

class ForegroundService : LifecycleService() {

    private val CHANNEL_ID = "ForegroundService Kotlin"

    companion object {

        fun startService(context: Context, message: String) {
            val startIntent = Intent(context, ForegroundService::class.java)
            startIntent.putExtra("inputExtra", message)
            ContextCompat.startForegroundService(context, startIntent)
        }
        fun stopService(context: Context) {
            val stopIntent = Intent(context, ForegroundService::class.java)
            context.stopService(stopIntent)
            Toast.makeText(context,
                "Capture process stopped",
                Toast.LENGTH_SHORT).show()
        }
        private const val TAG = "MeRoForegroundService"
    }

    private var handler: Handler = Handler()
    private var runnable: Runnable? = null
    private var delay = 10000

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    lateinit var imageTaker : ImageTaker

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        //do heavy work on a background thread
        val input = intent?.getStringExtra("inputExtra")
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
                this,
                0, notificationIntent, 0
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service Kotlin Example")
                .setContentText(input)
                //.setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .build()
        startForeground(1, notification)
        //stopSelf();

        val task = Runnable {
            claimCameraAndTakePicture()
            handler.postDelayed(runnable!!, delay.toLong())
        }
            handler.postDelayed(task.also { runnable = it }, 0)

        return START_NOT_STICKY
    }

    private fun claimCameraAndTakePicture() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            imageCapture = ImageCapture.Builder()
                .build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this@ForegroundService, cameraSelector, imageCapture /*, imageAnalyzer*/)

            } catch(exc: Exception) {
                Log.e(ForegroundService.TAG, "Use case binding failed", exc)
            }

            Toast.makeText(this, "Taking a photo",
                Toast.LENGTH_SHORT).show()
            ImageTaker.getInstance().takePhoto( this@ForegroundService, imageCapture!!, true )

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable!!)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

}