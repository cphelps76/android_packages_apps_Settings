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

    private static final String[] COMMON_MODE_VALUE_LIST =  {"720","1080"};
    private final static String sel_720poutput_x = "ubootenv.var.720poutputx";
    private final static String sel_720poutput_y = "ubootenv.var.720poutputy";
    private final static String sel_720poutput_width = "ubootenv.var.720poutputwidth";
    private final static String sel_720poutput_height = "ubootenv.var.720poutputheight";
    private final static String sel_1080ioutput_x = "ubootenv.var.1080ioutputx";
    private final static String sel_1080ioutput_y = "ubootenv.var.1080ioutputy";
    private final static String sel_1080ioutput_width = "ubootenv.var.1080ioutputwidth";
    private final static String sel_1080ioutput_height = "ubootenv.var.1080ioutputheight";
    private final static String sel_1080poutput_x = "ubootenv.var.1080poutputx";
    private final static String sel_1080poutput_y = "ubootenv.var.1080poutputy";
    private final static String sel_1080poutput_width = "ubootenv.var.1080poutputwidth";
    private final static String sel_1080poutput_height = "ubootenv.var.1080poutputheight";
    private static final String ppscalerRectFile = "/sys/class/ppmgr/ppscaler_rect";
    private static final String updateFreescaleFb0File = "/sys/class/graphics/fb0/update_freescale";
    private static final String freescaleFb0File = "/sys/class/graphics/fb0/free_scale";
    private static final String freescaleFb1File = "/sys/class/graphics/fb1/free_scale";
    private static final String mHdmiPluggedVdac = "/sys/class/aml_mod/mod_off";
    private static final String mHdmiUnpluggedVdac = "/sys/class/aml_mod/mod_on";
    private static final String HDMI_SUPPORT_LIST_SYSFS = "/sys/class/amhdmitx/amhdmitx0/disp_cap";
    private static final String ppscalerFile = "/sys/class/ppmgr/ppscaler";
    private static final String videoAxisFile = "/sys/class/video/axis";
    private static final String request2XScaleFile = "/sys/class/graphics/fb0/request2XScale";
    private static final String scaleAxisOsd0File = "/sys/class/graphics/fb0/scale_axis";
    private static final String scaleAxisOsd1File = "/sys/class/graphics/fb1/scale_axis";
    private static final String scaleOsd1File = "/sys/class/graphics/fb1/scale";
    private static final String outputModeFile = "/sys/class/display/mode";
    private static final String outputAxisFile= "/sys/class/display/axis";
    private static final String windowAxisFile = "/sys/class/graphics/fb0/window_axis";
    private final static String DISPLAY_MODE_SYSFS = "/sys/class/display/mode";

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
        if (!Utils.platformHasHdmiSpdif())
            getPreferenceScreen().removePreference(findPreference(KEY_SPDIF));

        mAutoSwitchPref = (CheckBoxPreference) findPreference(KEY_AUTO_SWITCH);
        mAutoSwitchPref.setOnPreferenceChangeListener(this);
        if (!Utils.platformHasHdmiAutoSwitch())
            getPreferenceScreen().removePreference(findPreference(KEY_AUTO_SWITCH));

        mOutputModePref = (ListPreference) findPreference(KEY_OUTPUT_MODE);
        mOutputModePref.setOnPreferenceChangeListener(this);

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

    public static int[] getPosition(SystemWriteManager sw, String mode) {
        int[] curPosition = { 0, 0, OUTPUT720_FULL_WIDTH, OUTPUT720_FULL_HEIGHT };
        int index = 0; // 720p
        for (int i = 0; i < COMMON_MODE_VALUE_LIST.length; i++) {
            if (mode.equalsIgnoreCase(COMMON_MODE_VALUE_LIST[i]))
                index = i;
        }
        switch (index) {
            case 0:
                curPosition[0] = sw.getPropertyInt(sel_720poutput_x, 0);
                curPosition[1] = sw.getPropertyInt(sel_720poutput_y, 0);
                curPosition[2] = sw.getPropertyInt(sel_720poutput_width, OUTPUT720_FULL_WIDTH);
                curPosition[3] = sw.getPropertyInt(sel_720poutput_height, OUTPUT720_FULL_HEIGHT);
                break;
            case 1:
                curPosition[0] = sw.getPropertyInt(sel_1080poutput_x, 0);
                curPosition[1] = sw.getPropertyInt(sel_1080poutput_y, 0);
                curPosition[2] = sw.getPropertyInt(sel_1080poutput_width, OUTPUT1080_FULL_WIDTH);
                curPosition[3] = sw.getPropertyInt(sel_1080poutput_height, OUTPUT1080_FULL_HEIGHT);
                break;
            default:
                curPosition[0] = sw.getPropertyInt(sel_720poutput_x, 0);
                curPosition[1] = sw.getPropertyInt(sel_720poutput_y, 0);
                curPosition[2] = sw.getPropertyInt(sel_720poutput_width, OUTPUT720_FULL_WIDTH);
                curPosition[3] = sw.getPropertyInt(sel_720poutput_height, OUTPUT720_FULL_HEIGHT);
                break;
        }
        return curPosition;

    }

    public void updateSysfs(int mode) {
        int[] curPosition = getPosition(sw, String.valueOf(mode));
        String mWinAxis = curPosition[0]+ " " + curPosition[1]
                + " " + (curPosition[0]+curPosition[2]-1)+ " " + (curPosition[1]+curPosition[3]-1);
        String mVideoAxis = curPosition[0] + " " + curPosition[1]+ " " + (curPosition[2] + curPosition[0]-1)
                + " " + (curPosition[3] + curPosition[1]-1);
        String mDisplayAxis = curPosition[0] + " " + curPosition[1]
                + Utils.getDisplayAxisByMode(mode) + curPosition[0] + " " + curPosition[1] + " " + 18 + " " + 18;


        if (mode == OUTPUT1080_FULL_HEIGHT) {
            Utils.writeSysfs(sw, "/sys/class/graphics/fb0/freescale_mode", "1");
            Utils.writeSysfs(sw, "/sys/class/graphics/fb0/free_scale_axis", "0 0 1919 1079");
            Utils.writeSysfs(sw, "/sys/class/graphics/fb0/window_axis", mWinAxis);
            if (curPosition[0] == 0 && curPosition[1] == 0) {
                Utils.writeSysfs(sw, "/sys/class/graphics/fb0/free_scale","0");
            } else {
                Utils.writeSysfs(sw, "/sys/class/graphics/fb0/free_scale","0x10001");
            }
        } else if (mode == OUTPUT720_FULL_HEIGHT) {
            Utils.writeSysfs(sw, "/sys/class/graphics/fb0/freescale_mode","1");
            Utils.writeSysfs(sw, "/sys/class/graphics/fb0/free_scale_axis", "0 0 1279 719");
            Utils.writeSysfs(sw, "/sys/class/graphics/fb0/window_axis", mWinAxis);
            if (curPosition[0] == 0 && curPosition[1] == 0) {
                Utils.writeSysfs(sw, "/sys/class/graphics/fb0/free_scale", "0");
            } else {
                Utils.writeSysfs(sw, "/sys/class/graphics/fb0/free_scale", "0x10001");
            }
        }
        Utils.writeSysfs(sw,videoAxisFile, mVideoAxis);
        Utils.writeSysfs(sw, outputAxisFile, mDisplayAxis);
    }
     private void updateHdmiOutput(String newMode) {
         int res = OUTPUT720_FULL_HEIGHT;
         if (newMode.contains(String.valueOf(OUTPUT1080_FULL_HEIGHT))) {
             res = OUTPUT1080_FULL_HEIGHT;
             // Turn auto key off to allow 1080p smooth change
             Settings.System.putInt(getContentResolver(), Settings.System.HDMI_AUTO_SWITCH, 0);
         }
         getPosition(sw, newMode);
         setDensity(newMode);
         setDisplaySize(newMode);

         updateSysfs(res);
         sw.setProperty(COMMON_MODE_PROP, newMode);
         sw.setProperty(HDMI_MODE_PROP, newMode);
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
