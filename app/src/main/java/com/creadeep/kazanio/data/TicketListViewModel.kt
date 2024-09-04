package com.creadeep.kazanio.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData

/**
 * Stores the list of tickets to be shown in a RecyclerView in the tickets fragment.
 * If there is a new ticket that is being checked in another thread, the result of this thread is reflected on the UI right away.
 * Similarly, if a user deletes a ticket from any tab on Tickets fragment, the ticket also gets deleted from other tabs where it is listed.
 */
class TicketListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TicketRepository?
    private val allTickets: LiveData<List<TicketEntity>>?

    init {
        repository = TicketRepository(application)
        allTickets = repository.getAllTickets(0)
    }

    fun insert(ticket: TicketEntity){
        repository?.insert(ticket)
    }

    fun deletePartlyKnown(number: String, date: String){
        repository?.deletePartlyKnown(number, date)
    }

    fun getAllTickets(type: Int): LiveData<List<TicketEntity>>?{
        return repository?.getAllTickets(type)
    }

}
