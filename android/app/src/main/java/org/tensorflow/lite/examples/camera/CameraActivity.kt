package org.tensorflow.lite.examples.camera

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.AspectRatio.RATIO_4_3
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.android.synthetic.main.activity_camera.*
import org.tensorflow.lite.examples.camera.Constants.TAG
import org.tensorflow.lite.examples.ocr.MainActivity
import org.tensorflow.lite.examples.ocr.R
import org.tensorflow.lite.examples.ocr.databinding.ActivityCameraBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraActivity: AppCompatActivity() {

    private var uri: Uri? = null
    private lateinit var currentImagePath: String
    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        binding = ActivityCameraBinding.inflate(layoutInflater)

        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if(allPermissionGranted()){
            startCamera()
            Log.d("permission", "Permission has been granted!")
        }else{
            ActivityCompat.requestPermissions(this,
                Constants.REQUIRED_PERMISSION,
                Constants.REQUEST_CODE_PERMISION)
        }

        binding.btnCaptureAmount.setOnClickListener{
            takePhoto()
        }
    }

    private fun createImageFile(): File{
        val imageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "fast_currency_image",
            ".jpg",
            imageDirectory
        ).apply{
            currentImagePath = absolutePath
        }
    }

    private fun takePhoto(){
        val imageCapture = imageCapture ?: return
        val photoFile = createImageFile();

        val outPutOption = ImageCapture
            .OutputFileOptions
            .Builder(photoFile)
            .build()

        imageCapture.takePicture(
            outPutOption, ContextCompat.getMainExecutor(this),
            object :ImageCapture.OnImageSavedCallback{
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                    val saveUri = Uri.fromFile(photoFile)
                    Log.d("image", "image_saved in ${saveUri}")
                    val intent = Intent(this@CameraActivity, MainActivity::class.java)
                    intent.putExtra("uri_image", saveUri.toString())
                    startActivity(intent)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.d(Constants.TAG, "onError: " + exception.message, exception)
                }
            }
        )
    }

    private fun startCamera(){
        val cameraProviderFuture = ProcessCameraProvider
            .getInstance(this)
        cameraProviderFuture.addListener({

            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
            val preview = Preview.Builder()
                .build()
                .also{ mPreview ->

                    mPreview.setSurfaceProvider(
                        binding.viewFinder.surfaceProvider
                    )
                }
            imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size(1280,720))
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,cameraSelector, preview, imageCapture
                )
            }catch (e:Exception){
                Log.d(Constants.TAG,"startCamera fail:", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == Constants.REQUEST_CODE_PERMISION){
            if(allPermissionGranted()){
                //out code
                startCamera()
            }else{
                Toast.makeText(this,
                "Permission denied by the user!",
                Toast.LENGTH_SHORT).show()
                Log.d("permission", "Permission has been denied by the user!")
                finish()
            }
        }
    }

    private fun allPermissionGranted() =
        Constants.REQUIRED_PERMISSION.all{
            ContextCompat.checkSelfPermission(
                baseContext, it
            ) == PackageManager.PERMISSION_GRANTED
        }

    override fun onDestroy(){
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
