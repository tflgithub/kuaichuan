package com.cn.tfl.kuaichuan.core.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.cn.tfl.kuaichuan.ui.ChooseReceiverActivity;

import java.util.List;

/**
 * Created by Happiness on 2017/6/6.
 */

public class WifiMgr {

    private static WifiMgr mWifiMgr;
    private Context mContext;
    private WifiManager mWifiManager;

    //scan the result
    List<ScanResult> mScanResultList;
    List<WifiConfiguration> mWifiConfigurations;


    //current wifi configuration info
    WifiInfo mWifiInfo;

    private WifiMgr(Context context) {
        this.mContext = context;
        this.mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public static WifiMgr getInstance(Context context) {
        if (mWifiMgr == null) {
            synchronized (WifiMgr.class) {
                if (mWifiMgr == null) {
                    mWifiMgr = new WifiMgr(context);
                }
            }
        }
        return mWifiMgr;
    }

    /**
     * 打开wifi
     */
    public void openWifi() {
        if (!mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(true);
        }
    }


    /**
     * 关闭wifi
     */
    public void closeWifi() {
        if (mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(false);
        }
    }

    /**
     * wifi扫描
     */
    public void startScan() {
        mWifiManager.startScan();
        mScanResultList = mWifiManager.getScanResults();
        mWifiConfigurations = mWifiManager.getConfiguredNetworks();
    }

    public List<ScanResult> getScanResultList() {
        mScanResultList = ListUtils.filterWithNoPassword(mScanResultList);
        return mScanResultList;
    }

    public List<WifiConfiguration> getWifiConfigurations() {
        return mWifiConfigurations;
    }


    private WifiConfiguration isExsits(String SSID) {
        List<WifiConfiguration> existingConfigs = mWifiManager.getConfiguredNetworks();
        for (WifiConfiguration existingConfig : existingConfigs) {
            if (existingConfig.SSID.equals("\"" + SSID + "\"")) {
                return existingConfig;
            }
        }
        return null;
    }


    public enum WifiEncType {
        WEP, WPA, OPEN
    }

    /**
     * 连接热点wifi
     *
     * @param targetSsid
     * @param targetPsd
     * @param enc
     */
    public boolean connectWifi(String targetSsid, String targetPsd, WifiEncType enc) {
        try {
            // 1、注意热点和密码均包含引号，此处需要需要转义引号
            String ssid = "\"" + targetSsid + "\"";
            String psd = "\"" + targetPsd + "\"";
            WifiConfiguration conf = new WifiConfiguration();
            conf.SSID = ssid;
            switch (enc) {
                case WEP:
                    // 加密类型为WEP
                    conf.wepKeys[0] = psd;
                    conf.wepTxKeyIndex = 0;
                    conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                    break;
                case WPA:
                    // 加密类型为WPA
                    conf.preSharedKey = psd;
                    break;
                case OPEN:
                    //开放网络
                    conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            }

            //3、链接wifi
            mWifiManager.addNetwork(conf);
            List<WifiConfiguration> list = getWifiConfigurations();
            for (WifiConfiguration i : list) {
                if (i.SSID != null && i.SSID.equals(ssid)) {
                    mWifiManager.disconnect();
                    mWifiManager.enableNetwork(i.networkId, true);
                    mWifiManager.reconnect();
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(ChooseReceiverActivity.TAG, "连接热点出错" + e.getMessage());
            return false;
        }
        return true;
    }


    /**
     * 获取当前WifiInfo
     *
     * @return
     */
    public WifiInfo getWifiInfo() {
        mWifiInfo = mWifiManager.getConnectionInfo();
        return mWifiInfo;
    }

    /**
     * 获取当前Wifi所分配的Ip地址
     *
     * @return
     */
//  when connect the hotspot, is still returning "0.0.0.0".
    public String getCurrentIpAddress() {

        int address = mWifiManager.getDhcpInfo().ipAddress;
        String ipAddress = ((address & 0xFF)
                + "." + ((address >> 8) & 0xFF)
                + "." + ((address >> 16) & 0xFF)
                + "." + ((address >> 24) & 0xFF));
        return ipAddress;
    }


    /**
     * 设备连接Wifi之后， 设备获取Wifi热点的IP地址
     *
     * @return
     */
    public String getIpAddressFromHotspot() {
        // WifiAP ip address is hardcoded in Android.
        /* IP/netmask: 192.168.43.1/255.255.255.0 */
        DhcpInfo dhcpInfo = mWifiManager.getDhcpInfo();
        int address = dhcpInfo.serverAddress;
        String ipAddress = ((address & 0xFF)
                + "." + ((address >> 8) & 0xFF)
                + "." + ((address >> 16) & 0xFF)
                + "." + ((address >> 24) & 0xFF));
        return ipAddress;
    }


    public boolean isWifiConnect() {
        ConnectivityManager connManager = (ConnectivityManager) mContext.getSystemService(mContext.CONNECTIVITY_SERVICE);
        NetworkInfo ni = connManager.getActiveNetworkInfo();
        return (ni != null && ni.isConnectedOrConnecting());
    }

    /**
     * 开启热点之后，获取自身热点的IP地址
     *
     * @return
     */
    public String getHotspotLocalIpAddress() {
        // WifiAP ip address is hardcoded in Android.
        /* IP/netmask: 192.168.43.1/255.255.255.0 */
        String ipAddress = "192.168.43.1";
        DhcpInfo dhcpInfo = mWifiManager.getDhcpInfo();
        int address = dhcpInfo.ipAddress;
        ipAddress = ((address & 0xFF)
                + "." + ((address >> 8) & 0xFF)
                + "." + ((address >> 16) & 0xFF)
                + "." + ((address >> 24) & 0xFF));
        return ipAddress;
    }


    /**
     * 关闭Wifi
     */
    public void disableWifi() {
        if (mWifiManager != null) {
            mWifiManager.setWifiEnabled(false);
        }
    }
}
