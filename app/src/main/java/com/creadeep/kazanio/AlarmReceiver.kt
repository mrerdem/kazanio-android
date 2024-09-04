package com.creadeep.kazanio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AlarmReceiver: BroadcastReceiver() {
    private val mQueryUrl = arrayOf(
            "http://www.mpi.gov.tr/sonuclar/cekilisler/piyango/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/sayisal/SAY_",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/superloto/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/onnumara/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/sanstopu/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/superpiyango/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/bankopiyango/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/supersayisal/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/paraloto/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/superonnumara/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/supersanstopu/"
    )
    private var gameType = 1
    private var ticketType = 1
    private lateinit var mNumberText: String
    private lateinit var mDateText: String
    private lateinit var mFractionText: String
    private lateinit var ticketDbHelper: TicketDbHelper
    private lateinit var context: Context
    private var numNewResults = 0

    override fun onReceive (c: Context?, intent: Intent?) {
        val requestCode = intent?.getIntExtra("requestCode", 0)

        // Result service
        if (requestCode == 1 && c != null) {
            context = c

            // Get undrawn tickets
            ticketDbHelper = TicketDbHelper.getInstance(context)
            val ticketCursor: Cursor = ticketDbHelper.allUndrawnTickets

            // Cancel alarm if all tickets have results
            if (ticketCursor.count == 0) {
                AppUtils.disableResultService(context)
                ticketCursor.close()
            }

            // Loop through tickets to check their results
            else {
                if (AppUtils.isConnectionAvailable(context)) {
                    CoroutineScope(IO).launch {
                        checkWebForResult(ticketCursor)
                    }
                    // Enable result service
                    AppUtils.enableResultService(context)
                }
                else {
                    // Start checking again when network becomes available
                    val myWorkRequest: WorkRequest = OneTimeWorkRequest.Builder(NetworkStateWorker::class.java).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
                    WorkManager
                            .getInstance(context)
                            .enqueue(myWorkRequest)
                }
            }
        }

        // Reminder service
        else if (requestCode == 2 && c != null) {
            context = c

            // Notify user
            showNotification(0, intent.getIntExtra("gameType", 1)) // gameType parameter is unimportant

            // Set next alarm
            AppUtils.enableReminderService(context)
        }
    }

    /**
     * Checks tickets provided via Cursor parameter one by one from the Internet.
     * Suspend function since we want following tasks to wait this finish.
     */
    private suspend fun checkWebForResult (tc: Cursor) { // suspend means it can be used with coroutines
        while (tc.moveToNext()) {
            gameType = tc.getInt(tc.getColumnIndex("GameType"))
            ticketType = tc.getInt(tc.getColumnIndex("TicketType"))
            mNumberText = tc.getString(tc.getColumnIndex("Number"))
            mDateText = tc.getString(tc.getColumnIndex("Date"))
            mFractionText = tc.getString(tc.getColumnIndex("Fraction"))
            if (GameUtils.isDrawn(gameType, mDateText)) {
                withContext(IO) {
                    val url = URL(mQueryUrl[gameType - 1] + mDateText.substring(4, 8) + mDateText.substring(2, 4) + mDateText.substring(0, 2) + ".json")
                    val jsonResponse = getJsonResponse(url)
                    if (jsonResponse != null) {
                        val extractedTicket = parseJsonResponse(jsonResponse)
                        if (extractedTicket != null) {
                            storeTicket(extractedTicket)
                            numNewResults++
                        }
                    }
                }
            }
        }
        tc.close()
        if (numNewResults > 0)
            showNotification(numNewResults, 0) // gameType parameter is unimportant
    }

    /**
     * Returns Json response from web.
     */
    private fun getJsonResponse (url: URL): JSONObject? {
        var response = ""
        try {
            val br = BufferedReader(InputStreamReader(url.openStream()))
            val sb = StringBuilder()
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.readTimeout = 10000
            urlConnection.connectTimeout = 15000
            urlConnection.doOutput = true

            urlConnection.connect()
            var line: String? = br.readLine()
            while (line != null) {
                sb.append("$line\n")
                line = br.readLine()
            }
            response = sb.toString()
            br.close()
            urlConnection.disconnect()
        } catch (e: Exception) {
        }
        if (response == "" || (response.length > 9 && response.substring(0, 9) == "<!DOCTYPE"))
            return null
        return JSONObject(response)
    }

    /**
     * Parses Json response to generate Ticket object.
     */
    private fun parseJsonResponse (jsonResponse: JSONObject): Ticket? {
        try {
            if (gameType == 1) { // Milli Piyango
                var maxDigits = 0 // Required to correctly determine the number of digits (6 or 7)
                var maxPrize = 0.00 // Required to correctly determine teselli prize
                val resultList = jsonResponse.getJSONArray("sonuclar")
                for (i in 0 until resultList.length()) { // for each prize
                    val resultItem = resultList.getJSONObject(i)
                    var digitsToCheck = resultItem.getInt("haneSayisi") // number of digits to check
                    if (i == 0)
                        maxDigits = digitsToCheck
                    if (digitsToCheck == 0) // If teselli
                        digitsToCheck = maxDigits // Check entire number
                    val numberToCheck: String = mNumberText.substring(mNumberText.length - digitsToCheck, mNumberText.length)
                    val numberList = resultItem.getJSONArray("numaralar")
                    for (j in 0 until numberList.length()) {
                        if (numberToCheck == numberList.getString(j)) { // match
                            val prize  = when (mFractionText) {
                                "11" -> { // Tam Bilet
                                    resultItem.getString("ikramiye")
                                }
                                "12" -> { // Yarim Bilet
                                    String.format(AppUtils.getCurrentLocale(), "%.0f", resultItem.getString("ikramiye").toDouble() / 2.0)
                                }
                                else -> { // Ceyrek Bilet
                                    String.format(AppUtils.getCurrentLocale(), "%.0f", resultItem.getString("ikramiye").toDouble() / 4.0)
                                }
                            }
                            if (prize.toDouble() > maxPrize)
                                maxPrize = prize.toDouble()
                        }
                    }
                }
                return Ticket(1, ticketType, mNumberText, mDateText, mFractionText, maxPrize.toString(), 0, 1, 1)
            }
            else if (gameType == 2 || gameType == 3 || gameType == 8 || gameType == 9) { // Sayisal Loto, Super Loto, Super Sayisal Loto, Para Loto
                val data = jsonResponse.getJSONObject("data")
                val numbers = data.getString("rakamlar").split("#".toRegex()).toTypedArray()
                var prize = 0.0
                val rowNum: Int = mNumberText.length / 12
                for (i in 0 until rowNum) { // Process each row
                    val numberRow: String = mNumberText.substring(i * 12, (i + 1) * 12)
                    if (numberRow != "XXXXXXXXXXXX") { // if detected properly
                        var matchNum = 0 // Amount of matches
                        val loc = AppUtils.getCurrentLocale()
                        for (number in numbers) {
                            val numberFixed = String.format(loc, "%02d", number.toInt()) // Make it have two digits
                            for (k in 0..5) {
                                if (numberFixed == numberRow.substring(k * 2, (k + 1) * 2)) {
                                    matchNum++
                                }
                            }
                        }
                        if (matchNum > 2) {
                            val bilenKisiler = data.getJSONArray("bilenKisiler")
                            val numResult = bilenKisiler.getJSONObject(matchNum - 3)
                            prize += numResult.getDouble("kisiBasinaDusenIkramiye")
                        }
                    }
                }
                return Ticket(gameType, 0, mNumberText, mDateText, mFractionText, prize.toString(), 0, 1, 1)
            }
            else if (gameType == 4 || gameType == 10) { // On Numara, Super On Numara
                val data = jsonResponse.getJSONObject("data")
                val numbers = data.getString("rakamlar").split("#".toRegex()).toTypedArray()
                var prize = 0.0 // Total prize
                val rowNum: Int = mNumberText.length / 20
                for (i in 0 until rowNum) { // Process each row
                    val numberRow: String = mNumberText.substring(i * 20, (i + 1) * 20) // Get current row
                    if (numberRow != "XXXXXXXXXXXXXXXXXXXX") { // if detected properly
                        var matchNum = 0 // Amount of matches
                        val loc = AppUtils.getCurrentLocale()
                        for (number in numbers) { // For each drawn number
                            val numberFixed = String.format(loc, "%02d", number.toInt()) // Make it have two digits
                            for (k in 0..9) { // Check if it exists inside the row's numbers
                                if (numberFixed == numberRow.substring(k * 2, (k + 1) * 2)) {
                                    matchNum++
                                }
                            }
                        }
                        if (matchNum == 0 || matchNum > 5) {
                            val bilenKisiler = data.getJSONArray("bilenKisiler")
                            var numResult: JSONObject
                            numResult = if (matchNum == 0) bilenKisiler.getJSONObject(0) else bilenKisiler.getJSONObject(matchNum - 5)
                            prize += numResult.getDouble("kisiBasinaDusenIkramiye")
                        }
                    }
                }
                return Ticket(gameType, 0, mNumberText, mDateText, mFractionText, prize.toString(), 0, 1, 1)
            }
            else if (gameType == 6) { // Super Piyango
                val resultList: JSONArray = jsonResponse.getJSONArray("sonuclar")
                for (i in 0 until resultList.length()) { // for each prize
                    val resultItem = resultList.getJSONObject(i)
                    val digitsToCheck = resultItem.getInt("haneSayisi") // number of digits to check
                    val numberToCheck = mNumberText.substring((mNumberText.length - digitsToCheck).coerceAtLeast(0))
                    val numberList = resultItem.getJSONArray("numaralar")
                    for (j in 0 until numberList.length()) {
                        if (numberToCheck == numberList.getString(j)) { // match
                            val prize = resultItem.getString("ikramiye")
                            return Ticket(gameType, ticketType, mNumberText, mDateText, "0", prize, 0, 1, 1)
                        }
                    }
                }
                return Ticket(gameType, ticketType, mNumberText, mDateText, mFractionText, "0", 0, 1, 1)
            }
            else if (gameType == 7) { // Banko Piyango
                val resultList: JSONArray = jsonResponse.getJSONArray("sonuclar")
                for (i in 0 until resultList.length()) { // for each prize
                    val resultItem = resultList.getJSONObject(i)
                    val digitsToCheck = resultItem.getInt("haneSayisi") // number of digits to check
                    val numberToCheck = mNumberText.substring((mNumberText.length - digitsToCheck).coerceAtLeast(0))
                    val numberList = resultItem.getJSONArray("numaralar")
                    for (j in 0 until numberList.length()) {
                        if (numberToCheck == numberList.getString(j)) { // match
                            val prize = resultItem.getString("ikramiye")
                            return Ticket(gameType, ticketType, mNumberText, mDateText, "0", prize, 0, 1, 1)
                        }
                    }
                }
                return Ticket(gameType, ticketType, mNumberText, mDateText, mFractionText, "0", 0, 1, 1)
            }
            else { // Sans Topu
                val data = jsonResponse.getJSONObject("data")
                val numbers = data.getString("rakamlar").split("#".toRegex()).toTypedArray()
                var prize = 0.0
                val rowNum: Int = mNumberText.length / 12
                for (i in 0 until rowNum) { // Process each row
                    val numberRow: String = mNumberText.substring(i * 12, (i + 1) * 12)
                    if (numberRow != "XXXXXXXXXXXX") { // if detected properly
                        // Check main numbers
                        var mainMatchNum = 0 // Amount of matches
                        val loc = AppUtils.getCurrentLocale()
                        for (j in 0 until numbers.size - 1) { // Exclude the last number (+1)
                            for (k in 0 .. 4) {
                                val numberFixed = String.format(loc, "%02d", numbers[j].toInt()) // Make it have two digits
                                if (numberFixed == numberRow.substring(k * 2, (k + 1) * 2)) {
                                    mainMatchNum++
                                }
                            }
                        }
                        // Check extra +1 number
                        var extraMatchNum = 0
                        val numberFixed = String.format(loc, "%02d", numbers[5].toInt()) // Make it have two digits
                        if (numberFixed == numberRow.substring(10, 12)) {
                            extraMatchNum++
                        }
                        if (mainMatchNum > 0 && extraMatchNum == 1 || mainMatchNum > 2 && extraMatchNum == 0) {
                            val bilenKisiler = data.getJSONArray("bilenKisiler")
                            var numResult: JSONObject
                            // +1 matched
                            numResult = if (extraMatchNum == 1) {
                                when (mainMatchNum) {
                                    1 -> bilenKisiler.getJSONObject(0)
                                    2 -> bilenKisiler.getJSONObject(1)
                                    3 -> bilenKisiler.getJSONObject(3)
                                    4 -> bilenKisiler.getJSONObject(5)
                                    else -> bilenKisiler.getJSONObject(7)
                                }
                            } else {
                                when (mainMatchNum) {
                                    3 -> bilenKisiler.getJSONObject(2)
                                    4 -> bilenKisiler.getJSONObject(4)
                                    else -> bilenKisiler.getJSONObject(6)
                                }
                            }
                            prize += numResult.getDouble("kisiBasinaDusenIkramiye")
                        }
                    }
                }
                return Ticket(gameType, 0, mNumberText, mDateText, mFractionText, prize.toString(), 0, 1, 1)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Saves ticket into the database.
     */
    private fun storeTicket(ticketToStore: Ticket) { // stores the data with auto notify OFF TODO: Implement using ViewModel (don't know how) to trigger LiveData update while waiting in Tickets view or Result view
        ticketDbHelper.insertData(
                ticketToStore.gameType,
                ticketToStore.ticketType,
                ticketToStore.number,
                ticketToStore.date,
                ticketToStore.fraction,
                ticketToStore.prize,
                ticketToStore.notify,
                ticketToStore.isDrawn,
                ticketToStore.tease) // Add to dB
    }

    /**
     * Shows notification.
     */
    private fun showNotification(numResults: Int, gameType: Int) {
        // Set intent for notification click
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

        // Show notification
        val builder = NotificationCompat.Builder(context, "1")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
        // Set title based on the number of results available
        when (numResults) {
            0 -> { // Reminder service notification
                builder.setContentTitle(String.format(context.getString(R.string.notification_title_reminder), context.resources.getStringArray(R.array.game_names)[gameType - 1]))
                        .setContentText(context.getString(R.string.notification_text_reminder))
            }
            1 -> { // Result service notification with a single result
                builder.setContentTitle(context.getString(R.string.notification_title_result_available_single))
                        .setContentText(context.getString(R.string.notification_text_result_available))
            }
            else -> { // Result service notification with multiple results
                builder.setContentTitle(context.getString(R.string.notification_title_result_available_multiple))
                        .setContentText(context.getString(R.string.notification_text_result_available))
            }
        }
        val notificationManager = NotificationManagerCompat.from(context)
        when (numResults) {
            0 -> notificationManager.notify(2, builder.build()) // Reminder service notification
            else -> notificationManager.notify(1, builder.build()) // Result service notification
        }
    }
}
