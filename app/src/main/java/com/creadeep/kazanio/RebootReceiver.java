package com.creadeep.kazanio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

public class RebootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
      // Enable reminder service
      AppUtils.Companion.enableReminderService(context);

      // Enable result service
      TicketDbHelper ticketDb = TicketDbHelper.getInstance(context);
      try (Cursor ticketCursor = ticketDb.getAllUndrawnTickets()) {
        if (ticketCursor.getCount() > 0) {
            AppUtils.Companion.enableResultService(context);
        }
      }
    }
  }
}
