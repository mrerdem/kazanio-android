package com.creadeep.kazanio;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.creadeep.kazanio.data.TicketEntity;
import com.creadeep.kazanio.data.TicketListViewModel;
import com.creadeep.kazanio.data.TicketViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class ResultFragment extends Fragment {
    private Context context;
    private TextView mTextViewNumber; // TextView for detected numbers
    private TextView mTextViewDate; // TextView for detected draw date
    private TextView mTextViewResult; // TextView for status information ("checking result")
    private TextView mTextViewFraction; // TextView for fraction information (ceyrek, yarim, vs.)
    private TextView mTextViewPrice; // TextView for price info
    private ImageView mImageViewOverlay; // ImageView for ticket overlay
    private boolean updateNotifications = false; // Flag to update notification alarms when a new ticket is inserted
    private boolean isChecking = false; // Flag to represent the status of web query (used to hide/show loading animation)

    private CoordinatorLayout coordinatorLayout; // To be used with SnackBar
    private ProgressBar loadProgress; // Activated while querying the result from server

    private String[] mQueryUrl = {
            "http://www.mpi.gov.tr/sonuclar/cekilisler/piyango/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/sayisal/SAY_",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/superloto/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/onnumara/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/sanstopu/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/superpiyango/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/bankopiyango/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/supersayisal/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/paraloto/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/superonnumara/",
            "http://www.mpi.gov.tr/sonuclar/cekilisler/supersanstopu/"
    };
    private int gameType;
    private int ticketType; // Classic, Terminal, New Year's Eve
    private String mNumberText;
    private String mDateText;
    private String mFractionText;
    private Boolean mIsHit = false;
    private TicketDbHelper ticketDb;
    private boolean mAutoCheckerActive = true;
    private int autoNotifyStatus = 0; // Used to decide which menu items to be shown, 0:N/A, 1:active, 2:deactive

    private TicketListViewModel ticketListViewModel; // Used to insert ticket into database table to trigger LiveData feed to tickets fragment's RecyclerViews

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        context = getContext();
        ticketDb = TicketDbHelper.getInstance(context);

        // Get ticket data sent from other fragment
        if (getArguments() != null) {
            Ticket mTicket = (Ticket) getArguments().getSerializable("Ticket");
            if (mTicket != null) {
                gameType = mTicket.getGameType();
                mNumberText = mTicket.getNumber();
                mDateText = mTicket.getDate();
                mFractionText = mTicket.getFraction();
                ticketType = mTicket.getTicketType();
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (gameType == 1 && ticketType == 2)
            return (inflater.inflate(R.layout.fragment_result_mp_classic, container, false));
        else if (gameType == 3) // Super Loto
            return (inflater.inflate(R.layout.fragment_result_super_loto, container, false));
        else if (gameType == 4 || gameType == 10) // On Numara, Super On Numara
            return (inflater.inflate(R.layout.fragment_result_on_numara, container, false));
        else if (gameType == 5 || gameType == 11) // Sans Topu, Super Sans Topu
            return (inflater.inflate(R.layout.fragment_result_sans_topu, container, false));
        else
            return (inflater.inflate(R.layout.fragment_result_terminal, container, false));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mImageViewOverlay = view.findViewById(R.id.overlay_ticket);
        mTextViewNumber = view.findViewById(R.id.tv_number);
        mTextViewDate = view.findViewById(R.id.tv_date);
        mTextViewResult = view.findViewById(R.id.tv_result);
        mTextViewFraction = view.findViewById(R.id.tv_fraction);
        mTextViewPrice = view.findViewById(R.id.tv_price);
        coordinatorLayout = view.findViewById(R.id.coordinator_layout);
        loadProgress = view.findViewById(R.id.loadingAnimation);

        // Set the ratio of the ticket card
        view.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // Layout has happened here.
                        ConstraintSet set = new ConstraintSet();
                        Activity activity = getActivity();
                        if (activity != null) {
                            ConstraintLayout mLayout = activity.findViewById(R.id.constraintLayout);
                            set.clone(mLayout);
                            // calculate the ratio of the frame with 5dp margin all around, since we only know the card's ratio
                            int frameHeight = mLayout.getHeight();
                            float marginDp = 5f; // card margin is 5dp
                            Resources r = getResources();
                            float marginPx = TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP,
                                    marginDp,
                                    r.getDisplayMetrics()
                            );

                            // Set ratio except for new (terminal) Milli Piyango, Super Piyango, Banko Piyango
                            if (!((gameType == 1 && ticketType == 1) || gameType == 6 || gameType == 7)) {
                                int frameWidth = 0;
                                if (gameType == 1 && ticketType == 2) // Classical piyango ticket
                                    frameWidth = Math.round((frameHeight - 2 * marginPx) / (61 + 20) * 148 + 2 * marginPx);
                                else if (gameType == 2 || gameType == 8 || gameType == 9) // Sayisal Loto, Super Sayisal Loto, Para Loto
                                    frameWidth = Math.round((frameHeight - 2 * marginPx) / (((Float.parseFloat(mFractionText) - 1) * 45) / 7 + 124) * 82 + 2 * marginPx); // 1 row: 9mm, 8 rows: 54mm, 82: ticket width, 124: ticket height + 14
                                else if (gameType == 3) // Super Loto
                                    frameWidth = Math.round((frameHeight - 2 * marginPx) / (((Float.parseFloat(mFractionText) - 1) * 45) / 7 + 114) * 82 + 2 * marginPx); // 1 row: 9mm, 8 rows: 54mm, 82: ticket width, 124: ticket height + 14
                                else if (gameType == 4 || gameType == 10) // On Numara, Super On Numara
                                    frameWidth = Math.round((frameHeight - 2 * marginPx) / (((Float.parseFloat(mFractionText) - 1) * 40) / 4 + 114) * 82 + 2 * marginPx); // 1 row: 11mm, 5 rows: 51mm, 82: ticket width, 124: ticket height + 14
                                else if (gameType == 5 || gameType == 11) // Sans Topu, Super Sans Topu
                                    frameWidth = Math.round((frameHeight - 2 * marginPx) / (((Float.parseFloat(mFractionText) - 1) * 40) / 4 + 114) * 82 + 2 * marginPx); // 1 row: 11mm, 5 rows: 51mm, 82: ticket width, 124: ticket height + 14
                                String ratio = "V," + frameWidth + ":" + frameHeight;
                                set.setDimensionRatio(R.id.frameLayout, ratio);
                                set.applyTo(mLayout);
                            }
                        }
                        // Don't forget to remove your listener when you are done with it.
                        view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        FragmentActivity activity = getActivity();
        if (activity != null) {
            ticketListViewModel = new ViewModelProvider(activity).get(TicketListViewModel.class);
            // Stores currently shown ticket's data
            TicketViewModel ticketViewModel = new ViewModelProvider(activity).get(TicketViewModel.class);

            LiveData<TicketEntity> viewModelTicket = ticketViewModel.getTicket(gameType, mNumberText, mDateText);
            if (viewModelTicket != null) {
                try {
                    viewModelTicket.observe(getViewLifecycleOwner(), ticketEntity -> {
                        // Set image & ticket info
                        switch (gameType) {
                            // Milli Piyango
                            case 1:
                                mImageViewOverlay.setImageDrawable(getResources().getDrawable(getResources().getIdentifier("overlay_milli_piyango_" + ticketType, "drawable", context.getPackageName()), context.getTheme()));
                                // Set text views with preliminary info
                                mTextViewNumber.setText(mNumberText);
                                String tmpDateString;
                                if (ticketType == 2 || ticketType == 3) { // Classical / New Year's Eve Milli Piyango
                                    tmpDateString = mDateText.substring(4, 8) + "\t\t\t" + mDateText.substring(0, 2) + " ";
                                    switch (mDateText.substring(2, 4)) {
                                        case "01":
                                            tmpDateString += "OCAK";
                                            break;
                                        case "02":
                                            tmpDateString += "ŞUBAT";
                                            break;
                                        case "03":
                                            tmpDateString += "MART";
                                            break;
                                        case "04":
                                            tmpDateString += "NİSAN";
                                            break;
                                        case "05":
                                            tmpDateString += "MAYIS";
                                            break;
                                        case "06":
                                            tmpDateString += "HAZİRAN";
                                            break;
                                        case "07":
                                            tmpDateString += "TEMMUZ";
                                            break;
                                        case "08":
                                            tmpDateString += "AĞUSTOS";
                                            break;
                                        case "09":
                                            tmpDateString += "EYLÜL";
                                            break;
                                        case "10":
                                            tmpDateString += "EKİM";
                                            break;
                                        case "11":
                                            tmpDateString += "KASIM";
                                            break;
                                        case "12":
                                            tmpDateString += "ARALIK";
                                            break;
                                    }
                                    mTextViewDate.setText(tmpDateString);
                                } else // Terminal Milli Piyango
                                    mTextViewDate.setText("ÇEKİLİŞ TARİHİ: " + mDateText.substring(0, 2) + "." + mDateText.substring(2, 4) + "." + mDateText.substring(4, 8));

                                // Set price text
                                if (ticketType == 1) { // Terminal
                                    switch (mFractionText) {
                                        case "11":
                                            mTextViewFraction.setText("1/1 TAM BİLET");
                                            break;
                                        case "12":
                                            mTextViewFraction.setText("1/2 YARIM BİLET");
                                            break;
                                        case "14":
                                            mTextViewFraction.setText("1/4 ÇEYREK BİLET");
                                            break;
                                    }
                                    mTextViewPrice.setText(String.format(AppUtils.Companion.getCurrentLocale(), "%.2f TL", TicketUtils.findTicketCost(gameType, mFractionText, mDateText)));
                                } else if (ticketType == 2 || ticketType == 3) { // Classical or New Year's Eve Lottery
                                    switch (mFractionText) {
                                        case "11":
                                            mTextViewFraction.setText("1/1 TAM BİLET");
                                            break;
                                        case "12":
                                            mTextViewFraction.setText("1/2 YARIM BİLET");
                                            break;
                                        case "14":
                                            mTextViewFraction.setText("1/4 DÖRTTE BİR BİLET");
                                            break;
                                    }
                                    mTextViewPrice.setText(Math.round(TicketUtils.findTicketCost(gameType, mFractionText, mDateText)) + " TL");
                                }

                                LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        0,
                                        9.0f
                                );
                                mTextViewNumber.setLayoutParams(param);
                                break;

                            // Sayisal Loto, Super Loto, Super Sayisal Loto, Para Loto
                            case 2:
                            case 3:
                            case 8:
                            case 9:
                                if (gameType == 2)
                                    mImageViewOverlay.setImageDrawable(getResources().getDrawable(getResources().getIdentifier("overlay_sayisal_loto_" + String.valueOf(mFractionText), "drawable", context.getPackageName()), context.getTheme()));
                                else if (gameType == 3)
                                    mImageViewOverlay.setImageDrawable(getResources().getDrawable(getResources().getIdentifier("overlay_super_loto_" + String.valueOf(mFractionText), "drawable", context.getPackageName()), context.getTheme()));
                                else if (gameType == 8)
                                    mImageViewOverlay.setImageDrawable(getResources().getDrawable(getResources().getIdentifier("overlay_super_sayisal_loto_" + String.valueOf(mFractionText), "drawable", context.getPackageName()), context.getTheme()));
                                else
                                    mImageViewOverlay.setImageDrawable(getResources().getDrawable(getResources().getIdentifier("overlay_para_loto_" + String.valueOf(mFractionText), "drawable", context.getPackageName()), context.getTheme()));
                                String numberString = null;

                                numberString = mNumberText.substring(0, 2) + " " + mNumberText.substring(2, 4) + " " + mNumberText.substring(4, 6) + " " + mNumberText.substring(6, 8) + " " + mNumberText.substring(8, 10) + " " + mNumberText.substring(10, 12);
                                for (int i = 1; i < mNumberText.length() / 12; i++) {
                                    numberString += "\n";
                                    numberString += mNumberText.substring(i * 12, i * 12 + 2) + " " + mNumberText.substring(i * 12 + 2, i * 12 + 4) + " " + mNumberText.substring(i * 12 + 4, i * 12 + 6) + " " + mNumberText.substring(i * 12 + 6, i * 12 + 8) + " " + mNumberText.substring(i * 12 + 8, i * 12 + 10) + " " + mNumberText.substring(i * 12 + 10, i * 12 + 12);
                                }
                                mTextViewNumber.setLetterSpacing(0.0f);
                                mTextViewNumber.setScaleX(0.8f);
                                mTextViewNumber.setLineSpacing(0.0f, 0.8f);
                                mTextViewNumber.setText(numberString);

                                // Set height of number area based on number of rows
                                param = new LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        0,
                                        9.0f + (Integer.parseInt(mFractionText) - 1) * 45f / 7f // 1 row: 9mm, 8 rows: 54mm
                                );
                                mTextViewNumber.setLayoutParams(param);

                                mTextViewDate.setText(mDateText.substring(0, 2) + "." + mDateText.substring(2, 4) + "." + mDateText.substring(4, 8));
                                mTextViewPrice.setText(String.format(AppUtils.Companion.getCurrentLocale(), "%.2f TL", TicketUtils.findTicketCost(gameType, mFractionText, mDateText)));
                                break;

                            // On Numara, Super On Numara
                            case 4:
                            case 10:
                                if (gameType == 4)
                                    mImageViewOverlay.setImageDrawable(getResources().getDrawable(getResources().getIdentifier("overlay_on_numara_" + String.valueOf(mFractionText), "drawable", context.getPackageName()), context.getTheme()));
                                else
                                    mImageViewOverlay.setImageDrawable(getResources().getDrawable(getResources().getIdentifier("overlay_super_on_numara_" + String.valueOf(mFractionText), "drawable", context.getPackageName()), context.getTheme()));
                                numberString = mNumberText.substring(0, 2) + " " + mNumberText.substring(2, 4) + " " + mNumberText.substring(4, 6) + " " + mNumberText.substring(6, 8) + " " + mNumberText.substring(8, 10) + " " + mNumberText.substring(10, 12) + " " + mNumberText.substring(12, 14) + " " + mNumberText.substring(14, 16) + " " + mNumberText.substring(16, 18) + " " + mNumberText.substring(18, 20);
                                for (int i = 1; i < mNumberText.length() / 20; i++) {
                                    numberString += "\n";
                                    numberString += mNumberText.substring(i * 20, i * 20 + 2) + " " + mNumberText.substring(i * 20 + 2, i * 20 + 4) + " " + mNumberText.substring(i * 20 + 4, i * 20 + 6) + " " + mNumberText.substring(i * 20 + 6, i * 20 + 8) + " " + mNumberText.substring(i * 20 + 8, i * 20 + 10) + " " + mNumberText.substring(i * 20 + 10, i * 20 + 12) + " " + mNumberText.substring(i * 20 + 12, i * 20 + 14) + " " + mNumberText.substring(i * 20 + 14, i * 20 + 16) + " " + mNumberText.substring(i * 20 + 16, i * 20 + 18) + " " + mNumberText.substring(i * 20 + 18, i * 20 + 20);
                                }
                                mTextViewNumber.setLetterSpacing(-0.1f);
                                mTextViewNumber.setScaleX(0.8f);
                                mTextViewNumber.setLineSpacing(0.0f, 1.4f);
                                mTextViewNumber.setText(numberString);

                                // Set height of number area based on number of rows
                                param = new LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        0,
                                        11.0f + (Integer.parseInt(mFractionText) - 1) * 40f / 4f // 1 row: 9mm, 8 rows: 54mm
                                );
                                mTextViewNumber.setLayoutParams(param);

                                mTextViewDate.setText(mDateText.substring(0, 2) + "." + mDateText.substring(2, 4) + "." + mDateText.substring(4, 8));
                                mTextViewPrice.setText(String.format(AppUtils.Companion.getCurrentLocale(), "%.2f TL", TicketUtils.findTicketCost(gameType, mFractionText, mDateText)));
                                break;

                            // Sans Topu
                            case 5:
                            case 11:
                                if (gameType == 5)
                                    mImageViewOverlay.setImageDrawable(getResources().getDrawable(getResources().getIdentifier("overlay_sanstopu_" + String.valueOf(mFractionText), "drawable", context.getPackageName()), context.getTheme()));
                                else
                                    mImageViewOverlay.setImageDrawable(getResources().getDrawable(getResources().getIdentifier("overlay_super_sanstopu_" + String.valueOf(mFractionText), "drawable", context.getPackageName()), context.getTheme()));
                                numberString = mNumberText.substring(0, 2) + " " + mNumberText.substring(2, 4) + " " + mNumberText.substring(4, 6) + " " + mNumberText.substring(6, 8) + " " + mNumberText.substring(8, 10) + " + " + mNumberText.substring(10, 12);
                                for (int i = 1; i < mNumberText.length() / 12; i++) {
                                    numberString += "\n";
                                    numberString += mNumberText.substring(i * 12, i * 12 + 2) + " " + mNumberText.substring(i * 12 + 2, i * 12 + 4) + " " + mNumberText.substring(i * 12 + 4, i * 12 + 6) + " " + mNumberText.substring(i * 12 + 6, i * 12 + 8) + " " + mNumberText.substring(i * 12 + 8, i * 12 + 10) + " + " + mNumberText.substring(i * 12 + 10, i * 12 + 12);
                                }
                                mTextViewNumber.setLetterSpacing(-0.1f);
                                mTextViewNumber.setScaleX(0.8f);
                                mTextViewNumber.setLineSpacing(0.0f, 1.4f);
                                mTextViewNumber.setText(numberString);

                                // Set height of number area based on number of rows
                                param = new LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        0,
                                        11.0f + (Integer.parseInt(mFractionText) - 1) * 40f / 4f // 1 row: 9mm, 8 rows: 54mm
                                );
                                mTextViewNumber.setLayoutParams(param);

                                mTextViewDate.setText(mDateText.substring(0, 2) + "." + mDateText.substring(2, 4) + "." + mDateText.substring(4, 8));
                                mTextViewPrice.setText(String.format(AppUtils.Companion.getCurrentLocale(), "%.2f TL", TicketUtils.findTicketCost(gameType, mFractionText, mDateText)));
                                break;

                            // Super Piyango, Banko Piyango
                            case 6:
                            case 7:
                                if (gameType == 6)
                                    mImageViewOverlay.setImageDrawable(getResources().getDrawable(getResources().getIdentifier("overlay_super_piyango", "drawable", context.getPackageName()), context.getTheme()));
                                else
                                    mImageViewOverlay.setImageDrawable(getResources().getDrawable(getResources().getIdentifier("overlay_banko_piyango", "drawable", context.getPackageName()), context.getTheme()));
                                // Set text views with preliminary info
                                mTextViewNumber.setText(mNumberText);
                                mTextViewDate.setText("ÇEKİLİŞ TARİHİ: " + mDateText.substring(0, 2) + "." + mDateText.substring(2, 4) + "." + mDateText.substring(4, 8));

                                // Set price text
                                mTextViewPrice.setText(String.format(AppUtils.Companion.getCurrentLocale(), "%.2f TL", TicketUtils.findTicketCost(gameType, mFractionText, mDateText)));

                                param = new LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        0,
                                        9.0f
                                );
                                mTextViewNumber.setLayoutParams(param);
                                break;
                        }

                        // Check dB for result
                        Ticket ticketInDb = checkDbForResult(gameType, mNumberText, mDateText);
                        if (ticketInDb != null) { // ticket is in dB
                            showResult(ticketInDb); // show result on UI
                            // Cancel teasing if it was active (opened after a notification)
                            if (ticketInDb.getTease() == 1) {
                                ticketDb.cancelTease(ticketInDb);
                            }
                            // Update notification services to consider the new ticket
                            if (updateNotifications) { // Only if there has been a new entry into the db (to prevent running also when fragment first opens)
                                AppUtils.Companion.enableReminderService(context); // Insert happens after this line!!!
                                AppUtils.Companion.enableResultService(context);
                                updateNotifications = false;
                            }
                        }
                        else { // ticket is not in db
                            Ticket ticketToProcess = new Ticket(gameType, ticketType, mNumberText, mDateText, mFractionText, null, 1, 0, 0);
                            storeTicket(ticketToProcess);
                            // Check if the draw date has come
                            if (GameUtils.Companion.isDrawn(gameType, mDateText)) { // date has arrived, check from web
                                if (AppUtils.Companion.isConnectionAvailable(context)) { // Network is available, check result from web
                                    // Inform user
                                    mTextViewResult.setText(R.string.text_result_checking);
                                    // Check from web
                                    new checkWebForResultAsyncTask().execute();
                                }
                                else { // No network is available, create one time WorkManager to check when network becomes available
                                    mTextViewResult.setText(R.string.text_error_connection);
                                }
                            }
                            else { // Not yet drawn
                                mTextViewResult.setText(R.string.text_result_undrawn);
                            }
                        }
                    });
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // Inflate the menu; this adds items to the action bar if it is present.
        menu.clear(); // Remove previous items such as activate flash or number of rows actions
        getActivity().getMenuInflater().inflate(R.menu.top_menu_result, menu);
        switch (autoNotifyStatus) {
            case 0: // N/A
                menu.getItem(0).setVisible(false);
                menu.getItem(1).setVisible(false);
                menu.getItem(2).setVisible(false);
                break;
            case 1: // Auto-notify active
                menu.getItem(0).setVisible(false);
                menu.getItem(1).setVisible(false);
                menu.getItem(2).setVisible(true);
                break;
            case 2: // Auto-notify deactive
                menu.getItem(0).setVisible(true);
                menu.getItem(1).setVisible(true);
                menu.getItem(2).setVisible(false);
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        switch (item.getItemId()) {

            case android.R.id.home: // Handle back button action
                getActivity().getSupportFragmentManager().popBackStack();
                break;

            case R.id.action_delete: // Handle the help action
                new MaterialAlertDialogBuilder(getContext())
                        .setTitle(getResources().getString(R.string.dialog_title_delete))
                        .setPositiveButton(getResources().getString(R.string.dialog_accept), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ticketDb.deleteTicketFromDb(TextUtils.removeSpaces(mNumberText), mDateText);
                                getActivity().getSupportFragmentManager().popBackStack();
                                AppUtils.Companion.enableResultService(context); // Update result service alarm time TODO (MAY RUN BEFORE DB OPERATION ENDS. MOVE TO VIEWMODEL OBSERVATION.)
                                AppUtils.Companion.enableReminderService(context); // Update reminder service alarm time
                            }
                        })
                        .setNegativeButton(getResources().getString(R.string.dialog_cancel), null)
                        .show();
                break;

            case R.id.action_share: // Handle share action
                // Save ticket area as bitmap
                View ticketView = getActivity().getWindow().getDecorView().findViewById(R.id.cardViewTicket);
                ticketView.setDrawingCacheEnabled(true);
                Bitmap result = Bitmap.createBitmap(ticketView.getDrawingCache());
                ticketView.setDrawingCacheEnabled(false);

                // Prepare for image operations
                int w = result.getWidth();
                int h = result.getHeight();
                int logoSize = Math.round(w / 25f);

                Canvas canvas = new Canvas(result);

                // Add name watermark
                Paint paint = new Paint();
                paint.setColor(getResources().getColor(R.color.colorPrimaryDark));
                paint.setAlpha(128);
                paint.setTextSize(logoSize);
                paint.setAntiAlias(true);

                canvas.drawText(getResources().getString(R.string.app_name), w - Math.round(w / 6f), h - Math.round(w / 40f), paint);

                // Add creadeep_logo watermark
                Drawable drawable = getResources().getDrawable(R.mipmap.ic_launcher);
                drawable.setBounds(w - Math.round(w / 8.7f), h - Math.round(w / 10f), w - Math.round(w / 8.7f) + logoSize, h - Math.round(w / 10f) + logoSize);
                drawable.draw(canvas);

                // Save image to temp location
                try {
                    File cachePath = new File(context.getCacheDir(), "images");
                    cachePath.mkdirs(); // don't forget to make the directory
                    FileOutputStream stream = new FileOutputStream(cachePath + "/image.png"); // overwrites this image every time
                    result.compress(Bitmap.CompressFormat.PNG, 100, stream); // TODO: Make compression operation asynchronous
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Share image
                File imagePath = new File(context.getCacheDir(), "images");
                File newFile = new File(imagePath, "image.png");
                Uri contentUri = FileProvider.getUriForFile(context, "com.creadeep.kazanio.fileprovider", newFile);

                if (contentUri != null) {
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // temp permission for receiving app to read this file
                    shareIntent.setDataAndType(contentUri, getActivity().getContentResolver().getType(contentUri));
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    startActivity(Intent.createChooser(shareIntent, getActivity().getApplication().getString(R.string.label_share_target)));
                }
                break;

            case R.id.action_activate_notification: // Activate notification
                autoNotifyStatus = 1;
                getActivity().invalidateOptionsMenu();
                // Set notify field
                ticketDb.setAutoCheckerStatus(mNumberText, mDateText, "1");
                mAutoCheckerActive = true;
                Snackbar.make(coordinatorLayout, R.string.text_snackbar_notification_on, Snackbar.LENGTH_SHORT).show();
                AppUtils.Companion.enableResultService(context);
                break;

            case R.id.action_deactivate_notification: // Deactivate notification
                autoNotifyStatus = 2;
                getActivity().invalidateOptionsMenu();
                // Set notify field
                ticketDb.setAutoCheckerStatus(mNumberText, mDateText, "0");
                mAutoCheckerActive = false;
                Snackbar.make(coordinatorLayout, R.string.text_snackbar_notification_off, Snackbar.LENGTH_SHORT).show();
                AppUtils.Companion.enableResultService(context);
                break;

            case R.id.action_refresh: // Refresh result
                if (GameUtils.Companion.isDrawn(gameType, mDateText)) {
                    // Inform user
                    mTextViewResult.setText(R.string.text_result_checking);
                    // Check from web
                    new checkWebForResultAsyncTask().execute();
                }
                Snackbar.make(coordinatorLayout, R.string.text_snackbar_result_updated, Snackbar.LENGTH_SHORT).show();
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Checks if the result is available in db
     *
     * @param number number of the ticket to be checked
     * @param date date of the ticket to be checked
     * @return db entry
     */
    public Ticket checkDbForResult(int type, String number, String date) {
        Cursor dbResult = ticketDb.checkPresence(String.valueOf(type), number, date);
        if (dbResult.moveToFirst()) { // retrieve result from dB
            Ticket ticket = new Ticket(type, 0, number, date,
                    dbResult.getInt(dbResult.getColumnIndex("Id")),
                    dbResult.getString(dbResult.getColumnIndex("Fraction")),
                    dbResult.getString(dbResult.getColumnIndex("Prize")),
                    dbResult.getInt(dbResult.getColumnIndex("Notify")),
                    dbResult.getInt(dbResult.getColumnIndex("Drawn")),
                    dbResult.getInt(dbResult.getColumnIndex("Tease")));
            dbResult.close();
            return ticket;
        }
        else {
            dbResult.close();
            return null;
        }
    }

    /**
     * Puts the info of the provided Ticket on UI
     *
     * @param ticketToProcess Ticket to be used for drawing
     */
    public void showResult(Ticket ticketToProcess) {
        if (ticketToProcess.getPrize() != null) { // prize info is available
            float prizeWon = Float.parseFloat(ticketToProcess.getPrize());
            if (prizeWon > 0) { // congratulate the user
                mTextViewResult.setText(String.format(AppUtils.Companion.getCurrentLocale(), "%,.2f₺ Kazandınız", prizeWon));
                mTextViewResult.setTextColor(context.getResources().getColor(R.color.colorPrimary));
            } else { // console the user
                mTextViewResult.setText("Kazanamadınız");
                mTextViewResult.setTextColor(context.getResources().getColor(R.color.grey_font));
            }
            autoNotifyStatus = 0;
        }
        else { // prize info is not available
            if (ticketToProcess.getNotify() == 1) { // auto-notify is on
                autoNotifyStatus = 1; // used to update menu items
                mAutoCheckerActive = true;
            } else { // auto-notify is off
                autoNotifyStatus = 2;
                mAutoCheckerActive = false;
            }

            // Check if the draw date has come
            if (GameUtils.Companion.isDrawn(gameType, mDateText)) { // date has arrived, check from web
                if (AppUtils.Companion.isConnectionAvailable(context)) {
                    if (!isChecking)
                        mTextViewResult.setText(R.string.text_result_unannounced);
                }
                else
                    mTextViewResult.setText(R.string.text_error_connection);
            }
            else { // Not yet drawn
                mTextViewResult.setText(R.string.text_result_undrawn);
            }
        }

        if (!isChecking)
            loadProgress.setVisibility(View.GONE);
        Activity activity = getActivity();
        if (activity != null)
            getActivity().invalidateOptionsMenu();
    }

    /**
     * Checks if the result is available online
     */
    class checkWebForResultAsyncTask extends AsyncTask<String, Void, AsyncTaskResult<JSONObject>> {
        protected AsyncTaskResult<JSONObject> doInBackground(String... args) {
            isChecking = true;
            String response;
            try {
                URL url = new URL(mQueryUrl[gameType - 1] + mDateText.substring(4, 8) + mDateText.substring(2, 4) + mDateText.substring(0, 2) + ".json");
                BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
                StringBuilder sb = new StringBuilder();
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setReadTimeout(10000 /* milliseconds */);
                urlConnection.setConnectTimeout(15000 /* milliseconds */);
                urlConnection.setDoOutput(true);

                urlConnection.connect();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                response = sb.toString();
                br.close();
                urlConnection.disconnect();
                return new AsyncTaskResult<>(new JSONObject(response));
            } catch (Exception e) {
                return new AsyncTaskResult<>(e);
            }
        }

        protected void onPostExecute(AsyncTaskResult<JSONObject> result) {
            if (result.getError() != null) { // There was a problem
                if (!result.getError().toString().startsWith("java.io.FileNotFoundException"))
                    Snackbar.make(coordinatorLayout, R.string.text_error_connection, Snackbar.LENGTH_LONG).show();
                if (mAutoCheckerActive) {
                    if (isAdded()) { // to prevent exception if user presses back button while the result was being checked
                        Ticket ticketToProcess = new Ticket(gameType, ticketType, mNumberText, mDateText, mFractionText, null, 1, 0, 0);
                        storeTicket(ticketToProcess);
                    }
                }
                else {
                    if (isAdded()) {
                        Ticket ticketToProcess = new Ticket(gameType, ticketType, mNumberText, mDateText, mFractionText, null, 0, 0, 0);
                        storeTicket(ticketToProcess);
                    }
                }
                mTextViewResult.setText(R.string.text_result_unannounced);
                isChecking = false;
            }
            else { // json is successfully fetched
                JSONObject realResult = result.getResult();
                try {
                    // Milli Piyango
                    if (gameType == 1) {
                        int maxDigits = 0;
                        double maxPrize = 0; // Required to correctly determine teselli prize
                        JSONArray resultList = realResult.getJSONArray("sonuclar");
                        for (int i = 0; i < resultList.length(); i++) { // for each prize
                            JSONObject resultItem = resultList.getJSONObject(i);
                            int digitsToCheck = resultItem.getInt("haneSayisi"); // number of digits to check
                            if (i == 0) // Store max digits (6 or 7)
                                maxDigits = digitsToCheck;
                            if (digitsToCheck == 0) // If teselli
                                digitsToCheck = maxDigits; // Check entire number
                            String numberToCheck = mNumberText.substring(Math.max(mNumberText.length() - digitsToCheck, 0));
                            JSONArray numberList = resultItem.getJSONArray("numaralar");
                            for (int j = 0; j < numberList.length(); j++) {
                                if (numberToCheck.equals(numberList.getString(j))) { // match
                                    String prize;
                                    if (mFractionText.equals("11")) { // Tam bilet
                                        prize = resultItem.getString("ikramiye");
                                    } else if (mFractionText.equals("12")) { // Yarim bilet
                                        prize = String.format(AppUtils.Companion.getCurrentLocale(), "%.0f", Double.parseDouble(resultItem.getString("ikramiye")) / 2d);
                                    } else { // Ceyrek bilet
                                        prize = String.format(AppUtils.Companion.getCurrentLocale(), "%.0f", Double.parseDouble(resultItem.getString("ikramiye")) / 4d);
                                    }
                                    if (Double.parseDouble(prize) > maxPrize)
                                        maxPrize = Double.parseDouble(prize);
                                }
                            }
                        }
                        Ticket ticketToProcess = new Ticket(gameType, ticketType, mNumberText, mDateText, mFractionText, String.valueOf(maxPrize), 0, 1, 0);
                        storeTicket(ticketToProcess);
                    }

                    else if (gameType == 6) { // Super Piyango
                        JSONArray resultList = realResult.getJSONArray("sonuclar");
                        for (int i = 0; i < resultList.length(); i++) { // for each prize
                            JSONObject resultItem = resultList.getJSONObject(i);
                            int digitsToCheck = resultItem.getInt("haneSayisi"); // number of digits to check
                            String numberToCheck = mNumberText.substring(Math.max(mNumberText.length() - digitsToCheck, 0));
                            JSONArray numberList = resultItem.getJSONArray("numaralar");
                            for (int j = 0; j < numberList.length(); j++) {
                                if (numberToCheck.equals(numberList.getString(j))) { // match
                                    String prize = resultItem.getString("ikramiye");
                                    Ticket ticketToProcess = new Ticket(gameType, ticketType, mNumberText, mDateText, "0", prize, 0, 1, 0);
                                    storeTicket(ticketToProcess);
                                    mIsHit = true;
                                    break;
                                }
                            }
                            if (mIsHit)
                                break;
                        }
                        if (!mIsHit) { // No match
                            Ticket ticketToProcess = new Ticket(gameType, ticketType, mNumberText, mDateText, mFractionText, "0", 0, 1, 0);
                            storeTicket(ticketToProcess);
                        }
                    }

                    else if (gameType == 7) { // Banko Piyango
                        JSONArray resultList = realResult.getJSONArray("sonuclar");
                        for (int i = 0; i < resultList.length(); i++) { // for each prize
                            JSONObject resultItem = resultList.getJSONObject(i);
                            int digitsToCheck = resultItem.getInt("haneSayisi"); // number of digits to check
                            String numberToCheck = mNumberText.substring(Math.max(mNumberText.length() - digitsToCheck, 0));
                            JSONArray numberList = resultItem.getJSONArray("numaralar");
                            for (int j = 0; j < numberList.length(); j++) {
                                if (numberToCheck.equals(numberList.getString(j))) { // match
                                    String prize = resultItem.getString("ikramiye");
                                    Ticket ticketToProcess = new Ticket(gameType, ticketType, mNumberText, mDateText, "0", prize, 0, 1, 0);
                                    storeTicket(ticketToProcess);
                                    mIsHit = true;
                                    break;
                                }
                            }
                            if (mIsHit)
                                break;
                        }
                        if (!mIsHit) { // No match
                            Ticket ticketToProcess = new Ticket(gameType, ticketType, mNumberText, mDateText, mFractionText, "0", 0, 1, 0);
                            storeTicket(ticketToProcess);
                        }
                    }

                    // Sayisal Loto, Super Loto, Super Sayisal Loto, Para Loto
                    else if (gameType == 2 || gameType == 3 || gameType == 8 || gameType == 9) {
                        JSONObject data = realResult.getJSONObject("data");
                        String[] numbers = data.getString("rakamlar").split("#");

                        double prize = 0.0;
                        int rowNum = mNumberText.length() / 12;
                        for (int i = 0; i < rowNum; i++) { // Process each row
                            String numberRow = mNumberText.substring(i * 12, (i + 1) * 12);
                            if (!numberRow.equals("XXXXXXXXXXXX")) { // if detected properly
                                int matchNum = 0; // Amount of matches
                                Locale loc = AppUtils.Companion.getCurrentLocale();
                                for (String number : numbers) {
                                    number = String.format(loc, "%02d", Integer.parseInt(number)); // Make it have two digits
                                    for (int k = 0; k < 6; k++) {
                                        if (number.equals(numberRow.substring(k * 2, (k + 1) * 2))) {
                                            matchNum++;
                                        }
                                    }
                                }
                                if (matchNum > 2) {
                                    JSONArray bilenKisiler = data.getJSONArray("bilenKisiler");
                                    JSONObject numResult = bilenKisiler.getJSONObject(matchNum - 3);
                                    prize += numResult.getDouble("kisiBasinaDusenIkramiye");
                                }
                            }
                        }
                        Ticket ticketToProcess = new Ticket(gameType, 0, mNumberText, mDateText, mFractionText, String.valueOf(prize), 0, 1, 0);
                        storeTicket(ticketToProcess);
                    }

                    // On Numara, Super On Numara
                    else if (gameType == 4 || gameType == 10) {
                        JSONObject data = realResult.getJSONObject("data");
                        String[] numbers = data.getString("rakamlar").split("#");

                        double prize = 0.0; // Total prize
                        int rowNum = mNumberText.length() / 20;
                        for (int i = 0; i < rowNum; i++) { // Process each row
                            String numberRow = mNumberText.substring(i * 20, (i + 1) * 20); // Get current row
                            if (!numberRow.equals("XXXXXXXXXXXXXXXXXXXX")) { // if detected properly
                                int matchNum = 0; // Amount of matches
                                Locale loc = AppUtils.Companion.getCurrentLocale();
                                for (String number : numbers) { // For each drawn number
                                    number = String.format(loc, "%02d", Integer.parseInt(number)); // Make it have two digits
                                    for (int k = 0; k < 10; k++) { // Check if it exists inside the row's numbers
                                        if (number.equals(numberRow.substring(k * 2, (k + 1) * 2))) {
                                            matchNum++;
                                        }
                                    }
                                }
                                if (matchNum == 0 || matchNum > 5) {
                                    JSONArray bilenKisiler = data.getJSONArray("bilenKisiler");
                                    JSONObject numResult;
                                    if(matchNum == 0)
                                        numResult = bilenKisiler.getJSONObject(0);
                                    else
                                        numResult = bilenKisiler.getJSONObject(matchNum - 5);
                                        prize += numResult.getDouble("kisiBasinaDusenIkramiye");
                                }
                            }
                        }
                        Ticket ticketToProcess = new Ticket(gameType, 0, mNumberText, mDateText, mFractionText, String.valueOf(prize), 0, 1, 0);
                        storeTicket(ticketToProcess);
                    }

                    // Sans Topu, Super Sans Topu
                    else if (gameType == 5 || gameType == 11) {
                        JSONObject data = realResult.getJSONObject("data");
                        String[] numbers = data.getString("rakamlar").split("#");

                        double prize = 0.0;
                        int rowNum = mNumberText.length() / 12;
                        for (int i = 0; i < rowNum; i++) { // Process each row
                            String numberRow = mNumberText.substring(i * 12, (i + 1) * 12);
                            if (!numberRow.equals("XXXXXXXXXXXX")) { // if detected properly
                                // Check main numbers
                                int mainMatchNum = 0; // Amount of matches
                                Locale loc = AppUtils.Companion.getCurrentLocale();
                                for (int j = 0; j < numbers.length - 1; j++) { // Exclude the last number (+1)
                                    String number = String.format(loc, "%02d", Integer.parseInt(numbers[j])); // Make it have two digits
                                    for (int k = 0; k < 5; k++) {
                                        if (number.equals(numberRow.substring(k * 2, (k + 1) * 2))) {
                                            mainMatchNum++;
                                        }
                                    }
                                }
                                // Check extra +1 number
                                int extraMatchNum = 0;
                                String number = String.format(loc, "%02d", Integer.parseInt(numbers[5])); // Make it have two digits
                                if (number.equals(numberRow.substring(10, 12))) {
                                    extraMatchNum++;
                                }
                                if ((mainMatchNum > 0 && extraMatchNum == 1) || (mainMatchNum > 2 && extraMatchNum == 0)) {
                                    JSONArray bilenKisiler = data.getJSONArray("bilenKisiler");
                                    JSONObject numResult;
                                    // +1 matched
                                    if (extraMatchNum == 1) {
                                        switch (mainMatchNum) {
                                            case 1: // 1+1
                                                numResult = bilenKisiler.getJSONObject(0);
                                                break;
                                            case 2: // 2+1
                                                numResult = bilenKisiler.getJSONObject(1);
                                                break;
                                            case 3: // 3+1
                                                numResult = bilenKisiler.getJSONObject(3);
                                                break;
                                            case 4: // 4+1
                                                numResult = bilenKisiler.getJSONObject(5);
                                                break;
                                            default: // 5+1
                                                numResult = bilenKisiler.getJSONObject(7);
                                                break;
                                        }
                                    }
                                    // +1 not matched
                                    else {
                                        switch (mainMatchNum) {
                                            case 3: // 3 bilen
                                                numResult = bilenKisiler.getJSONObject(2);
                                                break;
                                            case 4: // 4 bilen
                                                numResult = bilenKisiler.getJSONObject(4);
                                                break;
                                            default: // 5 bilen
                                                numResult = bilenKisiler.getJSONObject(6);
                                                break;
                                        }
                                    }
                                        prize += numResult.getDouble("kisiBasinaDusenIkramiye");
                                }
                            }
                        }
                        Ticket ticketToProcess = new Ticket(gameType, 0, mNumberText, mDateText, mFractionText, String.valueOf(prize), 0, 1, 0);
                        storeTicket(ticketToProcess);
                    }

                    isChecking = false;
                } catch (JSONException e) {
                    e.printStackTrace();
                    isChecking = false;
                }
            }
        }
    }

    public void storeTicket(Ticket ticketToStore) { // stores the data with auto notify OFF TODO: Merge Ticket and TicketEntity classes
        TicketEntity ticketEntity = new TicketEntity(null,
                String.valueOf(ticketToStore.getGameType()),
                String.valueOf(ticketToStore.getTicketType()),
                ticketToStore.getNumber(),
                ticketToStore.getDate(),
                ticketToStore.getFraction(),
                ticketToStore.getPrize(),
                ticketToStore.getNotify(),
                ticketToStore.getIsDrawn(),
                ticketToStore.getTease());
        ticketListViewModel.insert(ticketEntity);
        updateNotifications = true; // Set flag so notification alarms will be updated in the next ViewModel observation
    }
}
