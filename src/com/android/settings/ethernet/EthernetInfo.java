/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings.ethernet;


import com.android.settings.R;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileReader;
import android.util.Log;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.os.SystemProperties;



public class EthernetInfo extends PreferenceFragment {
    private static final String TAG = "DeviceInfoSettingEthInfo";
    private final static String CONFIG_PATH = "/sys/class/efuse/mac";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.ethernet_info);

        findPreference("eth_ip_address").setSummary(new StringBuilder(getEthIP()).toString());
        findPreference("eth_dns").setSummary(new StringBuilder(getEthDNS1()).toString());
        findPreference("eth_gateway").setSummary(new StringBuilder(getEthGateway()).toString());
        findPreference("eth_mac").setSummary(new StringBuilder(getEthMacfromEfuse()).toString());
        findPreference("eth_mask").setSummary(new StringBuilder(getEthMask()).toString());

    }

    private String getEthMacfromEfuse() {
        String sn = null;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(CONFIG_PATH), 12);
            try {
                sn = reader.readLine();
            } finally {
                reader.close();
            }
            Log.d(TAG, "/sys/class/efuse/mac: " + sn);

            if(sn.equals("00:00:00:00:00:00")) {
                return SystemProperties.get("ubootenv.var.ethaddr",getString(R.string.status_unavailable));
            } else {
                return sn;
            }
        } catch (IOException e) {
            Log.e(TAG,"IO Exception when getting serial number for Device Info screen",e);
            return SystemProperties.get("ubootenv.var.ethaddr",getString(R.string.status_unavailable));
        }
    }

    private String getEthIP() {
        return SystemProperties.get("dhcp.eth0.ipaddress", getString(R.string.status_unavailable));
    }

    private String getEthDNS1() {
        return SystemProperties.get("dhcp.eth0.dns1", getString(R.string.status_unavailable));
    }

    private String getEthDNS2() {
        return SystemProperties.get("dhcp.eth0.dns2", getString(R.string.status_unavailable));
    }

    private String getEthMask()   {
        return SystemProperties.get("dhcp.eth0.mask", getString(R.string.status_unavailable));
    }

    private String getEthGateway()   {
        return SystemProperties.get("dhcp.eth0.gateway", getString(R.string.status_unavailable));
    }
}
