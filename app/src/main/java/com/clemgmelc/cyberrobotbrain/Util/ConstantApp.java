package com.clemgmelc.cyberrobotbrain.Util;

import java.util.UUID;

/**
 * Created by marco on 4/18/17.
 */

public class ConstantApp {

    public static final String TAG = "CyberRobot";
    public static final UUID UUID_MOVEMENT = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb");



    public static final byte[] forward = {0x31, 0x32, 0x44, 0x30, 0x53, 0x2d, 0x31};
    public static final byte[] backward = {0x31, 0x32, 0x44, 0x31, 0x53, 0x2d, 0x31};
    public static final byte[] left = {0x31, 0x32, 0x44, 0x32, 0x53, 0x2d, 0x31};
    public static final byte[] right = {0x31, 0x32, 0x44, 0x33, 0x53, 0x2d, 0x31};

}
