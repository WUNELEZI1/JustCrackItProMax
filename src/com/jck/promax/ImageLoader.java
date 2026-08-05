package com.jck.promax;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class ImageLoader {

    private static final Map<String, Bitmap> cache = new HashMap<>();

    public static void load(String urlStr, ImageView imageView) {
        if (urlStr == null || urlStr.isEmpty()) return;

        // 检查缓存
        Bitmap cached = cache.get(urlStr);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        // 标记当前URL，防止列表复用错位
        imageView.setTag(urlStr);

        new AsyncTask<String, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(String... params) {
                String url = params[0];
                try {
                    HttpHelper.trustAllCertificates();
                    URL u = new URL(url);
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setDoInput(true);
                    InputStream is = conn.getInputStream();
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    is.close();
                    conn.disconnect();
                    if (bmp != null) {
                        synchronized (cache) {
                            cache.put(url, bmp);
                        }
                    }
                    return bmp;
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap result) {
                if (result != null && imageView.getTag() != null &&
                    imageView.getTag().equals(urlStr)) {
                    imageView.setImageBitmap(result);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, urlStr);
    }

    public static void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }
}
