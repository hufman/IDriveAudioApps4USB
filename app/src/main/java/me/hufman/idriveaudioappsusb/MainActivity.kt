package me.hufman.idriveaudioappsusb

import android.app.Activity
import android.content.ComponentName
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

class MainActivity : Activity() {

	private val TAG = "IDriveAudioApp"

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		// prepare security service
		SecurityService.subscribe(Runnable {
			redrawCarStatus()
			combinedCallback()
		})
		SecurityService.connect(this)

		// wait for car connection
		IDriveConnectionListener.callback = Runnable {
			redrawCarStatus()
			combinedCallback()
		}
		IDriveConnectionListener.callback?.run()

		// update the discovered list of CarAPI apps
		val discoveryCallback = object: CarAPIDiscovery.DiscoveryCallback {
			override fun discovered(app: CarAPIClient) {
				redraw()
				combinedCallback()
			}
			fun redraw() {
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
		discoveryCallback.redraw()
		CarAPIDiscovery.discoverApps(this, discoveryCallback)
	}

	override fun onDestroy() {
		super.onDestroy()
		CarAPIDiscovery.cancelDiscovery(this)
	}

	fun redrawCarStatus() {
		if (IDriveConnectionListener.isConnected) {
			findViewById<TextView>(R.id.textView).text = "Connected to a ${IDriveConnectionListener.brand} via ${SecurityService.activeSecurityConnections.keys.firstOrNull() ?: "unknown security service"}"
		} else {
			findViewById<TextView>(R.id.textView).text = "Not connected to a car"
		}
	}

	/**
	 * Check whether the connection requirements are met, and then connect to car
	 */
	fun combinedCallback() {

		if (IDriveConnectionListener.isConnected && SecurityService.isConnected()) {
		//if (SecurityService.isConnected()) {
			StartAudioApps().execute()
		}
	}

	fun _findPackage(intent:Intent): ResolveInfo? {
		val listeners = this.applicationContext.packageManager.queryBroadcastReceivers(intent, GET_RESOLVED_FILTER)
		Log.d(TAG, "Found ${listeners.size} listeners for intent ${intent.action}")
		listeners.forEach {
			Log.d(TAG, "  - ${it.activityInfo.packageName}:${it.activityInfo.name}")
		}

		if (listeners?.size != 1) return null
		return listeners[0]
	}

	fun findConnectedApp(): String? {
		if (IDriveConnectionListener.brand == "BMW") {
			val installedBMW = SecurityService.activeSecurityConnections.keys.filter { it.startsWith("BMW") }.firstOrNull()
			if (installedBMW == null) {
				Log.e(TAG, "Failed to find Connected app to match the announced Brand ${IDriveConnectionListener.brand}")
				return null
			}
			return SecurityService.knownSecurityServices[installedBMW]?.split("\\.(?=[^.]*$)".toRegex())?.first()
		}
		if (IDriveConnectionListener.brand == "MINI") {
			val installedMini = SecurityService.activeSecurityConnections.keys.filter { it.startsWith("Mini") }.firstOrNull()
			if (installedMini == null) {
				Log.e(TAG, "Failed to find Connected app to match the announced Brand ${IDriveConnectionListener.brand}")
				return null
			}
			return SecurityService.knownSecurityServices[installedMini]?.split("\\.(?=[^.]*\$)".toRegex())?.first()
		}
		Log.e(TAG, "Unexpected car brand ${IDriveConnectionListener.brand}")
		return null
	}

	fun _startAllApps() {
		CarAPIDiscovery.discoveredApps.values.filterIsInstance<CarAPIClient>().filter { it.category == "Radio" || it.category == "Multimedia" }.forEach {
			_startApp(it)
		}
	}
	fun _startApp(app: CarAPIClient) {
		val connectedApp = findConnectedApp()
		if (connectedApp == null) {
			Log.e(TAG, "Unable to start CarAPI app ${app.title} due to not knowing which Connected app to use")
			return
		}
		Log.i(TAG, "Using Connected app ${connectedApp}")
		val intent = Intent("${connectedApp}.ACTION_CAR_APPLICATION_LAUNCHER")
		intent.setPackage(connectedApp)
		intent.putExtra("EXTRA_COMMAND", "start")
		intent.putExtra("EXTRA_APPLICATION_ID", app.id)
		intent.putExtra("EXTRA_APPLICATION_PKG_NAME", connectedApp)   // the Connected pkg name
		intent.putExtra("EXTRA_APPLICATION_VERSION", app.version)
		if (app.rhmiVersion != null)
			intent.putExtra("EXTRA_RHMI_VERSION", app.rhmiVersion)
		intent.putExtra("EXTRA_ACCESSORY_BRAND", IDriveConnectionListener.brand?.toLowerCase())
		intent.putExtra("address", IDriveConnectionListener.host)
		intent.putExtra("port", IDriveConnectionListener.port)
		intent.putExtra("instance_id", IDriveConnectionListener.instanceId)
		intent.putExtra("security_service", "${connectedApp}.SECURITY_SERVICE")
		Log.i(TAG, "Starting app " + app.title + " (" + app.id + ")")
		startService(intent)

	}

	inner class StartAudioApps: AsyncTask<Unit, Void, Unit>() {
		override fun doInBackground(vararg params: Unit?) {
			Log.i(TAG, "Preparing to start the following apps:")
			CarAPIDiscovery.discoveredApps.values.filterIsInstance<CarAPIClient>().filter { it.category == "Radio" || it.category == "Multimedia" }.forEach {
				Log.i(TAG, " - " + it.title + " (" + it.id + ")")
			}
			_startAllApps()
		}

	}

}