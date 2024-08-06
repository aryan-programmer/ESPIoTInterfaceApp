package com.aryanstein.esp_iot_interface_app

import android.os.Bundle
import android.webkit.URLUtil
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContentView(R.layout.activity_main)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
			insets
		}
		val goBtn: Button = findViewById(R.id.am__go_button)
		val ipAddrInput: EditText = findViewById(R.id.am__ip_addr_input)

		goBtn.setOnClickListener {
			var ipAddr = ipAddrInput.text.toString()
			if(ipAddr.isBlank()) {
				Snackbar.make(goBtn, "Enter a valid IP address or URL", Snackbar.LENGTH_LONG).show()
				return@setOnClickListener
			}
			if(!URLUtil.isValidUrl(ipAddr)) {
				ipAddr = "http://$ipAddr"
				if(!URLUtil.isValidUrl(ipAddr)) {
					Snackbar
						.make(goBtn, "Enter a valid IP address or URL", Snackbar.LENGTH_LONG)
						.show()
					return@setOnClickListener
				}
			}
			DeviceControlActivity.start(this, ipAddr)
		}
	}
}