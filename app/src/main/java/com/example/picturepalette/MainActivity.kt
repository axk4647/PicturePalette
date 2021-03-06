package com.example.picturepalette

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.picturepalette.model.ColorImageListViewModel
import com.example.picturepalette.model.ColorPaletteListViewModel
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


const val REQUEST_TAKE_PHOTO = 1
const val REQUEST_PERMISSION = 2
const val RESULT_LOAD_IMAGE = 3
const val KEY_PHOTO_URI = "photoUri"

class MainActivity : AppCompatActivity() {
    private lateinit var colorPaletteRecyclerView: RecyclerView
    private lateinit var colorImageRecyclerView: RecyclerView
    private lateinit var cameraButton: ImageButton
    private lateinit var galleryButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var colorPaletteAdapter: ColorPaletteAdapter
    private lateinit var colorImageAdapter: ColorImageAdapter
    private lateinit var currentPhotoPath: String
    private var photoURI: Uri? = null
    private var photoFile: File? = null

    private val colorImageListViewModel: ColorImageListViewModel by lazy {
        ViewModelProvider(this).get(ColorImageListViewModel::class.java)
    }

    private val colorPaletteListViewModel: ColorPaletteListViewModel by lazy {
        ViewModelProvider(this).get(ColorPaletteListViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setColorPaletteRecycleView()
        setColorImageRecycleView()
        setCameraButton()
        setGalleryButton()
        setRefreshButton()
        requestPermissions()
        restoreImageView(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (photoURI != null) {
            outState.putString(KEY_PHOTO_URI, photoURI.toString())
        }
    }

    private fun setColorPaletteRecycleView() {
        colorPaletteRecyclerView = findViewById(R.id.colorPalette_recycler_view)
        colorPaletteRecyclerView.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false
        )

        colorPaletteAdapter = ColorPaletteAdapter(colorPaletteListViewModel.colorList)
        colorPaletteRecyclerView.adapter = colorPaletteAdapter
    }

    private fun setColorImageRecycleView() {
        colorImageRecyclerView = findViewById(R.id.colorImage_recycler_view)
        colorImageRecyclerView.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false
        )

        colorImageAdapter = ColorImageAdapter(colorImageListViewModel.colorList)
        colorImageRecyclerView.adapter = colorImageAdapter
    }

    private fun setCameraButton() {
        cameraButton = findViewById(R.id.camera_button)
        cameraButton.setOnClickListener {
            dispatchTakePictureIntent()
        }
    }

    private fun setGalleryButton() {
        galleryButton = findViewById(R.id.select_image_button)
        galleryButton.setOnClickListener() {
            val intent = Intent(
                Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )
            startActivityForResult(intent, RESULT_LOAD_IMAGE)
        }
    }

    private fun setRefreshButton() {
        refreshButton = findViewById(R.id.refresh_button)
        refreshButton.setOnClickListener() {
            generatePalette()
            updateUI()
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_PERMISSION
        )
    }

    private fun restoreImageView(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            photoURI = Uri.parse(savedInstanceState.getString(KEY_PHOTO_URI))
            if (photoURI != null) {
                photo_ImageView.setImageURI(photoURI)
            }
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                photoFile = try {
                    createImageFile()
                } catch (ex: IOException) {
                    reportTakePictureError()
                    null
                }

                photoFile?.also {
                    photoURI = FileProvider.getUriForFile(
                        this,
                        "com.example.picturepalette.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO)
                }
            }
        }
    }

    private fun reportTakePictureError() {
        Toast.makeText(this, "PHOTO FILE COULD NOT BE CREATED", Toast.LENGTH_LONG)
            .show()
    }

    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storeDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storeDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            val bitmap =
                photoFile?.path?.let {
                    getScaledBitmap(
                        it,
                        photo_ImageView.width,
                        photo_ImageView.height
                    )
                }
            photo_ImageView.setImageBitmap(bitmap)
            if (bitmap != null) {
                saveImage(bitmap)
                updateColorImageRecycleViewer(bitmap)
            }
        }

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data) {
            this.photoURI = data.data!!
            setPhotoImageViewWithUri(photoURI)
            val bitmap = photo_ImageView.drawable.toBitmap()
            updateColorImageRecycleViewer(bitmap)
        }
    }

    private fun setPhotoImageViewWithUri(uri: Uri?) {
        photo_ImageView.setImageURI(uri)
    }

    private fun updateColorImageRecycleViewer(bitmap: Bitmap) {
        extractColorsFromImage(bitmap)
        generatePalette()
        updateUI()
    }

    private fun extractColorsFromImage(bitmap: Bitmap) {
        val colorBucket = createColorBucket(bitmap)
        for (i in colorBucket.indices) {
            colorImageListViewModel.colorList[i] = colorBucket[i]
        }
    }

    private fun generatePalette() {
        val sample = Palette(
            intArrayOf(
                colorImageListViewModel.colorList[0].toArgb(),
                colorImageListViewModel.colorList[1].toArgb(),
                colorImageListViewModel.colorList[2].toArgb(),
                colorImageListViewModel.colorList[3].toArgb(),
                colorImageListViewModel.colorList[4].toArgb()
            )
        )

        for (i in colorPaletteListViewModel.colorList.indices) {
            colorPaletteListViewModel.colorList[i] = sample.generateColor()
        }
    }

    private fun createColorBucket(bitmap: Bitmap): List<Color> {
        var colorList = mutableListOf<FloatArray>()

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val color = bitmap.getPixel(x, y)
                addHsvColorToList(color, colorList)
            }
        }

        val colorExtractor = ColorExtractor(colorList, 10, 5)
        return colorExtractor.extract()
    }

    private fun addHsvColorToList(color: Int, colorList: MutableList<FloatArray>) {
        var hsvColor = FloatArray(3)
        Color.colorToHSV(color, hsvColor)
        colorList.add(hsvColor)
    }

    private fun saveImage(bitmap: Bitmap) {
        val relativeLocation =
            Environment.DIRECTORY_PICTURES + File.pathSeparator + "PicturePalette"
        val contentValue = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis().toString())
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = this.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValue)

        try {
            uri?.let { uri ->
                val stream = resolver.openOutputStream(uri)
                stream?.let { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)) {
                        throw IOException("failed to saved bitmap")
                    }
                } ?: throw IOException("failed to get output stream")
            } ?: throw IOException("failed to create new MediaStore record")
        } catch (e: IOException) {
            if (uri != null) {
                resolver.delete(uri, null, null)
            }
            throw IOException(e)
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValue.put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
        }
    }

    private fun updateUI() {
        val imageColors = colorImageListViewModel.colorList
        colorImageAdapter = ColorImageAdapter(imageColors)
        colorImageRecyclerView.adapter = colorImageAdapter

        val paletteColors = colorPaletteListViewModel.colorList
        colorPaletteAdapter = ColorPaletteAdapter(paletteColors)
        colorPaletteRecyclerView.adapter = colorPaletteAdapter
    }
}