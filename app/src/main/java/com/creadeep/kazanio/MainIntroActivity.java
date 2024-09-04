package com.creadeep.kazanio;

import android.Manifest;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;
import com.heinrichreimersoftware.materialintro.app.IntroActivity;
import com.heinrichreimersoftware.materialintro.app.OnNavigationBlockedListener;
import com.heinrichreimersoftware.materialintro.slide.SimpleSlide;

public class MainIntroActivity extends IntroActivity {
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    /* Show/hide button */
    setButtonBackVisible(true);
    /* Use back button behavior */
    setButtonBackFunction(BUTTON_BACK_FUNCTION_BACK);
    /* Show/hide button */
    setButtonCtaVisible(true); // This is the button in the bottom middle above the page markers
    /* Tint button text */
    setButtonCtaTintMode(BUTTON_CTA_TINT_MODE_TEXT);
    /* Tint button background */
    setButtonCtaTintMode(BUTTON_CTA_TINT_MODE_BACKGROUND);

    /*Finish intro when skip intro button is pressed*/
    setButtonCtaClickListener(v -> goToLastSlide());

    // Show snackBar when user tries to skip permission
    addOnNavigationBlockedListener((position, direction) -> Snackbar.make(getContentView(), R.string.tutorial_permission_warning, Snackbar.LENGTH_LONG).show());

    // Welcome Slide
    addSlide(new SimpleSlide.Builder()
            .title(R.string.title_tutorial_overview)
            .description(R.string.text_tutorial_overview)
            .image(R.drawable.ic_launcher_foreground)
            .background(R.color.colorPrimary)
            .backgroundDark(R.color.colorPrimaryDark)
            .scrollable(false)
            .build());

    // Ticket Scanning Slide
    addSlide(new SimpleSlide.Builder()
            .title(R.string.title_tutorial_scan)
            .description(R.string.text_tutorial_scan)
            .image(R.drawable.ic_tutorial_camera)
            .background(R.color.colorPrimaryLight)
            .backgroundDark(R.color.colorPrimary)
            .scrollable(false)
            .permission(Manifest.permission.CAMERA)
            .build());

    // Learning Results slide
    addSlide(new SimpleSlide.Builder()
            .title(R.string.title_tutorial_result)
            .description(R.string.text_tutorial_result)
            .image(R.drawable.ic_tutorial_notifications)
            .background(R.color.colorPrimary)
            .backgroundDark(R.color.colorPrimaryDark)
            .scrollable(false)
            .build());
  }
}
