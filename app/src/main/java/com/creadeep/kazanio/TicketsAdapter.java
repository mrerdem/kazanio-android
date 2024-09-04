package com.creadeep.kazanio;

import android.content.Context;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.creadeep.kazanio.data.TicketEntity;

import java.util.List;

/*
Used to fill in the RecyclerView in child fragments in Tickets Fragment
 */
public class TicketsAdapter extends RecyclerView.Adapter<TicketsAdapter.MyViewHolder> {
  private List<TicketEntity> ticketsList;
  private Context context;

  public TicketsAdapter(Context context, List<TicketEntity> ticketsList) {
    this.ticketsList = ticketsList;
    this.context = context;
  }

  public static class MyViewHolder extends RecyclerView.ViewHolder {
    public TextView type, number, date, prize;
    public ImageView notificationIcon, prizeTeaseIcon;

    public MyViewHolder(View view) {
      super(view);
      type = view.findViewById(R.id.type);
      number = view.findViewById(R.id.number);
      date = view.findViewById(R.id.date);
      prize = view.findViewById(R.id.prize);
      notificationIcon = view.findViewById(R.id.imageViewNotificationIcon);
      prizeTeaseIcon = view.findViewById(R.id.prizeTeaseIcon);
    }
  }

  @Override
  public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View itemView = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.ticket_recyclerview_item, parent, false);
    return new MyViewHolder(itemView);
  }

  @Override
  public void onBindViewHolder(MyViewHolder holder, int position) {
    TicketEntity ticket = ticketsList.get(position);
    String ticketGameType = ticket.getGameType();
    if (ticketGameType != null)
      holder.type.setText(context.getResources().getStringArray(R.array.game_names)[Integer.parseInt(ticketGameType) - 1]);
    holder.number.setText(ticket.getNumber());
    String ticketDate = ticket.getDate();
    if (ticketDate != null)
      holder.date.setText(TicketUtils.getFormattedDate(ticketDate));
    if (ticket.getPrize() != null) { // prize is known
      holder.notificationIcon.setVisibility(View.GONE);
      Integer tease = ticket.getTease();
      if (tease != null) {
        if (tease == 0) { // Teasing is not desired
          holder.prizeTeaseIcon.setVisibility(View.GONE);
          double prizeValue = Double.parseDouble(ticket.getPrize());
          holder.prize.setText(String.format(AppUtils.Companion.getCurrentLocale(), "%,.2f₺", prizeValue));
          if (prizeValue == 0.0) {
            holder.prize.setTextColor(ContextCompat.getColor(context, R.color.grey_font));
          }
          else {
            holder.prize.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary));
          }
          holder.prize.setVisibility(View.VISIBLE);
        }
        else { // Tease the result
          holder.prize.setVisibility(View.GONE);
          holder.prizeTeaseIcon.setVisibility(View.VISIBLE);
        }
      }
    }
    else { // lottery not drawn yet, assign notification icon based on notify status
      holder.prize.setVisibility(View.GONE);
      holder.prizeTeaseIcon.setVisibility(View.GONE);
      Integer ticketNotify = ticket.getNotify();
      if (ticketNotify != null) {
        if (ticketNotify == 1) { // auto-checker is active
          holder.notificationIcon.setImageResource(R.drawable.ic_notifications_on_green_24dp);
        }
        else {
          holder.notificationIcon.setImageResource(R.drawable.ic_notifications_off_grey_24dp);
        }
      }
      holder.notificationIcon.setVisibility(View.VISIBLE);
    }
  }

  public void setTicketsList(List<TicketEntity> ticketsList) {
    this.ticketsList = ticketsList;
  }

  @Override
  public int getItemCount() {
    return ticketsList.size();
  }
}
