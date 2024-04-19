package com.example.minipaint

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import kotlin.math.abs
import java.io.File
import java.io.FileOutputStream

private const val STROKE_WIDTH = 12f

class MyCanvasView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private lateinit var extraCanvas: Canvas
    private lateinit var extraBitmap: Bitmap
    private var backgroundColor = ResourcesCompat.getColor(resources, R.color.colorBackground, null)
    private val drawColor = ResourcesCompat.getColor(resources, R.color.colorPaint, null)
    private var path = Path()
    private var motionTouchEventX = 0f
    private var motionTouchEventY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private val touchTolerance = ViewConfiguration.get(context).scaledTouchSlop

    private val paint = Paint().apply {
        color = drawColor
        isAntiAlias = true //smooth out the edges
        isDither = true //for down-sampling
        style = Paint.Style.STROKE //default: FILL
        strokeJoin = Paint.Join.ROUND //default: MITER
        strokeCap = Paint.Cap.ROUND //default: BUTT
        strokeWidth = STROKE_WIDTH //default: hairline width
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (::extraBitmap.isInitialized) extraBitmap.recycle()
        extraBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        extraCanvas = Canvas(extraBitmap)
        extraCanvas.drawColor(backgroundColor)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(extraBitmap, 0f, 0f, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        motionTouchEventX = event.x
        motionTouchEventY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> touchStart()
            MotionEvent.ACTION_MOVE -> touchMove()
            MotionEvent.ACTION_UP -> touchUp()
        }
        return true
    }

    private fun touchStart() {
        path.reset()
        path.moveTo(motionTouchEventX, motionTouchEventY)
        currentX = motionTouchEventX
        currentY = motionTouchEventY
    }

    private fun touchMove() {
        val dx = abs(motionTouchEventX - currentX)
        val dy = abs(motionTouchEventY - currentY)
        if (dx >= touchTolerance || dy >= touchTolerance) {
            path.quadTo(currentX, currentY, (motionTouchEventX + currentX) / 2,
                (motionTouchEventY + currentY) / 2)
            currentX = motionTouchEventX
            currentY = motionTouchEventY
            extraCanvas.drawPath(path, paint)
        }
        invalidate()
    }

    private fun touchUp() {
        path.reset()
    }
    fun changeBackgroundColor(color: Int) {
        backgroundColor = color
        extraCanvas.drawColor(color)
        invalidate()
    }

    fun changeDrawColor(color: Int) {
        paint.color = color
    }
    fun clearCanvas() {
        path.reset()
        if (::extraBitmap.isInitialized) {
            extraBitmap.recycle()  // Recycle the bitmap to free up memory
        }
        extraBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        extraCanvas = Canvas(extraBitmap)
        extraCanvas.drawColor(backgroundColor)
        invalidate()
    }
    fun setImageBackground(imageUri: Uri, contentResolver: ContentResolver) {
        try {
            contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true  // Load image bounds only
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                // Calculate inSampleSize
                options.inSampleSize = calculateInSampleSize(options, width, height)

                // Decode bitmap with inSampleSize set
                options.inJustDecodeBounds = false
                val scaledBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(imageUri), null, options)
                scaledBitmap?.let { bitmap ->
                    // Create a mutable bitmap from the scaled bitmap
                    extraBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    extraCanvas = Canvas(extraBitmap)

                    // Calculate the positioning of the bitmap to center it on the canvas
                    val left = (width - bitmap.width) / 2f
                    val top = (height - bitmap.height) / 2f

                    // Draw the bitmap centered on the canvas
                    extraCanvas.drawBitmap(bitmap, left, top, null)
                    bitmap.recycle()  // Recycle the original scaledBitmap as it's no longer needed

                    invalidate()
                } ?: run {
                    Toast.makeText(context, "Failed to decode image.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to load image: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun saveImageToGallery() {
        val bitmap = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        this.draw(canvas)

        try {
            val mediaStorageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val imageFile = File(mediaStorageDir, "drawn_image_${System.currentTimeMillis()}.png")
            val fos = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.close()

            // Notify gallery about the new file
            MediaScannerConnection.scanFile(context, arrayOf(imageFile.toString()), null) { _, uri ->
                Toast.makeText(context, "File saved: $uri", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error saving image: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
    fun saveImageToGalleryNewAPI() {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "drawn_image_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            context.contentResolver.openOutputStream(it).use { outputStream ->
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                draw(canvas)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                bitmap.recycle()
                Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_LONG).show()
            }
        } ?: run {
            Toast.makeText(context, "Error saving image", Toast.LENGTH_LONG).show()
        }
    }

}