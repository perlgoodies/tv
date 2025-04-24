package com.geekiestgeek.universaltvoff.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.geekiestgeek.universaltvoff.MainActivity
import com.geekiestgeek.universaltvoff.R
import com.geekiestgeek.universaltvoff.ir.CodeDatabase
import com.geekiestgeek.universaltvoff.ir.CodeTransmitter
import com.geekiestgeek.universaltvoff.ir.IrBlasterManager
import com.geekiestgeek.universaltvoff.ui.manufacturers.ManufacturersFragment
import com.geekiestgeek.universaltvoff.ui.settings.SettingsFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeFragment : Fragment(), MainActivity.HomeFragmentListener {

    // UI components
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var deviceTypeText: TextView

    // Add a debug button for direct testing
    private lateinit var debugButton: Button

    // Managers
    private lateinit var irBlasterManager: IrBlasterManager
    private lateinit var codeTransmitter: CodeTransmitter

    // Transmission state
    private var isTransmitting = false
    private var transmissionJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI components
        statusText = view.findViewById(R.id.status_text)
        startButton = view.findViewById(R.id.start_button)
        stopButton = view.findViewById(R.id.stop_button)
        progressBar = view.findViewById(R.id.progress_bar)
        progressText = view.findViewById(R.id.progress_text)
        deviceTypeText = view.findViewById(R.id.device_type_text)

        // Add debug button if it exists in layout
        try {
            debugButton = view.findViewById(R.id.debug_button)
            debugButton.visibility = View.VISIBLE
            debugButton.setOnClickListener {
                testTclCode()
            }
        } catch (e: Exception) {
            // Debug button not in layout, that's fine
        }

        // Get the IR blaster manager from the activity
        irBlasterManager = (requireActivity() as MainActivity).getIrBlasterManager()

        // Initialize the code transmitter
        codeTransmitter = CodeTransmitter(requireContext(), irBlasterManager)

        // Set initial UI state
        updateUiState(transmitting = false)

        // Set up button listeners
        setupButtonListeners()

        // Update the status
        checkForUsbDevice()
    }

    private fun setupButtonListeners() {
        startButton.setOnClickListener {
            startTransmission()
        }

        stopButton.setOnClickListener {
            stopTransmission()
        }
    }

    private fun checkForUsbDevice() {
        // Check if the IR blaster is connected
        if (irBlasterManager.hasConnectedBlaster()) {
            onUsbDeviceDetected()
            if (irBlasterManager.connect()) {
                onUsbDeviceConnected()
            } else {
                onUsbDeviceConnectionFailed()
            }
        } else {
            onUsbDeviceDetectionFailed()
        }
    }

    /**
     * Direct test function for TCL 75Q750G
     */
    private fun testTclCode() {
        if (!irBlasterManager.hasConnectedBlaster()) {
            Toast.makeText(requireContext(), "No IR Blaster connected", Toast.LENGTH_SHORT).show()
            return
        }

        // Show testing status
        statusText.text = "Testing TCL 75Q750G codes..."
        progressText.visibility = View.VISIBLE
        progressText.text = "Sending test codes..."

        lifecycleScope.launch {
            try {
                // First try the direct TCL power code
                progressText.text = "Sending direct power code..."
                var success = irBlasterManager.sendTclPowerCode()

                if (!success) {
                    // If that failed, try the debug function with multiple codes
                    progressText.text = "Trying debug power codes..."
                    success = irBlasterManager.debugTclPowerCodes()
                }

                if (!success) {
                    // Try the specialized 75Q750G code with different timings
                    progressText.text = "Trying specialized timing patterns..."
                    success = irBlasterManager.send75Q750GPowerCode()
                }

                if (success) {
                    statusText.text = "Successfully sent TCL power code!"
                    progressText.text = "TV should have responded."
                } else {
                    statusText.text = "Failed to send any working TCL code"
                    progressText.text = "Please check IR blaster placement and try again"
                }
            } catch (e: Exception) {
                statusText.text = "Error: ${e.message}"
                progressText.text = "Exception during code test"
            }

            // Hide progress text after a delay
            kotlinx.coroutines.delay(5000)
            if (isAdded) {
                progressText.visibility = View.GONE
            }
        }
    }

    private fun startTransmission() {
        if (!irBlasterManager.hasConnectedBlaster()) {
            Toast.makeText(requireContext(), "No IR Blaster connected", Toast.LENGTH_SHORT).show()
            return
        }

        isTransmitting = true
        updateUiState(transmitting = true)

        // Get selected manufacturers
        val manufacturersFragment = parentFragmentManager.fragments
            .filterIsInstance<ManufacturersFragment>()
            .firstOrNull()

        val selectedManufacturers = manufacturersFragment?.getSelectedManufacturers() ?: listOf("TCL")

        // Get settings
        val settingsFragment = parentFragmentManager.fragments
            .filterIsInstance<SettingsFragment>()
            .firstOrNull()

        val useQuickMode = settingsFragment?.getQuickModeSetting() ?: true
        val powerAction = settingsFragment?.getPowerActionSetting() ?: CodeDatabase.PowerAction.TOGGLE

        // Select device types (default to TV only)
        val deviceTypes = listOf(CodeDatabase.DeviceType.TV)

        // Start the transmission
        transmissionJob = lifecycleScope.launch {
            codeTransmitter.startSendingCodes(
                deviceTypes = deviceTypes,
                selectedManufacturers = selectedManufacturers,
                useQuickMode = useQuickMode,
                powerAction = powerAction
            ).collect { progress ->
                // Update the UI with progress
                val progressPercent = progress.progressPercent
                progressBar.progress = progressPercent

                // Update device type text
                deviceTypeText.visibility = View.VISIBLE
                deviceTypeText.text = progress.currentDeviceType?.name ?: ""

                // Update progress text
                val statusMessage = if (progress.currentManufacturer.isNotEmpty()) {
                    "${progress.currentManufacturer} (${progress.current}/${progress.total})"
                } else if (progress.completed) {
                    "Transmission complete"
                } else {
                    "Preparing transmission..."
                }

                progressText.text = statusMessage
                progressText.visibility = View.VISIBLE

                // If the transmission is complete, update the UI
                if (progress.completed) {
                    stopTransmission()
                }
            }
        }
    }

    private fun stopTransmission() {
        isTransmitting = false
        codeTransmitter.stopTransmission()
        transmissionJob?.cancel()
        updateUiState(transmitting = false)
    }

    private fun updateUiState(transmitting: Boolean) {
        if (transmitting) {
            startButton.visibility = View.GONE
            stopButton.visibility = View.VISIBLE
            progressBar.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE
            deviceTypeText.visibility = View.VISIBLE
            progressBar.isIndeterminate = false
            progressBar.progress = 0

            // Hide debug button during transmission
            try {
                debugButton.visibility = View.GONE
            } catch (e: Exception) {
                // Debug button might not exist, that's fine
            }
        } else {
            startButton.visibility = View.VISIBLE
            stopButton.visibility = View.GONE
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
            deviceTypeText.visibility = View.GONE

            // Show debug button again
            try {
                debugButton.visibility = View.VISIBLE
            } catch (e: Exception) {
                // Debug button might not exist, that's fine
            }
        }
    }

    // HomeFragmentListener implementation
    override fun onUsbDeviceDetected() {
        statusText.text = "IR Blaster detected. Ready to use."
    }

    override fun onUsbDeviceConnected() {
        statusText.text = "IR Blaster connected and ready."
        startButton.isEnabled = true
    }

    override fun onUsbDeviceConnectionFailed() {
        statusText.text = "Failed to connect to IR Blaster."
        startButton.isEnabled = false
    }

    override fun onUsbDeviceDetectionFailed() {
        statusText.text = "No IR Blaster detected. Please connect device."
        startButton.isEnabled = false
    }

    override fun onUsbPermissionDenied() {
        statusText.text = "USB permission denied. Please try again."
        startButton.isEnabled = false
    }

    override fun onUsbDeviceDisconnected() {
        statusText.text = "IR Blaster disconnected."
        startButton.isEnabled = false
        if (isTransmitting) {
            stopTransmission()
        }
    }
}