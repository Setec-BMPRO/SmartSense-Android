package com.smartsense.app.demo

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.smartsense.app.data.ble.BleConstants
import com.smartsense.app.data.ble.ParsedSensor
import com.smartsense.app.data.ble.ScannedSensor
import com.smartsense.app.data.ble.SensorAdvertParser
import com.smartsense.app.domain.model.TankType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.Random
import kotlin.math.roundToInt

object DemoInjector {
    private const val TAG = "SSPDemo"
    private const val PREFS = "ssp_demo_controls"
    const val KEY_ENABLED = "enabled"
    /** Demo mode is off until switched on in SSP Demo Controls, so debug builds use
     *  real Bluetooth by default. (Release builds get a no-op stub of this object.) */
    const val DEFAULT_ENABLED = false
    private const val KEY_SENSORS = "sensors"

    @Volatile
    private var appContext: Context? = null
    private var emitter: Thread? = null
    private var flow: MutableSharedFlow<ScannedSensor>? = null

    @JvmStatic
    fun init(context: Context?) {
        if (context == null || appContext != null) return
        appContext = context.applicationContext
        ensureDefaults(appContext!!)
    }

    @JvmStatic
    fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    val isActive: Boolean
        get() {
            val ctx = appContext ?: return false
            return prefs(ctx).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
        }

    @JvmStatic
    @Synchronized
    fun scanFlowOrNull(): Flow<ScannedSensor>? {
        if (!isActive) return null

        if (flow == null) {
            flow = MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = 256,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }

        if (emitter == null || emitter?.isAlive == false) {
            emitter = Thread({ emitLoop() }, "SSPDemoEmitter").apply {
                isDaemon = true
                start()
            }
        }
        Log.i(TAG, "Fake G300 scan flow attached")
        return flow
    }

    private fun emitLoop() {
        while (true) {
            try {
                if (isActive) {
                    val ctx = appContext
                    if (ctx != null) {
                        val sensors = loadSensors(ctx)
                        for (s in sensors) {
                            if (s.broadcasting) {
                                val scan = buildScan(s)
                                if (scan != null) {
                                    flow?.tryEmit(scan)
                                }
                            }
                        }
                    }
                }
                Thread.sleep(1000L)
            } catch (e: InterruptedException) {
                return
            } catch (t: Throwable) {
                Log.w(TAG, "emit failed", t)
                try {
                    Thread.sleep(1000L)
                } catch (e: InterruptedException) {
                    return
                }
            }
        }
    }

    @JvmStatic
    fun buildScan(demoSensor: DemoSensor): ScannedSensor? {
        val isSyncing = System.currentTimeMillis() < demoSensor.syncUntil
        val payload = encodeG300(demoSensor, isSyncing)
        val setec: ParsedSensor = SensorAdvertParser.parseSetec(
            payload,
            demoSensor.rssi,
            demoSensor.mac
        ) ?: run {
            Log.w(TAG, "App parser rejected fake payload for ${demoSensor.mac}")
            return null
        }
        return ScannedSensor(
            address = demoSensor.mac,
            name = BleConstants.SETEC_G300_ADV_NAME,
            rssi = demoSensor.rssi,
            parsed = setec
        )
    }

    @JvmStatic
    fun encodeG300(demoSensor: DemoSensor, syncActive: Boolean): ByteArray {
        val arr = ByteArray(17)
        arr[0] = 0xFF.toByte()
        arr[1] = ((if (syncActive) 0x80 else 0) or 0x01).toByte()
        arr[2] = 0x01
        arr[3] = 0x10
        System.arraycopy(macBytes(demoSensor.mac), 0, arr, 4, 6)
        val rawBatt = Math.max(0, Math.min(255, Math.round((demoSensor.batteryV - 1.22f) / 0.01f)))
        arr[10] = rawBatt.toByte()
        arr[11] = 0x00
        arr[12] = 0x06 // Sensor type = Gas
        val hMm = Math.max(0, Math.min(65535, demoSensor.heightMm()))
        arr[13] = ((hMm shr 8) and 0xFF).toByte()
        arr[14] = (hMm and 0xFF).toByte()
        arr[15] = (demoSensor.serial and 0xFF).toByte()
        arr[16] = ((demoSensor.intervalCode and 0x0F) or ((demoSensor.quality and 0x0F) shl 4)).toByte()
        return arr
    }

    @JvmStatic
    fun macBytes(mac: String): ByteArray {
        val parts = mac.split(":")
        val b = ByteArray(6)
        for (i in 0 until 6) {
            b[i] = parts[i].toInt(16).toByte()
        }
        return b
    }

    private fun ensureDefaults(context: Context) {
        if (prefs(context).contains(KEY_SENSORS)) return
        val list = listOf(DemoSensor.create("F4:CE:36:5A:21:7C"))
        saveSensors(context, list)
    }

    @JvmStatic
    fun loadSensors(context: Context): List<DemoSensor> {
        val list = mutableListOf<DemoSensor>()
        try {
            val jsonStr = prefs(context).getString(KEY_SENSORS, "[]") ?: "[]"
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                list.add(DemoSensor.fromJson(array.getJSONObject(i)))
            }
        } catch (e: Exception) {
            Log.w(TAG, "bad sensor prefs", e)
        }
        return list
    }

    @JvmStatic
    fun saveSensors(context: Context, list: List<DemoSensor>) {
        val array = JSONArray()
        try {
            for (s in list) {
                array.put(s.toJson())
            }
        } catch (e: Exception) {
            Log.w(TAG, "save failed", e)
        }
        prefs(context).edit().putString(KEY_SENSORS, array.toString()).commit()
    }

    @JvmStatic
    fun randomMac(): String {
        val r = Random()
        return String.format(
            Locale.US,
            "F4:CE:36:%02X:%02X:%02X",
            r.nextInt(256),
            r.nextInt(256),
            r.nextInt(256)
        )
    }

    class DemoSensor {
        var mac: String = "F4:CE:36:00:00:01"
        var syncUntil: Long = 0L
        var broadcasting: Boolean = true
        var tankType: String = "KG_4"
        var customTankMm: Int = 300
        var levelPercent: Float = 72.0f
        var batteryV: Float = 2.95f
        var rssi: Int = -48
        var quality: Int = 3
        var intervalCode: Int = 2
        var serial: Int = 66

        fun tankHeightMm(): Int {
            if ("CUSTOM" == tankType) return customTankMm
            for (t in TankType.values()) {
                if (t.name == tankType) {
                    return (t.heightMeters * 1000.0).roundToInt()
                }
            }
            return 342
        }

        fun heightMm(): Int {
            return ((tankHeightMm() * levelPercent) / 100.0f).roundToInt()
        }

        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put("mac", mac)
            json.put("on", broadcasting)
            json.put("tank", tankType)
            json.put("customMm", customTankMm)
            json.put("pct", levelPercent.toDouble())
            json.put("batt", batteryV.toDouble())
            json.put("rssi", rssi)
            json.put("q", quality)
            json.put("ivl", intervalCode)
            json.put("serial", serial)
            json.put("syncUntil", syncUntil)
            return json
        }

        companion object {
            @JvmStatic
            fun create(macStr: String): DemoSensor {
                val s = DemoSensor()
                s.mac = macStr
                s.serial = Random().nextInt(200) + 20
                return s
            }

            @JvmStatic
            fun fromJson(json: JSONObject): DemoSensor {
                val s = DemoSensor()
                s.mac = json.optString("mac", "F4:CE:36:00:00:01")
                s.broadcasting = json.optBoolean("on", true)
                s.tankType = json.optString("tank", "KG_4")
                s.customTankMm = json.optInt("customMm", 300)
                s.levelPercent = json.optDouble("pct", 72.0).toFloat()
                s.batteryV = json.optDouble("batt", 2.95).toFloat()
                s.rssi = json.optInt("rssi", -48)
                s.quality = json.optInt("q", 3)
                s.intervalCode = json.optInt("ivl", 2)
                s.serial = json.optInt("serial", 66)
                s.syncUntil = json.optLong("syncUntil", 0L)
                return s
            }
        }
    }
}
