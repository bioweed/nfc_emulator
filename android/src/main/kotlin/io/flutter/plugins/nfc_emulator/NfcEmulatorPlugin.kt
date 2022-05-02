package io.flutter.plugins.nfc_emulator

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler

/** NfcEmulatorPlugin  */
class NfcEmulatorPlugin : FlutterPlugin, ActivityAware, MethodCallHandler {
    private var activity: Activity? = null

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel : MethodChannel

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "nfc_emulator")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (call.method == "getPlatformVersion") {
            result.success("Android ${android.os.Build.VERSION.RELEASE}")
        } else if (call.method == "getNfcStatus") {
            val nfcStatus = nfcStatus
            result.success(nfcStatus)
        } else if (call.method == "startNfcEmulator") {
            val data = call.argument<String>("data")!!
            startNfcEmulator(data)
            result.success(null)
        } else if (call.method == "stopNfcEmulator") {
            stopNfcEmulator()
            result.success(null)
        } else {
            result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(activityPluginBinding: ActivityPluginBinding) {
        activity = activityPluginBinding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(activityPluginBinding: ActivityPluginBinding) {
        activity = activityPluginBinding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    private val nfcStatus: Int
        get() {
            if (activity == null) {
                return -1
            }
            val nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
                ?: return 1 // This device does not support NFC
            return if (!nfcAdapter.isEnabled) {
                // NFC not enabled
                2
            } else 0
        }

    private fun startNfcEmulator(data: String) {
        Log.i("plugin", "startNfcEmulator")
        val intent = Intent(activity, KHostApduService::class.java)
        intent.putExtra("ndefMessage", data)
        activity!!.startService(intent)
    }

    private fun stopNfcEmulator() {
        Log.i("plugin", "stopNfcEmulator")
        val intent = Intent(activity, KHostApduService::class.java)
        activity!!.stopService(intent)
    }
}