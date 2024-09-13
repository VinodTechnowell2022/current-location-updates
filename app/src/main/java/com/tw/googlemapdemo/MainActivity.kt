package com.tw.googlemapdemo

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.tw.googlemapdemo.databinding.ActivityMainBinding

@RequiresApi(Build.VERSION_CODES.Q)
class MainActivity : BaseActivity() {

    lateinit var mbinding: ActivityMainBinding
    private val TAG = this.javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mbinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mbinding.root)


        mbinding.btnRefresh.setOnClickListener {
            updateLocation()
        }
    }

    private fun updateLocation() {
        mbinding.tvLocation.text = latitude +", " + longitude +"\n"+strAddress
    }

}