package com.creadeep.kazanio;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import java.io.InputStream;

public class LicenseActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_license);

    TextView tvLicense = findViewById(R.id.tv_license);
    String library = getIntent().getStringExtra("content");
    if (library != null) {
      switch (library) {
        case "retrofit":
          try {
            InputStream stream = getResources().openRawResource(R.raw.license_retrofit);
            byte[] b = new byte[stream.available()];
            stream.read(b);
            tvLicense.setText(new String(b));
            setTitle(R.string.title_license_activity);
          } catch (Exception e) {
            e.printStackTrace();
            tvLicense.setText("Error: Can't show license.");
          }
          break;
        case "materialIntro":
          try {
            InputStream stream = getResources().openRawResource(R.raw.license_materialintro);
            byte[] b = new byte[stream.available()];
            stream.read(b);
            tvLicense.setText(new String(b));
            setTitle(R.string.title_license_activity);
          } catch (Exception e) {
            e.printStackTrace();
            tvLicense.setText("Error: Can't show license.");
          }
          break;
        case "tapTargetView":
          try {
            InputStream stream = getResources().openRawResource(R.raw.license_taptargetview);
            byte[] b = new byte[stream.available()];
            stream.read(b);
            tvLicense.setText(new String(b));
            setTitle(R.string.title_license_activity);
          } catch (Exception e) {
            e.printStackTrace();
            tvLicense.setText("Error: Can't show license.");
          }
          break;
      }
    }
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null)
      getSupportActionBar().setDisplayHomeAsUpEnabled(true); // back button
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) { // Top bar back button
    int id = item.getItemId();
    if (id == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}
