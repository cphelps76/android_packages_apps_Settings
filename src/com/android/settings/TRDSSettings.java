/*
 * Copyright (C) 2014 Matricom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Yi Sun <beyounn@gmail.com>
 */

package com.android.settings;

import android.os.RemoteException;
import android.preference.*;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.TRDSEnabler;
import com.android.settings.Utils;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Switch;

public class TRDSSettings extends SettingsPreferenceFragment {
    private static final String LOG_TAG = "TRDS";

    private TRDSEnabler mTRDSEnabler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            initToggles();
        } catch (RemoteException e) {
            // fail quietly
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mTRDSEnabler != null) {
            mTRDSEnabler.resume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mTRDSEnabler != null) {
            mTRDSEnabler.pause();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if(Utils.platformHasMbxUiMode()){
	        final Activity activity = getActivity();
	        activity.getActionBar().setDisplayOptions(0, ActionBar.DISPLAY_SHOW_CUSTOM);
	        activity.getActionBar().setCustomView(null);
        }
    }

    private void initToggles() throws RemoteException {
        // For MultiPane preference, the switch is on the left column header.
        // Other layouts unsupported for now.

        final Activity activity = getActivity();
        Switch actionBarSwitch = new Switch(activity);
        if (activity instanceof PreferenceActivity) {
            PreferenceActivity preferenceActivity = (PreferenceActivity) activity;
            if (Utils.platformHasMbxUiMode()) {
                final int padding = activity.getResources().getDimensionPixelSize(
                        R.dimen.action_bar_switch_padding);
                actionBarSwitch.setPadding(0, 0, padding, 0);
                activity.getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                        ActionBar.DISPLAY_SHOW_CUSTOM);
                activity.getActionBar().setCustomView(actionBarSwitch, new ActionBar.LayoutParams(
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL | Gravity.RIGHT));
            }
            else if (preferenceActivity.onIsHidingHeaders() || !preferenceActivity.onIsMultiPane()) {
                final int padding = activity.getResources().getDimensionPixelSize(
                        R.dimen.action_bar_switch_padding);
                actionBarSwitch.setPadding(0, 0, padding, 0);
                activity.getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                        ActionBar.DISPLAY_SHOW_CUSTOM);
                activity.getActionBar().setCustomView(actionBarSwitch, new ActionBar.LayoutParams(
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL | Gravity.RIGHT));
            }
            mTRDSEnabler = new TRDSEnabler(activity, actionBarSwitch);
        }
    }
}
