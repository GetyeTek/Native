package com.example.myandroid

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

object PhoneManager {

    data class CallStats(
        val totalDuration: Long = 0,
        val totalCalls: Int = 0,
        val incoming: Int = 0,
        val outgoing: Int = 0,
        val missed: Int = 0,
        val topContact: String = "None",
        val contactCount: Int = 0
    )

    fun getStats(ctx: Context): CallStats {
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CALL_LOG) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return CallStats()
        }

        var duration = 0L
        var total = 0
        var inc = 0
        var out = 0
        var miss = 0
        val contactFreq = HashMap<String, Int>()

        try {
            val cursor = ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null, CallLog.Calls.DATE + " DESC"
            )
            
            cursor?.use {
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)

                while (it.moveToNext()) {
                    val dur = it.getLong(durIdx)
                    val type = it.getInt(typeIdx)
                    val num = it.getString(numIdx)

                    total++
                    duration += dur
                    
                    when (type) {
                        CallLog.Calls.INCOMING_TYPE -> inc++
                        CallLog.Calls.OUTGOING_TYPE -> out++
                        CallLog.Calls.MISSED_TYPE -> miss++
                    }
                    
                    if (num != null) {
                        contactFreq[num] = contactFreq.getOrDefault(num, 0) + 1
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Get Contact Count
        var cCount = 0
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
             try {
                 val cCursor = ctx.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null)
                 cCount = cCursor?.count ?: 0
                 cCursor?.close()
             } catch (e: Exception) { }
        }
        
        // Resolve Top Contact Name
        val topNum = contactFreq.maxByOrNull { it.value }?.key ?: "None"
        var topName = topNum
        if (topNum != "None" && cCount > 0) {
            topName = getContactName(ctx, topNum)
        }

        return CallStats(duration, total, inc, out, miss, topName, cCount)
    }

    fun getCallLogs(ctx: Context): JSONArray {
        val list = JSONArray()
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CALL_LOG) != android.content.pm.PackageManager.PERMISSION_GRANTED) return list

        try {
            val cursor = ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null, CallLog.Calls.DATE + " DESC LIMIT 50"
            )
            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)

                while(it.moveToNext()) {
                    val obj = JSONObject()
                    obj.put("num", it.getString(numIdx))
                    obj.put("ts", it.getLong(dateIdx))
                    obj.put("dur", it.getLong(durIdx))
                    obj.put("type", it.getInt(typeIdx))
                    list.put(obj)
                }
            }
        } catch(e: Exception) {}
        return list
    }

    fun getContacts(ctx: Context): JSONArray {
        val list = JSONArray()
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return list
        
        try {
             val cursor = ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while(it.moveToNext()) {
                    val obj = JSONObject()
                    obj.put("name", it.getString(nameIdx))
                    obj.put("num", it.getString(numIdx))
                    list.put(obj)
                }
            }
        } catch(e: Exception) {}
        return list
    }

    private fun getContactName(ctx: Context, phoneNumber: String): String {
        val uri = android.net.Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(phoneNumber))
        val cursor = ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
        var name = phoneNumber
        cursor?.use {
            if (it.moveToFirst()) {
                name = it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
            }
        }
        return name
    }
}