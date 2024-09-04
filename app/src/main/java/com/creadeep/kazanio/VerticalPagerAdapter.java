package com.creadeep.kazanio;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

public class VerticalPagerAdapter extends RecyclerView.Adapter<VerticalPagerAdapter.OverlayViewHolder> {
    private Context context;
    private int gameType;
    private boolean transitionReady = false; // Stores the information for instruction animation to switch to transition animation. Used for better transition between instruction and scanning animations.
    private boolean stopScanningAnimation = false; // Stores the information for scanning animation to
    private VerticalPagerAdapter.OverlayViewHolder[] overlayViewHolders; // used to access each page's animation ImageView

    /*
    For each vertical_overlay_page creates a view and a viewHolder which will detect view elements to be used for setting their properties (image/text/another viewPager) in onBindViewHolder()
     */
    @NonNull
    @Override
    public OverlayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.vertical_overlay_page, parent, false);
        return new OverlayViewHolder(view);
    }

    public VerticalPagerAdapter(Context context, int game) {
        this.context = context;
        this.gameType = game;
        overlayViewHolders = new VerticalPagerAdapter.OverlayViewHolder[context.getResources().getIntArray(R.array.game_row_variations)[game-1]];
    }

    /*
    Setup each overlay frame within their vertical ViewPager
     */
    @Override
    public void onBindViewHolder(@NonNull OverlayViewHolder holder, int position) {

        // Set instruction animation
        if (gameType == 1 && position == 0)
            holder.animatedVectorDrawableCompat1 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("instruction_animation_millipiyango_1", "drawable", context.getPackageName()));
        else if (gameType == 1 && position == 1)
            holder.animatedVectorDrawableCompat1 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("instruction_animation_millipiyango_2", "drawable", context.getPackageName()));
        else if (gameType == 2 && position == 0)
            holder.animatedVectorDrawableCompat1 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("instruction_animation_sayisalloto_1", "drawable", context.getPackageName()));
        else if (gameType == 3 && (position == 0 || position == 1 || position == 2))
            holder.animatedVectorDrawableCompat1 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("instruction_animation_superloto_" + (position + 1), "drawable", context.getPackageName()));
        else if (gameType == 4 && (position == 0 || position == 1))
            holder.animatedVectorDrawableCompat1 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("instruction_animation_onnumara_" + (position + 1), "drawable", context.getPackageName()));
        else if (gameType == 5 && (position == 0 || position == 1))
            holder.animatedVectorDrawableCompat1 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("instruction_animation_sanstopu_" + (position + 1), "drawable", context.getPackageName()));
        else
            holder.animatedVectorDrawableCompat1 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("instruction_animation", "drawable", context.getPackageName()));
        holder.instructionAnimationImage.setImageDrawable(holder.animatedVectorDrawableCompat1);
        holder.instructionAnimationImage.setVisibility(View.VISIBLE);
        final Handler mainHandler1 = new Handler(Looper.getMainLooper());
        holder.animatedVectorDrawableCompat1.registerAnimationCallback(new Animatable2Compat.AnimationCallback() {
            @Override
            public void onAnimationEnd(final Drawable drawable) {
                if (transitionReady) {
                    mainHandler1.post(() -> {
                        holder.instructionAnimationImage.setVisibility(View.INVISIBLE);
                        holder.transitionAnimationImage.setVisibility(View.VISIBLE);
                        holder.animatedVectorDrawableCompat2.start();
                    });
                }
                else {
                    mainHandler1.post(() -> holder.animatedVectorDrawableCompat1.start());
                }
            }
        });
        holder.animatedVectorDrawableCompat1.start();

        // Set transition animation
        if (gameType == 1 && position == 0)
            holder.animatedVectorDrawableCompat2 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("transition_animation_millipiyango_1", "drawable", context.getPackageName()));
        else if (gameType == 1 && position == 1)
            holder.animatedVectorDrawableCompat2 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("transition_animation_millipiyango_2", "drawable", context.getPackageName()));
        else if (gameType == 2 && position == 0)
            holder.animatedVectorDrawableCompat2 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("transition_animation_sayisalloto_1", "drawable", context.getPackageName()));
        else if (gameType == 3 && (position == 0 || position == 1 || position == 2))
            holder.animatedVectorDrawableCompat2 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("transition_animation_superloto_" + (position + 1), "drawable", context.getPackageName()));
        else if (gameType == 4 && (position == 0 || position == 1))
            holder.animatedVectorDrawableCompat2 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("transition_animation_onnumara_" + (position + 1), "drawable", context.getPackageName()));
        else if (gameType == 5 && (position == 0 || position == 1))
            holder.animatedVectorDrawableCompat2 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("transition_animation_sanstopu_" + (position + 1), "drawable", context.getPackageName()));
        else
            holder.animatedVectorDrawableCompat2 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("transition_animation", "drawable", context.getPackageName()));

        holder.transitionAnimationImage.setImageDrawable(holder.animatedVectorDrawableCompat2);
        holder.transitionAnimationImage.setVisibility(View.INVISIBLE);
        final Handler mainHandler2 = new Handler(Looper.getMainLooper());
        holder.animatedVectorDrawableCompat2.registerAnimationCallback(new Animatable2Compat.AnimationCallback() {
            @Override
            public void onAnimationEnd(final Drawable drawable) {
                mainHandler2.post(() -> {
                    holder.transitionAnimationImage.setVisibility(View.INVISIBLE);
                    holder.scanningAnimationImage.setVisibility(View.VISIBLE);
                    holder.animatedVectorDrawableCompat3.start();
                });
            }

            @Override
            public void onAnimationStart(Drawable drawable) {
                super.onAnimationStart(drawable);
            }
        });

        // Set viewfinder overlay & scanning animation
        if (gameType == 1 && position == 0) {
            holder.animatedVectorDrawableCompat3 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("scanning_animation_millipiyango_1", "drawable", context.getPackageName()));
            holder.frameImage.setImageResource(context.getResources().getIdentifier("viewfinder_overlay_millipiyango_1", "drawable", context.getPackageName()));
        }
        else if (gameType == 1 && position == 1) {
            holder.animatedVectorDrawableCompat3 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("scanning_animation_millipiyango_2", "drawable", context.getPackageName()));
            holder.frameImage.setImageResource(context.getResources().getIdentifier("viewfinder_overlay_millipiyango_2", "drawable", context.getPackageName()));
        }
        else if (gameType == 2 && (position == 0 || position == 1 || position == 2)) {
            holder.animatedVectorDrawableCompat3 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("scanning_animation_sayisalloto_" + (position + 1), "drawable", context.getPackageName()));
            holder.frameImage.setImageResource(context.getResources().getIdentifier("viewfinder_overlay_sayisalloto_" + (position+1), "drawable", context.getPackageName()));
        }
        else if (gameType == 3 && (position == 0 || position == 1 || position == 2 || position == 3 || position == 4)) {
            holder.animatedVectorDrawableCompat3 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("scanning_animation_superloto_" + (position + 1), "drawable", context.getPackageName()));
            holder.frameImage.setImageResource(context.getResources().getIdentifier("viewfinder_overlay_superloto_" + (position+1), "drawable", context.getPackageName()));
        }
        else if (gameType == 4 && (position == 0 || position == 1 || position == 2)) {
            holder.animatedVectorDrawableCompat3 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("scanning_animation_onnumara_" + (position + 1), "drawable", context.getPackageName()));
            holder.frameImage.setImageResource(context.getResources().getIdentifier("viewfinder_overlay_onnumara_" + (position+1), "drawable", context.getPackageName()));
        }
        else if (gameType == 5 && (position == 0 || position == 1 || position == 2)) {
            holder.animatedVectorDrawableCompat3 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("scanning_animation_sanstopu_" + (position + 1), "drawable", context.getPackageName()));
            holder.frameImage.setImageResource(context.getResources().getIdentifier("viewfinder_overlay_sanstopu_" + (position+1), "drawable", context.getPackageName()));
        }
        else {
            holder.animatedVectorDrawableCompat3 = AnimatedVectorDrawableCompat.create(context, context.getResources().getIdentifier("scanning_animation", "drawable", context.getPackageName()));
            holder.frameImage.setImageResource(context.getResources().getIdentifier("viewfinder_overlay", "drawable", context.getPackageName()));
        }
        holder.scanningAnimationImage.setImageDrawable(holder.animatedVectorDrawableCompat3);
        holder.scanningAnimationImage.setVisibility(View.INVISIBLE);
        final Handler mainHandler3 = new Handler(Looper.getMainLooper());
        holder.animatedVectorDrawableCompat3.registerAnimationCallback(new Animatable2Compat.AnimationCallback() {
            @Override
            public void onAnimationEnd(final Drawable drawable) {
                mainHandler3.post(() -> {
                    if (!stopScanningAnimation) {
                        holder.animatedVectorDrawableCompat3.start();
                    }
                    else {
                        holder.scanningAnimationImage.setVisibility(View.INVISIBLE);
                        stopScanningAnimation = false;
                    }
                });
            }
            // Prevent transition to scanning animation after user leaves a row and comes back to it by swiping while transition animation is active
            @Override
            public void onAnimationStart(Drawable drawable) {
                super.onAnimationStart(drawable);
                if (!transitionReady) {
                    holder.scanningAnimationImage.setVisibility(View.INVISIBLE);
                    transitionReady = false;
                }
            }
        });

        this.overlayViewHolders[position] = holder;
    }

    /*
    Returns the number of possible row number variations
     */
    @Override
    public int getItemCount() {
        int[] row_variations = context.getResources().getIntArray(R.array.game_row_variations_limited);
        return row_variations[gameType - 1];
    }

    public static class OverlayViewHolder extends RecyclerView.ViewHolder {
        public ImageView frameImage;
        public ImageView scanningAnimationImage;
        public ImageView instructionAnimationImage;
        public ImageView transitionAnimationImage;
        public AnimatedVectorDrawableCompat animatedVectorDrawableCompat3;
        public AnimatedVectorDrawableCompat animatedVectorDrawableCompat1;
        public AnimatedVectorDrawableCompat animatedVectorDrawableCompat2;

        public OverlayViewHolder(View view) {
            super(view);
            frameImage = view.findViewById(R.id.overlay_frame);
            scanningAnimationImage = view.findViewById(R.id.overlay_scanning_animation);
            instructionAnimationImage = view.findViewById(R.id.overlay_instruction_animation);
            transitionAnimationImage = view.findViewById(R.id.overlay_transition_animation);
        }
    }

    public void setScanningAnimation(int row, boolean state){
        // Update UI from UI thread
        ((MainActivity)context).runOnUiThread(() -> {
            if(overlayViewHolders.length > row && overlayViewHolders[row] != null) {
                if (state) {
                    transitionReady = true;
                    stopScanningAnimation = false;
                }
                // Stop scanning animation. (Animation is not directly stopped since there is no method to reset it. Thus, the flag is set here, and stopping happens at onAnimationEnd callback. Introduces possible jitter when user swipes left/right while scanning and comes back.)
                else {
                    transitionReady = false;
                    overlayViewHolders[row].scanningAnimationImage.setVisibility(View.INVISIBLE);
                    stopScanningAnimation = true;
                }
            }
        });
    }

    public void setInstructionAnimation(int row, boolean state){
        // Update UI from UI thread
        ((MainActivity)context).runOnUiThread(() -> {
            if(overlayViewHolders.length > row && overlayViewHolders[row] != null) {
                if (state) {
                    overlayViewHolders[row].instructionAnimationImage.setVisibility(View.VISIBLE);
                    overlayViewHolders[row].animatedVectorDrawableCompat1.start();
                }
            }
        });
    }
}
