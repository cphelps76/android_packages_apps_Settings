/**
 * Copyright (C) 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.settings;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.SystemWriteManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceActivity.Header;
import android.preference.PreferenceFrameLayout;
import android.preference.PreferenceGroup;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContacts;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TabWidget;

import com.android.settings.users.ProfileUpdateReceiver;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class Utils {

    public static final String TAG = Utils.class.getSimpleName();

    /**
     * Set the preference's title to the matching activity's label.
     */
    public static final int UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY = 1;

    /**
     * The opacity level of a disabled icon.
     */
    public static final float DISABLED_ALPHA = 0.4f;

    /**
     * Name of the meta-data item that should be set in the AndroidManifest.xml
     * to specify the icon that should be displayed for the preference.
     */
    private static final String META_DATA_PREFERENCE_ICON = "com.android.settings.icon";

    /**
     * Name of the meta-data item that should be set in the AndroidManifest.xml
     * to specify the title that should be displayed for the preference.
     */
    private static final String META_DATA_PREFERENCE_TITLE = "com.android.settings.title";

    /**
     * Name of the meta-data item that should be set in the AndroidManifest.xml
     * to specify the summary text that should be displayed for the preference.
     */
    private static final String META_DATA_PREFERENCE_SUMMARY = "com.android.settings.summary";

    /**
     * Finds a matching activity for a preference's intent. If a matching
     * activity is not found, it will remove the preference.
     *
     * @param context The context.
     * @param parentPreferenceGroup The preference group that contains the
     *            preference whose intent is being resolved.
     * @param preferenceKey The key of the preference whose intent is being
     *            resolved.
     * @param flags 0 or one or more of
     *            {@link #UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY}
     *            .
     * @return Whether an activity was found. If false, the preference was
     *         removed.
     */
    public static boolean updatePreferenceToSpecificActivityOrRemove(Context context,
            PreferenceGroup parentPreferenceGroup, String preferenceKey, int flags) {

        Preference preference = parentPreferenceGroup.findPreference(preferenceKey);
        if (preference == null) {
            return false;
        }

        Intent intent = preference.getIntent();
        if (intent != null) {
            // Find the activity that is in the system image
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            int listSize = list.size();
            for (int i = 0; i < listSize; i++) {
                ResolveInfo resolveInfo = list.get(i);
                if ((resolveInfo.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
                        != 0) {

                    // Replace the intent with this specific activity
                    preference.setIntent(new Intent().setClassName(
                            resolveInfo.activityInfo.packageName,
                            resolveInfo.activityInfo.name));

                    if ((flags & UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY) != 0) {
                        // Set the preference title to the activity's label
                        preference.setTitle(resolveInfo.loadLabel(pm));
                    }

                    return true;
                }
            }
        }

        // Did not find a matching activity, so remove the preference
        parentPreferenceGroup.removePreference(preference);

        return false;
    }

    /**
     * Finds a matching activity for a preference's intent. If a matching
     * activity is not found, it will remove the preference. The icon, title and
     * summary of the preference will also be updated with the values retrieved
     * from the activity's meta-data elements. If no meta-data elements are
     * specified then the preference title will be set to match the label of the
     * activity, an icon and summary text will not be displayed.
     *
     * @param context The context.
     * @param parentPreferenceGroup The preference group that contains the
     *            preference whose intent is being resolved.
     * @param preferenceKey The key of the preference whose intent is being
     *            resolved.
     *
     * @return Whether an activity was found. If false, the preference was
     *         removed.
     *
     * @see {@link #META_DATA_PREFERENCE_ICON}
     *      {@link #META_DATA_PREFERENCE_TITLE}
     *      {@link #META_DATA_PREFERENCE_SUMMARY}
     */
    public static boolean updatePreferenceToSpecificActivityFromMetaDataOrRemove(Context context,
            PreferenceGroup parentPreferenceGroup, String preferenceKey) {

        IconPreferenceScreen preference = (IconPreferenceScreen)parentPreferenceGroup
                .findPreference(preferenceKey);
        if (preference == null) {
            return false;
        }

        Intent intent = preference.getIntent();
        if (intent != null) {
            // Find the activity that is in the system image
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA);
            int listSize = list.size();
            for (int i = 0; i < listSize; i++) {
                ResolveInfo resolveInfo = list.get(i);
                if ((resolveInfo.activityInfo.applicationInfo.flags
                        & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    Drawable icon = null;
                    String title = null;
                    String summary = null;

                    // Get the activity's meta-data
                    try {
                        Resources res = pm
                                .getResourcesForApplication(resolveInfo.activityInfo.packageName);
                        Bundle metaData = resolveInfo.activityInfo.metaData;

                        if (res != null && metaData != null) {
                            icon = res.getDrawable(metaData.getInt(META_DATA_PREFERENCE_ICON));
                            title = res.getString(metaData.getInt(META_DATA_PREFERENCE_TITLE));
                            summary = res.getString(metaData.getInt(META_DATA_PREFERENCE_SUMMARY));
                        }
                    } catch (NameNotFoundException e) {
                        // Ignore
                    } catch (NotFoundException e) {
                        // Ignore
                    }

                    // Set the preference title to the activity's label if no
                    // meta-data is found
                    if (TextUtils.isEmpty(title)) {
                        title = resolveInfo.loadLabel(pm).toString();
                    }

                    // Set icon, title and summary for the preference
                    preference.setIcon(icon);
                    preference.setTitle(title);
                    preference.setSummary(summary);

                    // Replace the intent with this specific activity
                    preference.setIntent(new Intent().setClassName(
                            resolveInfo.activityInfo.packageName,
                            resolveInfo.activityInfo.name));

                   return true;
                }
            }
        }

        // Did not find a matching activity, so remove the preference
        parentPreferenceGroup.removePreference(preference);

        return false;
    }

    public static boolean updateHeaderToSpecificActivityFromMetaDataOrRemove(Context context,
            List<Header> target, Header header) {

        Intent intent = header.intent;
        if (intent != null) {
            // Find the activity that is in the system image
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA);
            int listSize = list.size();
            for (int i = 0; i < listSize; i++) {
                ResolveInfo resolveInfo = list.get(i);
                if ((resolveInfo.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
                        != 0) {
                    Drawable icon = null;
                    String title = null;
                    String summary = null;

                    // Get the activity's meta-data
                    try {
                        Resources res = pm.getResourcesForApplication(
                                resolveInfo.activityInfo.packageName);
                        Bundle metaData = resolveInfo.activityInfo.metaData;

                        if (res != null && metaData != null) {
                            icon = res.getDrawable(metaData.getInt(META_DATA_PREFERENCE_ICON));
                            title = res.getString(metaData.getInt(META_DATA_PREFERENCE_TITLE));
                            summary = res.getString(metaData.getInt(META_DATA_PREFERENCE_SUMMARY));
                        }
                    } catch (NameNotFoundException e) {
                        // Ignore
                    } catch (NotFoundException e) {
                        // Ignore
                    }

                    // Set the preference title to the activity's label if no
                    // meta-data is found
                    if (TextUtils.isEmpty(title)) {
                        title = resolveInfo.loadLabel(pm).toString();
                    }

                    // Set icon, title and summary for the preference
                    // TODO:
                    //header.icon = icon;
                    header.title = title;
                    header.summary = summary;
                    // Replace the intent with this specific activity
                    header.intent = new Intent().setClassName(resolveInfo.activityInfo.packageName,
                            resolveInfo.activityInfo.name);

                    return true;
                }
            }
        }

        // Did not find a matching activity, so remove the preference
        target.remove(header);

        return false;
    }

    /**
     * Returns true if Monkey is running.
     */
    public static boolean isMonkeyRunning() {
        return ActivityManager.isUserAMonkey();
    }

    /**
     * Returns whether the device is voice-capable (meaning, it is also a phone).
     */
    public static boolean isVoiceCapable(Context context) {
        TelephonyManager telephony =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephony != null && telephony.isVoiceCapable();
    }

    public static boolean isWifiOnly(Context context) {
        return SystemProperties.getBoolean("hw.nophone", true);
            /*
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        return (cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) == false);
        */
    }

    /**
     * Returns the WIFI IP Addresses, if any, taking into account IPv4 and IPv6 style addresses.
     * @param context the application context
     * @return the formatted and newline-separated IP addresses, or null if none.
     */
    public static String getWifiIpAddresses(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        LinkProperties prop = cm.getLinkProperties(ConnectivityManager.TYPE_WIFI);
        return formatIpAddresses(prop);
    }

    public static String getEtherProperties(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        LinkProperties prop = cm.getLinkProperties(ConnectivityManager.TYPE_ETHERNET);
        return prop.toString();
    }
    /**
     * Returns the default link's IP addresses, if any, taking into account IPv4 and IPv6 style
     * addresses.
     * @param context the application context
     * @return the formatted and newline-separated IP addresses, or null if none.
     */
    public static String getDefaultIpAddresses(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        LinkProperties prop = cm.getActiveLinkProperties();
        return formatIpAddresses(prop);
    }

    private static String formatIpAddresses(LinkProperties prop) {
        if (prop == null) return null;
        Iterator<InetAddress> iter = prop.getAllAddresses().iterator();
        // If there are no entries, return null
        if (!iter.hasNext()) return null;
        // Concatenate all available addresses, comma separated
        String addresses = "";
        while (iter.hasNext()) {
            addresses += iter.next().getHostAddress();
            if (iter.hasNext()) addresses += "\n";
        }
        return addresses;
    }

    public static Locale createLocaleFromString(String localeStr) {
        // TODO: is there a better way to actually construct a locale that will match?
        // The main problem is, on top of Java specs, locale.toString() and
        // new Locale(locale.toString()).toString() do not return equal() strings in
        // many cases, because the constructor takes the only string as the language
        // code. So : new Locale("en", "US").toString() => "en_US"
        // And : new Locale("en_US").toString() => "en_us"
        if (null == localeStr)
            return Locale.getDefault();
        String[] brokenDownLocale = localeStr.split("_", 3);
        // split may not return a 0-length array.
        if (1 == brokenDownLocale.length) {
            return new Locale(brokenDownLocale[0]);
        } else if (2 == brokenDownLocale.length) {
            return new Locale(brokenDownLocale[0], brokenDownLocale[1]);
        } else {
            return new Locale(brokenDownLocale[0], brokenDownLocale[1], brokenDownLocale[2]);
        }
    }

    public static boolean isBatteryPresent(Intent batteryChangedIntent) {
        return batteryChangedIntent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
    }

    public static String getBatteryPercentage(Intent batteryChangedIntent) {
        int level = batteryChangedIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = batteryChangedIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return String.valueOf(level * 100 / scale) + "%";
    }

    public static String getBatteryStatus(Resources res, Intent batteryChangedIntent) {
        final Intent intent = batteryChangedIntent;

        int plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        String statusString;
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
            statusString = res.getString(R.string.battery_info_status_charging);
            if (plugType > 0) {
                int resId;
                if (plugType == BatteryManager.BATTERY_PLUGGED_AC) {
                    resId = R.string.battery_info_status_charging_ac;
                } else if (plugType == BatteryManager.BATTERY_PLUGGED_USB) {
                    resId = R.string.battery_info_status_charging_usb;
                } else {
                    resId = R.string.battery_info_status_charging_wireless;
                }
                statusString = statusString + " " + res.getString(resId);
            }
        } else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) {
            statusString = res.getString(R.string.battery_info_status_discharging);
        } else if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
            statusString = res.getString(R.string.battery_info_status_not_charging);
        } else if (status == BatteryManager.BATTERY_STATUS_FULL) {
            statusString = res.getString(R.string.battery_info_status_full);
        } else {
            statusString = res.getString(R.string.battery_info_status_unknown);
        }

        return statusString;
    }

    public static void forcePrepareCustomPreferencesList(
            ViewGroup parent, View child, ListView list, boolean ignoreSidePadding) {
        list.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        list.setClipToPadding(false);
        prepareCustomPreferencesList(parent, child, list, ignoreSidePadding);
    }

    /**
     * Prepare a custom preferences layout, moving padding to {@link ListView}
     * when outside scrollbars are requested. Usually used to display
     * {@link ListView} and {@link TabWidget} with correct padding.
     */
    public static void prepareCustomPreferencesList(
            ViewGroup parent, View child, View list, boolean ignoreSidePadding) {
        final boolean movePadding = list.getScrollBarStyle() == View.SCROLLBARS_OUTSIDE_OVERLAY;
        if (movePadding && parent instanceof PreferenceFrameLayout) {
            ((PreferenceFrameLayout.LayoutParams) child.getLayoutParams()).removeBorders = true;

            final Resources res = list.getResources();
            final int paddingSide = res.getDimensionPixelSize(R.dimen.settings_side_margin);
            final int paddingBottom = res.getDimensionPixelSize(
                    com.android.internal.R.dimen.preference_fragment_padding_bottom);

            final int effectivePaddingSide = ignoreSidePadding ? 0 : paddingSide;
            list.setPaddingRelative(effectivePaddingSide, 0, effectivePaddingSide, paddingBottom);
        }
    }

    /**
     * Return string resource that best describes combination of tethering
     * options available on this device.
     */
    public static int getTetheringLabel(ConnectivityManager cm) {
        String[] usbRegexs = cm.getTetherableUsbRegexs();
        String[] wifiRegexs = cm.getTetherableWifiRegexs();
        String[] bluetoothRegexs = cm.getTetherableBluetoothRegexs();

        boolean usbAvailable = usbRegexs.length != 0;
        boolean wifiAvailable = wifiRegexs.length != 0;
        boolean bluetoothAvailable = bluetoothRegexs.length != 0;

        if (wifiAvailable && usbAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_all;
        } else if (wifiAvailable && usbAvailable) {
            return R.string.tether_settings_title_all;
        } else if (wifiAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_all;
        } else if (wifiAvailable) {
            return R.string.tether_settings_title_wifi;
        } else if (usbAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_usb_bluetooth;
        } else if (usbAvailable) {
            return R.string.tether_settings_title_usb;
        } else {
            return R.string.tether_settings_title_bluetooth;
        }
    }
    public static boolean hwNoPhone() {
        return SystemProperties.getBoolean("hw.nophone", true);
    }

    public static boolean hwNoBattery() {
        return SystemProperties.getBoolean("hw.nobattery", false);
    }

    public static boolean hwNoSoundPartition() {
        return SystemProperties.getBoolean("hw.nosoundpartition", false);
    }

	public static boolean hwNoSoundCategoryFeedback() {
		return SystemProperties.getBoolean("hw.nosoundcategoryfeedback", false);
    }

    public static boolean platformHasScreenBrightness() {
        return SystemProperties.getBoolean("ro.screen.has.brightness", true);
    }

	public static boolean hwHasBluetooth() {
		return SystemProperties.getBoolean("hw.has.bluetooth", true);	
	}
	
    public static boolean platformHasScreenTimeout() {
        return SystemProperties.getBoolean("ro.screen.has.timeout", true);
    }

    public static boolean platformHasScreenFontSize() {
        return SystemProperties.getBoolean("ro.screen.has.fontsize", true);
    }
	
    public static boolean platformHasSecurity() {
        return SystemProperties.getBoolean("ro.platform.has.security", true);
    }

    public static boolean platformHasEncrypt() {
        return SystemProperties.getBoolean("ro.platform.has.encrypt", false);
    }

    public static boolean platformHasMbxUiMode() {
        return SystemProperties.getBoolean("ro.platform.has.mbxuimode", false);
    }

    public static boolean platformHasHdmiDualDisp() {
        return SystemProperties.getBoolean("ro.vout.dualdisplay2", false) || SystemProperties.getBoolean("ro.vout.dualdisplay3", false);
    }
    public static boolean platformHasHdmiAutoSwitch() {
        return SystemProperties.getBoolean("ro.hdmi.autoswitch", true);
    }     
    public static boolean platformHasHdmiSpdif() {
        return SystemProperties.getBoolean("ro.hdmi.spdif", false);
    }    
    public static boolean platformHasHdmiSettings() {
        return !platformHasMbxUiMode() && (platformHasHdmiDualDisp() || platformHasHdmiSpdif() || platformHasHdmiAutoSwitch());
    } 
    
    public static boolean hwHasEthernet() {
        return SystemProperties.getBoolean("hw.hasethernet", false);
    }

	public static boolean hwNoDataUsage() {
		return SystemProperties.getBoolean("hw.nodatausage", false);
	}

	public static boolean hwNoSyncSetting() {
		return SystemProperties.getBoolean("hw.nosyncsetting", false);
	}
	
	public static boolean hwNoLocation() {
		return SystemProperties.getBoolean("hw.nolocation", false);
	}
	
	public static boolean hwNoAccessibility() {
		return SystemProperties.getBoolean("hw.noaccessibility", false);
	}
	
	public static boolean hwNoAboutDevice() {
		return SystemProperties.getBoolean("hw.noaboutdevice", false);
	}
	public static boolean platformHasTvUiMode() {
		return SystemProperties.getBoolean("ro.platform.has.tvuimode", false);
	}
	
	public static int platformHas1080Scale() {
		return SystemProperties.getInt("ro.platform.has.1080scale", 0);
	}
	
	public static boolean platformHasCpuMode() {
		return SystemProperties.getBoolean("ro.has.cpu.setting", false);
	}	 

	public static boolean platformHasDefaultTVFreq() {
        return SystemProperties.getBoolean("ro.platform.has.defaulttvfreq", false);
    }    
    public static boolean platformHasHdmiCECSwitch() {
        return SystemProperties.getBoolean("ro.platform.has.hdmicec", false);
    }    
    
    public static boolean platformHasDRC()
    {
            return SystemProperties.getBoolean("ro.platform.has.drc", false);
    }

    /* Used by UserSettings as well. Call this on a non-ui thread. */
    public static boolean copyMeProfilePhoto(Context context, UserInfo user) {
        Uri contactUri = Profile.CONTENT_URI;

        InputStream avatarDataStream = Contacts.openContactPhotoInputStream(
                    context.getContentResolver(),
                    contactUri, true);
        // If there's no profile photo, assign a default avatar
        if (avatarDataStream == null) {
            return false;
        }
        int userId = user != null ? user.id : UserHandle.myUserId();
        UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        Bitmap icon = BitmapFactory.decodeStream(avatarDataStream);
        um.setUserIcon(userId, icon);
        try {
            avatarDataStream.close();
        } catch (IOException ioe) { }
        return true;
    }

    public static String getMeProfileName(Context context, boolean full) {
        if (full) {
            return getProfileDisplayName(context);
        } else {
            return getShorterNameIfPossible(context);
        }
    }

    private static String getShorterNameIfPossible(Context context) {
        final String given = getLocalProfileGivenName(context);
        return !TextUtils.isEmpty(given) ? given : getProfileDisplayName(context);
    }

    private static String getLocalProfileGivenName(Context context) {
        final ContentResolver cr = context.getContentResolver();

        // Find the raw contact ID for the local ME profile raw contact.
        final long localRowProfileId;
        final Cursor localRawProfile = cr.query(
                Profile.CONTENT_RAW_CONTACTS_URI,
                new String[] {RawContacts._ID},
                RawContacts.ACCOUNT_TYPE + " IS NULL AND " +
                        RawContacts.ACCOUNT_NAME + " IS NULL",
                null, null);
        if (localRawProfile == null) return null;

        try {
            if (!localRawProfile.moveToFirst()) {
                return null;
            }
            localRowProfileId = localRawProfile.getLong(0);
        } finally {
            localRawProfile.close();
        }

        // Find the structured name for the raw contact.
        final Cursor structuredName = cr.query(
                Profile.CONTENT_URI.buildUpon().appendPath(Contacts.Data.CONTENT_DIRECTORY).build(),
                new String[] {CommonDataKinds.StructuredName.GIVEN_NAME,
                    CommonDataKinds.StructuredName.FAMILY_NAME},
                Data.RAW_CONTACT_ID + "=" + localRowProfileId,
                null, null);
        if (structuredName == null) return null;

        try {
            if (!structuredName.moveToFirst()) {
                return null;
            }
            String partialName = structuredName.getString(0);
            if (TextUtils.isEmpty(partialName)) {
                partialName = structuredName.getString(1);
            }
            return partialName;
        } finally {
            structuredName.close();
        }
    }

    private static final String getProfileDisplayName(Context context) {
        if(context == null){
            Log.d("Utils","context is null !!!");    
            return null;
        }
        final ContentResolver cr = context.getContentResolver();
        if(cr == null) {   
            Log.d("Utils","getContentResolver is null !!!");    
            return null ;
        }
        final Cursor profile = cr.query(Profile.CONTENT_URI,
                new String[] {Profile.DISPLAY_NAME}, null, null, null);
        if (profile == null) return null;

        try {
            if (!profile.moveToFirst()) {
                return null;
            }
            return profile.getString(0);
        } catch (Exception e){
            Log.d("Utils","getProfileDisplayName error : " + e.toString());    
            return null;
        } finally {
            profile.close();
        }
    }

    /** Not global warming, it's global change warning. */
    public static Dialog buildGlobalChangeWarningDialog(final Context context, int titleResId,
            final Runnable positiveAction) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(titleResId);
        builder.setMessage(R.string.global_change_warning);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                positiveAction.run();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);

        return builder.create();
    }

    public static boolean hasMultipleUsers(Context context) {
        return ((UserManager) context.getSystemService(Context.USER_SERVICE))
                .getUsers().size() > 1;
    }

    public static String getProp(String prop) {
        String value = "";
        try {
            Process p = new ProcessBuilder("/system/bin/getprop", prop)
                    .redirectErrorStream(true).start();

            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                value = line;
            }
            //if (p != null) p.destroy();
        } catch (IOException io) {
            io.printStackTrace();
        } catch (Exception e) {
            // hate this but sometimes erroneous ErrnoException is thrown
            // when the process for whatever reason can't be killed
        }
        return value;
    }

    public static void writeSysfs(SystemWriteManager sw, String path, String value) {
        if (sw == null || path == null || value == null) {
            Log.d(TAG, "Condition of writeSysfs is null");
            return;
        }
        sw.writeSysfs(path, value);
    }
}
