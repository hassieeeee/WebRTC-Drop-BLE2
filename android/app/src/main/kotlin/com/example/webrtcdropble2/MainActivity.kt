package com.example.webrtcdropble2


import android.os.Build.VERSION_CODES
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.util.Log
import androidx.annotation.UiThread
//ChatServerの内容////////////////////////////////////////////////////
import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
//ScanActivityの内容///////////////////////////////////////////////
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
//AdvertiseActivityの内容////////////////////////////////////////////
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
//RecyclerAdopterの内容//////////////////////////////////////////////
import java.util.UUID
import org.json.JSONObject


@RequiresApi(VERSION_CODES.LOLLIPOP)
class MainActivity : FlutterActivity() {

    private val CHANNEL = "samples.flutter.dev/ble"


    private var scanCallback: DeviceScanCallback? = null

    private val scanFilters: List<ScanFilter>

    private val scanSettings: ScanSettings

    private val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    private var scanner: BluetoothLeScanner? = null

    private val scanResults = mutableMapOf<String, BluetoothDevice>()
    private val scanResults2 = mutableMapOf<String, String>()

    private lateinit var bluetoothManager: BluetoothManager

    //private val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    private var gattServer: BluetoothGattServer? = null
    private var gattServerCallback: BluetoothGattServerCallback? = null

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    private var advertiseSettings: AdvertiseSettings = buildAdvertiseSettings()

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    private var advertiseData: AdvertiseData = buildAdvertiseData()

    private var gattClient: BluetoothGatt? = null
    private var gattClientCallback: BluetoothGattCallback? = null

    private var gatt: BluetoothGatt? = null
    private var messageCharacteristic: BluetoothGattCharacteristic? = null

    //methodchannel受け取り
    @RequiresApi(VERSION_CODES.LOLLIPOP)
    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { methodCall, result ->
            if (methodCall.method == "KotlinStart") {

                startServer(application)

                //result.success(n)
            } else if (methodCall.method == "Scan") {
                if (!adapter.isMultipleAdvertisementSupported) {
                    Log.d(TAG, "startScan: failed")
                }
                if (scanCallback == null) {
                    scanner = adapter.bluetoothLeScanner
                    Log.d(TAG, "startScan: start scanning")

                    Handler(Looper.myLooper()!!).postDelayed({
                        Log.d(TAG, "stopScanning: stop scanning")
                        if (ActivityCompat.checkSelfPermission(
                                application,
                                Manifest.permission.BLUETOOTH_SCAN
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                        }
                        scanner?.stopScan(scanCallback)
                        //Log.d(TAG, "stopScanning: adddress : " + scanResults.values)
                        for (map in scanResults) {
                            if (scanResults.size != 0) {
                                scanResults2.put(map.value.name, map.value.address)
                                Log.d(TAG, "configureFlutterEngine: address : " + map.value)
                            }
                        }
                        val str = JSONObject(scanResults2 as Map<*, *>?).toString()
                        scanCallback = null
                        Handler(Looper.getMainLooper()).post {
                            MethodChannel(
                                flutterEngine?.dartExecutor!!.binaryMessenger,
                                CHANNEL
                            ).invokeMethod("ScanResultTerminalMap", str)
                        }
                    }, Companion.SCAN_PERIOD)

                    scanCallback = DeviceScanCallback()

                    if (ActivityCompat.checkSelfPermission(
                            application,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.d(TAG, "startScan: permission denied")
                    }
                    scanner?.startScan(scanFilters, scanSettings, scanCallback)
                }
                android.util.Log.d(TAG, "configureFlutterEngine: address" + scanResults.values)

            } else if (methodCall.method == "connect") {
                Log.d(TAG, "configureFlutterEngine: connect start")
                val address = methodCall.arguments<String>()!!

                Log.d(TAG, "configureFlutterEngine: address : $address")
                connectfromaddress(address, application)

            } else if (methodCall.method == "WriteMessage") {
                val message = methodCall.arguments<String>()!!
                sendMessage(message, application)

            } else {
                result.notImplemented()
            }
        }

    }

    private fun rep(x: String): String {

        return "hello"
    }


    @RequiresApi(VERSION_CODES.LOLLIPOP)
    fun startServer(app: Application) {
        bluetoothManager = app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (!adapter.isEnabled) {
            Log.d(TAG, "startServer: bluetooth unable")
        } else {
            setupGattServer(app)
            startAdvertisement(app)
        }
    }

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    fun stopServer(app: Application) {
        stopAdvertising(app)
    }

    @RequiresApi(VERSION_CODES.JELLY_BEAN_MR2)
    private fun setupGattServer(app: Application) {
        gattServerCallback = GattServerCallback(app)

        if (ActivityCompat.checkSelfPermission(
                app,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        gattServer = bluetoothManager.openGattServer(app, gattServerCallback).apply {
            addService(setupGattService())
        }
    }

    @RequiresApi(VERSION_CODES.JELLY_BEAN_MR2)
    private fun setupGattService(): BluetoothGattService {
        val service =
            BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val messageCharacteristic = BluetoothGattCharacteristic(
            MESSAGE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(messageCharacteristic)
        val confirmCharacteristic = BluetoothGattCharacteristic(
            CONFIRM_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(confirmCharacteristic)
        return service
    }

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    private fun startAdvertisement(app: Application) {
        advertiser = adapter.bluetoothLeAdvertiser
        Log.d(TAG, "startAdvertisement: with advertiser $advertiser")

        if (advertiseCallback == null) {
            advertiseCallback = DeviceAdvertiseCallback()

            if (ActivityCompat.checkSelfPermission(
                    app,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            advertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
        }
    }

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    private fun stopAdvertising(app: Application) {
        Log.d(TAG, "stopAdvertising: stop advertiser $advertiser")
        if (ActivityCompat.checkSelfPermission(
                app,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        advertiser?.stopAdvertising(advertiseCallback)
        advertiseCallback = null
    }

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    private fun buildAdvertiseData(): AdvertiseData {
        val dataBuilder = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(true)

        return dataBuilder.build()
    }

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    private fun buildAdvertiseSettings(): AdvertiseSettings {
        return AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTimeout(0)
            .build()
    }

    @RequiresApi(VERSION_CODES.JELLY_BEAN_MR2)
    fun connectToChatDevice(device: BluetoothDevice, app: Application) {
        gattClientCallback = GattClientCallback(app)
        if (ActivityCompat.checkSelfPermission(
                app,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        gattClient = device.connectGatt(app, false, gattClientCallback)

    }

    @RequiresApi(VERSION_CODES.JELLY_BEAN_MR2)
    fun sendMessage(message: String, app: Application): Boolean {
        Log.d(TAG, "sendMessage: start")
        messageCharacteristic?.let { characteristic ->
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            val messageBytes = message.toByteArray(Charsets.UTF_8)
            characteristic.value = messageBytes
            gatt?.let {
                if (ActivityCompat.checkSelfPermission(
                        app,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
                val success = it.writeCharacteristic(messageCharacteristic)
                Log.d(TAG, "sendMessage: send $message")
            } ?: kotlin.run {
                Log.d(TAG, "sendMessage: no gatt connection")
            }
        }
        return true
    }

    @RequiresApi(VERSION_CODES.JELLY_BEAN_MR2)
    fun connectfromaddress(address: String, app: Application) {
        val device: BluetoothDevice
        adapter.let { adapter ->
            device = adapter.getRemoteDevice(address)
        }
        connectToChatDevice(device, app)
    }

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    private class DeviceAdvertiseCallback : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            val errorMessage = "Advertise failed with error: $errorCode"
            Log.d(TAG, "onStartFailure: Advertising Failed")
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "onStartSuccess: Advertising successfully started")
        }
    }

    @RequiresApi(VERSION_CODES.JELLY_BEAN_MR2)
    @UiThread
    private inner class GattServerCallback(val application: Application) :
        BluetoothGattServerCallback() {
        override fun onConnectionStateChange(
            device: BluetoothDevice?,
            status: Int,
            newState: Int
        ) {
            super.onConnectionStateChange(device, status, newState)
            val isSuccess = status == BluetoothGatt.GATT_SUCCESS
            val isConnected = newState == BluetoothProfile.STATE_CONNECTED
        }

        @UiThread
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(
                device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            if (characteristic != null) {
                if (characteristic.uuid == MESSAGE_UUID) {
                    if (ActivityCompat.checkSelfPermission(
                            application,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null
                    )
                    val message = value?.toString(Charsets.UTF_8)
                    Log.d(TAG, "onCharacteristicWriteRequest: Have message: $message")

                    Log.d(TAG, "${Thread.currentThread() == application.mainLooper.thread}")
                    Handler(Looper.getMainLooper()).post {
                        MethodChannel(
                            flutterEngine?.dartExecutor!!.binaryMessenger,
                            CHANNEL
                        ).invokeMethod("receive", message)
                    }


                }
            }
        }
    }

    @RequiresApi(VERSION_CODES.JELLY_BEAN_MR2)
    private inner class GattClientCallback(val application: Application) : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val isSuccess = status == BluetoothGatt.GATT_SUCCESS
            val isConnected = newState == BluetoothProfile.STATE_CONNECTED
            Log.d(
                TAG,
                "onConnectionStateChange: Client $gatt  success: $isSuccess  connected: $isConnected"
            )
            if (isSuccess && isConnected) {
                if (ActivityCompat.checkSelfPermission(
                        application,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "onConnectionStateChange: permission")
                    return
                }
                Log.d(TAG, "onConnectionStateChange: gattdiscoverservice")
                gatt?.discoverServices()
            }
        }

        override fun onServicesDiscovered(discoveredgatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(discoveredgatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onServicesDiscovered: Have gatt $discoveredgatt")
                gatt = discoveredgatt
                val service = discoveredgatt?.getService(SERVICE_UUID)
                if (service != null) {
                    messageCharacteristic = service.getCharacteristic(MESSAGE_UUID)
                }
            }
        }
    }


//    private val scanlist = mutableListOf<RecyclerAdapter.Item>()
//
//
//    private lateinit var recyclerView: RecyclerView
//    private lateinit var radapter: RecyclerAdapter

    init {
        scanFilters = buildScanFilters()
        scanSettings = buildScanSettings()
    }

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_scan)
//        Log.d(TAG, "onCreate: start ScanActivity")
//
//        startScan(application)
//
//    }

//    @RequiresApi(VERSION_CODES.JELLY_BEAN_MR2)
//    private fun handleItemClick(item: RecyclerAdapter.Item) {
//        val device = item.device
//        val address: String = device.address
//        Log.d(TAG, "handleItemClick: $address")
//        //ChatServer.connectToChatDevice(device,application)
//        ChatServer.connectfromaddress(address, application)
//    }


//    override fun onDestroy() {
//        super.onDestroy()
//        stopScanning(application)
//    }

//    @RequiresApi(VERSION_CODES.LOLLIPOP)
//    fun startScan(app: Application) {
//        if (!adapter.isMultipleAdvertisementSupported) {
//            Log.d(TAG, "startScan: failed")
//            return
//        }
//        if (scanCallback == null) {
//            scanner = adapter.bluetoothLeScanner
//            Log.d(TAG, "startScan: start scanning")
//
//            Handler(Looper.myLooper()!!).postDelayed({ stopScanning(app) }, Companion.SCAN_PERIOD)
//
//            scanCallback = DeviceScanCallback()
//
//            if (ActivityCompat.checkSelfPermission(
//                    app,
//                    Manifest.permission.BLUETOOTH_SCAN
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                Log.d(TAG, "startScan: permission denied")
//                return
//            }
//            scanner?.startScan(scanFilters, scanSettings, scanCallback)
//        }
//    }
//
//    @RequiresApi(VERSION_CODES.LOLLIPOP)
//    private fun stopScanning(app: Application) {
//        Log.d(TAG, "stopScanning: stop scanning")
////        Log.d(TAG, "stopScanning: " + scanResults.keys)
////        for (map in scanResults) {
////            scanlist.add(RecyclerAdapter.Item(map.value.address, map.value.name, map.value))
////        }
////
////        recyclerView = findViewById(R.id.list)
////        recyclerView.layoutManager = LinearLayoutManager(applicationContext)
////        Log.d(TAG, "adapter set")
////        radapter = RecyclerAdapter(scanlist) { item ->
////            handleItemClick(item)
////            Log.d(TAG, "onCreate: itemclick")
////        }
////        recyclerView.adapter = radapter
//        if (ActivityCompat.checkSelfPermission(
//                app,
//                Manifest.permission.BLUETOOTH_SCAN
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            return
//        }
//        scanner?.stopScan(scanCallback)
//        Log.d(TAG, "stopScanning: adddress"+scanResults.values)
//        scanCallback = null
//    }

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    private fun buildScanFilters(): List<ScanFilter> {
        val builder = ScanFilter.Builder()
        builder.setServiceUuid(ParcelUuid(SERVICE_UUID))
        val filter = builder.build()
        return listOf(filter)
    }

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    private fun buildScanSettings(): ScanSettings {
        return ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
    }

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    private inner class DeviceScanCallback : ScanCallback() {
        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            for (item in results) {
                item.device?.let { device ->
                    scanResults[device.address] = device
                }
            }
        }

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            if (result != null) {
                result.device?.let { device ->
                    scanResults[device.address] = device
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            val errorMessage = "Scan failed with error $errorCode"
        }
    }

    companion object {
        private const val SCAN_PERIOD = 10000L
    }

}

//ChatServerの内容//////////////////////////////////////////////////////////////////
//package com.example.kotlinble


//////////////////////////////////////////////////////////////////////

//constaintsの内容/////////////////////////////////////////////////////


val SERVICE_UUID: UUID = UUID.fromString("00001111-0000-1000-8000-00805F9B34FB")

val MESSAGE_UUID: UUID = UUID.fromString("4038a48e-c736-4c20-9fe0-5cdb66c55326")

val CONFIRM_UUID: UUID = UUID.fromString("efc0256b-13f7-437a-9f51-5462b11a4ba1")
//////////////////////////////////////////////////////////////////////

//ScanActivityの内容//////////////////////////////////////////////////
//@RequiresApi(VERSION_CODES.LOLLIPOP)
//@Suppress("DEPRECATION")
//class ScanActivity(){

//    private var scanCallback: DeviceScanCallback? = null
//
//    private val scanFilters: List<ScanFilter>
//
//    private val scanSettings: ScanSettings
//
//    private val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
//
//    private var scanner: BluetoothLeScanner? = null
//
//    private val scanResults = mutableMapOf<String, BluetoothDevice>()
//
////    private val scanlist = mutableListOf<RecyclerAdapter.Item>()
////
////
////    private lateinit var recyclerView: RecyclerView
////    private lateinit var radapter: RecyclerAdapter
//
//    init {
//        scanFilters = buildScanFilters()
//        scanSettings = buildScanSettings()
//    }
//
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        setContentView(R.layout.activity_scan)
////        Log.d(TAG, "onCreate: start ScanActivity")
////
////        startScan(application)
////
////    }
//
////    @RequiresApi(VERSION_CODES.JELLY_BEAN_MR2)
////    private fun handleItemClick(item: RecyclerAdapter.Item) {
////        val device = item.device
////        val address: String = device.address
////        Log.d(TAG, "handleItemClick: $address")
////        //ChatServer.connectToChatDevice(device,application)
////        ChatServer.connectfromaddress(address, application)
////    }
//
//
////    override fun onDestroy() {
////        super.onDestroy()
////        stopScanning(application)
////    }
//
//    @RequiresApi(VERSION_CODES.LOLLIPOP)
//    fun startScan(app: Application) {
//        if (!adapter.isMultipleAdvertisementSupported) {
//            Log.d(TAG, "startScan: failed")
//            return
//        }
//        if (scanCallback == null) {
//            scanner = adapter.bluetoothLeScanner
//            Log.d(TAG, "startScan: start scanning")
//
//            Handler(Looper.myLooper()!!).postDelayed({ stopScanning(app) }, Companion.SCAN_PERIOD)
//
//            scanCallback = DeviceScanCallback()
//
//            if (ActivityCompat.checkSelfPermission(
//                    app,
//                    Manifest.permission.BLUETOOTH_SCAN
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                Log.d(TAG, "startScan: permission denied")
//                return
//            }
//            scanner?.startScan(scanFilters, scanSettings, scanCallback)
//        }
//    }
//
//    @RequiresApi(VERSION_CODES.LOLLIPOP)
//    private fun stopScanning(app: Application) {
////        Log.d(TAG, "stopScanning: stop scanning")
////        Log.d(TAG, "stopScanning: " + scanResults.keys)
////        for (map in scanResults) {
////            scanlist.add(RecyclerAdapter.Item(map.value.address, map.value.name, map.value))
////        }
////
////        recyclerView = findViewById(R.id.list)
////        recyclerView.layoutManager = LinearLayoutManager(applicationContext)
////        Log.d(TAG, "adapter set")
////        radapter = RecyclerAdapter(scanlist) { item ->
////            handleItemClick(item)
////            Log.d(TAG, "onCreate: itemclick")
////        }
////        recyclerView.adapter = radapter
//        if (ActivityCompat.checkSelfPermission(
//                app,
//                Manifest.permission.BLUETOOTH_SCAN
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            return
//        }
//        scanner?.stopScan(scanCallback)
//        scanCallback = null
//    }
//
//    @RequiresApi(VERSION_CODES.LOLLIPOP)
//    private fun buildScanFilters(): List<ScanFilter> {
//        val builder = ScanFilter.Builder()
//        builder.setServiceUuid(ParcelUuid(SERVICE_UUID))
//        val filter = builder.build()
//        return listOf(filter)
//    }
//
//    @RequiresApi(VERSION_CODES.LOLLIPOP)
//    private fun buildScanSettings(): ScanSettings {
//        return ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
//    }
//
//    @RequiresApi(VERSION_CODES.LOLLIPOP)
//    private inner class DeviceScanCallback : ScanCallback() {
//        override fun onBatchScanResults(results: List<ScanResult>) {
//            super.onBatchScanResults(results)
//            for (item in results) {
//                item.device?.let { device ->
//                    scanResults[device.address] = device
//                }
//            }
//        }
//
//        override fun onScanResult(callbackType: Int, result: ScanResult?) {
//            super.onScanResult(callbackType, result)
//            if (result != null) {
//                result.device?.let { device ->
//                    scanResults[device.address] = device
//                }
//            }
//        }
//
//        override fun onScanFailed(errorCode: Int) {
//            super.onScanFailed(errorCode)
//            val errorMessage = "Scan failed with error $errorCode"
//        }
//    }
//
//    companion object {
//        private const val SCAN_PERIOD = 30000L
//    }
//}
/////////////////////////////////////////////////////////////////////

//AdvertiseActivityの内容////////////////////////////////////////////
//class AdvertiseActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_advertise)
//        Log.d(TAG, "onCreate: start AdvertisingActivity")
//
//        ChatServer.startServer(application)
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        ChatServer.stopServer(application)
//    }
//}
////////////////////////////////////////////////////////////////////

//RecyclerAdopterの内容//////////////////////////////////////////////
//class RecyclerAdapter(private val dataList: MutableList<Item>,
//                      private val onItemClickListener: (Item) -> Unit
//) :
//    RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {
//
//    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        val textView1: TextView = itemView.findViewById(R.id.name)
//        val textView2: TextView = itemView.findViewById(R.id.id)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
//        val itemView = LayoutInflater.from(parent.context)
//            .inflate(R.layout.listitem, parent, false)
//        return ViewHolder(itemView)
//    }
//
//    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//        val item = dataList[position]
//        holder.textView1.text = item.text1
//        holder.textView2.text = item.text2
//
//        holder.itemView.setOnClickListener {
//            onItemClickListener.invoke(item)
//        }
//    }
//
//    override fun getItemCount(): Int {
//        return dataList.size
//    }
//
//    data class Item(val text1: String,val text2:String ,val device: BluetoothDevice)
//
//}
////////////////////////////////////////////////////////////////////////////

