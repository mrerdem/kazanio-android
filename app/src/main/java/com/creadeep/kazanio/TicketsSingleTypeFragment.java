package com.creadeep.kazanio;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.creadeep.kazanio.data.TicketEntity;
import com.creadeep.kazanio.data.TicketListViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
Child fragment that holds recyclerView for each ticket type in Tickets Fragment
 */
public class TicketsSingleTypeFragment extends Fragment {
    private Context context;
    private RecyclerView recyclerView;
    private List<TicketEntity> ticketList = new ArrayList<>();
    private TicketDbHelper ticketDb;
    private TicketsAdapter adapter;
    private CoordinatorLayout coordinatorLayout;
    private TextView textViewEmptyDb;
    private int type;

    private FragmentTransaction fragmentTransaction; // To change to a different fragment
    private Bundle resultBundle; // Contains data to be passed to the next fragment

    private TicketListViewModel ticketListViewModel;

    public TicketsSingleTypeFragment(int t) {
        type = t;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ticketDb = TicketDbHelper.getInstance(getActivity());
        adapter = new TicketsAdapter(getActivity(), ticketList);
        FragmentActivity activity = getActivity();
        if (activity != null)
            ticketListViewModel = new ViewModelProvider(getActivity()).get(TicketListViewModel.class);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tickets_single_type, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.recycler_view);
        coordinatorLayout = view.findViewById(R.id.coordinator_layout);
        textViewEmptyDb = view.findViewById(R.id.tv_empty_db);

        // Populate recyclerview
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);

        // Set RecyclerView Adapter as LiveData observer for the tickets
        ticketListViewModel.getAllTickets(type).observe(getViewLifecycleOwner(), ticketEntities -> {
            adapter.setTicketsList(ticketEntities);
            adapter.notifyDataSetChanged();

            if(ticketEntities.size() != 0) { // Hide empty dB warning if necessary
                textViewEmptyDb.setVisibility(View.GONE);
            }
            else{
                recyclerView.setVisibility(View.GONE);
                textViewEmptyDb.setVisibility(View.VISIBLE);
            }
        });

        // Recycler view touch actions
        recyclerView.addOnItemTouchListener(
                new RecyclerItemClickListener(getActivity(), recyclerView ,new RecyclerItemClickListener.OnItemClickListener() {
                    @Override public void onItemClick(View view, int position) {
                        RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(position);
                        if (viewHolder != null) {
                            TextView tvItemNumber = viewHolder.itemView.findViewById(R.id.number);
                            TextView tvItemDate = viewHolder.itemView.findViewById(R.id.date);
                            TextView tvItemType = viewHolder.itemView.findViewById(R.id.type);

                            String tmp = tvItemDate.getText().toString();
                            String dateFormatted = tmp.substring(0, 2) + tmp.substring(3, 5) + tmp.substring(6, 10);

                            String[] gameNames = getResources().getStringArray(R.array.game_names);
                            resultBundle.putSerializable("Ticket", ticketDb.fetchTicketFromDb(Arrays.asList(gameNames).indexOf(tvItemType.getText().toString()) + 1, tvItemNumber.getText().toString(), dateFormatted));
                            ResultFragment resultFragment = new ResultFragment();
                            resultFragment.setArguments(resultBundle);
                            fragmentTransaction.replace(R.id.fragment_container, resultFragment);
                            fragmentTransaction.addToBackStack(null); // To enable back button come back to tickets list
                            fragmentTransaction.commit();
                        }
                    }

                    @Override public void onLongItemClick(View view, int position) {
                        RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(position);
                        if (viewHolder != null) {
                            TextView itemNumber = viewHolder.itemView.findViewById(R.id.number);
                            TextView itemDate = viewHolder.itemView.findViewById(R.id.date);
                            new MaterialAlertDialogBuilder(context)
                                    .setTitle(getResources().getString(R.string.dialog_title_delete))
                                    .setPositiveButton(getResources().getString(R.string.dialog_accept), (dialog, which) -> {
                                        String dateFormatted = TicketUtils.getUnformattedDate(itemDate.getText().toString());
                                        if (dateFormatted != null) {
                                            ticketListViewModel.deletePartlyKnown(itemNumber.getText().toString(), dateFormatted);
                                            adapter.notifyDataSetChanged();
                                            Snackbar.make(coordinatorLayout, R.string.ticket_removal_info, Snackbar.LENGTH_LONG).show();
                                            AppUtils.Companion.enableResultService(context); // Update result service alarm time
                                            AppUtils.Companion.enableReminderService(context); // Update reminder service alarm time
                                        }
                                    })
                                    .setNegativeButton(getResources().getString(R.string.dialog_cancel), null)
                                    .show();
                        }
                    }
                })
        );
    }

    @Override
    public void onResume() {
        super.onResume();

        // Prepare for transition to result fragment
        FragmentActivity activity = getActivity();
        if (activity != null) {
            fragmentTransaction = activity.getSupportFragmentManager().beginTransaction();
            fragmentTransaction.setCustomAnimations(R.animator.flip_right_in, R.animator.flip_right_out, R.animator.flip_left_in, R.animator.flip_left_out);
            // Set bundle to pass to result fragment
            resultBundle = new Bundle();
        }
    }
}
