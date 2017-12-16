package com.ryuta46.nemiotunlocklib

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import com.ryuta46.nemkotlin.util.ConvertUtils


class BluetoothController {
    companion object {
        private val TAG = "NemIoTUnlockTest"
    }

    private var currentScanCallback: ScanCallback? = null
    private var scanner: BluetoothLeScanner? = null

    private var gatt: BluetoothGatt? = null
    private var characteristics: BluetoothGattCharacteristic? = null

    fun prepare(context: Context, deviceAddress: String, serviceUuid: String, characteristicUuid: String, ready:(Boolean) -> Unit ) {
        enableAdapter(context) { adapter ->
            Log.d(TAG, "Start searching target")

            val scanner = adapter.bluetoothLeScanner

            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    super.onScanResult(callbackType, result)
                    val device = result?.device ?: return
                    Log.d(TAG, "Found BT device. name = ${device.name}, address = ${device.address}")

                    if (device.address != deviceAddress) {
                        return
                    }
                    Log.d(TAG, "Found Target device device.")

                    device.connectGatt(context, true, object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                            super.onConnectionStateChange(gatt, status, newState)
                            Log.d(TAG, "Connection State $newState")
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                gatt?.discoverServices()
                            } else {
                                Log.d(TAG, "Not Ready.")
                                ready(false)
                                this@BluetoothController.gatt = null
                                this@BluetoothController.characteristics = null
                            }
                        }

                        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                            super.onServicesDiscovered(gatt, status)
                            val serviceList = gatt?.services ?: return
                            serviceList.find { it.uuid.toString() == serviceUuid }?.let {
                                Log.d(TAG, "Find Service")
                                it.characteristics.find { it.uuid.toString() == characteristicUuid }?.let {
                                    Log.d(TAG, "Find Handle")
                                    this@BluetoothController.gatt = gatt
                                    this@BluetoothController.characteristics = it
                                    Log.d(TAG, "Ready")
                                    ready(true)
                                }
                            }
                        }
                    }).apply {
                        connect()
                    }
                }
            }
            if (currentScanCallback != null) {
                scanner.stopScan(scanCallback)
            }
            currentScanCallback = scanCallback
            this.scanner = scanner
            scanner.startScan(scanCallback)

        }
    }


    fun send(bytes: ByteArray): Boolean {
        val gatt = gatt ?: return false
        val characteristics = characteristics ?: return false

        Log.d(TAG,"Send ${ConvertUtils.toHexString(bytes)}")
        characteristics.value = bytes
        gatt.writeCharacteristic(characteristics)
        return true
    }

    private fun enableAdapter(context: Context, enabledCallback:(adapter: BluetoothAdapter) -> Unit) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter.isEnabled) {
            enabledCallback(adapter)
        }
    }

    fun terminate() {
        scanner?.stopScan(currentScanCallback)
    }
}