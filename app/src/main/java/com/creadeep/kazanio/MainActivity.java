// Based on: https://blog.csdn.net/torvalbill/article/details/40378539

/*
TODO: Add sorting functionality in Tickets Activity
TODO: Add search functionality in Tickets Activity
TODO: Store result json locally to decrease the amount of web queries
TODO: Remove notification if the result is checked manually entering the result while the app was open
TODO: Fix result activity layout on split screen
TODO: Create contextual action bar on long press on recycler view ticket item
TODO: Fix flashlight icon after swiping horizontally while the torch is on
*/

package com.creadeep.kazanio;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.gms.vision.text.TextRecognizer;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.GravityCompat;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, ScanFragment.TorchStatusListener, ScanFragment.GameTypeListener, ScanFragment.RowNumberListener2, ScanFragment.cameraOperationListener, ScanFragment.FragmentUiStatusListener, TicketsFragment.FragmentUiStatusListener, PurchasesUpdatedListener {

    private Intent mSettingsActivityIntent;

    private AdView mAdView;

    private ActionBarDrawerToggle drawerToggle; // Action bar drawer toggle
    private boolean mFlashState = false; // used to change the torch action image, false: disabled, true: enabled
    private boolean mFlashVisState = false; // used to change torch action visibility
    private int activeFragmentIndex = 9; // Stores the active fragment index to prevent loading Scan Fragment incorrectly
    private int activeGameType; // Stores the active game index to enable/disable row action menu item
    private int activeRowNum; // Number of rows in the ticket to be scanned
    private int defaultRowNum; // Number of rows in the ticket to be scanned
    private int defaultMpType; // Default Milli Piyango ticket type (new/old)
    private BottomNavigationView bottomNavigationView;
    private ScanFragment scanFragment;
    private TicketsFragment ticketsFragment;
    private SharedPreferences prefs;
    private Fragment selectedFragment;
    private boolean isCameraOperationActive; // True if camera is being opened or closed. Used for deactivating or delaying the functionality of the bottom nav buttons.
    private MenuItem targetItem = null; // Used to store the next fragment in case of delayed bottom nav button functionality to prevent too quick ScanFragment entrances/exits.
    private boolean isActivityVisible; // Used to prevent fragment changes while the app is in the background (caused by delayed camera operations that aims to prevent too quick ScanFragment entrances/exits).
    private View actionMenuRowNumberView = null; // Stores reference to row number action menu item to be used in the tutorial
    private View actionMenuAddTicketView = null; // Stores reference to add ticket action menu item to be used in the tutorial
    private View tabLayoutView = null; // Stores reference to tab layout to be used in the tutorial
    private View bottomNavigationScanView = null; // Stores reference to bottom navigation scan item to be used in the tutorial
    private BillingClient billingClient; // To implement Billing Library functions in IAP
    private AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener; // To listen to purchase IAP operations

    private final int EXPIRY_GRACE_PERIOD = 3; // The number of months the app will run since the build

    public static TextRecognizer mTextRecognizer; // Extracts digits

    private BottomNavigationView.OnNavigationItemSelectedListener bottomNavListener = new BottomNavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            // Change to the selected fragment right away if there is no ongoing camera operation or the current or target fragments are not using camera (not ScanFragment)
            if(!isCameraOperationActive || (getSupportFragmentManager().findFragmentById(R.id.fragment_container).getClass() != ScanFragment.class && item.getItemId() != R.id.nav_scan)) {
                switchToFragment(item);
                return true; // Change fragment
            }
            // Otherwise store the target fragment, to be activated when the camera operation ends (informed via onCameraOperationStatusChanged interface)
            else {
                targetItem = item;
                return false; // Do not change fragment
            }
        }
    };

    /**
     * Changes active fragment. Does not update the selected item in the bottom nav bar.
     * Changing selected item is done automatically in OnNavigationItemSelectedListener, and manually in onCameraOperationStatusChanged.
     * @param item target bottom navigation menu item
     */
    private void switchToFragment(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_scan:
                selectedFragment = new ScanFragment();
                activeFragmentIndex = 0;
                // Show help drawer item
                this.runOnUiThread(() -> {
                    NavigationView navigationView = findViewById(R.id.navigationView);
                    Menu nav_Menu = navigationView.getMenu();
                    nav_Menu.findItem(R.id.nav_help).setVisible(true);
                });
                break;
            case R.id.nav_tickets:
                selectedFragment = new TicketsFragment();
                activeFragmentIndex = 1;
                mFlashVisState = false; // Make torch action invisible until TextureView is available in future scan fragment selection
                // Show help drawer item
                this.runOnUiThread(() -> {
                    NavigationView navigationView = findViewById(R.id.navigationView);
                    Menu nav_Menu = navigationView.getMenu();
                    nav_Menu.findItem(R.id.nav_help).setVisible(true);
                });
                break;
            case R.id.nav_stats:
                selectedFragment = new StatsFragment();
                activeFragmentIndex = 2;
                mFlashVisState = false; // Make torch action invisible until TextureView is available in future scan fragment selection
                // Hide help drawer item
                this.runOnUiThread(() -> {
                    NavigationView navigationView = findViewById(R.id.navigationView);
                    Menu nav_Menu = navigationView.getMenu();
                    nav_Menu.findItem(R.id.nav_help).setVisible(false);
                });
                break;
        }

        // Replace fragment with the new one
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment == null) { // If this is the first ever fragment
            ft.replace(R.id.fragment_container, selectedFragment);
            ft.commit();
        }
        else { // If there is already a fragment
            if (currentFragment.getClass() != selectedFragment.getClass()) { // Replace only if the new fragment is different
                if ((selectedFragment.getClass() == ScanFragment.class && prefs.getBoolean("isScanningTutorialShown", false)) ||
                        (selectedFragment.getClass() == TicketsFragment.class && prefs.getBoolean("isTicketsTutorialShown", false)) ||
                        (selectedFragment.getClass() == StatsFragment.class))// Show animation tutorial is already shown (animation causes vertical offset on tap targets)
                    ft.setCustomAnimations(R.animator.fade_in, R.animator.fade_out, R.animator.fade_in, R.animator.fade_out);
                ft.replace(R.id.fragment_container, selectedFragment);
                ft.commit();
                drawerToggle.setDrawerIndicatorEnabled(true); // enable navigation drawer hamburger icon (important for restoring it after returning back from result fragment)
                this.runOnUiThread(new Runnable() { // Update UI from UI thread
                    @Override
                    public void run() {
                        getSupportActionBar().setTitle(R.string.app_name); // Restore activity title (important for restoring it after returning back from result fragment)
                    }
                });
            }
            else { // If tickets fragment is selected again use it to collapse bottom sheet
                if (currentFragment.getClass() == TicketsFragment.class) {
                    View v = currentFragment.getView();
                    if (v != null) {
                        NestedScrollView sv = v.findViewById(R.id.scroll_view);
                        BottomSheetBehavior<View> b = BottomSheetBehavior.from(sv);
                        if (b.getState() == BottomSheetBehavior.STATE_EXPANDED) { // collapse if expanded
                            sv.smoothScrollTo(0, 0); // Scroll to top before possible next expansion
                            b.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        }
                    }
                }
            }
        }
    }

    /**
     * Executed when returned from another activity started by this activity (for now MainIntroActivity).
     * @param requestCode 1 for MainIntroActivity
     * @param resultCode If returned by properly finishing other activity or via back button
     * @param data Any additional data carried in between
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Returning from intro activity
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                // Set intro shown flag
                SharedPreferences.Editor e = prefs.edit();
                e.putBoolean("isIntroShown", true);
                e.apply();
                // Draw ui elements since creation of textureView is allowed to trigger permission check now (otherwise it would cause permission popup to overlap with intro)
                createUi();
                // Show ads
                showAds();
                checkSubscription();
            }
            // User cancelled intro by pressing back button, so close the app (only valid for the first run)
            else {
                finish();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme); // Revert splash screen theme

        super.onCreate(savedInstanceState);

        prefs = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext()); // Initialize SharedPreferences

        // Prevent side loaded APKs (they cause ResourceNotFound exception on material-intro library)
        boolean fromPlayStore = true;
        try { // Try to read a mock image from resources folder
            getResources().getDrawable(R.drawable.p, getApplicationContext().getTheme());
        }
        catch (Resources.NotFoundException ex) { // Warn the user to install it from Play Store
            fromPlayStore = false;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.dialog_title_expired))
                    .setMessage(getString(R.string.dialog_message_expired))
                    .setPositiveButton(getString(R.string.dialog_accept), (dialogInterface, i) -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.creadeep.kazanio")));
                        } catch (ActivityNotFoundException a) {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.creadeep.kazanio")));
                        }
                        finish();
                    })
                    .setCancelable(false) // Prevent closing dialog by tapping outside/back button
                    .show();
        }

        // Package legitimacy check (to prevent package name change and re-upload)
        if(!getApplicationContext().getPackageName().equals(AppUtils.Companion.getMyPackageName())){
            // Go into infinite loop and crash
            Object[] o = null;
            while (true) {
                o = new Object[] {o};
            }
        }

        // Date expiration check
        if (fromPlayStore) {
            Calendar currentCalendar = Calendar.getInstance(); // Get current date
            Date buildDate = BuildConfig.BUILD_TIME; // Get expiration date
            Calendar expiryCalendar = Calendar.getInstance();
            expiryCalendar.setTime(buildDate);
            expiryCalendar.add(Calendar.MONTH, EXPIRY_GRACE_PERIOD);

            if (currentCalendar.compareTo(expiryCalendar) > 0) { // if expired show dialog saying "update the app"
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.dialog_title_expired))
                        .setMessage(getString(R.string.dialog_message_expired))
                        .setPositiveButton(getString(R.string.dialog_accept), (dialogInterface, i) -> {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.creadeep.kazanio")));
                            } catch (ActivityNotFoundException a) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.creadeep.kazanio")));
                            }
                            finish();
                        })
                        .setCancelable(false) // Prevent closing dialog by tapping outside/back button
                        .show();
            } else { // Not expired, so create the ui
                // If intro is not shown (If the activity has never started before)
                if (!prefs.getBoolean("isIntroShown", false)) {
                    //  Launch app intro
                    final Intent i = new Intent(MainActivity.this, MainIntroActivity.class);
                    runOnUiThread(() -> startActivityForResult(i, 1));
                }
                // Not the first run
                else { // Required to prevent layout inflation causing permission pop-up on fast phones
                    createUi();
                    // Prepare in app billing client
                    initializeBillingClient();
                    // Request ads
                    showAds();
                    checkSubscription();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityVisible = false;
        if (mTextRecognizer != null)
            mTextRecognizer.release();
        if (mAdView != null)
            mAdView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (billingClient != null)
            billingClient.endConnection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        defaultRowNum = Integer.parseInt(prefs.getString("defaultRowNumber", "1")); // to update after going into settings and changing it
        defaultMpType = Integer.parseInt(prefs.getString("defaultMPTicketType", "1"));
        activeGameType = Integer.parseInt(prefs.getString("defaultGameType", "2"));
        if(activeGameType != 1) // Milli Piyango
            activeRowNum = defaultRowNum;
        else
            activeRowNum = defaultMpType;

        // Respect screen awake preference
        if(prefs.getBoolean("switchScreenAwake", true))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//Don't let screen turn off
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        isActivityVisible = true;
        if (mAdView != null)
            mAdView.resume();
    }

    /*
    Required to prevent half-ass saving with only activity status but not fragment causing scan fragment to load but any other bottom nav item to stay selected after a background kill
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.clear();
    }

    /*
    If scan fragment is started then enable it to listen for torch activation, horizontal page (game type) change and vertical page (row number) change events
    */
    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof ScanFragment) {
            scanFragment = (ScanFragment) fragment;
            scanFragment.setTorchStatusListener(this);
            scanFragment.setGameTypeListener(this);
            scanFragment.setRowNumberListener2(this);
            scanFragment.setCameraOperationListener(this);
            scanFragment.setFragmentUiAvailableListener(this);
        }
        else if (fragment instanceof TicketsFragment) {
            ticketsFragment = (TicketsFragment) fragment;
            ticketsFragment.setFragmentUiAvailableListener(this);
        }
    }

    @Override
    public void onBackPressed() {
        // Close navigation drawer if open
        DrawerLayout drawer = findViewById(R.id.drawerLayout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        }
        // Manage for fragment navigation
        else {
            // If Tickets Fragment is active, quit app
            if (getSupportFragmentManager().findFragmentById(R.id.fragment_container).getClass() == TicketsFragment.class)
            {
                super.onBackPressed();
                finish();
            }
            // If Result Fragment is active go to the previous page wherever it is
            else if (getSupportFragmentManager().findFragmentById(R.id.fragment_container).getClass() == ResultFragment.class) {
                // Prevent user rapidly going back to scan fragment while the camera is being closed causing camera not opened (black screen)
                if (!isCameraOperationActive) { // If camera is not active than go back to wherever you are
                    super.onBackPressed();
                }
                // Otherwise store the target fragment, to be activated when the camera operation ends (informed via onCameraOperationStatusChanged interface)
                else { // If camera closing operation is active set the flag to be used when the operation finishes detected via a listener
                    targetItem = bottomNavigationView.getMenu().getItem(0);
                }
            }
            // If Any other Fragment is active (Scan or Stats), go to Tickets Fragment (This way, user will go to home screen before exiting the app via 2nd back button press)
            else
            {
                BottomNavigationView mBottomNavigationView = findViewById(R.id.bottom_navigation);
                mBottomNavigationView.setSelectedItemId(R.id.nav_tickets);
            }
        }
    }

    /*
    Navigation drawer item actions
     */
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.nav_help: // Handle the help action
                if (activeFragmentIndex == 0) {
                    // Get view references
                    actionMenuRowNumberView = findViewById(R.id.action_row_number);
                    TabLayout tabLayout = findViewById(R.id.tabLayout);
                    tabLayoutView = ((ViewGroup) tabLayout.getChildAt(0)).getChildAt(tabLayout.getSelectedTabPosition());
                    // Invalidate tutorial flag before starting it
                    SharedPreferences.Editor e = prefs.edit();
                    e.putBoolean("isScanningTutorialShown", false);
                    e.apply();
                    startTutorial(0);
                }
                else if (activeFragmentIndex == 1) {
                    // Get view references
                    actionMenuAddTicketView = findViewById(R.id.action_generate_ticket);
                    bottomNavigationScanView = findViewById(R.id.nav_scan);
                    // Invalidate tutorial flag before starting it
                    SharedPreferences.Editor e = prefs.edit();
                    e.putBoolean("isTicketsTutorialShown", false);
                    e.apply();
                    startTutorial(1);
                }
                break;

            case R.id.nav_settings: // Handle the tickets action
                startActivity(mSettingsActivityIntent);
                break;

            case R.id.nav_about: // Handle about action
                Intent mAboutActivityIntent = new Intent( MainActivity.this, AboutActivity.class);
                startActivity(mAboutActivityIntent);
                break;

            case R.id.nav_rate: // Handle rate action
                Uri uri = Uri.parse("market://details?id=" + getApplicationContext().getPackageName());
                Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
                // To count with Play market backstack, After pressing back button,
                // to taken back to our application, we need to add following flags to intent.
                goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY |
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                try {
                    startActivity(goToMarket);
                } catch (ActivityNotFoundException e) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("http://play.google.com/store/apps/details?id=" + getApplicationContext().getPackageName())));
                }
                break;

            case R.id.nav_subscription: // Handle subscription action
                initializeBillingClient();
                billingClient.startConnection(new BillingClientStateListener() {
                    @Override
                    public void onBillingSetupFinished(BillingResult billingResult) {
                        if (billingResult.getResponseCode() ==  BillingClient.BillingResponseCode.OK) {
                            if (billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS).getResponseCode() == BillingClient.BillingResponseCode.OK) { // Subscriptions is supported
                                // Subscribe if the specific subscription is available
                                List<String> skuList = new ArrayList<>();
                                skuList.add("remove_ads");
                                SkuDetailsParams skuDetailsParams = SkuDetailsParams.newBuilder()
                                        .setSkusList(skuList).setType(BillingClient.SkuType.SUBS).build();
                                billingClient.querySkuDetailsAsync(skuDetailsParams,
                                        new SkuDetailsResponseListener() {
                                            @Override
                                            public void onSkuDetailsResponse(BillingResult billingResult, List<SkuDetails> list) {
                                                // Subscribe
                                                if (list != null && list.size() > 0) {
                                                    BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                                                            .setSkuDetails(list.get(0))
                                                            .build();
                                                    BillingResult billingResult1 = billingClient.launchBillingFlow(MainActivity.this, flowParams);
                                                }
                                                else {
                                                    // Subscriptions not supported
                                                    Snackbar.make(findViewById(R.id.fragment_container), R.string.text_warning_subscription_not_available, Snackbar.LENGTH_SHORT).show();
                                                }
                                            }
                                        });
                            }
                            else {
                                // Subscriptions not supported
                                Snackbar.make(findViewById(R.id.fragment_container), R.string.text_warning_subscription_not_available, Snackbar.LENGTH_SHORT).show();
                            }
                        }
                    }
                    @Override
                    public void onBillingServiceDisconnected() {
                    }
                });
                break;
        }
        DrawerLayout drawer = findViewById(R.id.drawerLayout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Defines what will be done when top bar items are clicked
     * @param item selected item from top action bar
     * @return result
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(drawerToggle.onOptionsItemSelected(item))
            return true;
        // Handle item selection
        if (item.getItemId() == R.id.action_switch_torch) {
            if (!mFlashState) {
                item.setIcon(R.drawable.ic_flash_on_white_24dp);
                mFlashState = true;
            } else {
                item.setIcon(R.drawable.ic_flash_off_white_24dp);
                mFlashState = false;
            }

            // Sending flash state to scan fragment
            ScanFragment scanFragment = (ScanFragment)
                    getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (scanFragment != null) {
                scanFragment.setTorch(mFlashState);
            }
            return true;
            // Row number action behaviour is defined in onCreateOptionsMenu
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(activeFragmentIndex == 0) { // If Scan fragment is active
            getMenuInflater().inflate(R.menu.top_menu_scan, menu);
            menu.findItem(R.id.action_switch_torch).setVisible(mFlashVisState); // Set flash action visibility
            // Draw row number action menu item
            MenuItem rowItem = menu.findItem(R.id.action_row_number);
            RelativeLayout rowNumActionLayout = (RelativeLayout) rowItem.getActionView();
            TextView tv = rowNumActionLayout.findViewById(R.id.tv_row_num);
            if(activeGameType == 1) { // Milli Piyango
                // Update action icon
                if(activeRowNum == 1){ // Terminal
                    tv.setText(getResources().getString(R.string.milli_piyango_type_action_new_short));
                }
                else { // Classical
                    tv.setText(getResources().getString(R.string.milli_piyango_type_action_classical_short));
                }
            }
            else{
                // Update action icon
                tv.setText(String.valueOf(activeRowNum));
            }
            // Set dropdown menu (row number action)
            rowItem.getActionView().setOnClickListener(v -> showRowNumberPopupMenu(v, activeGameType));
            // Get reference to row number action menu item
            if (!prefs.getBoolean("isScanningTutorialShown", false) && actionMenuRowNumberView == null) {
                new Handler().post(() -> {
                    actionMenuRowNumberView = findViewById(R.id.action_row_number);
                    // Start tutorial if other view to be focused is also determined
                    if (actionMenuRowNumberView != null && tabLayoutView != null) {
                        startTutorial(0);
                    }
                });
            }
        }
        else if (activeFragmentIndex == 1) {
            getMenuInflater().inflate(R.menu.top_menu_tickets, menu);
            // Get reference to bottom navigation scan item
            bottomNavigationScanView = findViewById(R.id.nav_scan);
            // Get reference to add ticket action menu item
            if (!prefs.getBoolean("isTicketsTutorialShown", false) && actionMenuAddTicketView == null) {
                new Handler().post(() -> {
                    actionMenuAddTicketView = findViewById(R.id.action_generate_ticket);
                    // Start tutorial if other view to be focused is also determined
                    if (actionMenuAddTicketView != null && bottomNavigationScanView != null) {
                        startTutorial(1);
                    }
                });
            }
        }
        return true;
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> list) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                && list != null) {
            for (Purchase purchase : list) {
                // Hide purchase feature
                if (purchase.getSku().equals("remove_ads")) {
                    findViewById(R.id.adView).setVisibility(View.GONE);
                }
            }
        }
    }

    public void enableTorchButton() {
        mFlashVisState = true;
        mFlashState = false; // Required when pressed back button after going into result fragment
        invalidateOptionsMenu(); // force redrawing top menu
    }

    /*
    Enable/disable row action menu item on top bar
    Executed by onPageSelected() of horizontal ViewPager in ScanFragment
     */
    public void onGameTypeChanged(int index){
        activeGameType = index + 1; // MP at index 0
        invalidateOptionsMenu(); // force redrawing top menu
    }

    /*
    Update number of rows on the action item in the top menu
    Executed by onRowNumberChanged() in ScanFragment which is executed by onPageSelected() in HorizontalPagerAdapter
    The update takes 2 steps: onRowNumberChanged: VerticalPageAdapter -> ScanFragment, onRowNumberChanged: ScanFragment -> MainActivity
    Shorten if possible?
     */
    public void onRowNumberChanged2(int rowNum){
        activeRowNum = rowNum;
        invalidateOptionsMenu(); // force redrawing top menu
    }

    /*
    Draws row number selection popup menu when top menu item is clicked
    */
    public void showRowNumberPopupMenu(View v, int gameType){
        PopupMenu popup = new PopupMenu(this, v);
        if(gameType != 1) {
            // Add title item
            popup.getMenu().add(getResources().getString(R.string.row_number_action_title));
            popup.getMenu().getItem(0).setEnabled(false);
            // Add remaining items
            int[] row_variations = getResources().getIntArray(R.array.game_row_variations);
            int[] row_variations_limited = getResources().getIntArray(R.array.game_row_variations_limited);
            for (int i = 0; i < row_variations[gameType - 1]; i++) {
                if(i > (row_variations_limited[gameType - 1] - 1)) { // Deactivate non-fully functional row numbers for now. TODO: Enable reading more than 4 rows
                    popup.getMenu().add(Menu.NONE, Menu.NONE, i, (i + 1) + " " + getString(R.string.row_number_action_suffix));
                    popup.getMenu().getItem(i + 1).setEnabled(false);
                }
                else
                    popup.getMenu().add(Menu.NONE, Menu.NONE, i, String.valueOf(i + 1));
            }
        }
        else{
            // Add title item
            popup.getMenu().add(getResources().getString(R.string.milli_piyango_type_action_title));
            popup.getMenu().getItem(0).setEnabled(false);
            // Add remaining items
            popup.getMenu().add(Menu.NONE, Menu.NONE, 0, getResources().getString(R.string.milli_piyango_type_action_new));
            popup.getMenu().add(Menu.NONE, Menu.NONE, 1, getResources().getString(R.string.milli_piyango_type_action_classical));
            popup.getMenu().add(Menu.NONE, Menu.NONE, 2, getResources().getString(R.string.milli_piyango_type_action_special) + " " + getString(R.string.row_number_action_suffix));
            popup.getMenu().getItem(3).setEnabled(false);
        }
        // Set listener
        popup.setOnMenuItemClickListener(menuItem -> {
            if (scanFragment != null)
                scanFragment.setRowNum(menuItem.getOrder()+1); // Send the selected number to vertical viewPager
            return false;
        });
        popup.show();
    }

    /**
     * Used to be informed when there is an ongoing camera operation (open/close).
     * Interface provided by ScanFragment.
     * If there is a delayed fragment change, it is executed here to prevent it take place while a camera operation exists.
     * @param state represents presence of active camera operation.
     */
    @Override
    public void onCameraOperationStatusChanged(boolean state) {
        isCameraOperationActive = state;
        if(!state && targetItem != null && isActivityVisible){
            switchToFragment(targetItem);

            // Update the active bottom nav item
            // Update UI from UI thread
            this.runOnUiThread(() -> {
                if (targetItem != null) {
                    switch (targetItem.getItemId()) {
                        case R.id.nav_scan:
                            bottomNavigationView.getMenu().getItem(0).setChecked(true);
                            break;
                        case R.id.nav_tickets:
                            bottomNavigationView.getMenu().getItem(1).setChecked(true);
                            break;
                        case R.id.nav_stats:
                            bottomNavigationView.getMenu().getItem(2).setChecked(true);
                            break;
                    }
                }
                targetItem = null;
            });
        }
    }

    /**
     * Executed when any Fragment's views are drawn and ready to be referenced.
     * Used to decide when to start tutorial since it needs proper references for the views it will focus.
     */
    @Override
    public void onFragmentUiAvailable() {
        // Scan Fragment is available
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container).getClass() == ScanFragment.class) {
            // Get reference to tab layout
            TabLayout tabLayout = findViewById(R.id.tabLayout);
            tabLayoutView = ((ViewGroup) tabLayout.getChildAt(0)).getChildAt(tabLayout.getSelectedTabPosition());
            // Start tutorial if other view to be focused is also determined
            if (actionMenuRowNumberView != null && tabLayoutView != null) {
                startTutorial(0);
            }
        }
        // Tickets Fragment is available
        else if (getSupportFragmentManager().findFragmentById(R.id.fragment_container).getClass() == TicketsFragment.class) {
            // Get reference to ticket add action item
            actionMenuAddTicketView = findViewById(R.id.action_generate_ticket);
            // Start tutorial if other view to be focused is also determined
            if (actionMenuAddTicketView != null && bottomNavigationScanView != null) {
                startTutorial(1);
            }
        }
    }

    /**
     * Separated from onCreate since creation of UI before intro starts on the first run may cause permission pop-up to show up, ruining UX.
     * Called when intro is finished or if not the first run.
     */
    private void createUi() {
        setContentView(R.layout.activity_main);

        // Locale setting
        Configuration config = getBaseContext().getResources().getConfiguration();
        if (Locale.getDefault().getLanguage().equals("tr"))
            config.locale = new Locale("tr");
        else
            config.locale = new Locale("en");
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());

        // Set Bottom Navigation
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(bottomNavListener); // Listen for user clicks on navigation items

        // Set initial fragment
        bottomNavigationView.setSelectedItemId(R.id.nav_tickets); // Set correct selected item

        // Navigation drawer
        DrawerLayout drawer = findViewById(R.id.drawerLayout);
        drawerToggle = new ActionBarDrawerToggle(
                this, drawer, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);
        NavigationView navigationView = findViewById(R.id.navigationView);
        navigationView.setNavigationItemSelectedListener(this);

        mSettingsActivityIntent = new Intent(MainActivity.this, SettingsActivity.class);

        // Fragment listener to update activity title and replace hamburger action with back action when result fragment is active
        getSupportFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) instanceof ResultFragment) {
                    if (actionBar != null)
                        actionBar.setTitle(R.string.result_activity_title);
                    drawerToggle.setDrawerIndicatorEnabled(false); // disable navigation drawer hamburger icon to show back button instead
                } else { // Revert back
                    drawerToggle.setDrawerIndicatorEnabled(true); // enable navigation drawer hamburger icon
                    if (actionBar != null)
                        actionBar.setTitle(R.string.app_name);
                }
            }
        });
    }

    /**
     * Makes AdView visible and requests ads. Also hides subscription feature if already used.
     */
    private void showAds() {
        mAdView = findViewById(R.id.adView);
        if (prefs.getBoolean("showAds", false)) {
            mAdView.setVisibility(View.VISIBLE);
            AdRequest adRequest = new AdRequest.Builder()
//                        .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                    .addTestDevice("48238A90C3E3A03DAE2EED222A8A993F") // Samsung S7 Edge
                    .build();
            mAdView.loadAd(adRequest);
            // Show subscription feature
            NavigationView navigationView = (NavigationView) findViewById(R.id.navigationView);
            Menu nav_Menu = navigationView.getMenu();
            nav_Menu.findItem(R.id.nav_subscription).setVisible(true);
        }
        else {
            mAdView.setVisibility(View.GONE);
            if (mAdView != null)
                mAdView.resume();
            // Hide subscription feature
            NavigationView navigationView = (NavigationView) findViewById(R.id.navigationView);
            Menu nav_Menu = navigationView.getMenu();
            nav_Menu.findItem(R.id.nav_subscription).setVisible(false);
        }
    }

    /**
     * Initializes billing client for subscription operations
     */
    private void initializeBillingClient() {
        if (billingClient == null) {
            // Prepare in app billing client
            billingClient = BillingClient.newBuilder(getApplicationContext()).setListener(new PurchasesUpdatedListener() {
                @Override
                public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> list) {
                    // Acknowledge purchases
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                            && list != null) {
                        for (Purchase purchase : list) {
                            if (purchase.getSku().equals("remove_ads"))
                                handlePurchase(purchase);
                        }
                    }
                }
            }).enablePendingPurchases().build();
            acknowledgePurchaseResponseListener = new AcknowledgePurchaseResponseListener() {
                @Override
                public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
                }
            };
        }
    }

    /**
     * Performs acknowledgement when purchase is done.
     * @param purchase: The item purchased
     */
    void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            // Grant entitlement to the user.
            SharedPreferences.Editor e = prefs.edit();
            e.putBoolean("showAds", false);
            e.apply();
            showAds();
            // Acknowledge the purchase if it hasn't already been acknowledged.
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                        AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, acknowledgePurchaseResponseListener);
            }
        }
    }

    /**
     * Checks currently active subscriptions using Play Store's cache. Since server verification is not used, this method may take longer time to update.
     */
    private void checkSubscription() {
        initializeBillingClient();
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                List<Purchase> purchaseList = billingClient.queryPurchases(BillingClient.SkuType.SUBS).getPurchasesList();
                SharedPreferences.Editor e = prefs.edit();
                if (purchaseList != null) {
                    boolean removeAds = false;
                    for (Purchase purchase : purchaseList) {
                        // Process the result.
                        if (purchase.getSku().equals("remove_ads")) {
                            removeAds = true;
                            break;
                        }
                    }
                    if (removeAds) {
                        e.putBoolean("showAds", false);
                    } else {
                        e.putBoolean("showAds", true);
                    }
                }
                else {
                    e.putBoolean("showAds", true);
                }
                e.apply();
                // Show ads if necessary
                showAds();
            }

            @Override
            public void onBillingServiceDisconnected() {

            }
        });
    }

    /**
     * Shows tutorial where specific UI items gets focused with their explanation. Don't mix it with intro.
     * Intro happens full screen, where this uses app's ui.
     * Called when all the View items to be focused are available to be referenced.
     * @param targetTutorial 0: Show tutorial for Scanning, 1: Tickets
     */
    private void startTutorial(int targetTutorial) {
        // If tutorial is not already shown
        if (targetTutorial == 0 && !prefs.getBoolean("isScanningTutorialShown", false)) { // Scanning tutorial
            // Get action menu item's location
            int[] loc = new int[2];
            actionMenuRowNumberView.getLocationOnScreen(loc);
            // If location is correct, show the tutorial (first time this function is executed the value gets 0, don't know why)
            if (loc[0] != 0) {
                // Edit preference to prevent 2nd time drawing of tutorial
                SharedPreferences.Editor e = prefs.edit();
                e.putBoolean("isScanningTutorialShown", true);
                e.apply();
                // Get action item's location (otherwise the tutorial may not past the tabLayout focus, bug i think)
                Rect targetRect = new Rect(loc[0], loc[1], loc[0] + actionMenuRowNumberView.getWidth(), loc[1] + actionMenuRowNumberView.getHeight());
                // Prepare targets' sequence
                TapTargetSequence tapTargetSequence = new TapTargetSequence(this)
                        .targets(
                                // Game type focus
                                TapTarget.forView(tabLayoutView, getResources().getString(R.string.tutorial_instruction_select_game), "")
                                        .outerCircleAlpha(0.96f)            // Specify the alpha amount for the outer circle
                                        .titleTextSize(20)                  // Specify the size (in sp) of the title text
                                        .descriptionTextSize(10)            // Specify the size (in sp) of the description text
                                        .textTypeface(Typeface.SANS_SERIF)  // Specify a typeface for the text
                                        .dimColor(android.R.color.black)    // If set, will dim behind the view with 30% opacity of the given color
                                        .drawShadow(true)                   // Whether to draw a drop shadow or not
                                        .cancelable(true)                  // Whether tapping outside the outer circle dismisses the view
                                        .tintTarget(true)                   // Whether to tint the target view's color
                                        .transparentTarget(false)           // Specify whether the target is transparent (displays the content underneath)
                                        .targetRadius(60),                  // Specify the target radius (in dp)
                                // Row number focus
                                TapTarget.forBounds(targetRect, getResources().getString(R.string.tutorial_instruction_select_type), "")
                                        .outerCircleAlpha(0.96f)            // Specify the alpha amount for the outer circle
                                        .titleTextSize(20)                  // Specify the size (in sp) of the title text
                                        .descriptionTextSize(10)            // Specify the size (in sp) of the description text
                                        .textTypeface(Typeface.SANS_SERIF)  // Specify a typeface for the text
                                        .drawShadow(true)                   // Whether to draw a drop shadow or not
                                        .cancelable(true)                   // Whether tapping outside the outer circle dismisses the view
                                        .tintTarget(true)                   // Whether to tint the target view's color
                                        .transparentTarget(false)           // Specify whether the target is transparent (displays the content underneath)
                                        .targetRadius(60)                   // Specify the target radius (in dp)
                                        .icon(getResources().getDrawable(R.drawable.ic_tutorial_rownum)))
                        .listener(new TapTargetSequence.Listener() {
                            @Override
                            public void onSequenceFinish() {
                            }

                            @Override
                            public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                            }

                            @Override
                            public void onSequenceCanceled(TapTarget lastTarget) {
                            }
                        })
                        .continueOnCancel(true);
                tapTargetSequence.start();
            }
        }
        else if (targetTutorial == 1 && !prefs.getBoolean("isTicketsTutorialShown", false)) { // Tickets tutorial
            int[] loc = new int[2];
            actionMenuAddTicketView.getLocationOnScreen(loc);
            // If location is correct, show the tutorial (first time this function is executed the value gets 0, don't know why)
            if (loc[0] != 0) {
                // Edit preference to prevent 2nd time drawing of tutorial
                SharedPreferences.Editor e = prefs.edit();
                e.putBoolean("isTicketsTutorialShown", true);
                e.apply();
                // Get action item's location (otherwise the tutorial may not past the tabLayout focus, bug i think)
                Rect targetRect = new Rect(loc[0], loc[1], loc[0] + actionMenuAddTicketView.getWidth(), loc[1] + actionMenuAddTicketView.getHeight());
                // Prepare targets' sequence
                TapTargetSequence tapTargetSequence = new TapTargetSequence(this)
                        .targets(
                                // Game type focus
                                TapTarget.forView(bottomNavigationScanView, getResources().getString(R.string.tutorial_instruction_scan_ticket), "")
                                        .outerCircleAlpha(0.96f)            // Specify the alpha amount for the outer circle
                                        .titleTextSize(20)                  // Specify the size (in sp) of the title text
                                        .descriptionTextSize(10)            // Specify the size (in sp) of the description text
                                        .textTypeface(Typeface.SANS_SERIF)  // Specify a typeface for the text
                                        .dimColor(android.R.color.black)    // If set, will dim behind the view with 30% opacity of the given color
                                        .drawShadow(true)                   // Whether to draw a drop shadow or not
                                        .cancelable(true)                  // Whether tapping outside the outer circle dismisses the view
                                        .tintTarget(true)                   // Whether to tint the target view's color
                                        .transparentTarget(false)           // Specify whether the target is transparent (displays the content underneath)
                                        .targetRadius(60),                  // Specify the target radius (in dp)
                                // Row number focus
                                TapTarget.forBounds(targetRect, getResources().getString(R.string.tutorial_instruction_add_ticket), "")
                                        .outerCircleAlpha(0.96f)            // Specify the alpha amount for the outer circle
                                        .titleTextSize(20)                  // Specify the size (in sp) of the title text
                                        .descriptionTextSize(10)            // Specify the size (in sp) of the description text
                                        .textTypeface(Typeface.SANS_SERIF)  // Specify a typeface for the text
                                        .drawShadow(true)                   // Whether to draw a drop shadow or not
                                        .cancelable(true)                  // Whether tapping outside the outer circle dismisses the view
                                        .tintTarget(true)                   // Whether to tint the target view's color
                                        .transparentTarget(false)           // Specify whether the target is transparent (displays the content underneath)
                                        .targetRadius(60)
                                        .icon(getResources().getDrawable(getResources().getIdentifier("ic_menu_add", "drawable", getApplicationContext().getPackageName()), getApplicationContext().getTheme())))                  // Specify the target radius (in dp)
                        .listener(new TapTargetSequence.Listener() {
                            @Override
                            public void onSequenceFinish() {
                            }

                            @Override
                            public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                            }

                            @Override
                            public void onSequenceCanceled(TapTarget lastTarget) {
                            }
                        })
                        .continueOnCancel(true);
                tapTargetSequence.start();
            }
        }
    }
}
