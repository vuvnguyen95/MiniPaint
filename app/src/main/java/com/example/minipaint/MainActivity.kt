package com.example.minipaint

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_MEDIA_IMAGES_PERMISSION = 101
        private const val IMAGE_PICK_CODE = 102
        private const val REQUEST_WRITE_STORAGE_PERMISSION = 103
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check and request necessary permissions
        checkAndRequestPermissions()

        val myCanvasView = MyCanvasView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val container = findViewById<FrameLayout>(R.id.container)
        container.addView(myCanvasView)
        setupFABs(myCanvasView)
    }

    private fun setupFABs(myCanvasView: MyCanvasView) {
        val mainFab: FloatingActionButton = findViewById(R.id.main_fab)
        val backgroundColorFab: FloatingActionButton = findViewById(R.id.background_color_fab)
        val drawColorFab: FloatingActionButton = findViewById(R.id.draw_color_fab)
        val clearCanvasFab: FloatingActionButton = findViewById(R.id.clear_canvas_fab)
        val importImageFab: FloatingActionButton = findViewById(R.id.import_image_fab)
        val saveImageFab: FloatingActionButton = findViewById(R.id.save_image_fab)

        backgroundColorFab.setOnClickListener {
            showColorPicker(true, myCanvasView)
        }
        drawColorFab.setOnClickListener {
            showColorPicker(false, myCanvasView)
        }
        clearCanvasFab.setOnClickListener {
            myCanvasView.clearCanvas()
        }
        importImageFab.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), REQUEST_MEDIA_IMAGES_PERMISSION)
            } else {
                pickImageFromGallery()
            }
        }
        saveImageFab.setOnClickListener {
            saveCanvasImage(myCanvasView)
        }
        mainFab.setOnClickListener {
            val visibility = if (backgroundColorFab.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            backgroundColorFab.visibility = visibility
            drawColorFab.visibility = visibility
            clearCanvasFab.visibility = visibility
            importImageFab.visibility = visibility
            saveImageFab.visibility = visibility
        }
    }
    private val pickImageResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val myCanvasView = findViewById<FrameLayout>(R.id.container).getChildAt(0) as MyCanvasView
                myCanvasView.setImageBackground(uri, contentResolver)
            }
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageResultLauncher.launch(intent)
    }

    private fun saveCanvasImage(myCanvasView: MyCanvasView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            myCanvasView.saveImageToGalleryNewAPI()
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_STORAGE_PERMISSION)
            } else {
                myCanvasView.saveImageToGallery()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), REQUEST_MEDIA_IMAGES_PERMISSION)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MEDIA_IMAGES_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Read Image Permission granted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Read Image Permission denied.", Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun showColorPicker(isBackground: Boolean, myCanvasView: MyCanvasView) {
        ColorPickerDialog.Builder(this)
            .setTitle("Pick Color")
            .setPositiveButton("Confirm", ColorEnvelopeListener { envelope, _ ->
                if (isBackground) {
                    myCanvasView.changeBackgroundColor(envelope.color)
                } else {
                    myCanvasView.changeDrawColor(envelope.color)
                }
            })
            .setNegativeButton("Cancel") { dialogInterface, _ -> dialogInterface.dismiss() }
            .show()
    }
}
