package com.creadeep.kazanio.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "TicketData", indices = [Index(value = ["GameType", "Number", "Date"], unique = true)])
data class TicketEntity(
        @PrimaryKey(autoGenerate = true)
        var Id: Int?,
        var GameType: String?,
        var TicketType: String?,
        var Number: String?,
        var Date: String?,
        var Fraction: String?,
        var Prize: String?,
        var Notify: Int?,
        var Drawn: Int?,
        var Tease: Int?
)
