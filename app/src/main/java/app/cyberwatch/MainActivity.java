package app.cyberwatch;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.BatteryManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.Build;
import android.content.Context;
import android.content.IntentFilter;
import android.app.ActivityManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.*;
import android.widget.Toast;
import java.io.OutputStream;
public class MainActivity extends Activity {
    private WebView webView; private ValueCallback<Uri[]> fileCallback;
    public class Saver {
        @JavascriptInterface public void saveBase64(String name, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, name.endsWith(".webm") ? "video/webm" : "application/octet-stream");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(data); os.close();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Saved to Downloads: " + name, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }

    public class SystemBridge {
        @JavascriptInterface public String getSystemInfo() {
            try {
                Intent batt = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int level = batt != null ? batt.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
                int scale = batt != null ? batt.getIntExtra(BatteryManager.EXTRA_SCALE, 100) : 100;
                int status = batt != null ? batt.getIntExtra(BatteryManager.EXTRA_STATUS, -1) : -1;
                int pct = scale > 0 ? (int)(100f * level / scale) : -1;
                boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
                StatFs fs = new StatFs(getFilesDir().getAbsolutePath());
                long free = fs.getAvailableBytes(); long total = fs.getTotalBytes();
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo(); am.getMemoryInfo(mi);
                String net = "offline";
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                Network n = cm.getActiveNetwork();
                if (n != null) { NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                    if (nc != null) { if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) net = "Wi-Fi"; else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) net = "Mobile data"; else net = "Connected"; } }
                return "{\"model\":\"" + Build.MODEL + "\",\"maker\":\"" + Build.MANUFACTURER + "\",\"android\":\"" + Build.VERSION.RELEASE + "\",\"sdk\":" + Build.VERSION.SDK_INT + ",\"battery\":" + pct + ",\"charging\":" + charging + ",\"storageFree\":" + free + ",\"storageTotal\":" + total + ",\"ramFree\":" + mi.availMem + ",\"ramTotal\":" + mi.totalMem + ",\"cores\":" + Runtime.getRuntime().availableProcessors() + ",\"network\":\"" + net + "\",\"uptimeMs\":" + SystemClock.elapsedRealtime() + "}";
            } catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
        }
    }
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        webView = new WebView(this); setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); s.setAllowFileAccess(true);
        webView.addJavascriptInterface(new Saver(), "AndroidSaver");
        webView.addJavascriptInterface(new SystemBridge(), "AndroidSystem");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest req) { req.grant(req.getResources()); }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                fileCallback = cb;
                Intent i = p.createIntent();
                try { startActivityForResult(i, 42); } catch (Exception e) { fileCallback = null; return false; }
                return true;
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }
    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == 42 && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCallback = null;
        }
    }
    @Override public void onBackPressed() { if (webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
}
