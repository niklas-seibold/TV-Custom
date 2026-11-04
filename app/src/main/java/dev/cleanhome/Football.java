package dev.cleanhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Today's Champions League / World Cup / Euros matches (ESPN public scoreboard) + where they air in Germany. */
final class Football {
    static final class Match {
        String home, away, comp, channel;
        Date kickoff;
        boolean live;
        String time() { return new SimpleDateFormat("HH:mm", Locale.GERMANY).format(kickoff); }
    }

    private static final String[][] LEAGUES = {
            {"uefa.champions", "Champions League"},
            {"fifa.world", "World Cup"},
            {"uefa.euro", "Euros"},
    };

    private Football() {}

    /** Blocking; call off the UI thread. */
    static List<Match> today() {
        List<Match> out = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        for (String[] lg : LEAGUES) {
            try {
                byte[] b = MainActivity.readUrl("https://site.api.espn.com/apis/site/v2/sports/soccer/" + lg[0] + "/scoreboard");
                JSONArray events = new JSONObject(new String(b, "UTF-8")).optJSONArray("events");
                if (events == null) continue;
                for (int i = 0; i < events.length(); i++) {
                    JSONObject e = events.getJSONObject(i);
                    Date when = parse(e.optString("date"));
                    if (when == null) continue;
                    Calendar k = Calendar.getInstance();
                    k.setTime(when);
                    if (k.get(Calendar.YEAR) != now.get(Calendar.YEAR) || k.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR)) continue;
                    String state = e.optJSONObject("status").optJSONObject("type").optString("state");
                    if ("post".equals(state)) continue;
                    Match m = new Match();
                    m.kickoff = when;
                    m.live = "in".equals(state);
                    m.comp = lg[1];
                    JSONArray comps = e.getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors");
                    for (int c = 0; c < comps.length(); c++) {
                        JSONObject t = comps.getJSONObject(c);
                        String name = t.getJSONObject("team").optString("shortDisplayName", t.getJSONObject("team").optString("displayName"));
                        if ("home".equals(t.optString("homeAway"))) m.home = name; else m.away = name;
                    }
                    m.channel = channel(lg[0], k);
                    out.add(m);
                }
            } catch (Exception ignored) {
            }
        }
        java.util.Collections.sort(out, (a, b) -> a.kickoff.compareTo(b.kickoff));
        return out;
    }

    /** German TV rights (rule of thumb, 2024–2027 cycle). */
    static String channel(String league, Calendar k) {
        switch (league) {
            case "uefa.champions":
                return k.get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY ? "DAZN · top game on Prime Video" : "DAZN";
            case "fifa.world":
                return "MagentaTV · selected games on ARD/ZDF";
            default:
                return "ARD/ZDF · MagentaTV";
        }
    }

    private static Date parse(String s) {
        for (String f : new String[]{"yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"}) {
            try {
                SimpleDateFormat p = new SimpleDateFormat(f, Locale.US);
                p.setTimeZone(TimeZone.getTimeZone("UTC"));
                return p.parse(s);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
