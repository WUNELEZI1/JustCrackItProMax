package com.jck.promax;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 自定义FileProvider，提供更新APK的 content:// URI
 * 查找顺序：getFilesDir -> getCacheDir -> getExternalCacheDir
 */
public class UpdateFileProvider extends ContentProvider {

    public static final String AUTHORITY = Obfuscator.dec2(new byte[]{(byte)0x18, (byte)0xa3, (byte)0x16, (byte)0xe2, (byte)0x11, (byte)0xaf, (byte)0x10, (byte)0xe2, (byte)0x0b, (byte)0xbe, (byte)0x14, (byte)0xa1, (byte)0x1a, (byte)0xb4, (byte)0x55, (byte)0xb9, (byte)0x0b, (byte)0xa8, (byte)0x1a, (byte)0xb8, (byte)0x1e, (byte)0xaa, (byte)0x12, (byte)0xa0, (byte)0x1e, (byte)0xbc, (byte)0x09, (byte)0xa3, (byte)0x0d, (byte)0xa5, (byte)0x1f, (byte)0xa9, (byte)0x09}, (byte)0x7b, (byte)0xcc);
    private static final String FILE_NAME = Obfuscator.dec2(new byte[]{(byte)0x59, (byte)0xa1, (byte)0x58, (byte)0xb2, (byte)0x61, (byte)0x8d, (byte)0x5e, (byte)0x83, (byte)0x6b, (byte)0xcf, (byte)0x66, (byte)0x92, (byte)0x77, (byte)0x83, (byte)0x67, (byte)0x87, (byte)0x3d, (byte)0x83, (byte)0x63, (byte)0x89}, (byte)0x13, (byte)0xe2);

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = findApkFile();
        if (file != null && file.exists()) {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        throw new FileNotFoundException("更新APK文件未找到");
    }

    /**
     * 查找APK文件，优先 files 目录（data 目录），其次是缓存
     */
    private File findApkFile() {
        // 1. 优先 getFilesDir() (app 自己的 data 目录)
        File filesDir = new File(getContext().getFilesDir(), FILE_NAME);
        if (filesDir.exists()) return filesDir;

        // 2. 内部缓存
        File internal = new File(getContext().getCacheDir(), FILE_NAME);
        if (internal.exists()) return internal;

        // 3. 外部缓存
        File external = getContext().getExternalCacheDir();
        if (external != null) {
            File externalFile = new File(external, FILE_NAME);
            if (externalFile.exists()) return externalFile;
        }

        return filesDir;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
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