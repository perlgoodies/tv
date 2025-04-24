package com.geekiestgeek.universaltvoff

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.geekiestgeek.universaltvoff.ir.IrBlasterManager

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var navController: NavController
    private lateinit var irBlasterManager: IrBlasterManager

    // Listener interface for Home Fragment
    interface HomeFragmentListener {
        fun onUsbDeviceDetected()
        fun onUsbDeviceConnected()
        fun onUsbDeviceConnectionFailed()
        fun onUsbDeviceDetectionFailed()
        fun onUsbPermissionDenied()
        fun onUsbDeviceDisconnected()
    }

    private var homeFragmentListener: HomeFragmentListener? = null

    // USB permission broadcast receiver
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == IrBlasterManager.ACTION_USB_PERMISSION) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                if (permissionGranted) {
                    device?.let {
                        // Permission granted, connect to the device
                        if (irBlasterManager.connect()) {
                            homeFragmentListener?.onUsbDeviceConnected()
                        } else {
                            homeFragmentListener?.onUsbDeviceConnectionFailed()
                        }
                    }
                } else {
                    // Permission denied
                    homeFragmentListener?.onUsbPermissionDenied()
                }
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                // USB device detached
                irBlasterManager.disconnect()
                homeFragmentListener?.onUsbDeviceDisconnected()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize IR blaster manager
        irBlasterManager = IrBlasterManager(this)

        // Set up navigation
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)

        // Setup AppBar with navigation controller
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.homeFragment, R.id.manufacturersFragment, R.id.settingsFragment)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Register USB broadcast receiver with the correct flags for Android 13+
        val filter = IntentFilter().apply {
            addAction(IrBlasterManager.ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        // Handle Android 13+ broadcast receiver registration security requirements
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }

        // Set up a listener for when the home fragment has been created
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.homeFragment) {
                // This will be called when the HomeFragment becomes the current destination
                // We'll wait a moment to ensure the fragment is fully initialized
                bottomNav.postDelayed({
                    checkForUsbDevice()
                }, 100)
            }
        }
    }

    /**
     * Checks for USB device and notifies the HomeFragment if it's registered
     */
    private fun checkForUsbDevice() {
        Log.d(TAG, "Checking for USB device")
        if (homeFragmentListener == null) {
            Log.d(TAG, "HomeFragmentListener not yet registered")
            return
        }

        // Check if the IR blaster is connected
        if (irBlasterManager.hasConnectedBlaster()) {
            homeFragmentListener?.onUsbDeviceDetected()
            if (irBlasterManager.connect()) {
                homeFragmentListener?.onUsbDeviceConnected()
            } else {
                homeFragmentListener?.onUsbDeviceConnectionFailed()
            }
        } else {
            homeFragmentListener?.onUsbDeviceDetectionFailed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        irBlasterManager.disconnect()
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            // Receiver not registered, that's fine
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_about -> {
                navController.navigate(R.id.aboutFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    // Method to get the IR blaster manager from fragments
    fun getIrBlasterManager(): IrBlasterManager {
        return irBlasterManager
    }

    // Method to set the home fragment listener - now with nullable parameter
    fun setHomeFragmentListener(listener: HomeFragmentListener?) {
        homeFragmentListener = listener

        // Only check for USB device if we're setting a non-null listener
        if (listener != null) {
            // If listener is being set after the activity is already created,
            // check for USB device immediately
            checkForUsbDevice()
        }
    }
}