package com.creadeep.kazanio;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.preference.PreferenceManager;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

public class KazanioApplication extends Application {

  private final String MANUFACTURER = Build.MANUFACTURER;
  private final String MODEL = Build.MODEL;
  private final String PRODUCT = Build.PRODUCT;
  private final String HOST = Build.HOST;
  private final String VERSION = Build.VERSION.RELEASE;

  public String getMANUFACTURER() {
    return MANUFACTURER;
  }

  public String getMODEL() {
    return MODEL;
  }

  public String getPRODUCT() {
    return PRODUCT;
  }

  public String getHOST() {
    return HOST;
  }

  public String getVERSION() {
    return VERSION;
  }

  @Override
  public void onCreate() {
    super.onCreate();

    Context context = getApplicationContext();
      MobileAds.initialize(context, initializationStatus -> {
      });

    createNotificationChannel();

    // Set default preferences (they are not set until user opens settings activity! WTF Android???)
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    PreferenceManager.setDefaultValues(this, R.xml.preferences_reminders, true);

    // Update reminder and result notification services
    AppUtils.Companion.enableResultService(context);
    AppUtils.Companion.enableReminderService(context);

//    Log.i("TAG","BOARD: " + Build.BOARD);
//    Log.i("TAG","HARDWARE: " + Build.HARDWARE);
//    Log.i("TAG","MANUFACTURER: " + Build.MANUFACTURER);
//    Log.i("TAG","MODEL: " + Build.MODEL);
//    Log.i("TAG","PRODUCT: " + Build.PRODUCT);
//    Log.i("TAG", "SERIAL: " + Build.SERIAL);
//    Log.i("TAG","ID: " + Build.ID);
//    Log.i("TAG","brand: " + Build.BRAND);
//    Log.i("TAG","type: " + Build.TYPE);
//    Log.i("TAG","user: " + Build.USER);
//    Log.i("TAG","BASE: " + Build.VERSION_CODES.BASE);
//    Log.i("TAG","INCREMENTAL " + Build.VERSION.INCREMENTAL);
//    Log.i("TAG","BRAND " + Build.BRAND);
//    Log.i("TAG","HOST " + Build.HOST);
//    Log.i("TAG","FINGERPRINT: "+Build.FINGERPRINT);
//    Log.i("TAG","Version Code: " + Build.VERSION.RELEASE);

  }

  private void createNotificationChannel() {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // Drawing results notifications
      CharSequence name = getString(R.string.notification_channel_name_results);
      String description = getString(R.string.notification_channel_description_results);
      int importance = NotificationManager.IMPORTANCE_DEFAULT;
      NotificationChannel channel = new NotificationChannel("1", name, importance);
      channel.setDescription(description);
      // Drawing reminder notifications
      CharSequence name2 = getString(R.string.notification_channel_name_reminders);
      String description2 = getString(R.string.notification_channel_description_reminders);
      NotificationChannel channel2 = new NotificationChannel("2", name2, importance);
      channel.setDescription(description2);
      // Register the channel with the system; you can't change the importance
      // or other notification behaviors after this
      NotificationManager notificationManager = getSystemService(NotificationManager.class);
      if (notificationManager != null) {
        notificationManager.createNotificationChannel(channel);
        notificationManager.createNotificationChannel(channel2);
      }
    }
  }
}
