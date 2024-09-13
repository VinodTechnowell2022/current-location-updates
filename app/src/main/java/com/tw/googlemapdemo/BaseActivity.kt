package com.tw.googlemapdemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.DexterError
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.Q)
open class BaseActivity : AppCompatActivity() {

    private val TAG: String = "BaseActivity"
    // private lateinit var sessionManager: SessionManager
    var strAddress:String=""
    var longitude:String? = ""
    var latitude:String? = ""

    // location last updated time
    private var mLastUpdateTime: String? = null

    // location updates interval - 10sec
    private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000

    // fastest updates interval - 10 sec
    // location updates will be received if another app is requesting the locations
    // than your app can handle
    private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000

    private val REQUEST_CHECK_SETTINGS = 100

    // bunch of location related apis
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private lateinit var mSettingsClient: SettingsClient
    private lateinit var mLocationRequest: LocationRequest
    private lateinit var mLocationSettingsRequest: LocationSettingsRequest
    private lateinit var mLocationCallback: LocationCallback
    private var mCurrentLocation: Location? = null

    // boolean flag to toggle the ui
    var mRequestingLocationUpdates: Boolean=false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        init()
        startLocationButtonClick()
    }


    private fun startLocationButtonClick() {
        // Requesting ACCESS_FINE_LOCATION using Dexter library
        Dexter.withContext(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(multiplePermissionsReport: MultiplePermissionsReport) {
                    if (multiplePermissionsReport.areAllPermissionsGranted()) {
                        Log.e(TAG, "permissions Granted")

                        mRequestingLocationUpdates = true
                        startLocationUpdates()
                    }
                    if (multiplePermissionsReport.isAnyPermissionPermanentlyDenied) {
                        Log.e(TAG, "permissions Denied")
                        showSettingsDialogAll()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(list: List<PermissionRequest>, permissionToken: PermissionToken) {
                    permissionToken.continuePermissionRequest()
                }
            }).withErrorListener { dexterError: DexterError ->
                Log.e(TAG, "permissions" + "dexterError :" + dexterError.name )
            }
            .onSameThread()
            .check()
    }

    fun showSettingsDialogAll() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Need Permissions")
        builder.setMessage("These permissions are mandatory for the application. Please allow access.")
        builder.setPositiveButton("GOTO SETTINGS") { dialog, _ ->
            dialog.cancel()
            openSettings()
        }
        builder.show()
    }

    private fun openSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts("package", "com.technowell.bmmsnew", null)
        intent.data = uri
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()

        // Resuming location updates depending on button state and allowed permissions
        if (mRequestingLocationUpdates && checkPermissions()) {
            startLocationUpdates()
        }
    }

    private fun checkPermissions(): Boolean {
        val permissionState = ActivityCompat.checkSelfPermission( this, Manifest.permission.ACCESS_FINE_LOCATION )
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    private fun init() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this@BaseActivity)
        mSettingsClient = LocationServices.getSettingsClient(this@BaseActivity)
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                // location is received
                locationResult.lastLocation
                mCurrentLocation = locationResult.lastLocation
                mLastUpdateTime = DateFormat.getTimeInstance().format(Date())
                updateLocationUI()
            }
        }
        mRequestingLocationUpdates = false
        mLocationRequest = LocationRequest.create()
            .setInterval(UPDATE_INTERVAL_IN_MILLISECONDS)
            .setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxWaitTime(100)
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest)
        mLocationSettingsRequest = builder.build()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
            .addOnSuccessListener(this) {
                Log.i(TAG, "All location settings are satisfied.")
                   Toast.makeText(applicationContext, "Started location updates!", Toast.LENGTH_SHORT).show();
                mFusedLocationClient!!.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.myLooper()!!)
                updateLocationUI()
            }
            .addOnFailureListener(this) { e ->
                when ((e as ApiException).statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " + "location settings ")
                        try {
                            // Show the dialog by calling startResolutionForResult(), and check the result in onActivityResult().
                            val rae = e as ResolvableApiException
                            rae.startResolutionForResult(this@BaseActivity, REQUEST_CHECK_SETTINGS)
                        } catch (sie: IntentSender.SendIntentException) {
                            Log.i(TAG, "PendingIntent unable to execute request.")
                        }
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        val errorMessage = "Location settings are inadequate, and cannot be " + "fixed here. Fix in Settings."
                        Log.e(TAG, errorMessage)
                    }
                }
            }
    }

    fun stopLocationUpdates() {
        mRequestingLocationUpdates = false
        // Removing location updates
        mFusedLocationClient?.removeLocationUpdates(mLocationCallback)?.addOnCompleteListener(this) { task -> }
    }

    fun updateLocationUI() {
        if (mCurrentLocation != null) {
            latitude = mCurrentLocation!!.latitude.toString()
            longitude = mCurrentLocation!!.longitude.toString()
            Log.e(TAG, "latitude -- "+ latitude!!)
            Log.e(TAG, "longitude --"+ longitude!!)

            //Save current location in your app session (Shared Preference, Room DB, etc)

            convertLocationToString(mCurrentLocation!!)
        } else {
            Log.e("latitude->", latitude + "")
            Log.e("longitude->", longitude + "")
        }
    }

    override fun onPause() {
        super.onPause()
        if (mRequestingLocationUpdates) {
            // pausing location updates
            stopLocationUpdates()
        }
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        //super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CHECK_SETTINGS -> when (resultCode) {
                RESULT_OK -> Log.e(TAG, "User agreed to make required location settings changes.")
                RESULT_CANCELED -> {
                    Log.e(TAG, "User chose not to make required location settings changes.")
                    mRequestingLocationUpdates = false
                }
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun convertLocationToString(location: Location): String {

        val gcd = Geocoder(baseContext, Locale.getDefault())
        val addresses: List<Address>
        try {
            addresses = gcd.getFromLocation(location.latitude, location.longitude, 1)!!
            if (addresses!=null) {
                val address: String =
                    addresses[0].getAddressLine(0) // If any additional address line present than only, check with max available address lines by getMaxAddressLineIndex()
                val locality: String = addresses[0].locality
                //   val subLocality: String = addresses[0].subLocality
                val state: String = addresses[0].adminArea
                //  val country: String = addresses[0].countryName
                val postalCode: String = addresses[0].postalCode
                val knownName: String = addresses[0].featureName

                strAddress= "$address, $knownName, $locality,  $state, $postalCode"
//                sessionManager.setCurrentAddress(strAddress)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return strAddress
    }

}