package com.techhurts.hisense_remote;

import android.content.Context;
import com.kunal52.AndroidRemoteContext;
import com.kunal52.AndroidRemoteTv;
import com.kunal52.AndroidTvListener;
import com.kunal52.remote.Remotemessage;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RemoteCommandExecutor {

    private static AndroidRemoteTv sAndroidRemoteTv;
    private static String sCurrentIp;

    public static void sendKey(Context context, String ip, String protocol, String key) {
        if ("android_tv".equalsIgnoreCase(protocol)) {
            sendAndroidTvKey(context, ip, key);
        } else {
            sendLegacyKey(ip, protocol, key);
        }
    }

    private static void sendAndroidTvKey(Context context, String ip, String key) {
        boolean isAppLaunch = key.startsWith(RemoteButtons.APP_PREFIX);
        Remotemessage.RemoteKeyCode code = isAppLaunch ? null : mapKeyToAndroidTv(key);
        if (code == null && !isAppLaunch) return;

        try {
            File keystore = new File(context.getFilesDir(), "androidtv.keystore");
            AndroidRemoteContext.getInstance().setKeyStoreFile(keystore);

            if (sAndroidRemoteTv != null && !ip.equals(sCurrentIp)) {
                sAndroidRemoteTv.disconnect();
                sAndroidRemoteTv = null;
            }
            sCurrentIp = ip;

            if (sAndroidRemoteTv == null) {
                sAndroidRemoteTv = new AndroidRemoteTv();
            }

            if (!sAndroidRemoteTv.isConnected()) {
                final CountDownLatch latch = new CountDownLatch(1);
                final boolean[] connectSuccess = new boolean[1];
                sAndroidRemoteTv.connect(ip, new AndroidTvListener() {
                    @Override public void onSessionCreated() {}
                    @Override public void onSecretRequested() { latch.countDown(); }
                    @Override public void onPaired() {}
                    @Override public void onConnectingToRemote() {}
                    @Override public void onConnected() {
                        connectSuccess[0] = true;
                        latch.countDown();
                    }
                    @Override public void onDisconnect() { latch.countDown(); }
                    @Override public void onError(String error) { latch.countDown(); }
                });

                latch.await(3000, TimeUnit.MILLISECONDS);
                if (!connectSuccess[0]) {
                    return;
                }
            }

            if (isAppLaunch) {
                sAndroidRemoteTv.sendAppLink(key.substring(RemoteButtons.APP_PREFIX.length()));
            } else {
                sAndroidRemoteTv.sendCommand(code, Remotemessage.RemoteDirection.SHORT);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (sAndroidRemoteTv != null) {
                sAndroidRemoteTv.disconnect();
                sAndroidRemoteTv = null;
            }
        }
    }

    private static Remotemessage.RemoteKeyCode mapKeyToAndroidTv(String key) {
        switch (key) {
            case "KEY_POWER": return Remotemessage.RemoteKeyCode.KEYCODE_POWER;
            case "KEY_INPUT": return Remotemessage.RemoteKeyCode.KEYCODE_TV_INPUT;
            case "KEY_UP": return Remotemessage.RemoteKeyCode.KEYCODE_DPAD_UP;
            case "KEY_DOWN": return Remotemessage.RemoteKeyCode.KEYCODE_DPAD_DOWN;
            case "KEY_LEFT": return Remotemessage.RemoteKeyCode.KEYCODE_DPAD_LEFT;
            case "KEY_RIGHT": return Remotemessage.RemoteKeyCode.KEYCODE_DPAD_RIGHT;
            case "KEY_OK": return Remotemessage.RemoteKeyCode.KEYCODE_DPAD_CENTER;
            case "KEY_BACK": return Remotemessage.RemoteKeyCode.KEYCODE_BACK;
            case "KEY_HOME": return Remotemessage.RemoteKeyCode.KEYCODE_HOME;
            case "KEY_VOL_UP": return Remotemessage.RemoteKeyCode.KEYCODE_VOLUME_UP;
            case "KEY_VOL_DOWN": return Remotemessage.RemoteKeyCode.KEYCODE_VOLUME_DOWN;
            case "KEY_MUTE": return Remotemessage.RemoteKeyCode.KEYCODE_VOLUME_MUTE;
            case "KEY_PLAY": return Remotemessage.RemoteKeyCode.KEYCODE_MEDIA_PLAY;
            case "KEY_PAUSE": return Remotemessage.RemoteKeyCode.KEYCODE_MEDIA_PAUSE;
            case "KEY_STOP": return Remotemessage.RemoteKeyCode.KEYCODE_MEDIA_STOP;
            case "KEY_REWIND": return Remotemessage.RemoteKeyCode.KEYCODE_MEDIA_REWIND;
            case "KEY_FORWARD": return Remotemessage.RemoteKeyCode.KEYCODE_MEDIA_FAST_FORWARD;
            case "KEY_MENU": return Remotemessage.RemoteKeyCode.KEYCODE_MENU;
            case "KEY_GUIDE": return Remotemessage.RemoteKeyCode.KEYCODE_GUIDE;
            case "KEY_SETTINGS": return Remotemessage.RemoteKeyCode.KEYCODE_SETTINGS;
            case "KEY_TV": return Remotemessage.RemoteKeyCode.KEYCODE_TV;
            case "KEY_HDMI_1": return Remotemessage.RemoteKeyCode.KEYCODE_TV_INPUT_HDMI_1;
            case "KEY_HDMI_2": return Remotemessage.RemoteKeyCode.KEYCODE_TV_INPUT_HDMI_2;
            case "KEY_HDMI_3": return Remotemessage.RemoteKeyCode.KEYCODE_TV_INPUT_HDMI_3;
            case "KEY_HDMI_4": return Remotemessage.RemoteKeyCode.KEYCODE_TV_INPUT_HDMI_4;
            case "KEY_MIC": return Remotemessage.RemoteKeyCode.KEYCODE_SEARCH;
            case "KEY_NETFLIX": return Remotemessage.RemoteKeyCode.KEYCODE_BUTTON_1;
            case "KEY_YOUTUBE": return Remotemessage.RemoteKeyCode.KEYCODE_BUTTON_2;
            default: return null;
        }
    }

    public static boolean testConnection(Context context, String ip, String protocol) {
        if ("android_tv".equalsIgnoreCase(protocol)) {
            File keystore = new File(context.getFilesDir(), "androidtv.keystore");
            if (!keystore.exists()) {
                // If not paired, we consider testConnection true if we can connect to port 6466
                return testSocket(ip, 6466);
            }
            return testSocket(ip, 6466);
        }
        
        int port = 8060;
        if ("ascii".equalsIgnoreCase(protocol)) port = 8088;
        else if ("hex".equalsIgnoreCase(protocol)) port = 5000;
        return testSocket(ip, port);
    }

    private static boolean testSocket(String ip, int port) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), 1500);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static void sendLegacyKey(String ip, String protocol, String key) {
        if ("roku".equalsIgnoreCase(protocol)) {
            sendRokuKey(ip, key);
        } else if ("ascii".equalsIgnoreCase(protocol)) {
            sendAsciiKey(ip, key);
        } else if ("hex".equalsIgnoreCase(protocol)) {
            sendHexKey(ip, key);
        }
    }

    private static void sendRokuKey(String ip, String key) {
        String rokuKey = mapKeyToRoku(key);
        if (rokuKey == null) return;
        
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://" + ip + ":8060/keypress/" + rokuKey);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            conn.getResponseCode();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void sendAsciiKey(String ip, String key) {
        String asciiCmd = mapKeyToAscii(key);
        if (asciiCmd == null) return;

        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, 8088), 1500);
            socket.setSoTimeout(1500);
            OutputStream os = socket.getOutputStream();
            String payload = asciiCmd + "\r\n";
            os.write(payload.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static void sendHexKey(String ip, String key) {
        byte[] hexCmd = mapKeyToHex(key);
        if (hexCmd == null) return;

        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, 5000), 1500);
            socket.setSoTimeout(1500);
            OutputStream os = socket.getOutputStream();
            os.write(hexCmd);
            os.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static String mapKeyToRoku(String key) {
        switch (key) {
            case "KEY_POWER": return "Power";
            case "KEY_INPUT": return "InputNext";
            case "KEY_UP": return "Up";
            case "KEY_DOWN": return "Down";
            case "KEY_LEFT": return "Left";
            case "KEY_RIGHT": return "Right";
            case "KEY_OK": return "Select";
            case "KEY_BACK": return "Back";
            case "KEY_HOME": return "Home";
            case "KEY_VOL_UP": return "VolumeUp";
            case "KEY_VOL_DOWN": return "VolumeDown";
            case "KEY_MUTE": return "VolumeMute";
            default: return null;
        }
    }

    private static String mapKeyToAscii(String key) {
        switch (key) {
            case "KEY_POWER": return "KEY:POWER";
            case "KEY_INPUT": return "KEY:INPUT";
            case "KEY_UP": return "KEY:UP";
            case "KEY_DOWN": return "KEY:DOWN";
            case "KEY_LEFT": return "KEY:LEFT";
            case "KEY_RIGHT": return "KEY:RIGHT";
            case "KEY_OK": return "KEY:OK";
            case "KEY_BACK": return "KEY:BACK";
            case "KEY_HOME": return "KEY:HOME";
            case "KEY_VOL_UP": return "KEY:VOLUME_UP";
            case "KEY_VOL_DOWN": return "KEY:VOLUME_DOWN";
            case "KEY_MUTE": return "KEY:MUTE";
            default: return null;
        }
    }

    private static byte[] mapKeyToHex(String key) {
        switch (key) {
            case "KEY_POWER":
                return new byte[]{(byte)0xDD, (byte)0xFF, 0x00, 0x06, 0x01, 0x02, 0x00, 0x01, 0x00, 0x04};
            case "KEY_UP":
                return new byte[]{(byte)0xDD, (byte)0xFF, 0x00, 0x06, 0x01, 0x04, 0x00, 0x01, 0x01, 0x03};
            case "KEY_DOWN":
                return new byte[]{(byte)0xDD, (byte)0xFF, 0x00, 0x06, 0x01, 0x04, 0x00, 0x01, 0x02, 0x00};
            case "KEY_LEFT":
                return new byte[]{(byte)0xDD, (byte)0xFF, 0x00, 0x06, 0x01, 0x04, 0x00, 0x01, 0x03, 0x01};
            case "KEY_RIGHT":
                return new byte[]{(byte)0xDD, (byte)0xFF, 0x00, 0x06, 0x01, 0x04, 0x00, 0x01, 0x04, 0x06};
            case "KEY_OK":
                return new byte[]{(byte)0xDD, (byte)0xFF, 0x00, 0x06, 0x01, 0x04, 0x00, 0x01, 0x05, 0x07};
            case "KEY_BACK":
                return new byte[]{(byte)0xDD, (byte)0xFF, 0x00, 0x06, 0x01, 0x04, 0x00, 0x01, 0x06, 0x04};
            case "KEY_HOME":
                return new byte[]{(byte)0xDD, (byte)0xFF, 0x00, 0x06, 0x01, 0x04, 0x00, 0x01, 0x07, 0x05};
            case "KEY_VOL_UP":
                return new byte[]{(byte)0xDD, (byte)0xFF, 0x00, 0x06, 0x01, 0x03, 0x00, 0x01, 0x01, 0x04};
            case "KEY_VOL_DOWN":
                return new byte[]{(byte)0xDD, (byte)0xFF, 0x00, 0x06, 0x01, 0x03, 0x00, 0x01, 0x02, 0x07};
            case "KEY_MUTE":
                return new byte[]{(byte)0xDD, (byte)0xFF, 0x00, 0x06, 0x01, 0x03, 0x00, 0x01, 0x03, 0x06};
            case "KEY_INPUT":
                return new byte[]{(byte)0xDD, (byte)0xFF, 0x00, 0x06, 0x01, 0x05, 0x00, 0x01, 0x00, 0x03};
            default:
                return null;
        }
    }
}
