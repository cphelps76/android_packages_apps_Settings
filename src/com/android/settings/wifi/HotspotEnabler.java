/*
 * Copyright (C) 2010 The Android Open Source Project
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
 */

package com.android.settings.wifi;

import com.android.settings.R;
import com.android.settings.WirelessSettings;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicBoolean;

public class HotspotEnabler implements CompoundButton.OnCheckedChangeListener {
    private final Context mContext;
    private Switch mSwitch;

    private WifiManager mWifiManager;
    private final IntentFilter mIntentFilter;

    ConnectivityManager mCm;
    private boolean mStateMachineEvent;
    private AtomicBoolean mConnected = new AtomicBoolean(false);

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                handleWifiApStateChanged(intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED));
            } else if (ConnectivityManager.ACTION_TETHER_STATE_CHANGED.equals(action)) {
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(
                        WifiManager.EXTRA_NETWORK_INFO);
                mConnected.set(info.isConnected());
                handleStateChanged(info.getDetailedState());
            }
        }
    };

    public HotspotEnabler(Context context, Switch switch_) {
        mContext = context;
        mSwitch = switch_;

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mCm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        mIntentFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    }

    public void resume() {
        mContext.registerReceiver(mReceiver, mIntentFilter);
        mSwitch.setOnCheckedChangeListener(this);
    }

    public void pause() {
        mContext.unregisterReceiver(mReceiver);
        mSwitch.setOnCheckedChangeListener(null);
    }

    public void setSwitch(Switch switch_) {
        if (mSwitch == switch_) return;
        mSwitch.setOnCheckedChangeListener(null);
        mSwitch = switch_;
        mSwitch.setOnCheckedChangeListener(this);

        boolean isAirplaneMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        int wifiState = mWifiManager.getWifiState();
        boolean isEnabled = wifiState == WifiManager.WIFI_AP_STATE_ENABLED;
        boolean isDisabled = wifiState == WifiManager.WIFI_AP_STATE_DISABLED;
        mSwitch.setChecked(!isAirplaneMode && isEnabled);
        mSwitch.setEnabled(isEnabled || isDisabled);
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        // Do nothing if called as a result of a state machine event
        if (mStateMachineEvent) {
            return;
        }

        final ContentResolver cr = mContext.getContentResolver();
        /**
         * Disable Wifi if enabling tethering
         */
        int wifiState = mWifiManager.getWifiState();
        if (isChecked && ((wifiState == WifiManager.WIFI_STATE_ENABLING) ||
                    (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
            mWifiManager.setWifiEnabled(false);
            Settings.Global.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 1);
        }

        if (mWifiManager.setWifiApEnabled(null, isChecked)) {
            /* Disable here, enabled on receiving success broadcast */
            mSwitch.setEnabled(false);
        }

        /**
         *  If needed, restore Wifi on tether disable
         */
        if (!isChecked) {
            int wifiSavedState = 0;
            try {
                wifiSavedState = Settings.Global.getInt(cr, Settings.Global.WIFI_SAVED_STATE);
            } catch (Settings.SettingNotFoundException e) {
            }
            if (wifiSavedState == 1) {
                mWifiManager.setWifiEnabled(true);
                Settings.Global.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 0);
            }
        }
    }

    private void handleWifiApStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
                mSwitch.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                setSwitchChecked(true);
                /* Doesnt need the airplane check */
                mSwitch.setEnabled(true);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                mSwitch.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                setSwitchChecked(false);
                mSwitch.setEnabled(true);
                break;
            default:
                setSwitchChecked(false);
                mSwitch.setEnabled(true);
        }
    }

    private void setSwitchChecked(boolean checked) {
        if (checked != mSwitch.isChecked()) {
            mStateMachineEvent = true;
            mSwitch.setChecked(checked);
            mStateMachineEvent = false;
        }
    }

    private void handleStateChanged(@SuppressWarnings("unused") NetworkInfo.DetailedState state) {
        // After the refactoring from a CheckBoxPreference to a Switch, this method is useless since
        // there is nowhere to display a summary.
        // This code is kept in case a future change re-introduces an associated text.
    }
}
