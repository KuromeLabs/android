package com.kuromelabs.kurome.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.models.Device

class DeviceAdapter(val onItemClicked: (Device) -> Unit) : ListAdapter<Device, DeviceAdapter.DeviceViewHolder>(DeviceDiffUtil()) {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val root =
            LayoutInflater.from(parent.context).inflate(R.layout.device_item_view, parent, false)
        return DeviceViewHolder(root)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device: Device = getItem(position)
        holder.setReference(device)
        val resources = holder.itemView.context.resources
        holder.deviceNameTextView.text = device.name
        holder.deviceStatusTextView.text =
            if (device.isPaired)
                if (device.isConnected)
                    resources.getString(R.string.status_connected)
                else
                    resources.getString(R.string.status_disconnected)
            else resources.getString(R.string.status_available)

    }

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        init{
            view.setOnClickListener(this)
        }
        val deviceNameTextView: TextView = view.findViewById(R.id.device_name)
        val deviceStatusTextView: TextView = view.findViewById(R.id.device_status)
        var device: Device? = null
        fun setReference(device: Device){
            this.device = device
        }

        override fun onClick(p0: View?) {
            p0.let { onItemClicked.invoke(device!!) }
        }
    }

    class DeviceDiffUtil : DiffUtil.ItemCallback<Device>() {
        override fun areItemsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem.id == newItem.id && oldItem.isPaired == newItem.isPaired && oldItem.isConnected == newItem.isConnected
        }
    }

}
