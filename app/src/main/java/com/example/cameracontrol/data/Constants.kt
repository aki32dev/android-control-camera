package com.example.cameracontrol.data

import java.util.*

object Constants {
    val zoomInOut = floatArrayOf(
        0.25F,
        0.50F,
        0.75F,
        1.00F
    )

    const val FILENAME_FORMAT           : String            = "yyyy-MM-dd-HH-mm-ss-SSS"

    const val bluetoothRequestPermit    : Int               = 101
    const val cameraRequestPermit       : Int               = 102

    const val messageStateChanged       : Int               = 0
    const val messageRead               : Int               = 1
    const val messageWrite              : Int               = 2
    const val messageDeviceName         : Int               = 3
    const val messageToast              : Int               = 4
    const val messageConnect            : Int               = 5
    const val deviceName                : String            = "deviceName"
    const val deviceMac                 : String            = "deviceMac"
    const val toast                     : String            = "toast"

    const val stateNone                 : Int               = 0
    const val stateListen               : Int               = 1
    const val stateConnecting           : Int               = 2
    const val stateConnected            : Int               = 3

    val       appUUID                   : UUID              = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    const val appName                   : String            = "Arduino Bluetooth"
}