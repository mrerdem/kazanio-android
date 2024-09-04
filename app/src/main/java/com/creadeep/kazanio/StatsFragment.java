package com.creadeep.kazanio;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StatsFragment extends Fragment {
  private View v;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    v = inflater.inflate(R.layout.fragment_stats, container, false);
    return v;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    TabLayout tabLayout = v.findViewById(R.id.tab_layout);
    ViewPager viewPager = v.findViewById(R.id.view_pager);
    GameTypePagerAdapter adapter = new GameTypePagerAdapter(getActivity().getSupportFragmentManager(), 1);

    // Prepare tab titles
    List<String> tabNames = new ArrayList<>();
    tabNames.add(getResources().getString(R.string.tab_name_all)); // Add only the first tab's title
    tabNames.addAll(Arrays.asList(getResources().getStringArray(R.array.game_names))); // Add game names as remaining titles

    // Populate pages with the adapter using tab titles
    adapter = new GameTypePagerAdapter(getActivity().getSupportFragmentManager(), 1);
    for (int i = 0; i < tabNames.size(); i++) {
      adapter.addFragment(new StatsSingleTypeFragment(i), tabNames.get(i));
    }
    viewPager.setAdapter(adapter);

    tabLayout.setupWithViewPager(viewPager);

  }
}
