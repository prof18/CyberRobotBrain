package com.clemgmelc.cyberrobotbrain.Util;

import java.util.UUID;

/**
 * Created by marco on 4/18/17.
 */

public class ConstantApp {

    public static final String TAG = "CyberRobot";
    public static final UUID UUID_MOVEMENT = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb");
    public static final String DEVICE_ADDRESS = "DEVICE_ADDRESS";

    //byte array for the movements of the robot
    public static final byte[] forward = {0x31, 0x32, 0x44, 0x30, 0x53, 0x2d, 0x31};
    public static final byte[] backward = {0x31, 0x32, 0x44, 0x31, 0x53, 0x2d, 0x31};
    public static final byte[] left = {0x31, 0x32, 0x44, 0x32, 0x53, 0x2d, 0x31};
    public static final byte[] right = {0x31, 0x32, 0x44, 0x33, 0x53, 0x2d, 0x31};

    public static final int CODE_FORWARD = 0;
    public static final int CODE_BACKWARD = 1;
    public static final int CODE_LEFT = 2;
    public static final int CODE_RIGHT = 3;

    //action for the service
    public final static String ACTION_GATT_CONNECTED = "ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "EXTRA_DATA";

    //shared preferences
    public static final String SHARED_NAME = "cyber_prefs";
    public static final String SHARED_ROBOT_LEFT_UPPER = "robot_left_upper";
    public static final String SHARED_ROBOT_LEFT_LOWER = "robot_left_lower";
    public static final String SHARED_ROBOT_RIGHT_UPPER = "robot_right_upper";
    public static final String SHARED_ROBOT_RIGHT_LOWER = "robot_right_lower";
    public static final String SHARED_TARGET_UPPER = "target_upper";
    public static final String SHARED_TARGET_LOWER = "target_lower";

}
