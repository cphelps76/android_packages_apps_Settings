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


import java.util.List;

import com.android.settings.R;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.NetworkUtils;
import android.net.DhcpInfo;
import android.net.ethernet.EthernetManager;
import android.net.ethernet.EthernetDevInfo;
import android.net.ProxyProperties;
import android.net.wifi.WifiConfiguration.ProxySettings;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Slog;
import com.android.settings.Utils;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EthernetConfigDialog extends AlertDialog implements
        DialogInterface.OnClickListener, DialogInterface.OnShowListener,
        DialogInterface.OnDismissListener {
    private final String TAG = "EthConfDialog";
    private static final boolean localLOGV = false;

    private static final boolean ENABLE_PROXY = true;
    /* These values come from "wifi_proxy_settings" resource array */
    public static final int PROXY_NONE = 0;
    public static final int PROXY_STATIC = 1;

    /* These values come from "network_ip_settings" resource array */
    private static final int DHCP = 0;
    private static final int STATIC_IP = 1;

    // Matches blank input, ips, and domain names
    private static final String HOSTNAME_REGEXP =
            "^$|^[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*(\\.[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*)*$";
    private static final Pattern HOSTNAME_PATTERN;
    private static final String EXCLLIST_REGEXP =
            "$|^(.?[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*(\\.[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*)*)+" +
            "(,(.?[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*(\\.[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*)*))*$";
    private static final Pattern EXCLLIST_PATTERN;
    static {
        HOSTNAME_PATTERN = Pattern.compile(HOSTNAME_REGEXP);
        EXCLLIST_PATTERN = Pattern.compile(EXCLLIST_REGEXP);
    }

    private View mView;
    private Spinner mDevList;
    private TextView mDevs;
    private RadioButton mConTypeDhcp;
    private RadioButton mConTypeManual;
    private EditText mIpaddr;
    private EditText mDns;
    private EditText mGw;
    private EditText mMask;

    // Indicates if we are in the process of setting up values and should not validate them yet.
    private boolean mSettingUpValues;
    private Spinner mProxySettingsSpinner;
    private TextView mProxyHostView;
    private TextView mProxyPortView;
    private TextView mProxyExclusionListView;
    private final TextWatcher textWatcher = new TextWatcherImpl();

    private EthernetLayer mEthLayer;
    private EthernetManager mEthManager;
    private EthernetDevInfo mEthInfo;
    private boolean mEnablePending;

    private Context mContext;

    public EthernetConfigDialog(Context context, EthernetManager ethManager) {
        super(context);
        mEthManager = ethManager;
        mEthLayer = new EthernetLayer(this, ethManager);
        mContext = context;
        buildDialogContent(context);
        setOnShowListener(this);
        setOnDismissListener(this);
		enableAfterConfig();
    }

    public void onShow(DialogInterface dialog) {
        if (localLOGV) Slog.d(TAG, "onShow");
        mEthLayer.resume();
        // soft keyboard pops up on the disabled EditText. Hide it.
        if (getCurrentFocus() != null && getCurrentFocus().getWindowToken() != null) {
            InputMethodManager imm = (InputMethodManager)mContext.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                    InputMethodManager.HIDE_IMPLICIT_ONLY);
        }
    }

    public void onDismiss(DialogInterface dialog) {
        if (localLOGV) Slog.d(TAG, "onDismiss");
        mEthLayer.pause();
    }

    private static String getAddress(int addr) {
        return NetworkUtils.intToInetAddress(addr).getHostAddress();
    }


    /* proxy */
    private void showProxyFields() {
        if (mProxySettingsSpinner.getSelectedItemPosition() == PROXY_STATIC) {
            mView.findViewById(R.id.proxy_fields).setVisibility(View.VISIBLE);
        } else {
            mView.findViewById(R.id.proxy_fields).setVisibility(View.GONE);
        }
    }

    private void enableSubmitIfAppropriate() {
        //setPositiveButtonEnabled(isProxyFieldsValid() && isIpFieldsValid());
        isProxyFieldsValid();
        //skip disabling PositveButton for now
    }

    private boolean isProxyFieldsValid() {
        if (mProxySettingsSpinner.getSelectedItemPosition() == PROXY_STATIC) {
            return validateProxyFields();
        }
        return true;
    }

    public static boolean isValidIpAddress(String ipAddress, boolean allowEmptyValue) {
        if (ipAddress == null || ipAddress.length() == 0) {
            return allowEmptyValue;
        }

        try {
            InetAddress.getByName(ipAddress);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Validates string with proxy exclusion list.
     *
     * @param exclList string to validate.
     * @return resource id of error message string or 0 if valid.
     */
    public static int validateProxyExclusionList(String exclList) {
        Matcher listMatch = EXCLLIST_PATTERN.matcher(exclList);
        return !listMatch.matches() ? R.string.proxy_error_invalid_exclusion_list : 0;
    }

    private boolean validateProxyFields() {
        if (!ENABLE_PROXY) {
            return true;
        }

        final Context context = getContext();
        boolean errors = false;

        if (isValidIpAddress(mProxyHostView.getText().toString(), false)) {
            mProxyHostView.setError(null);
        } else {
            mProxyHostView.setError(
                    context.getString(R.string.wifi_ip_settings_invalid_ip_address));
            errors = true;
        }

        int port = -1;
        try {
            port = Integer.parseInt(mProxyPortView.getText().toString());
            mProxyPortView.setError(null);
        } catch (NumberFormatException e) {
            // Intentionally left blank
        }
        if (port < 0) {
            mProxyPortView.setError(context.getString(R.string.proxy_error_invalid_port));
            errors = true;
        }

        final String exclusionList = mProxyExclusionListView.getText().toString();
        final int listResult = validateProxyExclusionList(exclusionList);
        if (listResult == 0) {
            mProxyExclusionListView.setError(null);
        } else {
            mProxyExclusionListView.setError(context.getString(listResult));
            errors = true;
        }

        return !errors;
    }

    private void setPositiveButtonEnabled(boolean enabled) {
        getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(enabled);
    }

    private class TextWatcherImpl implements TextWatcher {
        @Override
        public void afterTextChanged(Editable s) {
            // Do not validate fields while values are being setted up.
            if (!mSettingUpValues) {
                enableSubmitIfAppropriate();
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    private void setProxyPropertiesFromEdits(EthernetDevInfo info) {
        final ProxySettings proxySettings =
                ENABLE_PROXY && mProxySettingsSpinner.getSelectedItemPosition() == PROXY_STATIC
                        ? ProxySettings.STATIC : ProxySettings.NONE;

        if (proxySettings == ProxySettings.STATIC) {
            String port = mProxyPortView.getText().toString();
            if (TextUtils.isEmpty(port))
                port = "0";
            try {
                info.setProxy(
                        mProxyHostView.getText().toString(),
                        Integer.parseInt(port),
                        mProxyExclusionListView.getText().toString());
            } catch (IllegalArgumentException e) {
                // Should not happen if validations are done right
                throw new RuntimeException(e);
            }
        } else {
            info.setProxy(null, 0, null);
        }
    }

    private void buildProxyContent() {
        mProxySettingsSpinner = (Spinner) mView.findViewById(R.id.proxy_settings);
        mProxySettingsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                showProxyFields();
                enableSubmitIfAppropriate();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        mProxySettingsSpinner.setVisibility(View.VISIBLE);

        mProxyHostView = (TextView) mView.findViewById(R.id.proxy_hostname);
        mProxyHostView.addTextChangedListener(textWatcher);

        mProxyPortView = (TextView) mView.findViewById(R.id.proxy_port);
        mProxyPortView.addTextChangedListener(textWatcher);

        mProxyExclusionListView = (TextView) mView.findViewById(R.id.proxy_exclusionlist);
        mProxyExclusionListView.addTextChangedListener(textWatcher);

    }
    /* /proxy */

    public int buildDialogContent(Context context) {
        mSettingUpValues = true;
        this.setTitle(R.string.eth_config_title);
        this.setView(mView = getLayoutInflater().inflate(R.layout.eth_configure, null));
        mDevs = (TextView) mView.findViewById(R.id.eth_dev_list_text);
        mDevList = (Spinner) mView.findViewById(R.id.eth_dev_spinner);
        mConTypeDhcp = (RadioButton) mView.findViewById(R.id.dhcp_radio);
        mConTypeManual = (RadioButton) mView.findViewById(R.id.manual_radio);
        mIpaddr = (EditText)mView.findViewById(R.id.ipaddr_edit);
        mMask = (EditText)mView.findViewById(R.id.netmask_edit);
        mDns = (EditText)mView.findViewById(R.id.eth_dns_edit);
        mGw = (EditText)mView.findViewById(R.id.eth_gw_edit);

        mConTypeDhcp.setChecked(true);
        mConTypeManual.setChecked(false);
        mIpaddr.setEnabled(false);
        mMask.setEnabled(false);
        mDns.setEnabled(false);
        mGw.setEnabled(false);
        mConTypeManual.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
                mView.findViewById(R.id.eth_static_fields).setVisibility(View.VISIBLE);
                mIpaddr.setEnabled(true);
                mDns.setEnabled(true);
                mGw.setEnabled(true);
                mMask.setEnabled(true);
            }
        });

        mConTypeDhcp.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
                mView.findViewById(R.id.eth_static_fields).setVisibility(View.GONE);
                mIpaddr.setEnabled(false);
                mDns.setEnabled(false);
                mGw.setEnabled(false);
                mMask.setEnabled(false);
            }
        });

        buildProxyContent();

        this.setInverseBackgroundForced(true);
        this.setButton(BUTTON_POSITIVE, context.getText(R.string.menu_save), this);
        this.setButton(BUTTON_NEGATIVE, context.getText(R.string.menu_cancel), this);
        String[] Devs = mEthManager.getDeviceNameList();
        updateDevNameList(Devs);
        if (Devs != null) {
            if (mEthManager.isEthConfigured()) {
                String propties = Utils.getEtherProperties(mContext);
                Slog.d(TAG, "Properties: " + propties);

                mEthInfo = mEthManager.getSavedEthConfig();
                for (int i = 0 ; i < Devs.length; i++) {
                    if (Devs[i].equals(mEthInfo.getIfName())) {
                        mDevList.setSelection(i);
                        break;
                    }
                }
                /*if (mEthInfo.getConnectMode().equals(EthernetDevInfo.ETH_CONN_MODE_DHCP)) 
                {
                    DhcpInfo dhcpInfo = mEthManager.getDhcpInfo();
                    Slog.d(TAG, "ip  : " + getAddress(dhcpInfo.ipAddress));
                    Slog.d(TAG, "gw  : " + getAddress(dhcpInfo.gateway));
                    Slog.d(TAG, "mask: " + getAddress(dhcpInfo.netmask));
                    Slog.d(TAG, "dns1:" + getAddress(dhcpInfo.dns1));
                    Slog.d(TAG, "dns2:" + getAddress(dhcpInfo.dns2));
                }*/
                mIpaddr.setText(mEthInfo.getIpAddress());
                mGw.setText(mEthInfo.getRouteAddr());
                mDns.setText(mEthInfo.getDnsAddr());
                mMask.setText(mEthInfo.getNetMask());
                if (mEthInfo.getConnectMode().equals(EthernetDevInfo.ETH_CONN_MODE_DHCP)) {
                    mView.findViewById(R.id.eth_static_fields).setVisibility(View.GONE);
                    mIpaddr.setEnabled(false);
                    mDns.setEnabled(false);
                    mGw.setEnabled(false);
                    mMask.setEnabled(false);
                } else {
                    mConTypeDhcp.setChecked(false);
                    mConTypeManual.setChecked(true);
                    mView.findViewById(R.id.eth_static_fields).setVisibility(View.VISIBLE);
                    mIpaddr.setEnabled(true);
                    mDns.setEnabled(true);
                    mGw.setEnabled(true);
                    mMask.setEnabled(true);
                }
                if (ENABLE_PROXY) {
                    if (mEthInfo.hasProxy()) {
                        mProxySettingsSpinner.setSelection(PROXY_STATIC);
                        mProxyHostView.setText(mEthInfo.getProxyHost());
                        mProxyPortView.setText(String.valueOf(mEthInfo.getProxyPort()));
                        mProxyExclusionListView.setText(mEthInfo.getProxyExclusionList());
                    }
                }
            }
        }
        mSettingUpValues = false;
        return 0;
    }

    private void handle_saveconf() {
        String selected = null;
        if (mDevList.getSelectedItem() != null)
            selected = mDevList.getSelectedItem().toString();
        if (selected == null || selected.isEmpty())
            return;
        EthernetDevInfo info = new EthernetDevInfo();
        info.setIfName(selected);
        if (localLOGV)
            Slog.v(TAG, "Config device for " + selected);
        if (mConTypeDhcp.isChecked()) {
            info.setConnectMode(EthernetDevInfo.ETH_CONN_MODE_DHCP);
            info.setIpAddress(null);
            info.setRouteAddr(null);
            info.setDnsAddr(null);
            info.setNetMask(null);
        } else {
            Slog.i(TAG,"mode manual");
            if (isIpAddress(mIpaddr.getText().toString())
                    && isIpAddress(mGw.getText().toString())
                    && isIpAddress(mDns.getText().toString())
                    && isIpAddress(mMask.getText().toString())) {
                info.setConnectMode(EthernetDevInfo.ETH_CONN_MODE_MANUAL);
                info.setIpAddress(mIpaddr.getText().toString());
                info.setRouteAddr(mGw.getText().toString());
                info.setDnsAddr(mDns.getText().toString());
                info.setNetMask(mMask.getText().toString());
            } else {
                Toast.makeText(mContext, R.string.eth_settings_error, Toast.LENGTH_LONG).show();
                return;
            }
        }

        setProxyPropertiesFromEdits(info);

        mEthManager.updateEthDevInfo(info);
         if (mEnablePending) {
            	if(mEthManager.getEthState()==mEthManager.ETH_STATE_ENABLED){
					mEthManager.setEthEnabled(true);
				}
            mEnablePending = false;
        }
    }


    private boolean isIpAddress(String value) {
        int start = 0;
        int end = value.indexOf('.');
        int numBlocks = 0;

        while (start < value.length()) {
            if (end == -1) {
                end = value.length();
            }

            try {
                int block = Integer.parseInt(value.substring(start, end));
                if ((block > 255) || (block < 0)) {
                        return false;
                }
            } catch (NumberFormatException e) {
                    return false;
            }

            numBlocks++;

            start = end + 1;
            end = value.indexOf('.', start);
        }
        return numBlocks == 4;
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case BUTTON_POSITIVE:
                handle_saveconf();
                break;
            case BUTTON_NEGATIVE:
                //Don't need to do anything
                break;
            default:
        }
    }

    public void updateDevNameList(String[] DevList) {
        if (DevList == null) {
            DevList = new String[] {};
        }
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
                getContext(), android.R.layout.simple_spinner_item, DevList);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mDevList.setAdapter(adapter);
    }

    public void enableAfterConfig() {
        mEnablePending = true;
    }
}
