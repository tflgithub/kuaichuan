package com.cn.tfl.kuaichuan.core.entity;

import java.io.Serializable;
import java.net.InetAddress;

/**
 * Created by Happiness on 2017/6/7.
 */

public class IpPortInfo implements Serializable {

    InetAddress inetAddress;
    int port;

    public IpPortInfo(InetAddress inetAddress, int port) {
        this.inetAddress = inetAddress;
        this.port = port;
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }

    public void setInetAddress(InetAddress inetAddress) {
        this.inetAddress = inetAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
