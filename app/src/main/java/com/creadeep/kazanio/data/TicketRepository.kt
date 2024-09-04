package com.creadeep.kazanio.data

import android.app.Application
import android.os.AsyncTask
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

class TicketRepository (application: Application): CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main
    private val dao: TicketDao?

    init{
        val database = TicketDatabase.getInstance(application)
        dao = database?.ticketDao()
    }

    fun insert(ticket: TicketEntity){
        InsertAsyncTask(dao).execute(ticket) // TODO: Replace AsyncTask with Coroutines/RxJava
    }

    fun delete(ticket: TicketEntity){
        DeleteAsyncTask(dao).execute(ticket) // TODO: Replace AsyncTask with Coroutines/RxJava
    }

    fun deletePartlyKnown(number: String, date: String){
        DeletePartlyKnownAsyncTask(dao, number, date).execute() // TODO: Replace AsyncTask with Coroutines/RxJava
    }

    fun getAllTickets(type: Int) = when(type) {
        0 -> dao?.getAllTickets() // Get all the tickets if at 0th tab TODO: Perform this operation asynchronously
        else -> dao?.getAllTicketsOfType(type) // Get corresponding tickets only based on tab index TODO: Perform this operation asynchronously
    }

    fun getTicket(gameType: Int, number: String, date: String) = dao?.getTicket(gameType, number, date)

    private class InsertAsyncTask internal constructor(private val mAsyncTaskDao: TicketDao?) : AsyncTask<TicketEntity, Void, Void>() {
        override fun doInBackground(vararg params: TicketEntity): Void? {
            mAsyncTaskDao?.insert(params[0])
            return null
        }
    }

    private class DeleteAsyncTask internal constructor(private val mAsyncTaskDao: TicketDao?) : AsyncTask<TicketEntity, Void, Void>() {
        override fun doInBackground(vararg params: TicketEntity): Void? {
            mAsyncTaskDao?.delete(params[0])
            return null
        }
    }

    private class DeletePartlyKnownAsyncTask internal constructor(private val mAsyncTaskDao: TicketDao?, private val number: String, private val date: String) : AsyncTask<Void, Void, Void>() {
        override fun doInBackground(vararg params: Void): Void? {
            mAsyncTaskDao?.deletePartlyKnown(number, date)
            return null
        }
    }
}
