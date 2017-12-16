package com.ryuta46.nemiotunlocklib

import android.app.Activity
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import com.google.gson.Gson
import com.ryuta46.nemkotlin.util.ConvertUtils
import com.ryuta46.nemkotlin.util.Logger
import java.security.SecureRandom


/**
 * Skeleton of an Android Things activity.
 *
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 *
 * <pre>{@code
 * val service = PeripheralManagerService()
 * val mLedGpio = service.openGpio("BCM6")
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
 * mLedGpio.value = true
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 *
 */
class MainActivity : Activity() {
    companion object {
        private val TAG = "NemIoTUnlockTest"
        private val ADDRESS = "NAEZYI6YPR4YIRN4EAWSP3GEYU6ATIXKTXSVBEU5"
        private val PASS_MOSAIC = "ttech:ryuta"
        private val KEY_LENGTH = 15

        private val DEVICE_ADDRESS = ""
        private val SERVICE_UUID = "cba20d00-224d-11e6-9fb8-0002a5d5c51b"
        private val CHARACTERISTICS_UUID = "cba20002-224d-11e6-9fb8-0002a5d5c51b"
        private val PRESS_COMMAND = "570100"
    }

    private val nemClient = NemClientController(
            mainHosts = listOf(
                    "hachi.nem.ninja"
            ),
            logger = object : Logger {
                override fun log(level: Logger.Level, message: String) {
                    when (level) {
                        Logger.Level.Verbose -> Log.v(TAG, message)
                        Logger.Level.Debug -> Log.d(TAG, message)
                        Logger.Level.Info -> Log.i(TAG, message)
                        Logger.Level.Warn -> Log.w(TAG, message)
                        Logger.Level.Error -> Log.e(TAG, message)
                        else -> { /* Do nothing */
                        }
                    }
                }
            })

    private var prevKey: String = ""
    private var oneTimeKey: String = ""

    private lateinit var bluetoothController: BluetoothController

    private var isReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothController = BluetoothController()
        bluetoothController.prepare(this, DEVICE_ADDRESS, SERVICE_UUID, CHARACTERISTICS_UUID) {
            isReady = it
            //if (it) pass()
        }

        val buttonOpen: Button = findViewById(R.id.buttonOpen)
        //buttonOpen.setOnClickListener {

        //}
        nemClient.subscribeBlocks {
            refreshCode()
        }

    }

    override fun onDestroy() {
        bluetoothController.terminate()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        refreshCode()
        startTransactionWatch()
    }

    private fun refreshCode() {
        runOnUiThread {
            val keyBytes = ByteArray(KEY_LENGTH)
            SecureRandom().nextBytes(keyBytes)
            prevKey = oneTimeKey
            oneTimeKey = ConvertUtils.toHexString(keyBytes)

            val invoiceData = InvoiceContainer(data = InvoiceData("Authorize MOSAIC", ADDRESS, 0, oneTimeKey))

            val qrCodeImage: ImageView = findViewById(R.id.imageQrCode)
            val size = Math.min(qrCodeImage.width, qrCodeImage.height)
            val bitmap = ZXingWrapper.createBitmap(Gson().toJson(invoiceData).toByteArray(), size, size)

            val drawable = BitmapDrawable(resources, bitmap).apply {
                setAntiAlias(false)
            }
            qrCodeImage.setImageDrawable(drawable)
        }

    }

    private fun startTransactionWatch() {
        nemClient.subscribeTransaction(ADDRESS) { sender, message, _ ->
            if ( message == ConvertUtils.toHexString(oneTimeKey.toByteArray()) || message == ConvertUtils.toHexString(prevKey.toByteArray())) {
                nemClient.balance(sender , result = { mosaics ->
                    mosaics.find { it.mosaicId.fullName == PASS_MOSAIC }?.let {
                        pass()
                        refreshCode()
                        refreshCode()
                    }
                })
            }
        }
    }

    private fun pass() {
        if (isReady) {
            Log.i(TAG, "PASS !!")
            bluetoothController.send(ConvertUtils.toByteArray(PRESS_COMMAND))
        } else {
            Log.e(TAG,"Device is not ready!!")
        }
    }
}