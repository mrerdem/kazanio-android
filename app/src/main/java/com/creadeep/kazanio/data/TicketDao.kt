package com.creadeep.kazanio.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query

@Dao
interface TicketDao {

    @Insert(onConflict = REPLACE)
    fun insert(ticket: TicketEntity)

    @Delete
    fun delete(ticket: TicketEntity)

    @Query("DELETE FROM TicketData WHERE Number = :number AND Date = :date")
    fun deletePartlyKnown(number: String, date: String) // TODO: Use gameType also

    @Query("SELECT * FROM TicketData ORDER BY Tease DESC, Drawn ASC, Id DESC")
    fun getAllTickets(): LiveData<List<TicketEntity>>

    @Query("SELECT * FROM TicketData WHERE GameType = :type ORDER BY Tease DESC, Drawn ASC, Id DESC")
    fun getAllTicketsOfType(type: Int): LiveData<List<TicketEntity>>

    @Query("SELECT * FROM TicketData WHERE GameType = :type AND Number = :number AND Date = :date ORDER BY Drawn, Id DESC")
    fun getTicket(type: Int, number: String, date: String): LiveData<TicketEntity>

    @Query("SELECT * FROM TicketData WHERE GameType = :type AND Number = :number AND Date = :date ORDER BY Drawn, Id DESC")
    fun getId(type: Int, number: String, date: String): LiveData<TicketEntity>

}
