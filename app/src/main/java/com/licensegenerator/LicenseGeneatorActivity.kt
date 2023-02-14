package com.licensegenerator

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.jakewharton.rxbinding2.view.RxView
import kotlinx.android.synthetic.main.activity_license_geneator.*
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class LicenseGeneatorActivity : AppCompatActivity() {

    private var secretKeys: SecretKey? = null
    private var ivs: ByteArray? = null


    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = getColor(R.color.Transparent)
        window.navigationBarColor = getColor(R.color.black)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license_geneator)

        initListeners()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("CheckResult")
    private fun initListeners() {
        RxView.clicks(generate_lic).throttleFirst(2, TimeUnit.SECONDS).subscribe {

            val data =
                "${lic_text_signup.text.toString()}${date_text_Signup.text.toString()}${Imei_text_Signup.text.toString()}"

            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            secretKeys = keyGenerator.generateKey()
            ivs = ByteArray(16)
            SecureRandom().nextBytes(ivs)
            encryptData(data, secretKeys!!, ivs!!) { encryptedText ->
                generateQRCode(encryptedText,500,500)
               /* decryptData(encryptedText, secretKeys!!, ivs!!) { decryptedText ->
                    Log.d("Main: ", "Decrypted text: $decryptedText")
                }*/
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun decryptData(
        ciphertext: String,
        secretKey: SecretKey,
        iv: ByteArray,
        completion: (String) -> Unit
    ) {
        val ivParameterSpec = IvParameterSpec(iv)
        val secretKeySpec = SecretKeySpec(secretKey.encoded, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        val plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertext))
        val plainText2 = plaintext.toString(Charsets.UTF_8)

        Log.d("DECRYPT: ", "$plaintext")
        Log.d("DECRYPT2: ", plainText2)
        completion(plainText2)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun encryptData(
        plaintext: String,
        secretKey: SecretKey,
        iv: ByteArray,
        completion: (String) -> Unit
    ) {
        val ivParameterSpec = IvParameterSpec(iv)
        val secretKeySpec = SecretKeySpec(secretKey.encoded, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val cipherText2 = Base64.getEncoder().encodeToString(ciphertext)

        Log.d("ENCRYPT: ", "$ciphertext")
        Log.d("ENCRYPT2: ", cipherText2)
        completion(cipherText2)

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

            val date = date_text_Signup.text.toString()

            saveQRCodeToMediaStore(this, bitmap!!, date)
            Log.d("QRCODE:", "image: $bitmap")
            bitmap


        } catch (e: WriterException) {
            e.printStackTrace()
            null
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
            return uri
        }

        return null
    }

}