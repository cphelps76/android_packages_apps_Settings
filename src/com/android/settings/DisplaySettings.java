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

package com.android.settings;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.HdmiManager;
import android.hardware.display.WifiDisplay;
import android.hardware.display.WifiDisplayStatus;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.app.SystemWriteManager;

import android.view.*;
import android.widget.NumberPicker;
import android.widget.TextView;
import com.android.internal.view.RotationPolicy;

import java.util.ArrayList;

public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, OnPreferenceClickListener {
    private static final String TAG = "DisplaySettings";
    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_Brightness = "brightness";
    private static final String KEY_SCREEN_SAVER = "screensaver";
    private static final String KEY_WIFI_DISPLAY = "wifi_display";
    private static final String KEY_WALLPAPER = "wallpaper";
    private static final String KEY_OUTPUT_MODE = "output_mode";
    private static final String KEY_AUTO_ADJUST = "auto_adjust";
    private static final String KEY_POSITION = "position";

    private static final int DLG_GLOBAL_CHANGE_WARNING = 1;


    public static SystemWriteManager sw;
    private static HdmiManager mHdmiManager;

    private DisplayManager mDisplayManager;

    private CheckBoxPreference mAccelerometer;
    private WarnedListPreference mFontSizePref;
    private CheckBoxPreference mNotificationPulse;

    private final Configuration mCurConfig = new Configuration();

    private ListPreference mScreenTimeoutPreference;
    private Preference mScreenSaverPreference;
    private ListPreference mOutputModePref;
    private CheckBoxPreference mAutoAdjustPref;
    private Preference mPositionPref;

    private static float zoomStep = 3.0f; // defaulted to 720p
    private static float zoomStepWidth = 1.78f; //defaulted to 720p

    private static final int MAX_HEIGHT = 100;
    private static final int MAX_WIDTH = 100;

    private int mLeft, mTop, mWidth, mHeight, mRight, mBottom;
    private int mNewLeft, mNewTop, mNewRight, mNewBottom;

    private static final int MENU_ID_HDMI_RESET = Menu.FIRST;

    private WifiDisplayStatus mWifiDisplayStatus;
    private Preference mWifiDisplayPreference;

    private final RotationPolicy.RotationPolicyListener mRotationPolicyListener =
            new RotationPolicy.RotationPolicyListener() {
        @Override
        public void onChange() {
            updateAccelerometerRotationCheckbox();
        }
    };

    public void onDestroy(){
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ContentResolver resolver = getActivity().getContentResolver();

        addPreferencesFromResource(R.xml.display_settings);
        sw = (SystemWriteManager)getSystemService("system_write");
        mAccelerometer = (CheckBoxPreference) findPreference(KEY_ACCELEROMETER);
        mAccelerometer.setPersistent(false);
        if (RotationPolicy.isRotationLockToggleSupported(getActivity())) {
            // If rotation lock is supported, then we do not provide this option in
            // Display settings.  However, is still available in Accessibility settings.
            getPreferenceScreen().removePreference(mAccelerometer);
        }

        mScreenSaverPreference = findPreference(KEY_SCREEN_SAVER);
        if ((mScreenSaverPreference != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_dreamsSupported) == false)
			|| (Utils.platformHasMbxUiMode())) {
            getPreferenceScreen().removePreference(mScreenSaverPreference);
        }

        mScreenTimeoutPreference = (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        final long currentTimeout = Settings.System.getLong(resolver, SCREEN_OFF_TIMEOUT,
                FALLBACK_SCREEN_TIMEOUT_VALUE);
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        mScreenTimeoutPreference.setOnPreferenceChangeListener(this);
        disableUnusableTimeouts(mScreenTimeoutPreference);
        updateTimeoutPreferenceDescription(currentTimeout);

        mFontSizePref = (WarnedListPreference) findPreference(KEY_FONT_SIZE);
        mFontSizePref.setOnPreferenceChangeListener(this);
        mFontSizePref.setOnPreferenceClickListener(this);
        mNotificationPulse = (CheckBoxPreference) findPreference(KEY_NOTIFICATION_PULSE);
        if (mNotificationPulse != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_intrusiveNotificationLed) == false) {
            getPreferenceScreen().removePreference(mNotificationPulse);
        } else {
            try {
                mNotificationPulse.setChecked(Settings.System.getInt(resolver,
                        Settings.System.NOTIFICATION_LIGHT_PULSE) == 1);
                mNotificationPulse.setOnPreferenceChangeListener(this);
            } catch (SettingNotFoundException snfe) {
                Log.e(TAG, Settings.System.NOTIFICATION_LIGHT_PULSE + " not found");
            }
        }

        if(Utils.platformHasMbxUiMode()){
        	getPreferenceScreen().removePreference(findPreference(KEY_WALLPAPER));
        }

        if(!Utils.platformHasScreenBrightness()){
        	getPreferenceScreen().removePreference(findPreference(KEY_Brightness));
        }

        if(!Utils.platformHasScreenTimeout()){
        	getPreferenceScreen().removePreference(mScreenTimeoutPreference);
        }

        if(!Utils.platformHasScreenFontSize()){
        	getPreferenceScreen().removePreference(mFontSizePref);
        }

        mDisplayManager = (DisplayManager)getActivity().getSystemService(
                Context.DISPLAY_SERVICE);
        mWifiDisplayStatus = mDisplayManager.getWifiDisplayStatus();
        mWifiDisplayPreference = (Preference)findPreference(KEY_WIFI_DISPLAY);
        if (mWifiDisplayStatus.getFeatureState()
                == WifiDisplayStatus.FEATURE_STATE_UNAVAILABLE) {
            getPreferenceScreen().removePreference(mWifiDisplayPreference);
            mWifiDisplayPreference = null;
        }
        mHdmiManager = (HdmiManager) getSystemService(Context.HDMI_SERVICE);

        mOutputModePref = (ListPreference) findPreference(KEY_OUTPUT_MODE);
        mOutputModePref.setOnPreferenceChangeListener(this);
        mOutputModePref.setValue(mHdmiManager.getResolution());
        mOutputModePref.setEntries(mHdmiManager.getAvailableResolutions());
        mOutputModePref.setEntryValues(mHdmiManager.getAvailableResolutions());
        mOutputModePref.setSummary(mHdmiManager.getResolution());

        mPositionPref = (Preference) findPreference(KEY_POSITION);

        mAutoAdjustPref = (CheckBoxPreference) findPreference(KEY_AUTO_ADJUST);
        mAutoAdjustPref.setChecked(Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.HDMI_AUTO_ADJUST, 0) != 0);

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
                reset();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reset() {
        mHdmiManager.resetPosition();
        mLeft = 0;
        mRight = 0;
        mWidth = mHdmiManager.getFullWidthPosition();
        mHeight = mHdmiManager.getFullHeightPosition();
    }

    private void initPosition() {
        final int[] position = mHdmiManager.getPosition(mHdmiManager.getResolution());
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

    private void initSteps() {
        String resolution = mHdmiManager.getResolution();
        if (resolution.contains("480")) {
            zoomStep = 2.0f;
            zoomStepWidth = 1.50f;
        } else if (resolution.contains("576")) {
            zoomStep = 2.0f;
            zoomStepWidth = 1.25f;
        } else if (resolution.contains("720")) {
            zoomStep = 3.0f;
            zoomStepWidth = 1.78f;
        } else {
            zoomStep = 4.0f;
            zoomStepWidth = 1.78f;
        }
    }

    private int getCurrentWidthRate() {
        Log.d(TAG, "mLeft is " + mLeft);
        int savedValue = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.HDMI_OVERSCAN_WIDTH, 100);
        if (savedValue == 100) {
            float offset = mLeft / (zoomStep * zoomStepWidth);
            float curVal = MAX_WIDTH - offset;
            Log.d(TAG, "currentWidthRate=" + (int) curVal);
            return ((int) curVal);
        }
        return savedValue;
    }

    private int getCurrentHeightRate() {
        Log.d(TAG, "mTop is " + mTop);
        int savedValue = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.HDMI_OVERSCAN_WIDTH, 100);
        if (savedValue == 100) {
            float offset = mTop / zoomStep;
            float curVal = MAX_HEIGHT - offset;
            Log.d(TAG, "currentHeightRate=" + (int) curVal);
            return ((int) curVal);
        }
        return savedValue;
    }

    private void updateTimeoutPreferenceDescription(long currentTimeout) {
        ListPreference preference = mScreenTimeoutPreference;
        String summary;
        if (currentTimeout < 0) {
            // Unsupported value
            summary = "";
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            if (entries == null || entries.length == 0) {
                summary = "";
            } else {
                int best = 0;
                for (int i = 0; i < values.length; i++) {
                    long timeout = Long.parseLong(values[i].toString());
                    if (currentTimeout >= timeout) {
                        best = i;
                    }
                }
                if(currentTimeout >= (Integer.MAX_VALUE-1))
                {
                    summary = entries[best].toString();
                }else{
                    summary = preference.getContext().getString(R.string.screen_timeout_summary,entries[best]);
                }

            }
        }
        preference.setSummary(summary);
    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        if (maxTimeout == 0) {
            return; // policy not enforced
        }
        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.parseLong(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            final int userPreference = Integer.parseInt(screenTimeoutPreference.getValue());
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    int floatToIndex(float val) {
        String[] indices = getResources().getStringArray(R.array.entryvalues_font_size);
        float lastVal = Float.parseFloat(indices[0]);
        for (int i=1; i<indices.length; i++) {
            float thisVal = Float.parseFloat(indices[i]);
            if (val < (lastVal + (thisVal-lastVal)*.5f)) {
                return i-1;
            }
            lastVal = thisVal;
        }
        return indices.length-1;
    }

    public void readFontSizePreference(ListPreference pref) {
        try {
            mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to retrieve font size");
        }

        // mark the appropriate item in the preferences list
        int index = floatToIndex(mCurConfig.fontScale);
        pref.setValueIndex(index);

        // report the current size in the summary text
        final Resources res = getResources();
        String[] fontSizeNames = res.getStringArray(R.array.entries_font_size);
        pref.setSummary(String.format(res.getString(R.string.summary_font_size),
                fontSizeNames[index]));
    }

    @Override
    public void onResume() {
        super.onResume();
        updateState();

        RotationPolicy.registerRotationPolicyListener(getActivity(),
                mRotationPolicyListener);

        if (mWifiDisplayPreference != null) {
            getActivity().registerReceiver(mReceiver, new IntentFilter(
                    DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED));
            mWifiDisplayStatus = mDisplayManager.getWifiDisplayStatus();
        }

        updateState();
    }

    @Override
    public void onPause() {
        super.onPause();

        RotationPolicy.unregisterRotationPolicyListener(getActivity(),
                mRotationPolicyListener);

        if (mWifiDisplayPreference != null) {
            getActivity().unregisterReceiver(mReceiver);
        }
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        if (dialogId == DLG_GLOBAL_CHANGE_WARNING) {
            return Utils.buildGlobalChangeWarningDialog(getActivity(),
                    R.string.global_font_change_title,
                    new Runnable() {
                        public void run() {
                            mFontSizePref.click();
                        }
                    });
        }
        return null;
    }

    private void updateState() {
        updateAccelerometerRotationCheckbox();
        readFontSizePreference(mFontSizePref);
        updateScreenSaverSummary();
        updateRequestRotationCheckbox();
        updateWifiDisplaySummary();
    }

    private void updateScreenSaverSummary() {
        if (mScreenSaverPreference != null) {
            mScreenSaverPreference.setSummary(
                    DreamSettings.getSummaryTextWithDreamName(getActivity()));
        }
    }

    private void updateWifiDisplaySummary() {
        if (mWifiDisplayPreference != null) {
            switch (mWifiDisplayStatus.getFeatureState()) {
                case WifiDisplayStatus.FEATURE_STATE_OFF:
                    mWifiDisplayPreference.setSummary(R.string.wifi_display_summary_off);
                    break;
                case WifiDisplayStatus.FEATURE_STATE_ON:
                    mWifiDisplayPreference.setSummary(R.string.wifi_display_summary_on);
                    break;
                case WifiDisplayStatus.FEATURE_STATE_DISABLED:
                default:
                    mWifiDisplayPreference.setSummary(R.string.wifi_display_summary_disabled);
                    break;
            }
        }
    }

    private void updateAccelerometerRotationCheckbox() {
        if (getActivity() == null) return;

        mAccelerometer.setChecked(!RotationPolicy.isRotationLocked(getActivity()));
    }

	private void updateRequestRotationCheckbox() {
        if (getActivity() == null) return;
    }

    public void writeFontSizePreference(Object objValue) {
        try {
            mCurConfig.fontScale = Float.parseFloat(objValue.toString());
            ActivityManagerNative.getDefault().updatePersistentConfiguration(mCurConfig);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to save font size");
        }
    }

    private void showPositionDialog(Context context) {
        initPosition();
        initSteps();
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
                mHdmiManager.setPosition(mLeft, mTop, mRight, mBottom);
                mHdmiManager.savePosition(mLeft, mTop, mRight, mBottom);
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                mHdmiManager.setPosition(mLeft, mTop, mRight, mBottom);
                mHdmiManager.savePosition(mLeft, mTop, mRight, mBottom);
            }
        });
        builder.setPositiveButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mHdmiManager.savePosition(mNewLeft, mNewTop, mNewRight, mNewBottom);
                mLeft = mNewLeft;
                mTop = mNewTop;
                mRight = mNewRight;
                mBottom = mNewBottom;
                Settings.Secure.putInt(getActivity().getContentResolver(),
                        Settings.Secure.HDMI_OVERSCAN_WIDTH, width_rate[0]);
                Settings.Secure.putInt(getActivity().getContentResolver(),
                        Settings.Secure.HDMI_OVERSCAN_HEIGHT, height_rate[0]);
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
        mHdmiManager.setPosition(mNewLeft, mNewTop, mNewRight, mNewBottom);
    }

    private void zoomOutHeight() {
        mNewTop += zoomStep;
        mNewBottom -= zoomStep;
        Log.d(TAG, "left=" + mNewLeft + " top=" + mNewTop + " right=" + mNewRight + " bottom=" + mNewBottom);
        mHdmiManager.setPosition(mNewLeft, mNewTop, mNewRight, mNewBottom);
    }

    private void zoomInWidth() {
        mNewLeft -= (int)(zoomStep * zoomStepWidth);
        mNewRight += (int)(zoomStep * zoomStepWidth);
        Log.d(TAG, "left=" + mNewLeft + " top=" + mNewTop + " right=" + mNewRight + " bottom=" + mNewBottom);
        mHdmiManager.setPosition(mNewLeft, mNewTop, mNewRight, mNewBottom);
    }

    private void zoomInHeight() {
        mNewTop -= zoomStep;
        mNewBottom += zoomStep;
        Log.d(TAG, "left=" + mNewLeft + " top=" + mNewTop + " right=" + mNewRight + " bottom=" + mNewBottom);
        mHdmiManager.setPosition(mNewLeft, mNewTop, mNewRight, mNewBottom);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mAccelerometer) {
            RotationPolicy.setRotationLockForAccessibility(
                    getActivity(), !mAccelerometer.isChecked());
        } else if (preference == mNotificationPulse) {
            boolean value = mNotificationPulse.isChecked();
            Settings.System.putInt(getContentResolver(), Settings.System.NOTIFICATION_LIGHT_PULSE,
                    value ? 1 : 0);
            return true;
        } else if (preference == mPositionPref) {
            showPositionDialog(this.getActivity());
            return true;
        } else if (preference == mAutoAdjustPref) {
            Log.d(TAG, "auto adjust is " + mAutoAdjustPref.isChecked());
            int enabled = mAutoAdjustPref.isChecked() ? 1 : 0;
            Log.d(TAG, "setting HDMI_AUTO_ADJUST to " + enabled);
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.HDMI_AUTO_ADJUST, enabled);
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                updateTimeoutPreferenceDescription(value);
                return true;
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        } else if (KEY_FONT_SIZE.equals(key)) {
            writeFontSizePreference(objValue);
            return true;
        } else if (key.equals(KEY_OUTPUT_MODE)) {
            String newMode = objValue.toString();
            sw.writeSysfs(mHdmiManager.HDMI_PLUGGED, "vdac");
            if (mHdmiManager.isFreescaleClosed()) {
                mHdmiManager.setOutputWithoutFreescale(newMode);
            } else {
                mHdmiManager.setOutputMode(newMode);
            }
            sw.writeSysfs(mHdmiManager.BLANK_DISPLAY, "0");
            Settings.Secure.putString(getActivity().getContentResolver(),
                    Settings.Secure.HDMI_RESOLUTION, newMode);
            mOutputModePref.setSummary(newMode);
            // reset position after resolution change
            reset();
            return true;
        }

        return false;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED)) {
                mWifiDisplayStatus = (WifiDisplayStatus)intent.getParcelableExtra(
                        DisplayManager.EXTRA_WIFI_DISPLAY_STATUS);
                updateWifiDisplaySummary();
            }
        }
    };

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mFontSizePref) {
            if (Utils.hasMultipleUsers(getActivity())) {
                showDialog(DLG_GLOBAL_CHANGE_WARNING);
                return true;
            } else {
                mFontSizePref.click();
            }
        }
        return false;
    }
}
