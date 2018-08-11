package me.hufman.idriveaudioappsusb

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager.*
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import me.hufman.idriveconnectionkit.android.CarAPIClient
import me.hufman.idriveconnectionkit.android.CarAPIDiscovery
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService
import java.util.*
import kotlin.concurrent.timerTask

class MainActivity : Activity() {

	private val TAG = "IDriveAudioApp"
	private val refreshTimer = Timer()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		// refresh the screen
		refreshTimer.schedule(timerTask {
			this@MainActivity.runOnUiThread { redraw() }
		}, 0, 1000)

		// start discovering apps
		startService(Intent(this, AppLauncher::class.java).setAction(AppLauncher.START_COMMAND))
	}

	fun redraw() {
		if (IDriveConnectionListener.isConnected) {
			findViewById<TextView>(R.id.textView).text = "Connected to a ${IDriveConnectionListener.brand} via ${SecurityService.activeSecurityConnections.keys.firstOrNull() ?: "unknown security service"}"
		} else {
			findViewById<TextView>(R.id.textView).text = "Not connected to a car"
		}

		// discovered apps
		val parent = findViewById<LinearLayout>(R.id.carAppList)
		parent.removeAllViews()
		CarAPIDiscovery.discoveredApps.values.filterIsInstance<CarAPIClient>().forEach {
			val bitmap = BitmapFactory.decodeByteArray(it.appIcon, 0, it.appIcon?.size ?: 0)
			val row = LinearLayout(parent.context)
			val imageView = ImageView(parent.context)
			imageView.setImageBitmap(bitmap)
			val labelView = TextView(parent.context)
			labelView.text = it.title + " (${it.id})"
			row.addView(imageView)
			row.addView(labelView)
			parent.addView(row)
		}
	}

}