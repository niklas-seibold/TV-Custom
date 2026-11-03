package dev.cleanhome;

import android.content.Context;
import android.provider.Settings;

import com.tananaev.adblib.AdbBase64;
import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbCrypto;
import com.tananaev.adblib.AdbStream;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Finishes setup on the TV itself: connects to the TV's own ADB (enabled via
 * Developer Options) and runs the few commands a computer would otherwise run.
 * The TV shows an "Allow debugging?" prompt the first time.
 */
final class SelfSetup {
    static final String SERVICE = "dev.cleanhome/dev.cleanhome.HomeGuardService";
    private static final String[] DEBLOAT = {
            "com.amazon.kso", "com.amazon.device.crashmanager", "com.amazon.client.metrics"};

    private SelfSetup() {}

    static boolean guardEnabled(Context c) {
        String v = Settings.Secure.getString(c.getContentResolver(), "enabled_accessibility_services");
        return v != null && v.contains(SERVICE);
    }

    /** Blocking; call from a background thread. Returns null on success, else an error message. */
    static String run(Context c) {
        String cur = Settings.Secure.getString(c.getContentResolver(), "enabled_accessibility_services");
        String services = (cur == null || cur.isEmpty() || "null".equals(cur)) ? SERVICE
                : (cur.contains(SERVICE) ? cur : cur + ":" + SERVICE);

        StringBuilder cmd = new StringBuilder();
        cmd.append("settings put secure enabled_accessibility_services '").append(services).append("';");
        cmd.append("settings put secure accessibility_enabled 1;");
        cmd.append("settings put secure limit_ad_tracking 1;");
        for (String p : DEBLOAT) cmd.append("pm disable-user --user 0 ").append(p).append(" >/dev/null 2>&1;");
        cmd.append("echo CLEANHOME_OK");

        Socket socket = null;
        AdbConnection conn = null;
        try {
            AdbCrypto crypto = loadOrCreateKey(c);
            socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
            conn = AdbConnection.create(socket, crypto);
            conn.connect(); // waits until "Allow" is pressed on the TV
            AdbStream stream = conn.open("shell:" + cmd);
            StringBuilder out = new StringBuilder();
            try {
                while (!stream.isClosed()) out.append(new String(stream.read(), "UTF-8"));
            } catch (Exception endOfStream) {
                // stream closed by adbd after the command finished
            }
            if (out.indexOf("CLEANHOME_OK") >= 0 || guardEnabled(c)) return null;
            return "Setup commands didn't confirm. Output: " + out;
        } catch (java.net.ConnectException e) {
            return "Couldn't reach ADB. Turn on Settings → My Fire TV → Developer Options → ADB debugging, then try again.";
        } catch (Exception e) {
            return "Setup failed: " + e;
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
    }

    private static AdbCrypto loadOrCreateKey(Context c) throws Exception {
        AdbBase64 b64 = new AdbBase64() {
            @Override
            public String encodeToString(byte[] data) {
                return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
            }
        };
        File priv = new File(c.getFilesDir(), "adbkey");
        File pub = new File(c.getFilesDir(), "adbkey.pub");
        if (priv.exists() && pub.exists()) {
            try {
                return AdbCrypto.loadAdbKeyPair(b64, priv, pub);
            } catch (Exception ignored) {
                // fall through and regenerate
            }
        }
        AdbCrypto crypto = AdbCrypto.generateAdbKeyPair(b64);
        crypto.saveAdbKeyPair(priv, pub);
        return crypto;
    }
}
