package com.cn.tfl.kuaichuan.core.utils;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;

import java.lang.reflect.Method;

/**
 * Created by Happiness on 2017/6/8.
 */

public class ApMgr {

    //check whether wifi hotspot on or off
    public static boolean isApOn(Context context) {
        WifiManager wifimanager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
        try {
            Method method = wifimanager.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (Boolean) method.invoke(wifimanager);
        } catch (Throwable ignored) {
        }
        return false;
    }

    //close wifi hotspot
    public static void disableAp(Context context) {
        WifiManager wifimanager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
        try {
            Method method = wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifimanager, null, false);
        } catch (Throwable ignored) {

        }
    }

    // toggle wifi hotspot on or off, and specify the hotspot name
    public static WifiConfiguration configApState(Context context, String apName) {
        WifiManager wifimanager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
        if (wifimanager.isWifiEnabled()) {
            //如果wifi处于打开状态，则关闭wifi,
            wifimanager.setWifiEnabled(false);
        }
        WifiConfiguration wificonfiguration = new WifiConfiguration();
        wificonfiguration.SSID = apName;
        try {
            Method method = wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            boolean enable = (Boolean) method.invoke(wifimanager, wificonfiguration, true);
            return wificonfiguration;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
