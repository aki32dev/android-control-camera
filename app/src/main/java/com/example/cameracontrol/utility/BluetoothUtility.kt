package com.example.cameracontrol.utility

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Bundle
import android.os.Handler
import com.example.cameracontrol.data.Constants
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@SuppressLint("ServiceCast")
class BluetoothUtility(context : Context, private val handler : Handler){
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    var bluetoothAdapter : BluetoothAdapter

    var statenow                    : Int               = 0
    var isConnect                   : Boolean           = false
    var term                        : Boolean           = false

    private var acceptThread        : AcceptThread?     = null
    private var connectThread       : ConnectThread?    = null
    private var connectedThread     : ConnectedThread?  = null

    /*========================================CONSTRUCTOR========================================*/
    init {
        statenow = Constants.stateNone
        bluetoothAdapter = bluetoothManager.adapter
    }

    /*=========================================FUNCTION==========================================*/
//    fun getState():Int{
//        return statenow
//    }

    private fun setState(state : Int){
        this.statenow = state
        handler.obtainMessage(Constants.messageStateChanged, state, -1).sendToTarget()
    }

    @Synchronized
    private fun start() {
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }
        if (acceptThread == null) {
            acceptThread = AcceptThread()
            acceptThread!!.start()
        }
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }
        setState(Constants.stateListen)
    }

    @Synchronized
    fun stop() {
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }
        if (acceptThread != null) {
            acceptThread!!.cancel()
            acceptThread = null
        }
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }
        setState(Constants.stateNone)
    }

    fun connect(device: BluetoothDevice) {
        if (statenow == Constants.stateConnecting) {
            connectThread!!.cancel()
            connectThread = null
        }
        connectThread = ConnectThread(device)
        connectThread!!.start()
        term = true
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }
        setState(Constants.stateConnecting)
    }

    /*==========================================SUPPORT==========================================*/

    private fun connectionLost() {
        val message = handler.obtainMessage(Constants.messageToast)
        val bundle = Bundle()
        bundle.putString(Constants.toast, "Disconnected")
        message.data = bundle
        handler.sendMessage(message)
        term = false
        isConnect = false
        start()
    }

    @Synchronized
    private fun connectionFailed() {
        val message = handler.obtainMessage(Constants.messageToast)
        val bundle = Bundle()
        bundle.putString(Constants.toast, "Unable to connect with device")
        message.data = bundle
        handler.sendMessage(message)
        term = false
        isConnect = false
        start()
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun connected(socket: BluetoothSocket?, device: BluetoothDevice) {
        if (connectThread != null){
            connectThread!!.cancel()
            connectThread = null
        }
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }
        connectedThread = ConnectedThread(socket)
        connectedThread!!.start()
        val message = handler.obtainMessage(Constants.messageDeviceName)
        val bundle = Bundle()
        bundle.putString(Constants.deviceName, device.name)
        message.data = bundle
        handler.sendMessage(message)
        setState(Constants.stateConnected)
    }

    fun write(buffer: ByteArray?) {
        var connThread: ConnectedThread?
        synchronized(this) {
            if (statenow != Constants.stateConnected) {
                return
            }
            connThread = connectedThread
        }
        connThread!!.write(buffer)
    }

    /*==========================================THREAD===========================================*/
    @SuppressLint("MissingPermission")
    private inner class AcceptThread : Thread(){
        private val serverSocket : BluetoothServerSocket?

        init {
            var tmp : BluetoothServerSocket? = null
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(Constants.appName, Constants.appUUID)
            }catch (e : IOException){
                //Log.e("Accept->Constructor", e.toString())
            }
            serverSocket = tmp
        }

        override fun run() {
            var socket : BluetoothSocket? = null
            try {
                socket = serverSocket!!.accept()
            } catch (e : IOException){
                //Log.e("Accept->Run", e.toString())
                try {
                    serverSocket!!.close()
                } catch (e1: IOException) {
                    //Log.e("Accept->Close", e.toString())
                }
            }
            if (socket != null) {
                when (statenow) {
                    Constants.stateListen, Constants.stateConnecting -> connected(socket, socket.remoteDevice)
                    Constants.stateNone, Constants.stateConnected -> try {
                        socket.close()
                    } catch (e: IOException) {
                        //Log.e("Accept->CloseSocket", e.toString())
                    }
                }
            }
        }

        fun cancel(){
            try {
                serverSocket!!.accept()
            }catch (e : IOException){
                //Log.e("Accept->CloseServer", e.toString())
            }
        }
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread(){
        private val socket: BluetoothSocket?

        init {
            var tmp: BluetoothSocket? = null
            try {
                tmp = device.createRfcommSocketToServiceRecord(Constants.appUUID)
            } catch (e: IOException) {
                //Log.e("Connect->Constructor", e.toString())
            }
            socket = tmp
        }

        @SuppressLint("MissingPermission")
        override fun run() {
            try {
                socket!!.connect()
            } catch (e: IOException) {
                //Log.e("Connect->Run", e.toString())
                try {
                    socket!!.close()
                } catch (e1: IOException) {
                    //Log.e("Connect->CloseSocket", e.toString())
                }
                connectionFailed()
                return
            }
            synchronized(this@BluetoothUtility) {
                connectThread = null
            }
            connected(socket, device)
        }

        fun cancel() {
            try {
                socket!!.close()
            } catch (e: IOException) {
                //Log.e("Connect->Cancel", e.toString())
            }
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket?) : Thread() {
        private val inputStream: InputStream?
        private val outputStream: OutputStream?

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null
            isConnect = true
            try {
                tmpIn = socket!!.inputStream
                tmpOut = socket.outputStream
            } catch (e: IOException) {
            }
            inputStream = tmpIn
            outputStream = tmpOut
        }

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (term) {
                try {
                    bytes = inputStream!!.read(buffer)
                    handler.obtainMessage(Constants.messageRead, bytes, -1, buffer).sendToTarget()
                } catch (e: IOException) {
                    connectionLost()
                }
                try {
                    sleep(150)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }

        fun write(buffer: ByteArray?) {
            try {
                outputStream!!.write(buffer)
                handler.obtainMessage(Constants.messageWrite, -1, -1, buffer).sendToTarget()
            } catch (e: IOException) {
            }
        }

        fun cancel() {
            try {
                socket!!.close()
            } catch (e: IOException) {
            }
        }
    }
}