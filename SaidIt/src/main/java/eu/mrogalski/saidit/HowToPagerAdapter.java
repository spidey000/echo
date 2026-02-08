package eu.mrogalski.saidit;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class HowToPagerAdapter extends FragmentStateAdapter {

    private static final int NUM_PAGES = 3; // Example number of pages

    public HowToPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return HowToPageFragment.newInstance(position);
    }

    @Override
    public int getItemCount() {
        return NUM_PAGES;
    }
}
