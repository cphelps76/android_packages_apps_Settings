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
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
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

import com.android.internal.view.RotationPolicy;
import com.android.settings.DreamSettings;

import java.util.ArrayList;

import android.os.SystemProperties;
import android.app.Activity;
public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, OnPreferenceClickListener {
    private static final String TAG = "DisplaySettings";
	private ListPreference  mDisplayOutputmode;
    private CharSequence[] mEntryValues;
    private int sel_index;
	private int index_entry;
	private static final int GET_USER_OPERATION=1;
	private int index_cvbs;
    private int select_cvbs;
    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_Brightness = "brightness";
    private static final String KEY_DEFAULT_FREQUENCY = "default_frequency";
    private static final String KEY_SCREEN_SAVER = "screensaver";
    private static final String KEY_WALLPAPER = "wallpaper";
    private static final String KEY_AUTOBRIGHTNESS = "auto_brightness";
    private static final String KEY_HEADS_UP = "heads_up_enabled";


    private static final int DLG_GLOBAL_CHANGE_WARNING = 1;
    public static SystemWriteManager sw;

    private CheckBoxPreference mAccelerometer;
    private WarnedListPreference mFontSizePref;
    private CheckBoxPreference mNotificationPulse;

    private final Configuration mCurConfig = new Configuration();

    private ListPreference mScreenTimeoutPreference;
    private Preference mScreenSaverPreference;

    private ListPreference  mDefaultFrequency;
    private static final String STR_DEFAULT_FREQUENCY_VAR="ubootenv.var.defaulttvfrequency";
    private CharSequence[] mDefaultFrequencyEntries;

    private CheckBoxPreference mHeadsUpEnabled;

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
        String autoBrightness = SystemProperties.get("prop.sp.brightness","on");
        if(autoBrightness.equals("off")) {
            getActivity().getSharedPreferences(AutoBrightnessSwitch.AUTO_PREF_NAME,
                            Context.MODE_PRIVATE).edit()
                       .putBoolean(AutoBrightnessSwitch.AUTO_PREF_ON_OFF, false).commit();
            getPreferenceScreen().removePreference(findPreference(KEY_AUTOBRIGHTNESS));
        }

        sw = (SystemWriteManager)getSystemService("system_write");
        mAccelerometer = (CheckBoxPreference) findPreference(KEY_ACCELEROMETER);
        mAccelerometer.setPersistent(false);
        if (!RotationPolicy.isRotationSupported(getActivity())
                || RotationPolicy.isRotationLockToggleSupported(getActivity())) {
            // If rotation lock is supported, then we do not provide this option in
            // Display settings.  However, is still available in Accessibility settings,
            // if the device supports rotation.
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
        if(Utils.platformHasDefaultTVFreq()){
    	    mDefaultFrequency = (ListPreference) findPreference(KEY_DEFAULT_FREQUENCY);
    	    mDefaultFrequency.setOnPreferenceChangeListener(this);
    	    String valDefaultFrequency = SystemProperties.get(STR_DEFAULT_FREQUENCY_VAR);
    	    mDefaultFrequencyEntries = getResources().getStringArray(R.array.default_frequency_entries);
    	    if(valDefaultFrequency.equals("")){
    	        valDefaultFrequency = getResources().getString(R.string.tv_default_frequency_summary);
    	    }
    	    int index_DF = findIndexOfEntry(valDefaultFrequency, mDefaultFrequencyEntries);
    	    mDefaultFrequency.setValueIndex(index_DF);
			mDefaultFrequency.setSummary(valDefaultFrequency);
        } else {
            getPreferenceScreen().removePreference(findPreference(KEY_DEFAULT_FREQUENCY));
        }
        mHeadsUpEnabled = (CheckBoxPreference) findPreference(KEY_HEADS_UP);
        mHeadsUpEnabled.setOnPreferenceChangeListener(this);
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
            final int userPreference = Integer.parseInt(screenTimeoutPreference.getValue());
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else if (revisedValues.size() > 0
                    && Long.parseLong(revisedValues.get(revisedValues.size() - 1).toString())
                    == maxTimeout) {
                // If the last one happens to be the same as the max timeout, select that
                screenTimeoutPreference.setValue(String.valueOf(maxTimeout));
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

        updateState();
    }

    @Override
    public void onPause() {
        super.onPause();

        RotationPolicy.unregisterRotationPolicyListener(getActivity(),
                mRotationPolicyListener);
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
    }

    private void updateScreenSaverSummary() {
        if (mScreenSaverPreference != null) {
            mScreenSaverPreference.setSummary(
                    DreamSettings.getSummaryTextWithDreamName(getActivity()));
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

        } else if (preference == mHeadsUpEnabled) {
            boolean value = mHeadsUpEnabled.isChecked();
            Settings.Global.putInt(getContentResolver(), Settings.Global.HEADS_UP_ENABLED,
                    value ? 1 : 0);
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mScreenTimeoutPreference) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                updateTimeoutPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        } else if (preference == mFontSizePref) {
            writeFontSizePreference(objValue);
        } else if(preference == mDefaultFrequency) {
            try {
                int frequency_index = Integer.parseInt((String) objValue);
                mDefaultFrequency.setSummary(mDefaultFrequencyEntries[frequency_index]);
                SystemProperties.set(STR_DEFAULT_FREQUENCY_VAR,mDefaultFrequencyEntries[frequency_index].toString());
            }catch (NumberFormatException e) {
                Log.e(TAG, "could not persist default TV frequency setting", e);
            }
        }

        return true;
    }

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
}
