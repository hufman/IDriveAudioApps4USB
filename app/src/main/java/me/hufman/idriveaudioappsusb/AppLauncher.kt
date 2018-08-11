package me.hufman.idriveaudioappsusb

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import me.hufman.idriveconnectionkit.android.CarAPIClient
import me.hufman.idriveconnectionkit.android.CarAPIDiscovery
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService


class AppLauncher: Service() {
	companion object {
		val START_COMMAND = "me.hufman.idriveaudioappsusb.AppLauncher.START"
	}

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (intent?.action == START_COMMAND) {
			registerCarListener(this)
		}
		return Service.START_STICKY
	}

	override fun onDestroy() {
		CarAPIDiscovery.cancelDiscovery(this)
	}

	val TAG = "AppLauncher"

	fun registerCarListener(context: Context) {
		// IDriveConnectionListener listens all the time, but we should set a callback
		IDriveConnectionListener.callback = Runnable {
			combinedCallback()
		}
		// Also try to connect to the SecurityService
		SecurityService.subscribe(Runnable {
			combinedCallback()
		})
		SecurityService.connect(context)

		// also listen for all the car apps
		CarAPIDiscovery.discoverApps(context, AppDiscoveryCallback())
	}

	inner class AppDiscoveryCallback:CarAPIDiscovery.DiscoveryCallback {
		override fun discovered(app: CarAPIClient) {
			if (!isCarReady()) return   // we'll try again when the car connects
			// tell this newly-discovered app to start
			if (app.category == "Radio" || app.category == "Multimedia") {
				startApp(app)
			}
		}
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

	fun isCarReady(): Boolean {
		return IDriveConnectionListener.isConnected && SecurityService.isConnected()
	}

	/* Check preconditions and start all the apps */
	fun combinedCallback() {
		if (isCarReady()) startAllApps()
	}

	fun startAllApps() {
		CarAPIDiscovery.discoveredApps.values.filterIsInstance<CarAPIClient>().filter { it.category == "Radio" || it.category == "Multimedia" }.forEach {
			startApp(it)
		}
	}

	fun startApp(app: CarAPIClient) {
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
}