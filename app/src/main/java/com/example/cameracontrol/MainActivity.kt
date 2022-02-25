package com.example.cameracontrol

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.*
import android.os.Build.VERSION.SDK_INT
import android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cameracontrol.adapter.RecyclerViewPairedAdapter
import com.example.cameracontrol.data.Constants
import com.example.cameracontrol.databinding.ActivityMainBinding
import com.example.cameracontrol.utility.BluetoothUtility
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding
    lateinit var bluetoothUtility   : BluetoothUtility

    private val bluetoothAdapter : BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var dataString              : String                = ""
    private var connectedDevice         : String                = ""
    private lateinit var dialog         : Dialog

    private var backPressedTime         : Long                  = 0
    private var incDecZoom              : Float                 = 0.25F

    private var imageCapture            : ImageCapture?         = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor : ExecutorService

    private lateinit var cameraProvider : ProcessCameraProvider
    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>

    private var camera                  : Camera?               = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dialog = Dialog(this)
        bluetoothUtility = BluetoothUtility(handlerBluetooth)
        setSubtitle(getString(R.string.strNC))

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.rgZoom.setOnCheckedChangeListener { _, i ->
            when(i){
                R.id.rb1 -> incDecZoom = Constants.zoomInOut[0]
                R.id.rb2 -> incDecZoom = Constants.zoomInOut[1]
                R.id.rb3 -> incDecZoom = Constants.zoomInOut[2]
                R.id.rb4 -> incDecZoom = Constants.zoomInOut[3]
            }
        }

        binding.btZoomIn.setOnClickListener {
            CoroutineScope(Dispatchers.Default).launch {
                var scale = camera!!.cameraInfo.zoomState.value!!.zoomRatio
                for (i in 1..100){
                    scale += incDecZoom/100
                    camera!!.cameraControl.setZoomRatio(scale)
                    delay(2)
                }
            }
        }

        binding.btZoomOut.setOnClickListener {
            CoroutineScope(Dispatchers.Default).launch {
                var scale = camera!!.cameraInfo.zoomState.value!!.zoomRatio
                for (i in 1..100){
                    scale -= incDecZoom/100
                    camera!!.cameraControl.setZoomRatio(scale)
                    delay(2)
                }
            }
        }

        binding.btTakePicture.setOnClickListener { takePhoto() }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main_activity, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.menu_bluetooth -> {
                enableBluetooth()
                true
            }
            R.id.menu_setting -> {
                startActivity(Intent(ACTION_BLUETOOTH_SETTINGS))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed()
            stopCamera()
            bluetoothUtility.stop()
            finish()
        } else {
            Toast.makeText(this, "Press back again to leave the app", Toast.LENGTH_SHORT).show()
        }
        backPressedTime = System.currentTimeMillis()
    }

    @SuppressLint("InlinedApi")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when(requestCode){
            Constants.bluetoothRequestPermit -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    if(bluetoothUtility.isConnect){
                        stopCamera()
                        bluetoothUtility.stop()
                    }
                    else{
                        bluetoothDialog()
                    }
                } else {
                    Toast.makeText(this, "Bluetooth permission required on Android 12+", Toast.LENGTH_SHORT).show()
                }
            }
            Constants.cameraRequestPermit -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    binding.clMain.visibility = View.GONE
                    binding.clCamera.visibility = View.VISIBLE
                    startCamera()
                } else {
                    Toast.makeText(this, "Camera permission required to access camera hardware", Toast.LENGTH_SHORT).show()
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun setSubtitle(title : CharSequence) {
        supportActionBar!!.subtitle = title
    }

    private fun isBluetoothPermissionNotGranted() : Boolean {
        return (SDK_INT >= Build.VERSION_CODES.S) &&
                (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
    }

    private fun isCameraPermissionNotGranted() : Boolean {
        return ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
    }

    private var launchActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) { enableBluetooth() }
    }

    private fun enableBluetooth(){
        if(!bluetoothAdapter.isEnabled){
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            launchActivityResult.launch(enableIntent)
        } else {
            if (isBluetoothPermissionNotGranted()) {
                ActivityCompat.requestPermissions(this@MainActivity,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT), Constants.bluetoothRequestPermit)
            }
            else{
                if(bluetoothUtility.isConnect){
                    bluetoothUtility.stop()
                }
                else{
                    bluetoothDialog()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothDialog(){
        dialog.setContentView(R.layout.bottom_sheet)
        dialog.window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window!!.attributes.windowAnimations = R.style.DialogAnimation
        dialog.window!!.setGravity(Gravity.BOTTOM)
        dialog.show()

        val rvPaired = dialog.findViewById<RecyclerView>(R.id.rvPaired)
        val dataName = ArrayList<String>()
        val dataMac = ArrayList<String>()
        val pairedDevices : Set<BluetoothDevice> = bluetoothAdapter.bondedDevices

        if(pairedDevices.isNotEmpty()){
            for (device in pairedDevices) {
                dataName.add(device.name)
                dataMac.add(device.address)
            }
        }

        rvPaired.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(this)
        rvPaired.layoutManager = layoutManager
        val adapter = RecyclerViewPairedAdapter(handlerBluetooth, dataName, dataMac)
        rvPaired.adapter = adapter
    }

    private val handlerBluetooth = object:  Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when(msg.what){
                Constants.messageStateChanged     -> when(msg.arg1){
                    Constants.stateNone       -> {
                        stopCamera()
                        binding.clMain.visibility = View.VISIBLE
                        binding.clCamera.visibility = View.GONE
                        setSubtitle(getString(R.string.strNC))
                    }
                    Constants.stateListen     -> {
                        binding.clMain.visibility = View.VISIBLE
                        binding.clCamera.visibility = View.GONE
                        setSubtitle(getString(R.string.strNC))
                    }
                    Constants.stateConnecting -> {
                        binding.clMain.visibility = View.VISIBLE
                        binding.clCamera.visibility = View.GONE
                        setSubtitle(getString(R.string.strCTI))
                    }
                    Constants.stateConnected  -> {
                        dialog.dismiss()
                        val newText = this@MainActivity.resources.getString(R.string.strCTD, connectedDevice)
                        setSubtitle(newText)

                        if(isCameraPermissionNotGranted()){
                            ActivityCompat.requestPermissions(this@MainActivity,
                                arrayOf(Manifest.permission.CAMERA), Constants.cameraRequestPermit)
                        } else {
                            binding.clMain.visibility = View.GONE
                            binding.clCamera.visibility = View.VISIBLE
                            startCamera()
                        }

                    }
                }
                Constants.messageWrite            -> {
//                    var buffer1 : ByteArray? = msg.obj as ByteArray
                }
                Constants.messageRead             -> {
                    val buffer = msg.obj as ByteArray
                    val inputBuffer = String(buffer, 0, msg.arg1)
                    dataString += inputBuffer
                    CoroutineScope(Dispatchers.Default).launch {
                        delay(200)
                        if(dataString.isNotEmpty()){
                            when(dataString){
                                "I" ->{
                                    CoroutineScope(Dispatchers.Default).launch {
                                        var scale = camera!!.cameraInfo.zoomState.value!!.zoomRatio
                                        for (i in 1..100){
                                            scale += incDecZoom/100
                                            camera!!.cameraControl.setZoomRatio(scale)
                                            delay(2)
                                        }
                                    }
                                }
                                "O" ->{
                                    CoroutineScope(Dispatchers.Default).launch {
                                        var scale = camera!!.cameraInfo.zoomState.value!!.zoomRatio
                                        for (i in 1..100){
                                            scale -= incDecZoom/100
                                            camera!!.cameraControl.setZoomRatio(scale)
                                            delay(2)
                                        }
                                    }
                                }
                                "P" -> takePhoto()
                            }
                            dataString = ""
                        }
                    }
                }
                Constants.messageDeviceName       -> {
                    connectedDevice = msg.data.getString(Constants.messageString)!!
                }
                Constants.messageToast            -> {
                    val msgToast = msg.data.getString(Constants.messageString)
                    Toast.makeText(this@MainActivity, msgToast, Toast.LENGTH_SHORT).show()
                }
                Constants.messageConnect            -> {
                    val mac = msg.data.getString(Constants.deviceMac)
                    bluetoothUtility.connect(bluetoothAdapter.getRemoteDevice(mac))
                }
            }
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(
                Constants.FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    //Failed
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Save photo successfully"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun startCamera() {
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.createSurfaceProvider())
                }
            imageCapture = ImageCapture.Builder()
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                //ERROR
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraProvider.unbindAll()
        cameraProviderFuture.cancel(true)
    }
}