package uz.heritageedu.davomat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Heritage — heritageedu.uz saytini ilova ichida ochadi.
 * Sayt (index.html) o'zgarsa, ilovani qayta yig'ish shart emas: har safar ochilganda
 * eng so'nggi versiya serverdan olinadi.
 */
public class MainActivity extends Activity {

    // Sayt manzili. Domen o'zgarsa, faqat shu ikki qatorni o'zgartiring.
    private static final String SITE = "https://heritageedu.uz";
    private static final String HOST = "heritageedu.uz";

    private static final int REQ_FILE = 1001;

    private static final String OFFLINE_HTML =
            "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                    + "<body style='margin:0;background:#2a0447;color:#f4eeff;font-family:sans-serif;"
                    + "display:flex;min-height:100vh;align-items:center;justify-content:center;text-align:center'>"
                    + "<div><h2>Internet yo'q</h2><p>Ulanishni tekshirib, qayta urinib ko'ring.</p>"
                    + "<a href='" + SITE + "' style='display:inline-block;margin-top:12px;padding:12px 22px;"
                    + "background:#12f47a;color:#2a0447;border-radius:10px;text-decoration:none;font-weight:bold'>"
                    + "Qayta urinish</a></div></body></html>";

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private FrameLayout splash;
    private boolean splashHiding = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setBackgroundColor(0xFF2A0447);

        // Ilova ochilganda logotipli kirish ekrani (sayt yuklanguncha)
        splash = new FrameLayout(this);
        splash.setBackgroundColor(0xFF3A0261);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.splash_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = (int) (150 * getResources().getDisplayMetrics().density);
        splash.addView(logo, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));

        FrameLayout root = new FrameLayout(this);
        root.addView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(splash, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        web.postDelayed(this::hideSplash, 8000); // zaxira: internet sekin bo'lsa ham ekran yopiladi

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // Firebase Auth/Firestore uchun kerak
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);

        CookieManager.getInstance().setAcceptCookie(true);

        web.addJavascriptInterface(new Bridge(), "AndroidApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                String host = u.getHost();
                if ("blob".equals(scheme) || "about".equals(scheme) || "data".equals(scheme)) return false;
                if ("https".equals(scheme) && host != null
                        && (host.equals(HOST) || host.endsWith("." + HOST))) {
                    return false;
                }
                // Telegram, Instagram, telefon va boshqa tashqi havolalar — tashqarida ochiladi
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException ignored) {
                    Toast.makeText(MainActivity.this, "Ochib bo'lmadi", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                hideSplash();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    view.loadDataWithBaseURL(null, OFFLINE_HTML, "text/html", "UTF-8", null);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // <input type="file"> (rasm yuklash, Excel import) ishlashi uchun
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        // Yuklab olish (masalan, Excel hisobot). blob: havolalar JS orqali o'qiladi.
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url.startsWith("blob:")) {
                String name = "";
                if (contentDisposition != null && contentDisposition.toLowerCase().contains("filename")) {
                    name = URLUtil.guessFileName(url, contentDisposition, mimeType);
                }
                String js = "(function(){fetch('" + url + "')"
                        + ".then(function(r){return r.blob();})"
                        + ".then(function(b){var f=new FileReader();"
                        + "f.onloadend=function(){AndroidApp.saveFile(f.result," + JSONObject.quote(name) + ");};"
                        + "f.readAsDataURL(b);})"
                        + ".catch(function(e){AndroidApp.fail(String(e));});})();";
                web.evaluateJavascript(js, null);
            } else if (url.startsWith("http")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (ActivityNotFoundException ignored) {
                    toast("Yuklab bo'lmadi");
                }
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(SITE);
        }
    }

    // ---- Fayl tanlash natijasi ----
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (filePathCallback != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // ---- JS -> Android ko'prigi (faqat heritageedu.uz yuklanadi) ----
    private class Bridge {
        @JavascriptInterface
        public void saveFile(String dataUrl, String name) {
            try {
                int comma = dataUrl.indexOf(',');
                byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);

                String mime = "application/octet-stream";
                int semi = dataUrl.indexOf(';');
                if (dataUrl.startsWith("data:") && semi > 5 && semi < comma) {
                    mime = dataUrl.substring(5, semi);
                }
                String ext = null;
                // Fayl turi aniq bo'lmasa, boshlang'ich baytlardan taxmin qilamiz
                if (bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K') {
                    ext = "xlsx";
                    mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                } else if (bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P') {
                    ext = "pdf";
                    mime = "application/pdf";
                }
                if (ext == null) ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                if (ext == null) ext = "bin";

                String fileName = (name != null && !name.isEmpty())
                        ? name
                        : "Heritage_" + (System.currentTimeMillis() / 1000) + "." + ext;

                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    v.put(MediaStore.Downloads.MIME_TYPE, mime);
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("insert failed");
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        os.write(bytes);
                    }
                    toast("Yuklandi: Yuklashlar (Downloads) papkasi\n" + fileName);
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    File out = new File(dir, fileName);
                    try (FileOutputStream os = new FileOutputStream(out)) {
                        os.write(bytes);
                    }
                    toast("Yuklandi: " + out.getAbsolutePath());
                }
            } catch (Exception e) {
                toast("Faylni saqlab bo'lmadi");
            }
        }

        @JavascriptInterface
        public void fail(String msg) {
            toast("Yuklab bo'lmadi");
        }
    }

    private void hideSplash() {
        if (splash == null || splashHiding || splash.getVisibility() != View.VISIBLE) return;
        splashHiding = true;
        splash.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> splash.setVisibility(View.GONE)).start();
    }

    private void toast(final String text) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("AndroidApp");
            web.destroy();
        }
        super.onDestroy();
    }
}
