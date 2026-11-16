package com.duan.lynkmanager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 文件列表的一项。
 *
 * 只包一层 File 加缓存字段：车机上目录可能有几百个文件，
 * 每次 getView 都调 length() / lastModified() 会反复打系统调用。
 */
public class FileEntry {

    public final File file;
    /** 目录置 null（不统计大小，递归算会卡界面）。 */
    public final long size;
    public final long lastModified;
    public final boolean isDir;

    public FileEntry(File f) {
        this.file = f;
        this.isDir = f.isDirectory();
        this.size = isDir ? -1 : f.length();
        this.lastModified = f.lastModified();
    }

    public String name() {
        String n = file.getName();
        return n.isEmpty() ? file.getAbsolutePath() : n;
    }

    public String ext() {
        String n = name();
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(i + 1).toLowerCase(Locale.US) : "";
    }

    /** 人类可读大小。 */
    public static String sizeText(long bytes) {
        if (bytes < 0) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1048576) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1073741824L) {
            return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
        }
        return String.format(Locale.US, "%.2f GB", bytes / 1073741824.0);
    }

    public static String dateText(long ms) {
        if (ms <= 0) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(ms));
    }

    /** 按扩展名归类，决定显示什么图标和颜色。 */
    public int kind() {
        if (isDir) {
            return KIND_DIR;
        }
        String e = ext();
        switch (e) {
            case "apk":
                return KIND_APK;
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "webp":
            case "bmp":
                return KIND_IMAGE;
            case "mp4":
            case "mkv":
            case "avi":
            case "mov":
            case "3gp":
                return KIND_VIDEO;
            case "mp3":
            case "wav":
            case "flac":
            case "aac":
            case "m4a":
            case "ogg":
                return KIND_AUDIO;
            case "txt":
            case "log":
            case "md":
            case "json":
            case "xml":
            case "ini":
            case "conf":
                return KIND_TEXT;
            case "zip":
            case "rar":
            case "7z":
            case "gz":
            case "tar":
                return KIND_ARCHIVE;
            default:
                return KIND_OTHER;
        }
    }

    public static final int KIND_DIR = 0;
    public static final int KIND_APK = 1;
    public static final int KIND_IMAGE = 2;
    public static final int KIND_VIDEO = 3;
    public static final int KIND_AUDIO = 4;
    public static final int KIND_TEXT = 5;
    public static final int KIND_ARCHIVE = 6;
    public static final int KIND_OTHER = 7;

    /** 图标上显示的短标签。 */
    public String badge() {
        switch (kind()) {
            case KIND_DIR:
                return "DIR";
            case KIND_APK:
                return "APK";
            case KIND_IMAGE:
                return "IMG";
            case KIND_VIDEO:
                return "VID";
            case KIND_AUDIO:
                return "SND";
            case KIND_TEXT:
                return "TXT";
            case KIND_ARCHIVE:
                return "ZIP";
            default:
                String e = ext();
                return e.isEmpty() ? "?" : e.substring(0, Math.min(3, e.length())).toUpperCase(Locale.US);
        }
    }
}
