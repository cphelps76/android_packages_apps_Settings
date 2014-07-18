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

package com.android.settings;

import android.app.SystemWriteManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.IWindowManager;


public class HdmiSettings extends SettingsPreferenceFragment implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "HdmiSettings";

    // Preferences
    private static final String KEY_SPDIF = "spdif";
    private static final String KEY_OUTPUT_MODE ="output_mode";
    private static final String KEY_DEFAULT_FREQUENCY = "default_frequency";

    // 720p values
    private static final int OUTPUT720_FULL_WIDTH = 1280;
    private static final int OUTPUT720_FULL_HEIGHT = 720;
    // 1080p values
    private static final int OUTPUT1080_FULL_WIDTH = 1920;
    private static final int OUTPUT1080_FULL_HEIGHT = 1080;

    // u-boot props
    private static final String HDMI_MODE_PROP = "ubootenv.var.hdmimode";
    private static final String COMMON_MODE_PROP = "ubootenv.var.outputmode";
    private static final String DIGITAL_AUDIO_OUTPUT_PROP = "ubootenv.var.digitaudiooutput";
    private static final String DEFAULT_FREQUENCY_PROP = "ubootenv.var.defaulttvfrequency";

    // sysfs paths
    private static final String AUDIODSP_DIGITAL_RAW = "/sys/class/audiodsp/digital_raw";
    private static final String FB0_FREESCALE_MODE = "/sys/class/graphics/fb0/freescale_mode";
    private static final String FB1_FREESCALE_MODE = "/sys/class/graphics/fb1/freescale_mode";
    private static final String FREESCALE_AXIS = "/sys/class/graphics/fb0/free_scale_axis";
    private static final String PPMGR_PPSCALER = "/sys/class/ppmgr/ppscaler";
    private static final String DISABLE_VIDEO = "/sys/class/video/disable_video";
    private static final String DISPLAY_AXIS = "/sys/class/display/axis";
    private static final String DISPLAY_MODE = "/sys/class/display/mode";

    private ListPreference mSpdifPref;
    private ListPreference mOutputModePref;

    private ListPreference  mDefaultFrequency;
    private CharSequence[] mDefaultFrequencyEntries;
    private CharSequence[] mDigitalOutputEntries;

    private static SystemWriteManager sw;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.hdmi_prefs);

        mSpdifPref = (ListPreference) findPreference(KEY_SPDIF);
        mSpdifPref.setOnPreferenceChangeListener(this);

        mOutputModePref = (ListPreference) findPreference(KEY_OUTPUT_MODE);
        mOutputModePref.setOnPreferenceChangeListener(this);
        // until bugs are worked out disable OutputModePref
        getPreferenceScreen().removePreference(findPreference(KEY_OUTPUT_MODE));

        mDefaultFrequency = (ListPreference) findPreference(KEY_DEFAULT_FREQUENCY);
        mDefaultFrequency.setOnPreferenceChangeListener(this);

        mDefaultFrequencyEntries = getResources().getStringArray(R.array.default_frequency_entries);
        mDigitalOutputEntries = getResources().getStringArray(R.array.hdmi_audio_output_entries);


        sw = (SystemWriteManager) getSystemService("system_write");

        updateSummaries();
    }

    private void updateSummaries() {
        String valDefaultFrequency = SystemProperties.get(DEFAULT_FREQUENCY_PROP);
        if (valDefaultFrequency.equals("")) {
            valDefaultFrequency = getResources().getString(R.string.hdmi_default_frequency_summary);
        }
        int index_DF = findIndexOfEntry(valDefaultFrequency, mDefaultFrequencyEntries);
        mDefaultFrequency.setValueIndex(index_DF);
        mDefaultFrequency.setSummary(valDefaultFrequency);

        mSpdifPref.setSummary(mSpdifPref.getEntry());
    }

    private void setDensity(String mode) {
        int density = 240;

        if (mode.contains("720")) {
            density = 160;
        }
        IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.checkService(
                Context.WINDOW_SERVICE));
        if (wm == null) {
            Log.d(TAG, "Can't connect to window manager, is the system running?");
            return;
        }

        try {
            if (density > 0) {
                wm.setForcedDisplayDensity(Display.DEFAULT_DISPLAY, density);
            } else {
                wm.clearForcedDisplayDensity(Display.DEFAULT_DISPLAY);
            }
        } catch (RemoteException e) {
            // fail quietly
        }

    }

    private void setDisplaySize(String newMode) {
        int width;
        int height;
        int mode = OUTPUT720_FULL_HEIGHT;

        if (newMode.contains(String.valueOf(OUTPUT1080_FULL_HEIGHT))) {
            mode = OUTPUT1080_FULL_HEIGHT;
        }

        switch (mode) {
            case OUTPUT1080_FULL_HEIGHT:
                width = OUTPUT1080_FULL_WIDTH;
                height = OUTPUT1080_FULL_HEIGHT;
                break;
            case OUTPUT720_FULL_HEIGHT:
                width = OUTPUT720_FULL_WIDTH;
                height = OUTPUT720_FULL_HEIGHT;
                break;
            default:
                width = OUTPUT720_FULL_WIDTH;
                height = OUTPUT720_FULL_HEIGHT;
                break;
        }

        IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.checkService(
                Context.WINDOW_SERVICE));
        if (wm == null) {
            Log.d(TAG, "Can't connect to window manager; is the system running?");
            return;
        }

        try {
            if (width >= 0 && height >= 0) {
                wm.setForcedDisplaySize(Display.DEFAULT_DISPLAY, width, height);
            } else {
                wm.clearForcedDisplaySize(Display.DEFAULT_DISPLAY);
            }
        } catch (RemoteException e) {
            // fail quietly
        }

    }

    private int findIndexOfEntry(String value, CharSequence[] entry) {
        if (value != null && entry != null) {
            for (int i = entry.length - 1; i >= 0; i--) {
                if (entry[i].equals(value)) {
                    return i;
                }
            }
        }
        return getResources().getInteger(R.integer.outputmode_default_values);  //set 720p as default
    }

    public void disableFreescale() {
        // turn off fb freescale
        Utils.writeSysfs(sw, FB0_FREESCALE_MODE, "0");
        Utils.writeSysfs(sw, FB1_FREESCALE_MODE, "0");
        Utils.writeSysfs(sw, FREESCALE_AXIS, "0 0 1279 719");

        Utils.writeSysfs(sw, PPMGR_PPSCALER, "0");
        Utils.writeSysfs(sw, DISABLE_VIDEO, "0");
        // now default video display to off
        Utils.writeSysfs(sw, DISABLE_VIDEO, "1");

        // revert display axis
        Utils.writeSysfs(sw, DISPLAY_AXIS, "0 0 1279 719");
    }

    public void setResolution(String mode) {
        Utils.writeSysfs(sw, DISPLAY_MODE, mode);
    }

    public void updateModeProps(String newMode) {
        sw.setProperty(COMMON_MODE_PROP, newMode);
        sw.setProperty(HDMI_MODE_PROP, newMode);
    }

     private void updateHdmiOutput(String newMode) {
         setResolution(newMode);
         disableFreescale();
         updateModeProps(newMode);
    }

    private void setDigitalAudioValue(String value) {
        sw.setProperty(DIGITAL_AUDIO_OUTPUT_PROP, value);
        Utils.writeSysfs(sw, AUDIODSP_DIGITAL_RAW, value);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();

        if (key.equals(KEY_SPDIF)) {
            String newValue = objValue.toString();
            setDigitalAudioValue(newValue);
            mSpdifPref.setSummary(mDigitalOutputEntries[Integer.valueOf(newValue)]);
        } else if (key.equals(KEY_OUTPUT_MODE)) {
            String newMode = objValue.toString();
            updateHdmiOutput(newMode);
        } else if (key.equals(KEY_DEFAULT_FREQUENCY)){
            try {
                int frequency_index = Integer.parseInt((String) objValue);
                mDefaultFrequency.setSummary(mDefaultFrequencyEntries[frequency_index]);
                SystemProperties.set(DEFAULT_FREQUENCY_PROP, mDefaultFrequencyEntries[frequency_index].toString());
            } catch(NumberFormatException e) {
                Log.e(TAG, "could not persist default TV frequency setting", e);
            }
        }

        return true;
    }


}
