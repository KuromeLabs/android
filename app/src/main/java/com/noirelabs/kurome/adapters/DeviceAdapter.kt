package com.noirelabs.kurome.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noirelabs.kurome.R
import com.noirelabs.kurome.models.Device

class DeviceAdapter : ListAdapter<Device, DeviceAdapter.DeviceViewHolder>(DeviceDiffUtil()) {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val root = LayoutInflater.from(parent.context).inflate(R.layout.device_item_view,parent,false)
        return DeviceViewHolder(root)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device: Device = getItem(position)
        holder.deviceNameTextView.text = device.name
    }

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceNameTextView: TextView = view.findViewById(R.id.device_name)
    }

    class DeviceDiffUtil : DiffUtil.ItemCallback<Device>() {
        override fun areItemsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem.id == newItem.id
        }
    }

}
