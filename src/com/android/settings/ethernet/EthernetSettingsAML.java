/*
 * Copyright (C) 2010 The Android-x86 Open Source Project
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

package com.android.settings.ethernet;

import android.preference.*;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ethernet.EthernetManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Switch;
import android.util.Log;
import com.android.settings.wifi.AdvancedWifiSettings;

public class EthernetSettingsAML extends SettingsPreferenceFragment {
    private static final String LOG_TAG = "Ethernet";
    private static final String KEY_ETH_CONF = "ethernet_config";
    private static final String KEY_ETH_INFO = "ethernet_info";

    private EthernetEnabler mEthEnabler;
    private EthernetConfigDialog mEthConfigDialog;
    private Preference mEthConfigPref;
    private Preference mEthInfoPref;

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        super.onPreferenceTreeClick(preferenceScreen, preference);

        if (preference == mEthConfigPref) {
            mEthConfigDialog.show();
            return true;
        } else if (preference == mEthInfoPref) {
            ((PreferenceActivity) getActivity()).startPreferencePanel(
                    EthernetInfo.class.getCanonicalName(),
                    null,
                    R.string.ethernet_info_title, null,
                    this, 0);
            return true;
        }
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.ethernet_settings);

        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        mEthConfigPref = preferenceScreen.findPreference(KEY_ETH_CONF);
        mEthInfoPref = preferenceScreen.findPreference(KEY_ETH_INFO);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mEthEnabler != null) {
            mEthEnabler.resume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mEthEnabler != null) {
            mEthEnabler.pause();
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
}
