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
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.IWindowManager;


public class HdmiSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "HdmiSettings";

    private static final String KEY_DUAL_DISP = "dual_disp";
    private static final String KEY_SPDIF = "spdif";
    private static final String KEY_AUTO_SWITCH = "auto_switch";
    private static final String KEY_OUTPUT_MODE ="output_mode";

    private static final int OUTPUT720_FULL_WIDTH = 1280;
    private static final int OUTPUT720_FULL_HEIGHT = 720;
    private static final int OUTPUT1080_FULL_WIDTH = 1920;
    private static final int OUTPUT1080_FULL_HEIGHT = 1080;

    private static final String HDMI_MODE_PROP = "ubootenv.var.hdmimode";
    private static final String COMMON_MODE_PROP = "ubootenv.var.outputmode";

    private CheckBoxPreference mDualDispPref;
    private ListPreference mSpdifPref;
    private CheckBoxPreference mAutoSwitchPref;
    private ListPreference mOutputModePref;

    private static SystemWriteManager sw;

    private Context mContext;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.hdmi_prefs);

        mDualDispPref = (CheckBoxPreference) findPreference(KEY_DUAL_DISP);
        mDualDispPref.setOnPreferenceChangeListener(this);
        if (!Utils.platformHasHdmiDualDisp())
            getPreferenceScreen().removePreference(findPreference(KEY_DUAL_DISP));

        mSpdifPref = (ListPreference) findPreference(KEY_SPDIF);
        mSpdifPref.setOnPreferenceChangeListener(this);

        mAutoSwitchPref = (CheckBoxPreference) findPreference(KEY_AUTO_SWITCH);
        mAutoSwitchPref.setOnPreferenceChangeListener(this);
        if (!Utils.platformHasHdmiAutoSwitch())
            getPreferenceScreen().removePreference(findPreference(KEY_AUTO_SWITCH));

        mOutputModePref = (ListPreference) findPreference(KEY_OUTPUT_MODE);
        mOutputModePref.setOnPreferenceChangeListener(this);
        // until bugs are worked out disable OutputModePref
        getPreferenceScreen().removePreference(findPreference(KEY_OUTPUT_MODE));


        sw = (SystemWriteManager) getSystemService("system_write");

        updateUi();
    }

    private void updateUi() {
        if (mDualDispPref != null) {
            mDualDispPref.setChecked(Settings.System.getInt(getContentResolver(),
                    Settings.System.HDMI_DUAL_DISP, 1) != 0);
        }

        if (mSpdifPref != null) {
            mSpdifPref.setValue(String.valueOf(Settings.System.getInt(getContentResolver(),
                    Settings.System.HDMI_SPDIF, 0)));
            mSpdifPref.setSummary(mSpdifPref.getEntry());
        }

        if (mAutoSwitchPref != null) {
            mAutoSwitchPref.setChecked(Settings.System.getInt(getContentResolver(),
                    Settings.System.HDMI_AUTO_SWITCH, 1) != 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
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

    public void disableFreescale() {
        // turn off fb freescale
        Utils.writeSysfs(sw, "/sys/class/graphics/fb0/freescale_mode", "0");
        Utils.writeSysfs(sw, "/sys/class/graphics/fb1/freescale_mode", "0");
        Utils.writeSysfs(sw, "/sys/class/graphics/fb0/free_scale_axis", "0 0 1279 719");

        Utils.writeSysfs(sw, "/sys/class/ppmgr/ppscaler", "0");
        Utils.writeSysfs(sw, "/sys/class/video/disable_video", "0");
        // now default video display to off
        Utils.writeSysfs(sw, "/sys/class/video/disable_video", "1");

        // revert display axis
        Utils.writeSysfs(sw, "/sys/class/display/axis", "0 0 1279 719");
    }

    public void setResolution(String mode) {
        Utils.writeSysfs(sw, "/sys/class/display/mode", mode);
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

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();

        if (key.equals(KEY_SPDIF)) {
            Settings.System.putInt(getContentResolver(), Settings.System.HDMI_SPDIF,
                    Integer.parseInt((String) objValue));
        } else if (key.equals(KEY_OUTPUT_MODE)) {
            String newMode = objValue.toString();
            updateHdmiOutput(newMode);
        }

        updateUi();
        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mDualDispPref) {
            Settings.System.putInt(getContentResolver(), Settings.System.HDMI_DUAL_DISP,
                    mDualDispPref.isChecked() ? 1 : 0);
        } else if (preference == mAutoSwitchPref) {
            Settings.System.putInt(getContentResolver(), Settings.System.HDMI_AUTO_SWITCH,
                    mAutoSwitchPref.isChecked() ? 1 : 0);
        }

        updateUi();
        return true;
    }


}
