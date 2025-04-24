package com.geekiestgeek.universaltvoff.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.geekiestgeek.universaltvoff.R
import com.geekiestgeek.universaltvoff.ir.CodeDatabase

/**
 * Fragment for app settings
 */
class SettingsFragment : Fragment() {

    // Settings values
    private var useQuickMode = true
    private var delayBetweenCodes = 500L // ms
    private var powerAction = CodeDatabase.PowerAction.TOGGLE

    // UI elements
    private lateinit var quickModeSwitch: Switch
    private lateinit var delayValueText: TextView
    private lateinit var decreaseDelayButton: Button
    private lateinit var increaseDelayButton: Button
    private lateinit var powerActionRadioGroup: RadioGroup
    private lateinit var togglePowerRadio: RadioButton
    private lateinit var onPowerRadio: RadioButton
    private lateinit var offPowerRadio: RadioButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI components
        quickModeSwitch = view.findViewById(R.id.quick_mode_switch)
        delayValueText = view.findViewById(R.id.delay_value_text)
        decreaseDelayButton = view.findViewById(R.id.decrease_delay_button)
        increaseDelayButton = view.findViewById(R.id.increase_delay_button)

        // Power action radio buttons
        powerActionRadioGroup = view.findViewById(R.id.power_action_radio_group)
        togglePowerRadio = view.findViewById(R.id.toggle_power_radio)
        onPowerRadio = view.findViewById(R.id.on_power_radio)
        offPowerRadio = view.findViewById(R.id.off_power_radio)

        // Set initial values
        quickModeSwitch.isChecked = useQuickMode
        updateDelayText()

        // Set initial power action
        when (powerAction) {
            CodeDatabase.PowerAction.TOGGLE -> togglePowerRadio.isChecked = true
            CodeDatabase.PowerAction.ON -> onPowerRadio.isChecked = true
            CodeDatabase.PowerAction.OFF -> offPowerRadio.isChecked = true
        }

        // Set up listeners
        setupListeners()
    }

    private fun setupListeners() {
        quickModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            useQuickMode = isChecked
        }

        decreaseDelayButton.setOnClickListener {
            if (delayBetweenCodes > 200) {
                delayBetweenCodes -= 100
                updateDelayText()
            }
        }

        increaseDelayButton.setOnClickListener {
            if (delayBetweenCodes < 2000) {
                delayBetweenCodes += 100
                updateDelayText()
            }
        }

        // Power action radio group listener
        powerActionRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            powerAction = when (checkedId) {
                R.id.toggle_power_radio -> CodeDatabase.PowerAction.TOGGLE
                R.id.on_power_radio -> CodeDatabase.PowerAction.ON
                R.id.off_power_radio -> CodeDatabase.PowerAction.OFF
                else -> CodeDatabase.PowerAction.TOGGLE
            }
        }
    }

    private fun updateDelayText() {
        delayValueText.text = String.format("%d ms", delayBetweenCodes)
    }

    /**
     * Get the current quick mode setting
     */
    fun getQuickModeSetting(): Boolean {
        return useQuickMode
    }

    /**
     * Get the current delay between codes
     */
    fun getDelayBetweenCodes(): Long {
        return delayBetweenCodes
    }

    /**
     * Get the current power action setting
     */
    fun getPowerActionSetting(): CodeDatabase.PowerAction {
        return powerAction
    }
}