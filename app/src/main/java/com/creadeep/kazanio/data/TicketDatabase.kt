package com.creadeep.kazanio.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TicketEntity::class], version = 4)
abstract class TicketDatabase: RoomDatabase() {
    abstract fun ticketDao(): TicketDao

    companion object {
        private var INSTANCE: TicketDatabase? = null

        fun getInstance(context: Context): TicketDatabase? {
            if (INSTANCE == null) {
                synchronized(TicketDatabase::class) {
                    INSTANCE = Room.databaseBuilder(context.applicationContext,
                            TicketDatabase::class.java, "Kazanio.db")
                            .build()
                }
            }
            return INSTANCE
        }
    }
}
