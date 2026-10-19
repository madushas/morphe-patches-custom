package app.template.extension.pdb;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only access to the SQLite databases Pdb keeps in its own data directory.
 *
 * <p>Nothing is opened through the app's (obfuscated, per-release) Room classes: the
 * files are opened directly with {@link SQLiteDatabase#openDatabase}, read-only, so
 * this keeps working across Pdb releases and cannot corrupt anything.
 *
 * <p>Database file names and table names are <em>discovered</em> rather than
 * hard-coded: the databases directory is listed and probed for the table a tool
 * needs. On 2.134.6 that finds {@code pdb_app_go.db} (Pdb's own store) and the IM
 * store holding {@code messages}, {@code channels}, {@code users} and
 * {@code profile_view_record}.
 */
public final class PdbDatabase {

    private static final int MAX_CHARS = 6000;

    private PdbDatabase() {}

    /* ── discovery ── */

    public static File databasesDir(Context ctx) {
        return new File(ctx.getApplicationInfo().dataDir, "databases");
    }

    /** Every {@code *.db} file in the app's databases directory, WAL side files excluded. */
    public static List<File> dbFiles(Context ctx) {
        List<File> out = new ArrayList<>();
        File dir = databasesDir(ctx);
        File[] files = dir.listFiles();
        if (files == null) return out;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            String name = f.getName();
            if (!f.isFile()) continue;
            if (!name.endsWith(".db")) continue;
            out.add(f);
        }
        return out;
    }

    public static SQLiteDatabase open(File file) {
        return SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
    }

    public static List<String> tables(SQLiteDatabase db) {
        List<String> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
                    + " AND name NOT LIKE 'room_%' ORDER BY name",
                null);
            while (c.moveToNext()) out.add(c.getString(0));
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public static long rowCount(SQLiteDatabase db, String table) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM `" + table + "`", null);
            if (c.moveToFirst()) return c.getLong(0);
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return -1L;
    }

    /** The database file containing {@code table}, or {@code null}. */
    public static File dbWith(Context ctx, String table) {
        for (File f : dbFiles(ctx)) {
            SQLiteDatabase db = null;
            try {
                db = open(f);
                if (tables(db).contains(table)) return f;
            } catch (Throwable ignored) {
            } finally {
                if (db != null) db.close();
            }
        }
        return null;
    }

    /* ── tools ── */

    /** Database files, their tables and row counts. */
    public static String stats(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Databases in ").append(databasesDir(ctx).getAbsolutePath()).append('\n');
        List<File> files = dbFiles(ctx);
        if (files.isEmpty()) sb.append("  (none readable)\n");
        for (File f : files) {
            sb.append("\n").append(f.getName())
                .append("  (")
                .append(f.length() / 1024)
                .append(" KiB)\n");
            SQLiteDatabase db = null;
            try {
                db = open(f);
                for (String table : tables(db)) {
                    long count = rowCount(db, table);
                    sb.append("  ").append(table).append(": ")
                        .append(count < 0 ? "?" : String.valueOf(count)).append('\n');
                }
            } catch (Throwable t) {
                sb.append("  open failed: ").append(t).append('\n');
            } finally {
                if (db != null) db.close();
            }
        }
        return truncate(sb.toString());
    }

    /**
     * Most recent chat messages, newest first.
     *
     * @param query optional {@code LIKE} filter on the message text; empty for everything.
     */
    public static String chats(Context ctx, String query, int limit) {
        File file = dbWith(ctx, "messages");
        if (file == null) return "No chat database with a `messages` table was found.";
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = open(file);
            String filter = query == null || query.trim().isEmpty()
                ? "m.text IS NOT NULL AND m.text != ''"
                : "m.text LIKE ?";
            String[] args = query == null || query.trim().isEmpty()
                ? new String[]{String.valueOf(limit)}
                : new String[]{"%" + query.trim() + "%", String.valueOf(limit)};

            String sql = "SELECT m.createDate, m.channelId, m.creator, m.text,"
                + " (SELECT u.username FROM users u WHERE u.id = m.creator) AS uname,"
                + " m.isActive"
                + " FROM messages m WHERE " + filter
                + " ORDER BY m.createDate DESC LIMIT ?";

            c = db.rawQuery(sql, args);
            StringBuilder sb = new StringBuilder();
            sb.append(file.getName())
                .append(query == null || query.trim().isEmpty()
                    ? " — latest messages" : " — matches for \"" + query.trim() + "\"")
                .append('\n');
            int n = 0;
            while (c.moveToNext()) {
                n++;
                sb.append('\n')
                    .append('[').append(date(c.getLong(0))).append("] ")
                    .append(c.getString(4) != null ? "@" + c.getString(4) : shortId(c.getString(2)))
                    .append(c.getInt(5) == 0 ? " (deleted)" : "")
                    .append('\n')
                    .append(c.getString(3))
                    .append("\n  channel ").append(shortId(c.getString(1)))
                    .append("  sender ").append(shortId(c.getString(2)))
                    .append('\n');
            }
            if (n == 0) sb.append("\n(no messages)\n");
            else sb.append("\n").append(n).append(" message(s)\n");
            return truncate(sb.toString());
        } catch (Throwable t) {
            return "Chat read failed: " + t;
        } finally {
            if (c != null) c.close();
            if (db != null) db.close();
        }
    }

    /**
     * Local profile-view records — the app's own record of who opened a profile.
     * Not exposed anywhere in the stock UI beyond the server-side visitor page.
     */
    public static String visitorLog(Context ctx, int limit) {
        File file = dbWith(ctx, "profile_view_record");
        if (file == null) return "No database with a `profile_view_record` table was found.";
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = open(file);
            c = db.rawQuery(
                "SELECT r.created_at, r.uid, r.target_uid, r.type,"
                    + " (SELECT u.username FROM users u WHERE u.id = r.uid) AS uname"
                    + " FROM profile_view_record r ORDER BY r.created_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
            StringBuilder sb = new StringBuilder(file.getName() + " — profile views\n");
            Map<String, Integer> byViewer = new HashMap<>();
            int n = 0;
            while (c.moveToNext()) {
                n++;
                String uid = c.getString(1);
                sb.append('\n')
                    .append('[').append(date(c.getLong(0))).append("] ")
                    .append(c.getString(4) != null ? "@" + c.getString(4) : shortId(uid))
                    .append("  type ").append(c.getInt(3))
                    .append("\n  viewer ").append(shortId(uid))
                    .append("  profile ").append(shortId(c.getString(2)))
                    .append('\n');
                Integer seen = byViewer.get(uid);
                byViewer.put(uid, seen == null ? 1 : seen + 1);
            }
            if (n == 0) return "No profile views recorded locally.";
            sb.append('\n').append(n).append(" view event(s), ")
                .append(byViewer.size()).append(" distinct viewer(s)\n");
            sb.append("Most frequent:\n");
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(byViewer.entrySet());
            entries.sort((a, b) -> b.getValue() - a.getValue());
            for (int i = 0; i < Math.min(10, entries.size()); i++) {
                Map.Entry<String, Integer> e = entries.get(i);
                sb.append("  ").append(shortId(e.getKey())).append(" × ").append(e.getValue()).append('\n');
            }
            return truncate(sb.toString());
        } catch (Throwable t) {
            return "Visitor log read failed: " + t;
        } finally {
            if (c != null) c.close();
            if (db != null) db.close();
        }
    }

    /* ── cached user index ── */

    private static volatile Map<String, String> idToName;   // user id → username
    private static volatile java.util.Set<String> knownIds; // user id set
    private static volatile long cacheAt;
    private static final long CACHE_TTL = 10 * 60 * 1000L;

    /** Loads/refreshes the local users index (id → username). Safe to call often. */
    private static void loadCache(Context ctx) {
        long now = SystemClock.uptimeMillis();
        if (idToName != null && now - cacheAt < CACHE_TTL) return;
        synchronized (PdbDatabase.class) {
            if (idToName != null && now - cacheAt < CACHE_TTL) return;
            Map<String, String> map = new HashMap<>();
            java.util.Set<String> ids = new java.util.HashSet<>();
            File file = dbWith(ctx, "users");
            if (file != null) {
                SQLiteDatabase db = null;
                Cursor c = null;
                try {
                    db = open(file);
                    c = db.rawQuery("SELECT id, username FROM users", null);
                    while (c.moveToNext()) {
                        String id = c.getString(0);
                        String name = c.getString(1);
                        if (id != null) {
                            ids.add(id);
                            map.put(id, name);
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (c != null) c.close();
                    if (db != null) db.close();
                }
            }
            idToName = map;
            knownIds = ids;
            cacheAt = now;
        }
    }

    /** username for a local user id, or {@code null}. */
    public static String usernameFor(Context ctx, String id) {
        if (id == null) return null;
        loadCache(ctx);
        Map<String, String> map = idToName;
        return map == null ? null : map.get(id);
    }

    /** user id for a local username, or {@code null}. */
    public static String idForUsername(Context ctx, String username) {
        if (username == null) return null;
        loadCache(ctx);
        Map<String, String> map = idToName;
        if (map == null) return null;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (username.equalsIgnoreCase(e.getValue())) return e.getKey();
        }
        return null;
    }

    /** All locally-known user ids (used by the row-hook id detection). */
    public static java.util.Set<String> knownUserIds(Context ctx) {
        loadCache(ctx);
        java.util.Set<String> ids = knownIds;
        return ids == null ? new java.util.HashSet<String>() : ids;
    }

    /** Resolves loose input (id or @username) to a user id, or {@code null}. */
    public static String resolveUserId(Context ctx, String input) {
        if (input == null) return null;
        String value = input.trim().replaceFirst("^@", "").replaceFirst("^#", "");
        if (value.isEmpty()) return null;
        if (knownUserIds(ctx).contains(value)) return value;
        return idForUsername(ctx, value);
    }

    /* ── power-user reads ── */

    /**
     * Everything the local database knows about one user — id or @username.
     * Shows fields the stock UI hides behind premium or deep menus (pro state,
     * online visibility, last active, streak, your saved note).
     */
    public static String userById(Context ctx, String input) {
        String id = resolveUserId(ctx, input);
        if (id == null) return "Not found locally: \"" + input + "\"";
        File file = dbWith(ctx, "users");
        if (file == null) return "No local users table.";
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = open(file);
            c = db.rawQuery(
                "SELECT username, isPro, showOnline, lastActive, chatStreakProgress,"
                    + " note, locationDisplay, bio, gender, isMod, isActive"
                    + " FROM users WHERE id = ? LIMIT 1",
                new String[]{id});
            if (!c.moveToFirst()) return "Not found locally: \"" + input + "\"";
            StringBuilder sb = new StringBuilder();
            String name = c.getString(0);
            sb.append(name != null ? "@" + name : "(no name)").append("  #").append(id).append('\n');
            sb.append("Pro: ").append(c.getInt(1) != 0 ? "yes" : "no")
                .append("   Online visible: ").append(c.getInt(2) != 0 ? "yes" : "hidden")
                .append("   Active: ").append(c.getInt(10) != 0 ? "yes" : "deleted");
            if (c.getInt(9) != 0) sb.append("   Mod");
            sb.append('\n');
            long last = c.getLong(3);
            if (last > 0) sb.append("Last active: ").append(date(last)).append('\n');
            String streak = c.getString(4);
            if (streak != null && !streak.isEmpty()) sb.append("Streak: ").append(streak).append('\n');
            String location = c.getString(6);
            if (location != null && !location.isEmpty()) sb.append("Location: ").append(location).append('\n');
            String gender = c.getString(8);
            if (gender != null && !gender.isEmpty()) sb.append("Gender: ").append(gender).append('\n');
            String bio = c.getString(7);
            if (bio != null && !bio.isEmpty()) sb.append("Bio: ").append(bio, 0, Math.min(300, bio.length())).append('\n');
            String note = c.getString(5);
            if (note != null && !note.isEmpty()) sb.append("Your note: ").append(note).append('\n');
            sb.append("\nOpen profile: ").append(PdbNav.PROFILE_DETAIL).append(" (extra \"id\")");
            return sb.toString();
        } catch (Throwable t) {
            return "User lookup failed: " + t;
        } finally {
            if (c != null) c.close();
            if (db != null) db.close();
        }
    }

    /** Unsent message drafts per conversation (never surfaced by the stock app). */
    public static String drafts(Context ctx, int limit) {
        File file = dbWith(ctx, "channel_local_configs");
        if (file == null) return "No database with a `channel_local_configs` table was found.";
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = open(file);
            c = db.rawQuery(
                "SELECT l.channelId, l.draft, l.updatedAt,"
                    + " (SELECT ch.name FROM channels ch WHERE ch.id = l.channelId) AS cname"
                    + " FROM channel_local_configs l"
                    + " WHERE l.draft IS NOT NULL AND l.draft != ''"
                    + " ORDER BY l.updatedAt DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
            StringBuilder sb = new StringBuilder(file.getName() + " — unsent drafts\n");
            int n = 0;
            while (c.moveToNext()) {
                n++;
                String name = c.getString(3);
                sb.append('\n')
                    .append(name != null && !name.isEmpty() ? name : "channel " + shortId(c.getString(0)))
                    .append("  [").append(date(c.getLong(2))).append("]\n  ")
                    .append(c.getString(1)).append('\n');
            }
            if (n == 0) return "No unsent drafts stored locally.";
            sb.append('\n').append(n).append(" draft(s)");
            return truncate(sb.toString());
        } catch (Throwable t) {
            return "Draft read failed: " + t;
        } finally {
            if (c != null) c.close();
            if (db != null) db.close();
        }
    }

    /** Conversations ranked by chat streak (who is about to break one). */
    public static String streaks(Context ctx, int limit) {
        File file = dbWith(ctx, "users");
        if (file == null) return "No local users table.";
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = open(file);
            c = db.rawQuery(
                "SELECT id, username, chatStreakProgress, lastActive, isPro, showOnline"
                    + " FROM users WHERE chatStreakProgress IS NOT NULL AND chatStreakProgress != ''",
                null);
            List<long[]> byStreak = new ArrayList<>(); // streak value, row index
            List<String[]> rows = new ArrayList<>();
            while (c.moveToNext()) rows.add(new String[]{
                c.getString(0), c.getString(1), c.getString(2), String.valueOf(c.getLong(3)),
                String.valueOf(c.getInt(4)), String.valueOf(c.getInt(5))});
            for (int i = 0; i < rows.size(); i++) {
                byStreak.add(new long[]{streakValue(rows.get(i)[2]), i});
            }
            byStreak.sort((a, b) -> Long.compare(b[0], a[0]));
            StringBuilder sb = new StringBuilder("Streak radar (from the local users table)\n");
            int n = 0;
            for (long[] entry : byStreak) {
                if (n++ >= limit) break;
                String[] r = rows.get((int) entry[1]);
                sb.append('\n')
                    .append(r[1] != null ? "@" + r[1] : shortId(r[0]))
                    .append("  #").append(shortId(r[0]))
                    .append("\n  streak ").append(r[2])
                    .append(r[4].equals("1") ? "  PRO" : "")
                    .append("  online ").append(r[5].equals("1") ? "shown" : "hidden")
                    .append('\n');
                long last = 0;
                try {
                    last = Long.parseLong(r[3]);
                } catch (NumberFormatException ignored) {
                }
                if (last > 0) sb.append("  last active ").append(date(last)).append('\n');
            }
            if (n == 0) return "No chat streaks recorded locally yet.";
            return truncate(sb.toString());
        } catch (Throwable t) {
            return "Streak read failed: " + t;
        } finally {
            if (c != null) c.close();
            if (db != null) db.close();
        }
    }

    /** Aggregate statistics over the local message store. */
    public static String chatStats(Context ctx) {
        File file = dbWith(ctx, "messages");
        if (file == null) return "No chat database with a `messages` table was found.";
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = open(file);
            StringBuilder sb = new StringBuilder(file.getName() + " — chat statistics\n");
            c = db.rawQuery(
                "SELECT COUNT(*), COUNT(DISTINCT creator), COUNT(DISTINCT channelId),"
                    + " MIN(createDate), MAX(createDate) FROM messages", null);
            if (c.moveToFirst()) {
                sb.append("Messages: ").append(c.getLong(0))
                    .append("\nDistinct senders: ").append(c.getLong(1))
                    .append("\nConversations: ").append(c.getLong(2));
                if (c.getLong(3) > 0) sb.append("\nFirst: ").append(date(c.getLong(3)));
                if (c.getLong(4) > 0) sb.append("\nLatest: ").append(date(c.getLong(4)));
                sb.append('\n');
            }
            c.close();
            c = db.rawQuery(
                "SELECT creator, COUNT(*) AS n,"
                    + " (SELECT u.username FROM users u WHERE u.id = creator) AS uname"
                    + " FROM messages GROUP BY creator ORDER BY n DESC LIMIT 10", null);
            sb.append("\nTop partners\n");
            int n = 0;
            while (c.moveToNext()) {
                n++;
                String name = c.getString(2);
                sb.append("  ").append(name != null ? "@" + name : shortId(c.getString(0)))
                    .append(" — ").append(c.getLong(1)).append(" message(s)\n");
            }
            if (n == 0) sb.append("  (no messages)\n");
            return truncate(sb.toString());
        } catch (Throwable t) {
            return "Stats failed: " + t;
        } finally {
            if (c != null) c.close();
            if (db != null) db.close();
        }
    }

    /** Visitor digest: who looked at your profile, grouped per viewer with counts. */
    public static String visitorDigest(Context ctx, int limit) {
        File file = dbWith(ctx, "profile_view_record");
        if (file == null) return "No database with a `profile_view_record` table was found.";
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = open(file);
            c = db.rawQuery(
                "SELECT r.uid,"
                    + " (SELECT u.username FROM users u WHERE u.id = r.uid) AS uname,"
                    + " COUNT(*) AS n, MAX(r.created_at) AS last_at"
                    + " FROM profile_view_record r GROUP BY r.uid"
                    + " ORDER BY n DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
            StringBuilder sb = new StringBuilder("Visitor digest (grouped from local records)\n");
            int n = 0;
            while (c.moveToNext()) {
                n++;
                String name = c.getString(1);
                sb.append('\n')
                    .append(name != null ? "@" + name : shortId(c.getString(0)))
                    .append("  #").append(shortId(c.getString(0)))
                    .append("\n  ").append(c.getLong(2)).append(" view(s), last ")
                    .append(date(c.getLong(3))).append('\n');
            }
            if (n == 0) return "No profile views recorded locally.";
            sb.append('\n').append(n).append(" visitor(s)");
            return truncate(sb.toString());
        } catch (Throwable t) {
            return "Visitor digest failed: " + t;
        } finally {
            if (c != null) c.close();
            if (db != null) db.close();
        }
    }

    /** Numeric prefix of a streak text value ("4/7" → 4). */
    private static long streakValue(String raw) {
        if (raw == null) return 0;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isDigit(ch)) digits.append(ch);
            else if (digits.length() > 0) break;
        }
        try {
            return Long.parseLong(digits.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /* ── formatting ── */

    static String shortId(String id) {
        if (id == null) return "?";
        return id.length() <= 10 ? id : id.substring(0, 8) + "…";
    }

    static String date(long value) {
        long millis = value;
        if (millis > 0 && millis < 100000000000L) millis *= 1000L; // seconds → millis
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(millis));
        } catch (Throwable ignored) {
            return String.valueOf(value);
        }
    }

    static String truncate(String text) {
        if (text.length() <= MAX_CHARS) return text;
        return text.substring(0, MAX_CHARS) + "\n… (truncated)";
    }
}
