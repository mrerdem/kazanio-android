package com.creadeep.kazanio;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

public class AboutActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_about);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true); // back button
    TextView tvLicenseRetrofit = findViewById(R.id.btn_license_retrofit);
    TextView tvLicenseMaterialIntro = findViewById(R.id.btn_license_materialIntro);
    TextView tvLicenseTapTargetView = findViewById(R.id.btn_license_tapTargetView);

    tvLicenseMaterialIntro.setOnClickListener(v -> {
      final Intent i = new Intent(getApplicationContext(), LicenseActivity.class);
      i.putExtra("content", "materialIntro");
      startActivity(i);
    });

    tvLicenseRetrofit.setOnClickListener(v -> {
      final Intent i = new Intent(getApplicationContext(), LicenseActivity.class);
      i.putExtra("content", "retrofit");
      startActivity(i);
    });

    tvLicenseTapTargetView.setOnClickListener(v -> {
      final Intent i = new Intent(getApplicationContext(), LicenseActivity.class);
      i.putExtra("content", "tapTargetView");
      startActivity(i);
    });

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
