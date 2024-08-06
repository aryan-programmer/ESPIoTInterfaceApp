package com.aryanstein.esp_iot_interface_app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

class DeviceControlActivity : AppCompatActivity() {
	companion object {
		private const val IP_ADDRESS_INTENT_INPUT = "IP_ADDRESS_INTENT_INPUT"
		private const val TAG = "DeviceControlActivity"
		private const val DEVICE_DATA_LOADER_WORKER_UNIQUE_TASK_NAME =
			"DeviceControlActivity__DeviceDataLoaderWorker__UNIQUE_TASK_NAME"

		fun start(ctx: Context, ipAddr: String) {
			val downloadIntent = Intent(ctx, DeviceControlActivity::class.java)
			downloadIntent.putExtra(IP_ADDRESS_INTENT_INPUT, ipAddr)
			ctx.startActivity(downloadIntent)
		}
	}

	private lateinit var ipAddr: String

	private lateinit var light1Sw: SwitchMaterial
	private lateinit var light2Sw: SwitchMaterial
	private lateinit var acSw: SwitchMaterial
	private lateinit var fanSw: SwitchMaterial
	private lateinit var motorSw: SwitchMaterial
	private lateinit var refreshBtn: Button
	private lateinit var analogInputDisp: TextView
	private lateinit var loadingSpinner: ProgressBar
	private lateinit var workManager: WorkManager
	private var _isHttpRequestInProgress = false
	private var isHttpRequestInProgress: Boolean
		get() = _isHttpRequestInProgress
		set(isInProgress) {
			_isHttpRequestInProgress = isInProgress
			loadingSpinner.visibility = (if(isInProgress) View.VISIBLE else View.GONE)
			refreshBtn.isEnabled = !isInProgress
			light1Sw.isEnabled = !isInProgress
			light2Sw.isEnabled = !isInProgress
			acSw.isEnabled = !isInProgress
			fanSw.isEnabled = !isInProgress
			motorSw.isEnabled = !isInProgress
		}


	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContentView(R.layout.activity_device_control)
		setSupportActionBar(findViewById(R.id.adc__toolbar))
		supportActionBar?.apply {
			setDisplayShowHomeEnabled(true)
			setDisplayHomeAsUpEnabled(true)
		}
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
			insets
		}

		ipAddr = intent.getStringExtra(IP_ADDRESS_INTENT_INPUT)!!

		light1Sw = findViewById(R.id.adc__light1_sw)
		light2Sw = findViewById(R.id.adc__light2_sw)
		acSw = findViewById(R.id.adc__ac_sw)
		fanSw = findViewById(R.id.adc__fan_sw)
		motorSw = findViewById(R.id.adc__motor_sw)
		refreshBtn = findViewById(R.id.adc__refresh_btn)
		analogInputDisp = findViewById(R.id.adc__analog_device_input_disp)
		loadingSpinner = findViewById(R.id.adc__loading_spinner)
		workManager = WorkManager.getInstance(applicationContext)

		DeviceDataLoaderWorker.attachStateListener(
			DEVICE_DATA_LOADER_WORKER_UNIQUE_TASK_NAME,
			workManager,
			this,
			object : DeviceDataLoaderWorker.StateListener {
				override fun onSuccess(data: String) {
					super.onSuccess(data)
					onDeviceStatusFetch(data)
				}

				override fun whenRunning() {
					super.whenRunning()
					isHttpRequestInProgress = true
				}

				override fun onFailed() {
					super.onFailed()
					Snackbar.make(refreshBtn, "Failed to send request", Snackbar.LENGTH_LONG).show()
				}
			})

		setDeviceSwitchListener(light1Sw, URL_LIGHT1_PATH)
		setDeviceSwitchListener(light2Sw, URL_LIGHT2_PATH)
		setDeviceSwitchListener(acSw, URL_AC_PATH)
		setDeviceSwitchListener(fanSw, URL_FAN_PATH)
		setDeviceSwitchListener(motorSw, URL_MOTOR_PATH)

		isHttpRequestInProgress = false
		analogInputDisp.text = getString(R.string.analog_device_input_display_text, "Unknown")
		refreshBtn.setOnClickListener { scheduleLoadData(delayed = false) }

		scheduleLoadData(delayed = false)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when(item.itemId) {
			android.R.id.home -> {
				finish()
				return true
			}
			else              -> return super.onOptionsItemSelected(item)
		}
	}

	private fun onDeviceStatusFetch(v: String) {
		/*{
			"Light1": "off",
			"Light2": "off",
			"Fan": "on",
			"AirConditioner": "on",
			"ElectricMotor": "on",
			"AnalogInput": 348
		}*/
		val j = JSONObject(v)
		light1Sw.isChecked = j.getString("Light1") == "on"
		light2Sw.isChecked = j.getString("Light2") == "on"
		fanSw.isChecked = j.getString("Fan") == "on"
		acSw.isChecked = j.getString("AirConditioner") == "on"
		motorSw.isChecked = j.getString("ElectricMotor") == "on"
		analogInputDisp.text = getString(
			R.string.analog_device_input_display_text,
			j.getInt("AnalogInput").toString()
		)

		isHttpRequestInProgress = false

		scheduleLoadData(delayed = true)
	}

	private fun scheduleLoadData(delayed: Boolean) {
		DeviceDataLoaderWorker.scheduleLoadDataOnWorker(
			delayed,
			DEVICE_DATA_LOADER_WORKER_UNIQUE_TASK_NAME,
			ipAddr,
			workManager
		)
	}

	private fun setDeviceSwitchListener(sw: SwitchMaterial, subPath: String) {
		sw.setOnCheckedChangeListener { _, isChecked ->
			if(isHttpRequestInProgress) return@setOnCheckedChangeListener
			isHttpRequestInProgress = true
			val queryVal = if(isChecked) URL_ON_QUERY_VAL else URL_OFF_QUERY_VAL
			val uri = URI.create(ipAddr).resolve("$subPath?$URL_SET_QUERY_KEY=$queryVal")
			lifecycleScope.launch {
				var responseCode = 0
				withContext(Dispatchers.IO) {
					val url = uri.toURL()
					val urlConnection = url.openConnection() as HttpURLConnection
					urlConnection.readTimeout = TIMEOUT_MILLIS
					urlConnection.connectTimeout = TIMEOUT_MILLIS
					try {
						urlConnection.connect()
						responseCode = urlConnection.responseCode
					} catch(e: Exception) {
						Log.e(TAG, "setDeviceSwitchListener: Failed to send request $uri", e)
					} finally {
						urlConnection.disconnect()
					}
				}

				withContext(Dispatchers.Main) {
					isHttpRequestInProgress = false
					if(responseCode !in 200..299) {
						Snackbar.make(sw, "Failed to send request", Snackbar.LENGTH_LONG).show()
						scheduleLoadData(false)
					}
				}
			}
		}
	}
}