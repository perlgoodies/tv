package com.geekiestgeek.universaltvoff.ui.manufacturers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.geekiestgeek.universaltvoff.R

/**
 * Adapter for displaying manufacturer selection items in a RecyclerView
 */
class ManufacturersAdapter(
    private val manufacturers: List<String>,
    private val selectedManufacturers: MutableSet<String>,
    private val onManufacturerSelected: (String, Boolean) -> Unit
) : RecyclerView.Adapter<ManufacturersAdapter.ViewHolder>() {

    /**
     * ViewHolder for manufacturer items
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.manufacturer_checkbox)
        val nameText: TextView = view.findViewById(R.id.manufacturer_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manufacturer, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val manufacturer = manufacturers[position]

        // Set manufacturer name
        holder.nameText.text = manufacturer

        // Set checkbox state based on selection
        holder.checkbox.isChecked = selectedManufacturers.contains(manufacturer)

        // Setup click listeners for the whole item and checkbox
        val clickListener = View.OnClickListener {
            val isChecked = !holder.checkbox.isChecked
            holder.checkbox.isChecked = isChecked

            // Call the callback with selection status
            onManufacturerSelected(manufacturer, isChecked)
        }

        // Set click listener on the whole item
        holder.itemView.setOnClickListener(clickListener)

        // Set checkbox listener
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            onManufacturerSelected(manufacturer, isChecked)
        }
    }

    override fun getItemCount(): Int {
        return manufacturers.size
    }
}