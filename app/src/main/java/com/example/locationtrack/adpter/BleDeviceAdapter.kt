package com.example.locationtrack.adpter

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.locationtrack.R
import com.example.locationtrack.databinding.ItemBleDeviceBinding

class BleDeviceAdapter(
    private val context: Context,
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BleDeviceAdapter.BleDeviceViewHolder>() {

    private val devices = mutableListOf<BluetoothDevice>()
    private val deviceServicesMap = mutableMapOf<BluetoothDevice, List<BluetoothGattService>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BleDeviceViewHolder {
        val binding =
            ItemBleDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BleDeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BleDeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
    }

    override fun getItemCount() = devices.size

    inner class BleDeviceViewHolder(private val binding: ItemBleDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(device: BluetoothDevice) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }

            binding.apply {
                deviceName.text = "Device Name: ${device.name ?: "Unknown"}"
                deviceAddress.text = "Device Address: ${device.address}"

                when (device.type) {
                    BluetoothDevice.DEVICE_TYPE_LE -> deviceType.setImageResource(R.drawable.ic_bluetooth_24)
                    BluetoothDevice.DEVICE_TYPE_DUAL -> deviceType.setImageResource(R.drawable.ic_headphones_24)
                    else -> deviceType.setImageResource(R.drawable.ic_bluetooth_24)
                }

                binding.root.setOnClickListener {
                    onDeviceClick(device)
                }
            }
        }
    }

    fun addDevice(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        if (!devices.contains(device) && device.name != null) {
            devices.add(device)
            notifyDataSetChanged()
        }
    }

    fun addServicesAndCharacteristics(
        device: BluetoothDevice,
        gattServices: List<BluetoothGattService>?
    ) {
        if (gattServices.isNullOrEmpty()) return
        deviceServicesMap[device] = gattServices
    }
}

