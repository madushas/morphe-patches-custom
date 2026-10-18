package app.template.extension.pdb;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

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
