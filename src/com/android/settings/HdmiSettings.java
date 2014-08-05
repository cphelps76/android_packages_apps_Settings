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

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.SystemWriteManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.display.HdmiManager;
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
import android.view.*;
import android.view.Display;
import android.view.IWindowManager;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.TextView;


public class HdmiSettings extends SettingsPreferenceFragment implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "HdmiSettings";

    // Preferences
    private static final String KEY_SPDIF = "spdif";
    private static final String KEY_OUTPUT_MODE ="output_mode";
    private static final String KEY_DEFAULT_FREQUENCY = "default_frequency";
    private static final String KEY_OVERSCAN = "overscan";

    // u-boot props
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
    private static final String HDMI_MODE_PROP = "ubootenv.var.hdmimode";

    private ListPreference mSpdifPref;
    private ListPreference mOutputModePref;
    private Preference mOverscanPref;

    private ListPreference  mDefaultFrequency;
    private CharSequence[] mDefaultFrequencyEntries;
    private CharSequence[] mDigitalOutputEntries;

    private static SystemWriteManager sw;

    private static HdmiManager mHdmiManager;

    private static final float zoomStep = 4.0f;
    private static final float zoomStepWidth = 1.78f;

    private static final int MAX_HEIGHT = 100;
    private static final int MAX_WIDTH = 100;

    private int mLeft, mTop, mWidth, mHeight, mRight, mBottom;
    private int mNewLeft, mNewTop, mNewRight, mNewBottom;

    private static final int MENU_ID_HDMI_RESET = Menu.FIRST;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.hdmi_prefs);


        mHdmiManager = (HdmiManager) getSystemService(Context.HDMI_SERVICE);

        mSpdifPref = (ListPreference) findPreference(KEY_SPDIF);
        mSpdifPref.setOnPreferenceChangeListener(this);

        mOutputModePref = (ListPreference) findPreference(KEY_OUTPUT_MODE);
        mOutputModePref.setOnPreferenceChangeListener(this);

        mDefaultFrequency = (ListPreference) findPreference(KEY_DEFAULT_FREQUENCY);
        mDefaultFrequency.setOnPreferenceChangeListener(this);

        mDefaultFrequencyEntries = getResources().getStringArray(R.array.default_frequency_entries);
        mDigitalOutputEntries = getResources().getStringArray(R.array.hdmi_audio_output_entries);

        mOverscanPref = (Preference) findPreference(KEY_OVERSCAN);

        sw = (SystemWriteManager) getSystemService("system_write");

        updateSummaries();
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(Menu.NONE, MENU_ID_HDMI_RESET, 0, R.string.hdmi_menu_reset)
                .setEnabled(true)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ID_HDMI_RESET:
                int[] position = getPosition(mHdmiManager.getBestResolution());

                restorePosition(position[0], position[1], position[2], position[3]);
                return true;
        }
        return super.onOptionsItemSelected(item);
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

    private int[] getPosition(String mode) {
        return mHdmiManager.getPosition(mHdmiManager.getBestResolution());
    }

    private void initPosition() {
        final int[] position = getPosition(mHdmiManager.getBestResolution());
        mLeft = position[0];
        mTop = position[1];
        mWidth = position[2];
        mHeight = position[3];
        mRight = mWidth;// + mLeft;
        mBottom = mHeight;// + mTop;
        Log.d(TAG, "left=" + mLeft + " top=" + mTop + " width=" + mWidth + " height=" + mHeight + " right=" + mRight + " bottom=" + mBottom);
        mNewLeft = mLeft;
        mNewTop = mTop;
        mNewRight = mRight;
        mNewBottom = mBottom;
        Utils.writeSysfs(sw, mHdmiManager.FREESCALE_FB0, "1");
        Utils.writeSysfs(sw, mHdmiManager.FREESCALE_FB1, "1");
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


    private void setDigitalAudioValue(String value) {
        sw.setProperty(DIGITAL_AUDIO_OUTPUT_PROP, value);
        Utils.writeSysfs(sw, AUDIODSP_DIGITAL_RAW, value);
    }

    private int getCurrentWidthRate() {
        Log.d(TAG, "mLeft is " + mLeft);
        float offset = mLeft / (zoomStep*zoomStepWidth);
        float curVal = MAX_WIDTH - offset;
        Log.d(TAG, "currentWidthRate=" + (int)curVal);
        return ((int) curVal);
    }

    private int getCurrentHeightRate() {
        float offset = mTop / zoomStep;
        float curVal = MAX_HEIGHT - offset;
        Log.d(TAG, "currentHeightRate=" + (int)curVal);
        return ((int) curVal);
    }

    private void showOverscanDialog(Context context) {
        initPosition();
        // sysfs are written as progress is changed for real-time effect
        // cancel obviously reverts back to previous values
        final int[] width_rate = {getCurrentWidthRate()};
        final int[] height_rate = {getCurrentHeightRate()};
        //final int[] newWidth = new int[1], newHeight = new int[1];
        LayoutInflater inflater = this.getActivity().getLayoutInflater();
        View dialog = inflater.inflate(R.layout.overscan_dialog, null);
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(dialog);
        builder.setNegativeButton(R.string.dlg_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                restorePosition(mLeft, mTop, mRight, mBottom);
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                restorePosition(mLeft, mTop, mRight, mBottom);
            }
        });
        builder.setPositiveButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                savePosition(mNewLeft, mNewTop, mNewRight, mNewBottom);
            }
        });
        builder.setTitle(R.string.hdmi_overscan_title);
        builder.setMessage(R.string.hdmi_overscan_help);
        AlertDialog alert = builder.show();

        TextView mMessage = (TextView) alert.findViewById(android.R.id.message);
        mMessage.setGravity(Gravity.CENTER_HORIZONTAL);
        NumberPicker mWidthPicker = (NumberPicker) dialog.findViewById(R.id.width_picker);
        mWidthPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        mWidthPicker.setMinValue(0);
        mWidthPicker.setMaxValue(100);
        mWidthPicker.setValue(width_rate[0]);
        mWidthPicker.setWrapSelectorWheel(false);
        mWidthPicker.requestFocus();
        mWidthPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                if (oldVal > newVal) {
                    //zoom out
                    zoomOutWidth();
                } else {
                    // zoom in
                    zoomInWidth();
                }
                width_rate[0] = newVal;

            }
        });
        NumberPicker mHeightPicker = (NumberPicker) dialog.findViewById(R.id.height_picker);
        mHeightPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        mHeightPicker.setMinValue(0);
        mHeightPicker.setMaxValue(100);
        mHeightPicker.setValue(height_rate[0]);
        mHeightPicker.setWrapSelectorWheel(false);
        mHeightPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                if (oldVal > newVal) {
                    //zoom out
                    zoomOutHeight();
                } else {
                    // zoom in
                    zoomInHeight();
                }
                height_rate[0] = newVal;
            }
        });
    }

    private void zoomOutWidth() {
        mNewLeft += (int)(zoomStep * zoomStepWidth);
        mNewRight -= (int)(zoomStep * zoomStepWidth);
        Log.d(TAG, "left=" + mNewLeft + " top=" + mNewTop + " right=" + mNewRight + " bottom=" + mNewBottom);
        setPosition(mNewLeft, mNewTop, mNewRight, mNewBottom);
    }

    private void zoomOutHeight() {
        mNewTop += zoomStep;
        mNewBottom -= zoomStep;
        Log.d(TAG, "left=" + mNewLeft + " top=" + mNewTop + " right=" + mNewRight + " bottom=" + mNewBottom);
        setPosition(mNewLeft, mNewTop, mNewRight, mNewBottom);
    }

    private void zoomInWidth() {
        mNewLeft -= (int)(zoomStep * zoomStepWidth);
        mNewRight += (int)(zoomStep * zoomStepWidth);
        Log.d(TAG, "left=" + mNewLeft + " top=" + mNewTop + " right=" + mNewRight + " bottom=" + mNewBottom);
        setPosition(mNewLeft, mNewTop, mNewRight, mNewBottom);
    }

    private void zoomInHeight() {
        mNewTop -= zoomStep;
        mNewBottom += zoomStep;
        Log.d(TAG, "left=" + mNewLeft + " top=" + mNewTop + " right=" + mNewRight + " bottom=" + mNewBottom);
        setPosition(mNewLeft, mNewTop, mNewRight, mNewBottom);
    }

    private void setPosition(int left, int top, int right, int bottom) {
        String string = String.valueOf(left) +
                " " + String.valueOf(top) + " " + String.valueOf(right) + " " + String.valueOf(bottom) + " 0";
        Utils.writeSysfs(sw, mHdmiManager.PPSCALER_RECT, string);

    }

    private void savePosition(int left, int top, int right, int bottom) {
        int[] position = getPosition(mHdmiManager.getBestResolution());
        switch (position[3]) {
            case 480:
                sw.setProperty(mHdmiManager.UBOOT_480P_OUTPUT_X, String.valueOf(left));
                sw.setProperty(mHdmiManager.UBOOT_480P_OUTPUT_Y, String.valueOf(top));
                sw.setProperty(mHdmiManager.UBOOT_480P_OUTPUT_WIDTH, String.valueOf(right));
                sw.setProperty(mHdmiManager.UBOOT_480P_OUTPUT_HEIGHT, String.valueOf(bottom));
                break;
            case 576:
                sw.setProperty(mHdmiManager.UBOOT_576P_OUTPUT_X, String.valueOf(left));
                sw.setProperty(mHdmiManager.UBOOT_576P_OUTPUT_Y, String.valueOf(top));
                sw.setProperty(mHdmiManager.UBOOT_576P_OUTPUT_WIDTH, String.valueOf(right));
                sw.setProperty(mHdmiManager.UBOOT_576P_OUTPUT_HEIGHT, String.valueOf(bottom));
                break;
            case 720:
                sw.setProperty(mHdmiManager.UBOOT_720P_OUTPUT_X, String.valueOf(left));
                sw.setProperty(mHdmiManager.UBOOT_720P_OUTPUT_Y, String.valueOf(top));
                sw.setProperty(mHdmiManager.UBOOT_720P_OUTPUT_WIDTH, String.valueOf(right));
                sw.setProperty(mHdmiManager.UBOOT_720P_OUTPUT_HEIGHT, String.valueOf(bottom));
                break;
            case 1080:
                sw.setProperty(mHdmiManager.UBOOT_1080P_OUTPUT_X, String.valueOf(left));
                sw.setProperty(mHdmiManager.UBOOT_1080P_OUTPUT_Y, String.valueOf(top));
                sw.setProperty(mHdmiManager.UBOOT_1080P_OUTPUT_WIDTH, String.valueOf(right));
                sw.setProperty(mHdmiManager.UBOOT_1080P_OUTPUT_HEIGHT, String.valueOf(bottom));
                break;
            default:
                break;

        }

        mLeft = left;
        mTop = top;
        mRight = right;
        mBottom = bottom;
    }

    private void restorePosition(int left, int top, int right, int bottom) {
        setPosition(left, top, right, bottom);
        savePosition(left, top, right, bottom);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();

        if (key.equals(KEY_SPDIF)) {
            String newValue = objValue.toString();
            setDigitalAudioValue(newValue);
            mSpdifPref.setSummary(mDigitalOutputEntries[Integer.valueOf(newValue)]);
            return true;
        } else if (key.equals(KEY_OUTPUT_MODE)) {
            String newMode = objValue.toString();
            if (sw.getPropertyBoolean(mHdmiManager.HDMIONLY_PROP, true)) {
                sw.writeSysfs(mHdmiManager.HDMI_PLUGGED, "vdac");
                if (mHdmiManager.isFreescaleClosed()) {
                    mHdmiManager.setOutputWithoutFreescale(newMode);
                } else {
                    mHdmiManager.setOutputMode(newMode);
                }
                sw.writeSysfs(mHdmiManager.BLANK_DISPLAY, "0");
            }
            return true;
        } else if (key.equals(KEY_DEFAULT_FREQUENCY)){
            try {
                int frequency_index = Integer.parseInt((String) objValue);
                mDefaultFrequency.setSummary(mDefaultFrequencyEntries[frequency_index]);
                SystemProperties.set(DEFAULT_FREQUENCY_PROP, mDefaultFrequencyEntries[frequency_index].toString());
            } catch(NumberFormatException e) {
                Log.e(TAG, "could not persist default TV frequency setting", e);
            }
            return true;
        }
        return false;
    }


    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mOverscanPref) {
            showOverscanDialog(this.getActivity());
            return true;
        }
        return false;
    }
}
