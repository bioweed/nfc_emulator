package io.flutter.plugins.nfc_emulator;

import android.app.Service
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.*
import android.util.Log
import java.math.BigInteger
import java.util.*


/**
 * Created by Qifan on 05/12/2018.
 */

class KHostApduService : HostApduService() {

    private val TAG = "HostApduService"

    private val APDU_SELECT = listOf(
        0x00, // CLA	- Class - Class of instruction
        0xA4, // INS	- Instruction - Instruction code
        0x04, // P1	- Parameter 1 - Instruction parameter 1
        0x00, // P2	- Parameter 2 - Instruction parameter 2
        0x07, // Lc field	- Number of bytes present in the data field of the command
        0xD2, // AID start, RID
        0x76, // RID
        0x00, // RID
        0x00, // RID
        0x85, // RID
        0x01, // PIX
        0x01, // NDEF Tag Application name, AID end, PIX
        0x00  // Le field	- Maximum number of bytes expected in the data field of the response to the command
    ).map { e -> e.toByte() }.toByteArray()

    private val CAPABILITY_CONTAINER_OK = listOf(
        0x00, // CLA	- Class - Class of instruction
        0xa4, // INS	- Instruction - Instruction code
        0x00, // P1	- Parameter 1 - Instruction parameter 1
        0x0c, // P2	- Parameter 2 - Instruction parameter 2
        0x02, // Lc field	- Number of bytes present in the data field of the command
        0xe1, 0x03 // file identifier of the CC file
    ).map { e -> e.toByte() }.toByteArray()

    private val READ_CAPABILITY_CONTAINER = listOf(
        0x00, // CLA	- Class - Class of instruction
        0xb0, // INS	- Instruction - Instruction code
        0x00, // P1	- Parameter 1 - Instruction parameter 1
        0x00, // P2	- Parameter 2 - Instruction parameter 2
        0x0f  // Lc field	- Number of bytes present in the data field of the command
    ).map { e -> e.toByte() }.toByteArray()

    // In the scenario that we have done a CC read, the same byte[] match
    // for ReadBinary would trigger and we don't want that in succession
    private var READ_CAPABILITY_CONTAINER_CHECK = false

    private val READ_CAPABILITY_CONTAINER_RESPONSE = listOf(
        0x00, 0x11, // CCLEN length of the CC file
        0x20, // Mapping Version 2.0
        0xFF, 0xFF, // MLe maximum
        0xFF, 0xFF, // MLc maximum
        0x04, // T field of the NDEF File Control TLV
        0x06, // L field of the NDEF File Control TLV
        0xE1, 0x04, // File Identifier of NDEF file
        0xFF, 0xFE, // Maximum NDEF file size of 65534 bytes
        0x00, // Read access without any security
        0xFF, // Write access without any security
        0x90, 0x00 // A_OKAY
    ).map { e -> e.toByte() }.toByteArray()

    private val NDEF_SELECT_OK = listOf(
        0x00, // CLA	- Class - Class of instruction
        0xa4, // Instruction byte (INS) for Select command
        0x00, // Parameter byte (P1), select by identifier
        0x0c, // Parameter byte (P1), select by identifier
        0x02, // Lc field	- Number of bytes present in the data field of the command
        0xE1, 0x04 // file identifier of the NDEF file retrieved from the CC file
    ).map { e -> e.toByte() }.toByteArray()

    private val NDEF_READ_BINARY = listOf(
        0x00, // Class byte (CLA)
        0xb0 // Instruction byte (INS) for ReadBinary command
    ).map { e -> e.toByte() }.toByteArray()

    private val NDEF_READ_BINARY_NLEN = listOf(
        0x00, // Class byte (CLA)
        0xb0, // Instruction byte (INS) for ReadBinary command
        0x00, 0x00, // Parameter byte (P1, P2), offset inside the CC file
        0x02  // Le field
    ).map { e -> e.toByte() }.toByteArray()

    private val A_OKAY = listOf(
        0x90, // SW1	Status byte 1 - Command processing status
        0x00   // SW2	Status byte 2 - Command processing qualifier
    ).map { e -> e.toByte() }.toByteArray()

    private val A_ERROR = listOf(
        0x6A, // SW1	Status byte 1 - Command processing status
        0x82   // SW2	Status byte 2 - Command processing qualifier
    ).map { e -> e.toByte() }.toByteArray()

    private val NDEF_ID = listOf(0xE1, 0x04).map { e -> e.toByte() }.toByteArray()

    private var NDEF_URI =
        NdefMessage(createTextRecord("NFC Emulator Service not yet started", id = NDEF_ID))
    private var NDEF_URI_BYTES = NDEF_URI.toByteArray()
    private var NDEF_URI_LEN = fillByteArrayToFixedDimension(
        BigInteger.valueOf(NDEF_URI_BYTES.size.toLong()).toByteArray(), 2
    )

    private lateinit var vibrator: Vibrator

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                this.getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator;
        } else {
            @Suppress("DEPRECATION")
            this.getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.hasExtra("msg")) {
            val payload = intent.getStringExtra("msg")!!.toByteArray() //assign to your data
            val domain = intent.getStringExtra("id")
                ?: applicationContext.packageName //usually your app's package name
            val type = intent.getStringExtra("type") ?: "default"
            Log.d(TAG, "payload: " + intent.getStringExtra("msg")!!)
            Log.d(TAG, "id: $domain")
            Log.d(TAG, "type: $type")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Log.d(TAG, ">= LOLLIPOP")
                NDEF_URI = NdefMessage(
                    NdefRecord.createExternal(domain, type, payload),
                    NdefRecord.createApplicationRecord(domain),
                )
            } else {
                Log.d(TAG, "< LOLLIPOP")
                NDEF_URI = NdefMessage(
                    NdefRecord(
                        NdefRecord.TNF_EXTERNAL_TYPE,
                        "$domain:$type".toByteArray(Charsets.US_ASCII),
                        NDEF_ID,
                        payload
                    )
                )

            }

            NDEF_URI_BYTES = NDEF_URI.toByteArray()
            NDEF_URI_LEN = fillByteArrayToFixedDimension(
                BigInteger.valueOf(NDEF_URI_BYTES.size.toLong()).toByteArray(), 2
            )
        }

        Log.i(TAG, "onStartCommand() | NDEF$NDEF_URI")

        return Service.START_STICKY
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {

//        if(cardAid==null || cardAid.equals("") || cardUid==null || cardUid.equals("")) {
//            return UNKNOWN_CMD_SW; // don't start emulator TODO
//        }

        //
        // The following flow is based on Appendix E "Example of Mapping Version 2.0 Command Flow"
        // in the NFC Forum specification
        //
        Log.i(TAG, "processCommandApdu() | incoming commandApdu: " + commandApdu.toHex())
        Log.i(TAG, extras?.toString().toString())

        //
        // First command: NDEF Tag Application select (Section 5.5.2 in NFC Forum spec)
        //
        if (APDU_SELECT.contentEquals(commandApdu)) {
            Log.i(TAG, "APDU_SELECT triggered. Our Response: " + A_OKAY.toHex())
            return A_OKAY
        }

        //
        // Second command: Capability Container select (Section 5.5.3 in NFC Forum spec)
        //
        if (CAPABILITY_CONTAINER_OK.contentEquals(commandApdu)) {
            Log.i(TAG, "CAPABILITY_CONTAINER_OK triggered. Our Response: " + A_OKAY.toHex())
            return A_OKAY
        }

        //
        // Third command: ReadBinary data from CC file (Section 5.5.4 in NFC Forum spec)
        //
        if (READ_CAPABILITY_CONTAINER.contentEquals(commandApdu) && !READ_CAPABILITY_CONTAINER_CHECK
        ) {
            Log.i(
                TAG,
                "READ_CAPABILITY_CONTAINER triggered. Our Response: "
                        + READ_CAPABILITY_CONTAINER_RESPONSE.toHex()
            )
            READ_CAPABILITY_CONTAINER_CHECK = true
            return READ_CAPABILITY_CONTAINER_RESPONSE
        }

        //
        // Fourth command: NDEF Select command (Section 5.5.5 in NFC Forum spec)
        //
        if (NDEF_SELECT_OK.contentEquals(commandApdu)) {
            Log.i(TAG, "NDEF_SELECT_OK triggered. Our Response: " + A_OKAY.toHex())
            return A_OKAY
        }

        if (NDEF_READ_BINARY_NLEN.contentEquals(commandApdu)) {
            // Build our response
            val response = ByteArray(NDEF_URI_LEN.size + A_OKAY.size)
            System.arraycopy(NDEF_URI_LEN, 0, response, 0, NDEF_URI_LEN.size)
            System.arraycopy(A_OKAY, 0, response, NDEF_URI_LEN.size, A_OKAY.size)

            Log.i(TAG, "NDEF_READ_BINARY_NLEN triggered. Our Response: " + response.toHex())

            READ_CAPABILITY_CONTAINER_CHECK = false
            return response
        }

        if (commandApdu.sliceArray(0..1).contentEquals(NDEF_READ_BINARY)) {
            val offset = commandApdu.sliceArray(2..3).toHex().toInt(16)
            val length = commandApdu.sliceArray(4..4).toHex().toInt(16)

            val fullResponse = ByteArray(NDEF_URI_LEN.size + NDEF_URI_BYTES.size)
            System.arraycopy(NDEF_URI_LEN, 0, fullResponse, 0, NDEF_URI_LEN.size)
            System.arraycopy(
                NDEF_URI_BYTES,
                0,
                fullResponse,
                NDEF_URI_LEN.size,
                NDEF_URI_BYTES.size
            )

            Log.i(TAG, "NDEF_READ_BINARY triggered. Full data: " + fullResponse.toHex())
            Log.i(TAG, "READ_BINARY - OFFSET: $offset - LEN: $length")

            val slicedResponse = fullResponse.sliceArray(offset until fullResponse.size)

            // Build our response
            val realLength = if (slicedResponse.size <= length) slicedResponse.size else length
            val response = ByteArray(realLength + A_OKAY.size)

            System.arraycopy(slicedResponse, 0, response, 0, realLength)
            System.arraycopy(A_OKAY, 0, response, realLength, A_OKAY.size)

            Log.i(TAG, "NDEF_READ_BINARY triggered. Our Response: " + response.toHex())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        100,
                        10
                    )
                )
            } else {
                vibrator.vibrate(100)
            }

            READ_CAPABILITY_CONTAINER_CHECK = false
            return response
        }

        //
        // We're doing something outside our scope
        //
        Log.wtf(TAG, "processCommandApdu() | I don't know what's going on!!!")
        return A_ERROR
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "onDeactivated() Fired! Reason: $reason")
    }

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    fun ByteArray.toHex(): String {
        val result = StringBuffer()

        forEach {
            val octet = it.toInt()
            val firstIndex = (octet and 0xF0).ushr(4)
            val secondIndex = octet and 0x0F
            result.append(HEX_CHARS[firstIndex])
            result.append(HEX_CHARS[secondIndex])
        }

        return result.toString()
    }

    fun String.hexStringToByteArray(): ByteArray {

        val result = ByteArray(length / 2)

        for (i in 0 until length step 2) {
            val firstIndex = HEX_CHARS.indexOf(this[i]);
            val secondIndex = HEX_CHARS.indexOf(this[i + 1]);

            val octet = firstIndex.shl(4).or(secondIndex)
            result[i.shr(1)] = octet.toByte()
        }

        return result
    }

    private fun createTextRecord(
        payload: String,
        locale: Locale = Locale.ENGLISH,
        id: ByteArray = ByteArray(0),
        encodeInUtf8: Boolean = true
    ): NdefRecord {
        val langBytes = locale.language.toByteArray(Charsets.US_ASCII)
        val utfEncoding = if (encodeInUtf8) Charsets.UTF_8 else Charsets.UTF_16
        val textBytes = payload.toByteArray(utfEncoding)
        val utfBit: Int = if (encodeInUtf8) 0 else 1 shl 7
        val status = (utfBit + langBytes.size).toChar()
        val data = ByteArray(1 + langBytes.size + textBytes.size)
        data[0] = status.code.toByte()
        System.arraycopy(langBytes, 0, data, 1, langBytes.size)
        System.arraycopy(textBytes, 0, data, 1 + langBytes.size, textBytes.size)
        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, id, data)
    }

    private fun fillByteArrayToFixedDimension(array: ByteArray, fixedSize: Int): ByteArray {
        if (array.size == fixedSize) {
            return array
        }

        val start = byteArrayOf(0x00.toByte())
        val filledArray = ByteArray(start.size + array.size)
        System.arraycopy(start, 0, filledArray, 0, start.size)
        System.arraycopy(array, 0, filledArray, start.size, array.size)
        return fillByteArrayToFixedDimension(filledArray, fixedSize)
    }
}