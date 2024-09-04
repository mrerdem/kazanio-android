package com.creadeep.kazanio;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

/*
Child fragment that holds profit/loss stat for each ticket type in Stats Fragment
 */
public class StatsSingleTypeFragment extends Fragment {
    private View v;
    private int type;
    private TicketDbHelper ticketDb;
    private double net = 0f;
    private double profit = 0f;
    private double loss = 0f;

    StatsSingleTypeFragment(int t) {
        type = t;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.fragment_stats_single_type, container, false);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ticketDb = TicketDbHelper.getInstance(getActivity());
        TextView textViewEmpty = v.findViewById(R.id.tv_stats_empty);
        TextView textViewNet = v.findViewById(R.id.tv_net);
        TextView textViewProfit = v.findViewById(R.id.tv_profit);
        TextView textViewLoss = v.findViewById(R.id.tv_loss);
        ConstraintLayout allLabels = v.findViewById(R.id.all_labels);

        if(prepareTicketData()){
            // Net
            textViewNet.setText(String.format(AppUtils.Companion.getCurrentLocale(), "%,.2f₺", net));
            if (net > 0)
                textViewNet.setTextColor(getResources().getColor(R.color.colorPrimary));
            else if (net < 0)
                textViewNet.setTextColor(getResources().getColor(R.color.red_font));
            // Profit
            textViewProfit.setText(String.format(AppUtils.Companion.getCurrentLocale(), "%,.2f₺", profit));
            if (profit > 0)
                textViewProfit.setTextColor(getResources().getColor(R.color.colorPrimary));
            // Loss
            textViewLoss.setText(String.format(AppUtils.Companion.getCurrentLocale(), "%,.2f₺", loss));
            if (loss > 0)
                textViewLoss.setTextColor(getResources().getColor(R.color.red_font));
            // Empty textView
            textViewEmpty.setVisibility(View.INVISIBLE);
        }
        else{
            allLabels.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private boolean prepareTicketData() { // Reads from dB and fills in RecylerView, returns 0 if db is empty

        Cursor ticketCursor;
        net = 0f;
        profit = 0f;
        loss = 0f;

        if (type == 0) // Sum all results
            ticketCursor = ticketDb.getAllTickets();
        else // Check single result
            ticketCursor = ticketDb.getAllTicketsOfType(type);
        if (ticketCursor.getCount() == 0) { // Do nothing if there is no ticket in the dB
            ticketCursor.close();
            return false;
        }
        else {
            try {
                while (ticketCursor.moveToNext()) {
                    // Profit calculation
                    String prize = ticketCursor.getString(ticketCursor.getColumnIndex("Prize"));
                    if (prize != null) { // if prize is known
                        profit += Float.parseFloat(prize);
                    }
                    // Loss calculation
                    loss += TicketUtils.findTicketCost(
                            Integer.parseInt(ticketCursor.getString(ticketCursor.getColumnIndex("GameType"))),
                            ticketCursor.getString(ticketCursor.getColumnIndex("Fraction")),
                            ticketCursor.getString(ticketCursor.getColumnIndex("Date"))
                    );
                }
                // Net calculation
                net = profit - loss;
            } finally {
                ticketCursor.close();
            }
            return true;
        }
    }
}
