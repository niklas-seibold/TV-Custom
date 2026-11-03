package dev.cleanhome;

import android.content.Context;
import android.provider.Settings;

import com.tananaev.adblib.AdbBase64;
import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbCrypto;
import com.tananaev.adblib.AdbStream;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        if (HomeGuardService.running) return true;
        String v = Settings.Secure.getString(c.getContentResolver(), "enabled_accessibility_services");
        return v != null && (v.contains(SERVICE) || v.contains("dev.cleanhome/.HomeGuardService"));
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

        StringBuilder diag = new StringBuilder();
        AdbCrypto crypto;
        try {
            crypto = loadOrCreateKey(c);
        } catch (Exception e) {
            return "Key error: " + e;
        }
        for (String host : hosts()) {
            String probe = probe(host);
            diag.append(host).append(": ").append(probe);
            if (!probe.startsWith("AUTH") && !probe.startsWith("CNXN")) {
                diag.append('\n');
                continue;
            }
            for (int attempt = 0; attempt < 2; attempt++) {
                String r = runOn(host, crypto, cmd.toString(), c);
                if (r == null) return null;
                diag.append(" → ").append(r);
            }
            diag.append('\n');
        }
        return "Couldn't finish setup. Send a photo of this:\n\n" + diag;
    }

    /** Own network addresses first (Fire OS may block 127.0.0.1), then loopback. */
    private static List<String> hosts() {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address) out.add(a.getHostAddress());
                }
            }
        } catch (Exception ignored) {
        }
        out.add("127.0.0.1");
        return out;
    }

    /** Sends a raw ADB CNXN and reports what adbd answers (AUTH / CNXN / closed / refused). */
    private static String probe(String host) {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, 5555), 2000);
            s.setSoTimeout(3000);
            byte[] payload = "host::\0".getBytes("UTF-8");
            int sum = 0;
            for (byte b : payload) sum += (b & 0xff);
            int cmd = 0x4e584e43; // CNXN
            ByteBuffer bb = ByteBuffer.allocate(24 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(cmd).putInt(0x01000000).putInt(4096).putInt(payload.length).putInt(sum).putInt(~cmd).put(payload);
            OutputStream os = s.getOutputStream();
            os.write(bb.array());
            os.flush();
            byte[] hdr = new byte[24];
            InputStream is = s.getInputStream();
            int n = 0;
            while (n < 24) {
                int r = is.read(hdr, n, 24 - n);
                if (r < 0) break;
                n += r;
            }
            if (n < 4) return "closed by TV (" + n + " bytes)";
            return new String(hdr, 0, 4, "US-ASCII");
        } catch (java.net.ConnectException e) {
            return "refused";
        } catch (java.net.SocketTimeoutException e) {
            return "no answer";
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    private static String runOn(String host, AdbCrypto crypto, String cmd, Context c) {
        Socket socket = null;
        AdbConnection conn = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, 5555), 3000);
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
            return "no confirm: " + out;
        } catch (Exception e) {
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
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
