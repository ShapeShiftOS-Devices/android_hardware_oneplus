/*
 * Copyright (C) 2020-2022 ShapeShiftOS
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

package com.shapeshiftos.camera.handler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import android.os.Build;
import android.os.FileObserver;
import android.os.RemoteException;

import android.util.Log;

import android.view.KeyEvent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;

import com.android.internal.os.DeviceKeyHandler;

import vendor.oneplus.hardware.camera.V1_0.IOnePlusCameraProvider;

public class KeyHandler implements DeviceKeyHandler {
    private static final String TAG = KeyHandler.class.getSimpleName();

    public static final String CLIENT_PACKAGE_NAME = "com.oneplus.camera";
    public static final String CLIENT_PACKAGE_PATH = "/data/misc/custom/client_package_name";

    private final Context mContext;
    private boolean mDispOn;
    private ClientPackageNameObserver mClientObserver;
    private IOnePlusCameraProvider mProvider;
    private boolean isOPCameraAvail;

    private BroadcastReceiver mSystemStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                mDispOn = true;
                onDisplayOn();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                mDispOn = false;
                onDisplayOff();
            }
        }
    };

    public KeyHandler(Context context) {
        mContext = context;

        IntentFilter systemStateFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        systemStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mSystemStateReceiver, systemStateFilter);

        isOPCameraAvail = isAvailableApp("com.oneplus.camera", context);
        if (isOPCameraAvail) {
            mClientObserver = new ClientPackageNameObserver(CLIENT_PACKAGE_PATH);
            mClientObserver.startWatching();
        }
    }

    public KeyEvent handleKeyEvent(KeyEvent event) {
        return null;
    }

    private void onDisplayOff() {
        if (Build.IS_DEBUGGABLE) Log.i(TAG, "Display off");
        if (mClientObserver != null) {
            mClientObserver.stopWatching();
            mClientObserver = null;
        }
    }

    private void onDisplayOn() {
        if (Build.IS_DEBUGGABLE) Log.i(TAG, "Display on");
        if ((mClientObserver == null) && (isOPCameraAvail)) {
            mClientObserver = new ClientPackageNameObserver(CLIENT_PACKAGE_PATH);
            mClientObserver.startWatching();
        }
    }

    private class ClientPackageNameObserver extends FileObserver {

        public ClientPackageNameObserver(String file) {
            super(CLIENT_PACKAGE_PATH, MODIFY);
        }

        @Override
        public void onEvent(int event, String file) {
            String pkgName = getFileValue(CLIENT_PACKAGE_PATH, "0");
            if (event == FileObserver.MODIFY) {
                try {
                    Log.d(TAG, "client_package" + file + " and " + pkgName);
                    mProvider = IOnePlusCameraProvider.getService();
                    mProvider.setPackageName(pkgName);
                } catch (RemoteException e) {
                    Log.e(TAG, "setPackageName error", e);
                }
            }
        }
    }

    public static String getFileValue(String filename, String defValue) {
        String fileValue = readLine(filename);
        if (fileValue != null) {
            return fileValue;
        }
        return defValue;
    }

    public static String readLine(String filename) {
        if (filename == null) {
            return null;
        }
        BufferedReader br = null;
        String line = null;
        try {
            br = new BufferedReader(new FileReader(filename), 1024);
            line = br.readLine();
        } catch (IOException e) {
            return null;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return line;
    }

    public static boolean isAppInstalled(Context context, String appUri) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(appUri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAvailableApp(String packageName, Context context) {
        Context mContext = context;
        final PackageManager pm = mContext.getPackageManager();
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            int enabled = pm.getApplicationEnabledSetting(packageName);
            return enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (NameNotFoundException e) {
            return false;
        }
    }
}
