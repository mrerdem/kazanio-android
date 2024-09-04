package com.creadeep.kazanio

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.database.Cursor
import android.net.ConnectivityManager
import android.util.Log
import androidx.core.os.ConfigurationCompat
import androidx.preference.PreferenceManager
import androidx.work.*
import java.util.*

class AppUtils {
    companion object {
        /**
         * Returns current locale to be used in text formatting
         */
        fun getCurrentLocale(): Locale {
            return ConfigurationCompat.getLocales(Resources.getSystem().configuration).get(0)
        }

        /**
         * Activates background service to check results at the nearest required date and time.
         */
        fun enableResultService(context: Context) { // Set time to start checking
            // Check internet connection
            if (isConnectionAvailable(context)) {
                // Collect all tickets without result from db
                val db = TicketDbHelper.getInstance(context)
                val cursor: Cursor = db.allUndrawnTickets

                if (cursor.count > 0) {
                    // Find the ticket with the earliest draw
                    var checkTime = Calendar.getInstance()
                    checkTime.add(Calendar.YEAR, 100)
                    while (cursor.moveToNext()) {
                        val cursorCheckTime = GameUtils.findCheckTime(cursor)
                        if (cursorCheckTime.before(checkTime)) {
                            checkTime = cursorCheckTime
                        }
                    }
                    cursor.close()

                    // Create PendingIntent that will be executed by AlarmManager
                    // Set the task to be executed
                    var autoCheckerIntent = Intent(context, AlarmReceiver::class.java)
                    autoCheckerIntent.putExtra("requestCode", 1)
                    var autoCheckerPendingIntent = PendingIntent.getBroadcast(context, 1, autoCheckerIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    // If not already enabled
                    if (autoCheckerPendingIntent != null) {
                        // Set the task to be executed
                        autoCheckerIntent = Intent(context, AlarmReceiver::class.java)
                        // Create PendingIntent that will be executed by AlarmManager
                        autoCheckerPendingIntent = PendingIntent.getBroadcast(context, 1, autoCheckerIntent, 0)
                        // Set alarm based on user preference on timing
                        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                        val exactTime = sharedPreferences.getBoolean("exactTime", true)
                        val alarms = context.getSystemService(
                                Context.ALARM_SERVICE) as AlarmManager
                        if (exactTime) {
                            // Set calendar to 15 minutes later
                            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                                    checkTime.timeInMillis, autoCheckerPendingIntent)
                        }
                        else {
                            alarms.set(AlarmManager.RTC_WAKEUP,
                                    checkTime.timeInMillis,
                                    autoCheckerPendingIntent)
                        }
                    }
                }
                else {
                    // Disable result service
                    disableResultService(context)
                }
            }
            else {
                // Start checking again when network becomes available
                val myWorkRequest: WorkRequest = OneTimeWorkRequest.Builder(NetworkStateWorker::class.java).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
                WorkManager
                        .getInstance(context)
                        .enqueue(myWorkRequest)
            }
        }

        /**
         * Disables background service for checking results
         */
        fun disableResultService(context: Context) { // Set the task to be executed
            val autoCheckerIntent = Intent(context, AlarmReceiver::class.java)
            autoCheckerIntent.putExtra("requestCode", 1)
            // Get PendingIntent that will be executed by AlarmManager
            val autoCheckerPendingIntent = PendingIntent.getBroadcast(context, 1, autoCheckerIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            if (autoCheckerPendingIntent != null) {
                autoCheckerPendingIntent.cancel()
                // Cancel periodical alarm
                val alarms = context.getSystemService(
                        Context.ALARM_SERVICE) as AlarmManager
                alarms.cancel(autoCheckerPendingIntent)
            }
        }

        /**
         * Activates service for reminder notifications
         */
        fun enableReminderService(context: Context) {
            // Read selected games for reminders
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val reminderSwitch = sharedPreferences.getBoolean("reminderSwitch", true)
            val activeGames = sharedPreferences.getStringSet("reminderGames", setOf<String>())
            val timePreference = sharedPreferences.getString("reminderTime", "3")
            val omitExisting = sharedPreferences.getBoolean("omitExisting", true)
            val exactTime = sharedPreferences.getBoolean("exactTime", true)
            // Decide the next trigger time based on selected games
            if (reminderSwitch && activeGames != null && activeGames.size > 0) {
                // Set closest date
                var closestDate = Calendar.getInstance()
                closestDate.add(Calendar.YEAR, 100)
                var numRequiredAlarms = 0
                var reminderForGame = 0

                // Find closest date for each active game where there is no ticket
                for (game in activeGames) {
                    // Get current time considering the user preference time margin to prevent reminder loops
                    var current = Calendar.getInstance()
                    when (timePreference) {
                        "1" -> current.add(Calendar.MINUTE, 15)
                        "2" -> current.add(Calendar.MINUTE, 30)
                        "3" -> current.add(Calendar.HOUR_OF_DAY, 1)
                        "4" -> current.add(Calendar.HOUR_OF_DAY, 2)
                        "5" -> current.add(Calendar.HOUR_OF_DAY, 4)
                        "6" -> current.add(Calendar.HOUR_OF_DAY, 8)
                        "7" -> current.add(Calendar.HOUR_OF_DAY, 12)
                        "8" -> current.add(Calendar.DAY_OF_MONTH, 1)
                        "9" -> current.add(Calendar.DAY_OF_MONTH, 2)
                    }

                    // Get closest date with no tickets for current game type
                    var isNextDateFound = false
                    var nextDate = GameUtils.findNextDrawDateAfter(current, game.toInt())
                    if (omitExisting) { // If dates for existing tickets are to be skipped
                        while (!isNextDateFound) { // Search tickets until finding one that is not in the db
                            // Check if a ticket already exists at that date
                            val db = TicketDbHelper.getInstance(context)
                            if (!db.isPresent(game, GameUtils.calendarToString(nextDate))) { // Ticket does not exist
                                isNextDateFound = true
                            }
                            else { // There is already a ticket, skip this date
                                current = nextDate.clone() as Calendar
                                when (timePreference) {
                                    "1" -> current.add(Calendar.MINUTE, 15)
                                    "2" -> current.add(Calendar.MINUTE, 30)
                                    "3" -> current.add(Calendar.HOUR_OF_DAY, 1)
                                    "4" -> current.add(Calendar.HOUR_OF_DAY, 2)
                                    "5" -> current.add(Calendar.HOUR_OF_DAY, 4)
                                    "6" -> current.add(Calendar.HOUR_OF_DAY, 8)
                                    "7" -> current.add(Calendar.HOUR_OF_DAY, 12)
                                    "8" -> current.add(Calendar.DAY_OF_MONTH, 1)
                                    "9" -> current.add(Calendar.DAY_OF_MONTH, 2)
                                }
                                nextDate = GameUtils.findNextDrawDateAfter(current, game.toInt())
                            }
                        }
                    }
                    if (nextDate.before(closestDate)) {
                        closestDate = nextDate.clone() as Calendar
                        numRequiredAlarms++
                        reminderForGame = game.toInt()
                    }
                }

                if (numRequiredAlarms > 0) {
                    // Consider user preference
                    when (timePreference) {
                        "1" -> closestDate.add(Calendar.MINUTE, -15)
                        "2" -> closestDate.add(Calendar.MINUTE, -30)
                        "3" -> closestDate.add(Calendar.HOUR_OF_DAY, -1)
                        "4" -> closestDate.add(Calendar.HOUR_OF_DAY, -2)
                        "5" -> closestDate.add(Calendar.HOUR_OF_DAY, -4)
                        "6" -> closestDate.add(Calendar.HOUR_OF_DAY, -8)
                        "7" -> closestDate.add(Calendar.HOUR_OF_DAY, -12)
                        "8" -> closestDate.add(Calendar.DAY_OF_MONTH, -1)
                        "9" -> closestDate.add(Calendar.DAY_OF_MONTH, -2)
                    }

                    // Set the task to be executed
                    var reminderIntent = Intent(context, AlarmReceiver::class.java)
                    reminderIntent.putExtra("requestCode", 2)
                    reminderIntent.putExtra("gameType", reminderForGame)
                    var reminderPendingIntent = PendingIntent.getBroadcast(context, 2, reminderIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    // If not already enabled
                    if (reminderPendingIntent != null) {
                        // Set the task to be executed
                        reminderIntent = Intent(context, AlarmReceiver::class.java)
                        // Create PendingIntent that will be executed by AlarmManager
                        reminderPendingIntent = PendingIntent.getBroadcast(context, 2, reminderIntent, 0)
                        // Set single alarm
                        val alarms = context.getSystemService(
                                Context.ALARM_SERVICE) as AlarmManager
                        if (exactTime) {
                            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                                    closestDate.timeInMillis,
                                    reminderPendingIntent)
                        }
                        else {
                            alarms.set(AlarmManager.RTC_WAKEUP,
                                    closestDate.timeInMillis,
                                    reminderPendingIntent)
                        }
                    }
                }
            }
            else
                disableReminderService(context)
        }

        /**
         * Deactivates service for reminder notifications
         */
        private fun disableReminderService(context: Context) {
            val reminderIntent = Intent(context, AlarmReceiver::class.java)
            reminderIntent.putExtra("requestCode", 2)
            // Get PendingIntent that will be executed by AlarmManager
            val reminderPendingIntent = PendingIntent.getBroadcast(context, 2, reminderIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            if (reminderPendingIntent != null) {
                reminderPendingIntent.cancel()
                // Cancel periodical alarm
                val alarms = context.getSystemService(
                        Context.ALARM_SERVICE) as AlarmManager
                alarms.cancel(reminderPendingIntent)
            }
        }

        /**
         * Tells if there is an available internet connection.
         */
        fun isConnectionAvailable(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return connectivityManager.activeNetwork != null
        }

        /**
         * Returns the original package name to be used in packet legitimacy check
         */
        fun getMyPackageName(): String {
            var toReturn = ""
            toReturn += "c"
            toReturn += "o"
            toReturn += "m"
            toReturn += "."
            toReturn += "c"
            toReturn += "r"
            toReturn += "e"
            toReturn += "a"
            toReturn += "d"
            toReturn += "e"
            toReturn += "e"
            toReturn += "p"
            toReturn += "."
            toReturn += "k"
            toReturn += "a"
            toReturn += "z"
            toReturn += "a"
            toReturn += "n"
            toReturn += "i"
            toReturn += "o"
            return toReturn
        }
    }
}
