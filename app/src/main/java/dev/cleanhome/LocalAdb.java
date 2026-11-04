package dev.cleanhome;

import android.content.Context;

import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbCrypto;
import com.tananaev.adblib.AdbStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Runs shell commands through the TV's own ADB, trying IPv6 and IPv4 addresses. */
final class LocalAdb {
    static volatile String status = "not tried";
    private static volatile String workingHost;

    private LocalAdb() {}

    static File dir(Context c) {
        return c.getExternalFilesDir(null);
    }

    static boolean helperAlive(Context c) {
        File f = new File(dir(c), ".alive");
        return f.exists() && System.currentTimeMillis() - f.lastModified() < 8000;
    }

    /** Make sure tools/home.sh is running. Blocking. */
    static void ensureHelper(Context c) {
        if (helperAlive(c)) {
            status = "running";
            return;
        }
        try {
            File script = new File(dir(c), "home.sh");
            try (InputStream in = c.getAssets().open("home.sh"); FileOutputStream out = new FileOutputStream(script)) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
            }
            String r = exec(c, "nohup sh " + script.getAbsolutePath() + " >/dev/null 2>&1 & echo STARTED");
            status = r != null && r.contains("STARTED") ? "running" : "off (" + status + ")";
        } catch (Exception e) {
            status = "off (" + e.getMessage() + ")";
        }
    }

    /** Returns command output, or null if no address accepted the connection. */
    static String exec(Context c, String cmd) {
        AdbCrypto crypto;
        try {
            crypto = SelfSetup.loadOrCreateKey(c);
        } catch (Exception e) {
            status = "key error";
            return null;
        }
        List<String> hosts = new ArrayList<>();
        if (workingHost != null) hosts.add(workingHost);
        hosts.addAll(hosts());
        StringBuilder diag = new StringBuilder();
        for (String h : hosts) {
            Socket s = null;
            AdbConnection conn = null;
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(h, 5555), 1500);
                conn = AdbConnection.create(s, crypto);
                conn.connect();
                AdbStream st = conn.open("shell:" + cmd);
                StringBuilder out = new StringBuilder();
                try {
                    while (!st.isClosed()) out.append(new String(st.read(), "UTF-8"));
                } catch (Exception endOfStream) {
                }
                workingHost = h;
                status = "ok via " + h;
                return out.toString();
            } catch (Exception e) {
                diag.append(h).append(": ").append(e.getClass().getSimpleName()).append("; ");
            } finally {
                try { if (conn != null) conn.close(); } catch (Exception ignored) {}
                try { if (s != null) s.close(); } catch (Exception ignored) {}
            }
        }
        status = diag.toString();
        return null;
    }

    private static List<String> hosts() {
        List<String> v6 = new ArrayList<>(), v4 = new ArrayList<>();
        v6.add("::1");
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet6Address) {
                        String h = a.getHostAddress(); // includes %scope for link-local
                        v6.add(h);
                    } else if (a instanceof Inet4Address) {
                        v4.add(a.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        v6.addAll(v4);
        v6.add("127.0.0.1");
        return v6;
    }
}
