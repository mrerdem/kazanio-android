package com.creadeep.kazanio;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

public class HorizontalPagerAdapter extends RecyclerView.Adapter<HorizontalPagerAdapter.OverlayViewHolder> {
    private Context context;
    private int gamesNum; // Total number of games (MP, 10 numara, etc...)
    private int activeRowNum = 9;
    private RowNumberListener rowNumberCallback; // Used to inform activity from events happening in fragment
    private ViewPager2[] verticalViewPager;
    private VerticalPagerAdapter verticalPagerAdapter;

    /*
    For each horizontal_overlay_page creates a view and a viewHolder which will detect view elements to be used for setting their properties (image/text/another viewPager) in onBindViewHolder()
     */
    @NonNull
    @Override
    public OverlayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.horizontal_overlay_page, parent, false);
        return new OverlayViewHolder(view);
    }

    public HorizontalPagerAdapter(Context context, int numGame) {
        this.context = context;
        this.gamesNum = numGame;
        verticalViewPager = new ViewPager2[numGame];
    }

    /*
    Setup each vertical ViewPager within this horizontal ViewPager
     */
    @Override
    public void onBindViewHolder(@NonNull OverlayViewHolder holder, int hPosition) {
        verticalPagerAdapter = new VerticalPagerAdapter(context, hPosition + 1);
        verticalViewPager[hPosition] = holder.viewPager2;
        verticalViewPager[hPosition].setAdapter(verticalPagerAdapter);
        verticalViewPager[hPosition].setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        // Inform main activity when vertical page changes
        verticalViewPager[hPosition].registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
            }

            @Override
            public void onPageSelected(int vPosition) {
                super.onPageSelected(vPosition);
                // Inform main activity
                rowNumberCallback.onRowNumberChanged(verticalViewPager[hPosition].getCurrentItem() + 1, hPosition + 1); // Using array of vertical view pagers to prevent: if the user swipes vertically on game #2, the current item of game #3 is returned, which is fixed.
                // Reset animations on the current game & row before changing to another one
                setScanningAnimationStatus(hPosition + 1, activeRowNum - 1, false);
                setInstructionAnimationStatus(hPosition + 1, activeRowNum - 1, true);
                // Update status variable
                activeRowNum = verticalViewPager[hPosition].getCurrentItem() + 1;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
            }
        });
        // Set row if viewPager was not available via setRowNumber()
        if (activeRowNum != 9) {
            verticalViewPager[hPosition].setCurrentItem(activeRowNum - 1, false);
        }
    }

    /*
    Returns the total number of games
     */
    @Override
    public int getItemCount() {
        return gamesNum;
    }

    /*
    Interface to inform ScanFragment from changes made on vertical page (number of rows)
     */
    public void setRowNumberListener(RowNumberListener callback) {
        this.rowNumberCallback = callback;
    }

    /*
    Relays the vertical page number to be set to child fragment
     */
    public void setRowNumber(int row, int game) {
        if (verticalViewPager[game - 1] != null) {
            verticalViewPager[game - 1].setCurrentItem(row - 1, false); // Disable smoothScroll to prevent distracting vertical movement while user swipes horizontally
        }
        activeRowNum = row; // onBindViewHolder has not finished yet so store it in a variable to be used during binding
    }

    public void setScanningAnimationStatus(int game, int rows, boolean b) {
        if (verticalViewPager.length > game - 1 && verticalViewPager[game - 1] != null) {
            VerticalPagerAdapter verticalPagerAdapter = (VerticalPagerAdapter) verticalViewPager[game - 1].getAdapter();
            verticalPagerAdapter.setScanningAnimation(rows, b);
        }
    }

    public void setInstructionAnimationStatus(int game, int rows, boolean b) {
        if (verticalViewPager.length > game - 1 && verticalViewPager[game - 1] != null) {
            VerticalPagerAdapter verticalPagerAdapter = (VerticalPagerAdapter) verticalViewPager[game - 1].getAdapter();
            verticalPagerAdapter.setInstructionAnimation(rows, b);
        }
    }

    /*
    Sets the vertical page to the one ordered from Main Activity via top menu action item
     */
    public interface RowNumberListener {
        void onRowNumberChanged(int rowNum, int gameType);
    }

    public class OverlayViewHolder extends RecyclerView.ViewHolder {
        public ViewPager2 viewPager2;

        public OverlayViewHolder(View view) {
            super(view);
            viewPager2 = view.findViewById(R.id.view_pager_vertical);
        }
    }
}
