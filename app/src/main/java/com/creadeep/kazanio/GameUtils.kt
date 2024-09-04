package com.creadeep.kazanio

import android.database.Cursor
import java.util.*

class GameUtils {

    companion object {
        private val hours = arrayOf(15, 21, 21, 21, 21, 22, 22, 21, 21, 20, 21) // Draw time hour info
        private val minutes = arrayOf(0, 15, 15, 15, 15, 15, 0, 15, 15, 15, 15) // Draw time minute info

        /**
         * Finds closest draw date starting after the given date
         */
        fun findNextDrawDateAfter(c: Calendar, gameType: Int): Calendar {
            val currentCal = c.clone() as Calendar

            // If draw time has already passed, start checking from the next day
            if (currentCal.get(Calendar.DAY_OF_WEEK) == Calendar.WEDNESDAY && (gameType == 2 || gameType == 8)) { // For Sayisal Loto, draws on Wednesday are 30 min delayed
                if ((currentCal.get(Calendar.HOUR_OF_DAY) > hours[gameType - 1] ||
                        currentCal.get(Calendar.HOUR_OF_DAY) == hours[gameType - 1] && currentCal.get(Calendar.MINUTE) >= minutes[gameType - 1] + 30))
                    currentCal.add(Calendar.DATE, 1)
            }
            else {
                if (currentCal.get(Calendar.HOUR_OF_DAY) > hours[gameType - 1] ||
                        currentCal.get(Calendar.HOUR_OF_DAY) == hours[gameType - 1] && currentCal.get(Calendar.MINUTE) >= minutes[gameType - 1])
                    currentCal.add(Calendar.DATE, 1)
            }
            // Find next valid date
            while (TicketUtils.isDateInvalid(calendarToString(currentCal), gameType)) {
                currentCal.add(Calendar.DATE, 1)
            }

            // Set hour and time to draw's
            currentCal.set(Calendar.HOUR_OF_DAY, hours[gameType - 1])
            currentCal.set(Calendar.MINUTE, minutes[gameType - 1])

            // Consider Sayisal Loto draws on Wednesday (at 21:45 unlike 21:15 on Saturday)
            if ((gameType == 2 || gameType == 8) && currentCal.get(Calendar.DAY_OF_WEEK) == Calendar.WEDNESDAY)
                currentCal.add(Calendar.MINUTE, 30)

            return currentCal
        }

        /**
         * Provides correct draw time information for a given date and game type.
         */
        private fun getDrawTime (gameType: Int, c: Calendar): Calendar {
            val calendar = c.clone() as Calendar
            calendar.set(Calendar.HOUR_OF_DAY, hours[gameType - 1])
            calendar.set(Calendar.MINUTE, minutes[gameType - 1])
            if (gameType == 1)
                calendar.add(Calendar.HOUR_OF_DAY, 3) // Draws starts at 15:00 but results are available after 18:00
            else if ((gameType == 2 || gameType == 8) && calendar.get(Calendar.DAY_OF_WEEK) == Calendar.WEDNESDAY)
                calendar.add(Calendar.MINUTE, 30)
            return calendar
        }

        /**
         * Checks if the ticket with a given date is drawn
         */
        fun isDrawn(game: Int, dateToCheck: String): Boolean {
            // Get current date
            val currentCal = Calendar.getInstance()
            // Get draw date and time
            var drawCal = stringToCalendar(dateToCheck)
            drawCal = findNextDrawDateAfter(drawCal, game)
            drawCal = getDrawTime(game, drawCal)
            // Compare the two
            if (!currentCal.before(drawCal)) {
                return true
            }
            return false
        }

        /**
         * Returns calendar in DDMMYYYY format.
         */
        fun calendarToString (c: Calendar): String {
            return("${String.format("%02d", c.get(Calendar.DATE))}${String.format("%02d", c.get(Calendar.MONTH) + 1)}${String.format("%02d", c.get(Calendar.YEAR))}")
        }

        /**
         * Returns calendar given in string in DDMMYYYY format.
         */
        private fun stringToCalendar (s: String): Calendar {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.YEAR, s.substring(4,8).toInt())
            cal.set(Calendar.MONTH, s.substring(2,4).toInt() - 1)
            cal.set(Calendar.DATE, s.substring(0,2).toInt())
            return cal
        }

        /**
         * Returns date value within a cursor as calendar.
         */
        private fun cursorToCalendar (c: Cursor): Calendar {
            val result = Calendar.getInstance()
            val ticketDate = c.getString(c.getColumnIndex("Date"))
            result.set(Calendar.DATE, ticketDate.substring(0, 2).toInt())
            result.set(Calendar.MONTH, ticketDate.substring(2, 4).toInt() - 1)
            result.set(Calendar.YEAR, ticketDate.substring(4, 8).toInt())
            return result
        }

        /**
         * Returns the time to check the result for a given ticket.
         */
        fun findCheckTime (c: Cursor): Calendar {
            val gameType = c.getInt(c.getColumnIndex("GameType"))
            val current = Calendar.getInstance()
            val ticketDate = cursorToCalendar(c)
            val drawTime = getDrawTime(gameType, ticketDate)

            // Does not require frequent check, just start checking at draw time
            return if (drawTime.after(current)) {
                drawTime
            }
            else {
                // In frequent check zone (First 15 minutes after draw time): Check each minute
                val upperCheckLimit = drawTime.clone() as Calendar
                upperCheckLimit.add(Calendar.MINUTE, 15) // Each minute for 15 minutes
                if (upperCheckLimit.after(current)) {
                    current.add(Calendar.MINUTE, 1)
                    current
                }
                else {
                    // In infrequent check zone (15-30 minutes after draw)
                    upperCheckLimit.add(Calendar.MINUTE, 15) // Each 5 minutes for 15 minutes
                    if (upperCheckLimit.after(current)) {
                        current.add(Calendar.MINUTE, 5)
                        current
                    }
                    else {
                        // In infrequent check zone (30-60 minutes after draw): Check 30 minutes after draw time
                        upperCheckLimit.add(Calendar.MINUTE, 30) // Each 15 minutes for 30 minutes
                        if (upperCheckLimit.after(current)) {
                            current.add(Calendar.MINUTE, 15)
                            current
                        }
                        else {
                            // In infrequent check zone (1-2 hours after draw)
                            upperCheckLimit.add(Calendar.MINUTE, 60) // Each 30 minutes for 60 minutes
                            if (upperCheckLimit.after(current)) {
                                current.add(Calendar.MINUTE, 30)
                                current
                            }
                            // Already passed, check 7 days later again
                            else {
                                // In infrequent check zone (2-8 hours after draw)
                                upperCheckLimit.add(Calendar.HOUR_OF_DAY, 6) // Each 1 hour for 6 hours
                                if (upperCheckLimit.after(current)) {
                                    current.add(Calendar.MINUTE, 60)
                                    current
                                }
                                // Already passed, check 1 days later again
                                else {
                                    current.add(Calendar.DATE, 1)
                                    current
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
