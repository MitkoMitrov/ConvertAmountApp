/* Copyright 2021 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================
*/

package org.tensorflow.lite.examples.ocr

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.google.android.material.chip.ChipGroup
import kotlinx.android.synthetic.main.tfe_is_activity_main.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import org.json.JSONObject
import org.tensorflow.lite.examples.camera.CameraActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executors


private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

  private val tfImageName = "tensorflow.jpg"
  private val androidImageName = "android.jpg"
  private val chromeImageName = "chrome.jpg"
  private lateinit var viewModel: MLExecutionViewModel
  private lateinit var resultImageView: ImageView
  private var tfImageView: ImageView? = null
  private lateinit var androidImageView: ImageView
  private lateinit var chromeImageView: ImageView
  private lateinit var chipsGroup: ChipGroup
  private lateinit var runButton: Button
  private lateinit var textPromptTextView: TextView
  private var useGPU = false
  private var selectedImageName = "tensorflow.jpg"
  private var croppedImageUri = ""
  private var ocrModel: OCRModelExecutor? = null
  private val inferenceThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  private val mainScope = MainScope()
  private val mutex = Mutex()

  // Currency

  private lateinit var convertFromDropdownTextView: TextView
  private lateinit var convertToDropdownTextView: TextView;
  private lateinit var conversionRateText: TextView;
  private lateinit var amountToConvert: EditText;
  private var arrayList: ArrayList<String> = arrayListOf();
  private lateinit var convertButton: Button;
  private var country: Array<String> = arrayOf("EUR", "MKD", "TRY", "USD", "RSD", "BGN", "ALL", "DZD", "AOA", "CAD", "KYD", "AFN")
  private lateinit var convertFromValue: String;
  private lateinit var convertToValue: String;
  private lateinit var conversionValue: String;


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.tfe_is_activity_main)


    convertFromDropdownTextView = findViewById(R.id.convert_from_dropdown_menu);
    convertToDropdownTextView = findViewById(R.id.convert_to_dropdown_menu);
    conversionRateText = findViewById(R.id.conversionRateText);
    amountToConvert = findViewById(R.id.amountToConvertValueEditText);
    convertButton = findViewById(R.id.convertButton);
    var conversionRateResultText: TextView = findViewById(R.id.conversionRateResultText);

    for (i in country) {
      arrayList.add(i!!)
    }

    convertFromDropdownTextView.setOnClickListener(object: View.OnClickListener{
      override fun onClick(v: View?) {
        val fromDialog = Dialog(this@MainActivity)
        fromDialog.setContentView(R.layout.from_spinner);
        fromDialog.window!!.setLayout(650, 800);
        fromDialog.show()

        val editText = fromDialog.findViewById<EditText>(R.id.from_edit_text);
        val listView = fromDialog.findViewById<ListView>(R.id.from_list_view);

        val  adapter: ArrayAdapter<String> = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, arrayList)
        listView.setAdapter(adapter)
        editText.addTextChangedListener(object: TextWatcher{
          override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            TODO("Not yet implemented")
          }

          override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            adapter.getFilter().filter(s);
          }

          override fun afterTextChanged(s: Editable?) {
            TODO("Not yet implemented")
          }

        })
        listView.setOnItemClickListener(object: AdapterView.OnItemClickListener{
          override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
              convertFromDropdownTextView.setText(adapter.getItem(position))
              fromDialog.dismiss()
              convertFromValue = adapter.getItem(position)!!
          }

        })
      }
    })
    convertToDropdownTextView.setOnClickListener(object: View.OnClickListener{
      override fun onClick(v: View?) {
        val toDialog = Dialog(this@MainActivity)
        toDialog.setContentView(R.layout.to_spinner)
        toDialog.window!!.setLayout(650, 800)
        toDialog.show()

        var editText: EditText = toDialog.findViewById(R.id.to_edit_text)
        var listView: ListView = toDialog.findViewById(R.id.to_list_view)

        var adapter: ArrayAdapter<String>  = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, arrayList)
        listView.setAdapter(adapter)

        editText.addTextChangedListener(object: TextWatcher{
          override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

          }

          override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            adapter.getFilter().filter(s);
          }

          override fun afterTextChanged(s: Editable?) {

          }

        })
        listView.setOnItemClickListener(object: AdapterView.OnItemClickListener{
          override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            convertToDropdownTextView.setText(adapter.getItem(position))
            toDialog.dismiss()
            convertToValue = adapter.getItem(position)!!
          }
        })
      }
    })

    convertButton.setOnClickListener(object: View.OnClickListener{
      override fun onClick(v: View?) {
        try {
            //var amountConvert: Double = (this@MainActivity.amountToConvert.getText().toString()).toDouble()
            getConversionRate(convertFromValue, convertToValue, amountToConvert)

        }catch(e: Exception){

        }
      }

    })


    //save Image from camera capture
    if(intent.getStringExtra("uri_image") != null){

      val imageUri = Uri.parse(intent.getStringExtra("uri_image"))

      Log.d("debug", imageUri.toString())

      selectedImageName = imageUri.toString()

      //cropping image
      val bitMapToBeCropped: Bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), Uri.parse(selectedImageName))

      var croppedImage = cropImage(bitMapToBeCropped)

      val croppedImageUri = saveImageToExternalStorage(croppedImage).toString()


      viewModel = ViewModelProvider.AndroidViewModelFactory(application).create(MLExecutionViewModel::class.java)
      viewModel.resultingBitmap.observe(
        this,
        Observer { resultImage ->
          if (resultImage != null) {
            Log.d("debug", "true")
            updateUIWithResults(resultImage)
            //imageUri.toFile().delete()
          }
          //enableControls(true)
        }
      )

      mainScope.async(inferenceThread) { createModelExecutor(useGPU) }

      runModel(croppedImageUri)
    }

    val scanNumbers = findViewById<Button>(R.id.scanNumbers)

    scanNumbers.setOnClickListener(){
      val intent = Intent(this, CameraActivity::class.java)
      startActivity(intent)
    }
  }

  private fun getConversionRate(convertFromValue: String, convertToValue: String, amountToConvert: EditText): String {

    var conversionRateResult: String = ""

    val client = OkHttpClient()
    val request: Request =  Request.Builder()
      .url("https://api.apilayer.com/exchangerates_data/convert?to=${convertToValue}&from=${convertFromValue}&amount=${amountToConvert.text}")
      .addHeader("apikey", "x2g62FLxQ7AUaPqxVJC6ZFx3EHNuj8q0")
      .build();

    client.newCall(request).enqueue(object: Callback {
      override fun onFailure(call: Call, e: IOException) {
        Log.d("Failure", e.toString())
        Toast.makeText(this@MainActivity, e.toString(), Toast.LENGTH_SHORT).show()
      }

      override fun onResponse(call: Call, response: Response) {
        response.use {
          if(!response.isSuccessful) throw IOException ("Unexpected code $response")
          //Log.d("response", response.body!!.string())
          val jsonObject: JSONObject = JSONObject(response!!.body!!.string())
          conversionRateResult = jsonObject.getDouble("result").toString()
          Log.d("rate", conversionRateResult)
          conversionRateResultText.text = conversionRateResult
        }
      }
    })
      return conversionRateResult;
    }

  private fun saveImageToExternalStorage(bitmap:Bitmap):Uri{
    // Get the external storage directory path

    //Original
    //val path = Environment.getExternalStorageDirectory().toString()

    //Second
    val path = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

    // Create a file to save the image
    val file = File(path, "cropped_image.jpg")

    try {
      // Get the file output stream
      val stream: OutputStream = FileOutputStream(file)

      // Compress the bitmap
      bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)

      // Flush the output stream
      stream.flush()

      // Close the output stream
      stream.close()
      Toast.makeText(this, "Image saved successful.", Toast.LENGTH_SHORT).show()
    } catch (e: IOException){ // Catch the exception
      Log.d("Error", e.printStackTrace().toString())
      Toast.makeText(this, "Image is not being saved!.", Toast.LENGTH_SHORT).show()      }

    // Return the saved image path to uri
    return Uri.parse(file.absolutePath)
  }

  private fun cropImage(bitMapToBeCropped: Bitmap): Bitmap {
    return Bitmap.createBitmap(bitMapToBeCropped.rotate(90f), 497, 290, 750, 400)
  }

  fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
  }

  private fun runModel(ImageUri: String) {

    mainScope.async(inferenceThread) {
      mutex.withLock {
        if (ocrModel != null) {
          viewModel.onApplyModel(baseContext,ImageUri, ocrModel, inferenceThread)
        } else {
          Log.d(
            TAG,
            "Skipping running OCR since the ocrModel has not been properly initialized ..."
          )
        }
      }
    }
  }

  private suspend fun createModelExecutor(useGPU: Boolean) {
    mutex.withLock {
      if (ocrModel != null) {
        ocrModel!!.close()
        ocrModel = null
      }
      try {
        ocrModel = OCRModelExecutor(this, useGPU)
      } catch (e: Exception) {
        Log.e(TAG, "Fail to create OCRModelExecutor: ${e.message}")
        val logText: TextView = findViewById(R.id.log_view)
        logText.text = e.message
      }
    }
  }

  private fun setImageView(imageView: ImageView, image: Bitmap) {
    Glide.with(baseContext).load(image).override(250, 250).fitCenter().into(imageView)
  }

  //MAIN LOGIC FOR PASSING IMAGE TO THE MODEL AND RECEIVE RESULTS
  private fun updateUIWithResults(modelExecutionResult: ModelExecutionResult) {


    val logText: TextView = findViewById(R.id.resultNumbers)
    var words: String = ""

    for((word, color) in modelExecutionResult.itemsFound){
      words += word + " ";
      Log.d("word, color", word + color.toString())
    }
    Log.d("itemsFound: ", modelExecutionResult.itemsFound.entries.toString())

    //words = "150EUR"
    var resultNumbers = words.replace("[^0-9]".toRegex(), "")
    if(resultNumbers.length != 0){
      logText.text = resultNumbers
    }else{
      logText.text = words
    }
    amountToConvert.setText(resultNumbers)

    Log.d("resultNumber", resultNumbers)
  }

  private fun enableControls(enable: Boolean) {
    runButton.isEnabled = enable
  }

}
