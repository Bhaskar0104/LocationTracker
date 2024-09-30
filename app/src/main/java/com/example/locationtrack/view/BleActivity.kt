package com.example.locationtrack.view

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.locationtrack.adpter.BleDeviceAdapter
import com.example.locationtrack.databinding.ActivityBleBinding

class BleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBleBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var adapter: BleDeviceAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val bluetoothConnectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        } else {
            true
        }
        val bluetoothScanGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false
        } else {
            true
        }

        if (fineLocationGranted && bluetoothConnectGranted && bluetoothScanGranted) {
            setupRecyclerView()
            startBleScan()
        } else {
            showPermissionRationale(permissions)
        }
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                    }
                    gatt?.discoverServices() // Discover services after successful connection
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt?.close() // Ensure GATT is properly closed after disconnection
                    gatt?.device?.let { connectToDevice(it) }
                }
            } else {
                gatt?.close() // Close the connection if there's an error
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt?.let {
                    // Here are the information's of the gatt device
                    adapter.addServicesAndCharacteristics(it.device, it.services)
                }
            }
        }
    }

    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                val device =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val bondState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                when (bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        connectToDevice(device)
                    }

                    BluetoothDevice.BOND_BONDING -> {}
                    BluetoothDevice.BOND_NONE -> {}
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestLocationPermission()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            ActivityCompat.startActivityForResult(this, enableBtIntent, 2, null)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(bondStateReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    private fun setupRecyclerView() {
        adapter = BleDeviceAdapter(this) { selectedDevice ->
            pairWithDevice(selectedDevice)
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@BleActivity)
            adapter = this@BleActivity.adapter
        }
    }

    private fun connectToDevice(device: BluetoothDevice?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        device?.connectGatt(this, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun pairWithDevice(device: BluetoothDevice) {
        stopBleScan()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                2
            )
            return
        }
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            device.createBond()
        } else if (device.bondState == BluetoothDevice.BOND_BONDED) {
            Toast.makeText(
                this, "Device already bonded, attempting connection: ${device.name}",
                Toast.LENGTH_SHORT
            ).show()
            connectToDevice(device)
        }
    }

    private fun startBleScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                    1
                )
            }
        }
        bluetoothAdapter.bluetoothLeScanner?.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                binding.errorSms.visibility = View.GONE
                result.device?.let {
                    adapter.addDevice(it)
                }
            }
        })
    }

    private fun stopBleScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.BLUETOOTH_SCAN), 1
                )
            }
        }
        bluetoothAdapter.bluetoothLeScanner?.stopScan(object : ScanCallback() {})
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        bluetoothAdapter.bluetoothLeScanner?.stopScan(object : ScanCallback() {})
        unregisterReceiver(bondStateReceiver)
    }

    private fun requestLocationPermission() {
        val permissionsToRequest = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun showPermissionRationale(permissions: Map<String, Boolean>) {
        val deniedPermissions = permissions.filter { !it.value }.keys.toList()

        if (deniedPermissions.isNotEmpty()) {
            val shouldShowRationale = deniedPermissions.any {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }

            if (shouldShowRationale) {
                val message =
                    "The following permissions are required for the app to function correctly:\n${
                        deniedPermissions.joinToString(",\n")
                    }.\n Please grant them."

                AlertDialog.Builder(this)
                    .setTitle("Permissions Required")
                    .setMessage(message)
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                        requestPermissionLauncher.launch(deniedPermissions.toTypedArray())
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create()
                    .show()
            } else {
                showSettingsDialog()
            }
        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Please grant the necessary permissions from the app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
}
