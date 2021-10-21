package org.owntracks.android.ui.preferences;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.owntracks.android.R;
import org.owntracks.android.databinding.UiPreferencesBinding;
import org.owntracks.android.ui.base.BaseActivity;
import org.owntracks.android.ui.base.view.MvvmView;
import org.owntracks.android.ui.base.viewmodel.NoOpViewModel;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PreferencesActivity extends BaseActivity<UiPreferencesBinding, NoOpViewModel> implements MvvmView, PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    protected Fragment getStartFragment() {
        return new PreferencesFragment();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ui_preferences);
        bindAndAttachContentView(R.layout.ui_preferences, savedInstanceState);
        setHasEventBus(false);
        setSupportToolbar(this.binding.appbar.toolbar, true, true);
        setDrawer(binding.appbar.toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
                    if (getSupportFragmentManager().getFragments().isEmpty()) {
                        setToolbarTitle(getTitle());
                    } else {

                        setToolbarTitle(((PreferenceFragmentCompat) getSupportFragmentManager().getFragments().get(0)).getPreferenceScreen().getTitle());
                    }
                }
        );

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, getStartFragment(), null);
        fragmentTransaction.commit();
        getSupportFragmentManager().executePendingTransactions();
    }

    private void setToolbarTitle(CharSequence text) {
        binding.appbar.toolbar.setTitle(text);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        final Bundle args = pref.getExtras();
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                pref.getFragment());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);
        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content_frame, fragment)
                .addToBackStack(pref.getKey())
                .commit();
        return true;
    }
}
