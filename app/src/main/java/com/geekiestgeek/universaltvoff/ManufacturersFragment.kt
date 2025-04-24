package com.geekiestgeek.universaltvoff.ui.manufacturers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geekiestgeek.universaltvoff.R
import com.geekiestgeek.universaltvoff.ir.CodeDatabase
import com.geekiestgeek.universaltvoff.ir.CodeDatabase.DeviceType

/**
 * Fragment for selecting device manufacturers
 */
class ManufacturersFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ManufacturersAdapter
    private lateinit var deviceTypeGroup: RadioGroup

    // Set of selected manufacturers
    private val selectedManufacturers = mutableSetOf<String>()

    // Current device type
    private var currentDeviceType = DeviceType.TV

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_manufacturers, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.manufacturers_recycler_view)
        deviceTypeGroup = view.findViewById(R.id.device_type_group)

        // Set up the recycler view
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Set up initial manufacturers list
        loadManufacturersForDeviceType(DeviceType.TV)

        // Set up device type radio group
        deviceTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            // Clear selected manufacturers when switching device types
            selectedManufacturers.clear()

            currentDeviceType = when(checkedId) {
                R.id.radio_tv -> DeviceType.TV
                R.id.radio_monitor -> DeviceType.MONITOR
                R.id.radio_projector -> DeviceType.PROJECTOR
                R.id.radio_audio -> DeviceType.AUDIO_RECEIVER
                R.id.radio_dvd -> DeviceType.DVD_PLAYER
                R.id.radio_other -> DeviceType.OTHER
                else -> DeviceType.TV
            }

            loadManufacturersForDeviceType(currentDeviceType)
        }
    }

    /**
     * Load manufacturers for a specific device type
     */
    private fun loadManufacturersForDeviceType(deviceType: DeviceType) {
        // Get manufacturers for the device type
        val manufacturers = CodeDatabase.getManufacturersForDeviceType(deviceType)

        // Create adapter with click listener
        adapter = ManufacturersAdapter(
            manufacturers,
            selectedManufacturers
        ) { manufacturer, isSelected ->
            onManufacturerSelected(manufacturer, isSelected)
        }

        recyclerView.adapter = adapter

        // Default: select first manufacturer if available
        if (selectedManufacturers.isEmpty() && manufacturers.isNotEmpty()) {
            // For TV default to TCL as that's what we know works
            val defaultManufacturer = if (deviceType == DeviceType.TV &&
                manufacturers.contains("TCL")) {
                "TCL"
            } else if (manufacturers.isNotEmpty()) {
                manufacturers.first()
            } else {
                null
            }

            defaultManufacturer?.let {
                selectedManufacturers.add(it)
                adapter.notifyDataSetChanged()
            }
        }
    }

    /**
     * Called when a manufacturer is selected or deselected
     */
    private fun onManufacturerSelected(manufacturer: String, isSelected: Boolean) {
        if (isSelected) {
            selectedManufacturers.add(manufacturer)
        } else {
            selectedManufacturers.remove(manufacturer)
        }
    }

    /**
     * Get the list of currently selected manufacturers
     */
    fun getSelectedManufacturers(): List<String> {
        return selectedManufacturers.toList()
    }

    /**
     * Get the currently selected device type
     */
    fun getSelectedDeviceType(): DeviceType {
        return currentDeviceType
    }

    /**
     * Get the currently selected device types as a list (for multi-select in future)
     */
    fun getSelectedDeviceTypes(): List<DeviceType> {
        return listOf(currentDeviceType)
    }
}