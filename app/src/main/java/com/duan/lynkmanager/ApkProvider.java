package com.duan.lynkmanager;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 极简文件提供者：只把 uploads / export 目录下的 .apk 暴露给系统安装器。
 *
 * 为什么不用 androidx 的 FileProvider：本项目只依赖 zxing，为了一个安装入口
 * 引入整个 androidx.core 不划算，这里自己实现 openFile 即可。
 *
 * URI 形如 content://com.duan.lynkmanager.apk/apk?dir=uploads&name=xxx.apk
 * 只允许这两个目录、只允许 .apk，其它一律拒绝，避免变成任意文件读取的洞。
 */
public class ApkProvider extends ContentProvider {

    public static final String AUTHORITY = "com.duan.lynkmanager.apk";
    public static final String MIME_APK = "application/vnd.android.package-archive";

    /** 允许暴露的两个子目录名。 */
    private static final String DIR_UPLOADS = "uploads";
    private static final String DIR_EXPORT = "export";

    @Override
    public boolean onCreate() {
        return true;
    }

    /** 把 URI 解析成真实文件，路径越界或后缀不对就抛异常。 */
    private File resolve(Uri uri) throws FileNotFoundException {
        String dir = uri.getQueryParameter("dir");
        String name = uri.getQueryParameter("name");
        if (dir == null || name == null) {
            throw new FileNotFoundException("缺少 dir/name");
        }
        if (!DIR_UPLOADS.equals(dir) && !DIR_EXPORT.equals(dir)) {
            throw new FileNotFoundException("目录不允许：" + dir);
        }
        if (!name.toLowerCase(java.util.Locale.US).endsWith(".apk")) {
            throw new FileNotFoundException("只允许 .apk：" + name);
        }
        // 文件名不允许带路径分隔符，防止 ../ 穿越
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new FileNotFoundException("非法文件名");
        }
        File base = getContext() == null ? null : getContext().getExternalFilesDir(null);
        if (base == null) {
            throw new FileNotFoundException("存储不可用");
        }
        File f = new File(new File(base, dir), name);
        if (!f.isFile()) {
            throw new FileNotFoundException(name);
        }
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return MIME_APK;
    }

    /** 安装器会查这两个列来显示文件名与大小。 */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f;
        try {
            f = resolve(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
                row[i] = f.getName();
            } else if (OpenableColumns.SIZE.equals(cols[i])) {
                row[i] = f.length();
            } else {
                row[i] = null;
            }
        }
        c.addRow(row);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
