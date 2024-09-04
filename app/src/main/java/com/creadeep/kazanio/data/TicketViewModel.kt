package com.creadeep.kazanio.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData

/**
 * Stores information for a single ticket to be used in ResultFragment.
 * If the result is updated in a background thread, it is reflected on UI thanks to LiveData.
 */
class TicketViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TicketRepository?
    private val ticket: LiveData<TicketEntity>?

    init {
        repository = TicketRepository(application)
        ticket = repository.getTicket(1, "123456", "31129999")
    }

    fun insert(ticket: TicketEntity){
        repository?.insert(ticket)
    }

    fun delete(ticket: TicketEntity){
        repository?.delete(ticket)
    }

    fun deletePartlyKnown(number: String, date: String){
        repository?.deletePartlyKnown(number, date)
    }

    fun getAllTickets(type: Int): LiveData<List<TicketEntity>>?{
        return repository?.getAllTickets(type)
    }

    fun getTicket(type: Int, number: String, date: String): LiveData<TicketEntity>?{
        return repository?.getTicket(type, number, date)
    }
}
