package com.kircherelectronics.gyroscopeexplorer.activity

import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import com.kircherelectronics.fsensor.sensor.FSensor
import com.kircherelectronics.fsensor.filter.averaging.MeanFilter
import com.kircherelectronics.fsensor.observer.SensorSubject.SensorObserver
import android.os.Bundle
import com.kircherelectronics.gyroscopeexplorer.R
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import com.kircherelectronics.fsensor.sensor.gyroscope.GyroscopeSensor
import com.kircherelectronics.fsensor.sensor.gyroscope.ComplementaryGyroscopeSensor
import com.kircherelectronics.fsensor.sensor.gyroscope.KalmanGyroscopeSensor
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.*
import android.widget.FrameLayout
import androidx.appcompat.widget.Toolbar
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import com.kircherelectronics.gyroscopeexplorer.databinding.ActivityGyroscopeBinding
import java.util.*
import kotlin.math.abs

open class GyroscopeActivity : AppCompatActivity() {
    private var meanFilterEnabled = false
    private var fusedOrientation = FloatArray(3)

    // The gauge views. Note that these are views and UI hogs since they run in
    // the UI thread, not ideal, but easy to use.
    private lateinit var binding: ActivityGyroscopeBinding

    // Handler for the UI plots so everything plots smoothly
    protected lateinit var uiHandler: Handler
    private lateinit var uiRunnable: Runnable
    private var tvXAxis: TextView? = null
    private var tvYAxis: TextView? = null
    private var tvZAxis: TextView? = null
    private var fSensor: FSensor? = null
    private var meanFilter: MeanFilter? = null
    private var helpDialog: Dialog? = null
    private val sensorObserver = SensorObserver { values -> updateValues(values) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGyroscopeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        meanFilter = MeanFilter()
        uiHandler = Handler(Looper.getMainLooper())
        uiRunnable = object : Runnable {
            override fun run() {
                uiHandler.postDelayed(this, 100)
                updateText()
                updateGauges()
            }
        }
        initUI()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.gyroscope, menu)
        return true
    }

    @SuppressLint("NonConstantResourceId")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_reset -> fSensor!!.reset()
            R.id.action_config -> {
                val intent = Intent()
                intent.setClass(this, ConfigActivity::class.java)
                startActivity(intent)
            }
            R.id.action_help -> showHelpDialog()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    public override fun onResume() {
        super.onResume()
        when (readPrefs()) {
            Mode.GYROSCOPE_ONLY -> fSensor = GyroscopeSensor(this)
            Mode.COMPLIMENTARY_FILTER -> {
                fSensor = ComplementaryGyroscopeSensor(this)
                (fSensor as ComplementaryGyroscopeSensor).setFSensorComplimentaryTimeConstant(prefImuOCfQuaternionCoeff)
            }
            Mode.KALMAN_FILTER -> fSensor = KalmanGyroscopeSensor(this)
        }
        fSensor!!.register(sensorObserver)
        fSensor!!.start()
        uiHandler.post(uiRunnable)
    }

    public override fun onPause() {
        if (helpDialog != null && helpDialog!!.isShowing) helpDialog!!.dismiss()
        fSensor!!.unregister(sensorObserver)
        fSensor!!.stop()
        uiHandler.removeCallbacksAndMessages(null)
        super.onPause()
    }


    private fun initUI() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (supportActionBar != null) supportActionBar!!.setDisplayShowHomeEnabled(true)

        // Initialize the calibrated text views
        tvXAxis = findViewById(R.id.value_x_axis_calibrated)
        tvYAxis = findViewById(R.id.value_y_axis_calibrated)
        tvZAxis = findViewById(R.id.value_z_axis_calibrated)

        // Initialize the calibrated gauges views
    }

    private fun readPrefs(): Mode {
        meanFilterEnabled = prefMeanFilterEnabled
        val complimentaryFilterEnabled = prefComplimentaryEnabled
        val kalmanFilterEnabled = prefKalmanEnabled
        if (meanFilterEnabled) meanFilter!!.setTimeConstant(prefMeanFilterTimeConstant)
        val mode: Mode = if (!complimentaryFilterEnabled && !kalmanFilterEnabled) {
            Mode.GYROSCOPE_ONLY
        } else if (complimentaryFilterEnabled) {
            Mode.COMPLIMENTARY_FILTER
        } else {
            Mode.KALMAN_FILTER
        }
        return mode
    }

    private fun showHelpDialog() {
        helpDialog = Dialog(this)
        helpDialog!!.setCancelable(true)
        helpDialog!!.setCanceledOnTouchOutside(true)
        helpDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.layout_help_home, findViewById<View>(android.R.id.content) as ViewGroup, false)
        helpDialog!!.setContentView(view)
        helpDialog!!.show()
    }

    private fun updateText() {
        tvXAxis!!.text = String.format(Locale.getDefault(),"%.1f", Math.toDegrees(fusedOrientation[1].toDouble()))
//        tvYAxis!!.text = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(fusedOrientation[2].toDouble()) + 360) % 360)
        tvZAxis!!.text = String.format(Locale.getDefault(),"%.1f", Math.toDegrees(fusedOrientation[0].toDouble()))
        val param = FrameLayout.LayoutParams(20,20)
        param.leftMargin = abs(Math.toDegrees(fusedOrientation[0].toDouble()).toInt())*10
        param.topMargin = abs(Math.toDegrees(fusedOrientation[1].toDouble()).toInt())*10
        binding.pointer.layoutParams = param
    }

    private fun updateGauges() {
        binding.gaugeTiltCalibrated.updateRotation(fusedOrientation[1], fusedOrientation[2])

    }

    private fun updateValues(values: FloatArray) {
        fusedOrientation = values
        if (meanFilterEnabled) {
            fusedOrientation = meanFilter!!.filter(fusedOrientation)
        }
    }

    private enum class Mode {
        GYROSCOPE_ONLY, COMPLIMENTARY_FILTER, KALMAN_FILTER
    }

    private val prefMeanFilterEnabled: Boolean
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            return prefs.getBoolean(ConfigActivity.MEAN_FILTER_SMOOTHING_ENABLED_KEY, false)
        }
    private val prefMeanFilterTimeConstant: Float
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            return prefs.getString(ConfigActivity.MEAN_FILTER_SMOOTHING_TIME_CONSTANT_KEY, "0.5")!!.toFloat()
        }
    private val prefKalmanEnabled: Boolean
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            return prefs.getBoolean(ConfigActivity.KALMAN_QUATERNION_ENABLED_KEY, false)
        }
    private val prefComplimentaryEnabled: Boolean
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            return prefs.getBoolean(ConfigActivity.COMPLIMENTARY_QUATERNION_ENABLED_KEY, false)
        }
    private val prefImuOCfQuaternionCoeff: Float
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            return prefs.getString(ConfigActivity.COMPLIMENTARY_QUATERNION_COEFF_KEY, "0.5")!!.toFloat()
        }

}