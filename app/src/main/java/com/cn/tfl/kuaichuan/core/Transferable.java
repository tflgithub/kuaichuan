package com.cn.tfl.kuaichuan.core;

/**
 * Created by Happiness on 2017/6/6.
 */

public interface Transferable {


    /**
     *
     * @throws Exception
     */
    void init() throws Exception;


    /**
     *
     * @throws Exception
     */
    void parseHeader() throws Exception;


    /**
     *
     * @throws Exception
     */
    void parseBody() throws Exception;


    /**
     *
     * @throws Exception
     */
    void finish() throws Exception;

}
