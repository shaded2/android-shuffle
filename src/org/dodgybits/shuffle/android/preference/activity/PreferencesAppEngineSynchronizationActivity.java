package org.dodgybits.shuffle.android.preference.activity;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import com.google.inject.Inject;
import org.dodgybits.android.shuffle.R;
import org.dodgybits.shuffle.android.preference.fragment.PreferencesAppEngineSynchronizationFragment;
import org.dodgybits.shuffle.android.server.sync.GaeSyncService;
import roboguice.activity.RoboFragmentActivity;

public class PreferencesAppEngineSynchronizationActivity extends RoboFragmentActivity {
    private static final String TAG = "PrefAppEngSyncAct";

    public static final int ACCOUNTS_DIALOG = 1;


    @Inject
    private PreferencesAppEngineSynchronizationFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.fragment_container);

        PreferencesAppEngineSynchronizationFragment currentFragment =
                (PreferencesAppEngineSynchronizationFragment) getSupportFragmentManager().
                findFragmentById(R.id.fragment_container);
        if (currentFragment == null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.fragment_container, mFragment);
            ft.show(mFragment);
            ft.commit();
        } else {
            mFragment = currentFragment;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;

        switch (id) {
            case ACCOUNTS_DIALOG:
                dialog = mFragment.createAccountsDialog();
                break;
        }

        return dialog;
    }

    public void onToggleSyncClicked(View view) {
        mFragment.onToggleSyncClicked(view);
    }

    public void onSelectAccountClicked(View view) {
        mFragment.onSelectAccountClicked(view);
    }

    public void onLogoutClicked(View view) {
        mFragment.onLogoutClicked(view);
    }

    public void onSyncNowClicked(View view) {
        Intent intent = new Intent(this, GaeSyncService.class);
        startService(intent);

    }


}