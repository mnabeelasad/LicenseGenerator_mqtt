package com.licensegenerator

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.jakewharton.rxbinding2.view.RxView
import kotlinx.android.synthetic.main.activity_license_generator.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar.*
import java.util.concurrent.TimeUnit

class LicenseGeneratorActivity : AppCompatActivity() {

    private var mainHandlerThread = Handler(Looper.getMainLooper())

    var toD = ""
    var frD = ""

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = getColor(R.color.Transparent)
        window.navigationBarColor = getColor(R.color.black)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license_generator)


        initListeners()

    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("CheckResult")
    private fun initListeners() {
        RxView.clicks(generate_lic).throttleFirst(2, TimeUnit.SECONDS).subscribe {
            pBar(1)
            if (validate()) {

                val gson = Gson()
                val data = gson.toJson(EncryptionModel(
                    tv_to_date.text.toString(),
                    tv_from_date.text.toString(),
                    et_lic.text.toString(),
                    et_imei.text.toString()
                ))
                val encodedString: String = Base64.getEncoder().encodeToString(data.toByteArray())

                Log.d("HAHAHHA:", " $encodedString")
                generateQRCode(encodedString, 500, 500)

            } else {
                pBar(0)
            }

        }

        RxView.clicks(tv_to_date).throttleFirst(2, TimeUnit.SECONDS).subscribe {
            showDatePicker(this, tv_to_date, "dd-MMM-yyyy") { date ->
                toD = date.toString()
                tv_to_date.text = date.toString()
            }
        }

        RxView.clicks(tv_from_date).throttleFirst(2, TimeUnit.SECONDS).subscribe {
            showDatePicker(this, tv_from_date, "dd-MMM-yyyy") { date ->
                frD = date.toString()
                tv_from_date.text = date.toString()
            }

        }
    }

    private fun validate(): Boolean {

        var licNum = et_lic.text.toString().trim()
        var imeiNum = et_imei.text.toString().trim()
        var toDate = tv_to_date.text.toString().trim()
        var fromDate = tv_from_date.text.toString().trim()

        if (toDate.isEmpty() || toDate.isBlank()) {
            tv_to_date.requestFocus()
            Toast.makeText(this, "Please Select To Date", Toast.LENGTH_SHORT).show()
            return false
        }

        if (fromDate.isBlank() || fromDate.isEmpty()) {
            tv_from_date.requestFocus()
            Toast.makeText(this, "Please Select From Date", Toast.LENGTH_SHORT).show()
            return false

        }

        if (frD <= toD) {
            Toast.makeText(this, "Invalid Date", Toast.LENGTH_SHORT).show()
            return false

        }
        if (licNum.isEmpty() || licNum.isBlank()) {
            et_lic.requestFocus()
            Toast.makeText(this, "Please type License Number", Toast.LENGTH_SHORT).show()
            return false
        }

        if (imeiNum.isBlank() || imeiNum.isEmpty()) {
            et_imei.requestFocus()
            Toast.makeText(this, "Please type IMEI Number", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    fun showDatePicker(
        activity: Activity,
        textView: TextView?,
        format: String = "dd-MM-yyyy",
        completion: (String?) -> Unit
    ) {
        val c = Calendar.getInstance()
        val year = c.get(YEAR)
        val month = c.get(MONTH)
        val day = c.get(DAY_OF_MONTH)

        val dpd =
            activity.let {
                DatePickerDialog(
                    it,
                    { view, year, monthOfYear, dayOfMonth ->
                        run {
                            textView?.text = formatDate(year, monthOfYear, dayOfMonth, format)
                            completion(formatDate(year, monthOfYear, dayOfMonth, format))
                        }
                    },
                    year,
                    month,
                    day
                )
            }
        dpd.show()
    }


    fun formatDate(year: Int, month: Int, day: Int, format: String): String {
        val myCalendar = getInstance()
        myCalendar.set(year, month, day)
        val formatter = SimpleDateFormat(format, Locale.getDefault())
        return formatter.format(myCalendar.time)
    }

    fun generateQRCode(data: String, width: Int, height: Int): Bitmap? {
        val format = BarcodeFormat.QR_CODE
        val writer = MultiFormatWriter()
        return try {
            val bitMatrix: BitMatrix = writer.encode(data, format, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }

            val imei = et_imei.text.toString()

            saveQRCodeToMediaStore(this, bitmap!!, imei)
            Log.d("QRCODE:", "image: $bitmap")
            bitmap


        } catch (e: WriterException) {
            e.printStackTrace()
            null
        }
    }

    private var progressbar: AlertDialog? = null
    fun pBar(showOrHide: Int) {
        if (progressbar == null) {
            progressbar =
                AlertDialog.Builder(this).setView(R.layout.dialog_loader).setCancelable(false)
                    .create()
            progressbar?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        if (progressbar != null)
            if (showOrHide == 1) {
                progressbar?.show()
            } else if (showOrHide == 0) {
                progressbar?.dismiss()
            }
    }

    fun saveQRCodeToMediaStore(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.TITLE, fileName)
        }
        val uri =
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }

            pBar(0)
            Toast.makeText(this, "Completed", Toast.LENGTH_SHORT).show()
            return uri
        }

        return null
    }


}
