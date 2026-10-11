package io.github.drcesarcantu.calistenia;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** La app es la página publicada en GitHub Pages; aquí solo se le da casa en Android y el botón de compartir nativo. */
public class MainActivity extends Activity {
    private static final String URL = "https://drcesarcantu.github.io/calistenia/";
    private static final int ELEGIR_FOTO = 7;
    private WebView web;
    private ValueCallback<Uri[]> fotoCallback;

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0B0F14);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUserAgentString(s.getUserAgentString() + " CalisteniaApp");

        web.addJavascriptInterface(new Puente(), "CalisteniaAndroid");
        // confirm() y prompt() + el selector de fotos para «Sobre mi foto»
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fotoCallback != null) fotoCallback.onReceiveValue(null);
                fotoCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), ELEGIR_FOTO);
                } catch (Exception e) {
                    fotoCallback = null;
                    return false;
                }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (u.getHost() != null && u.getHost().equals("drcesarcantu.github.io")
                        && u.getPath() != null && u.getPath().startsWith("/calistenia")) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, u)); // lo demás se abre fuera
                return true;
            }
        });

        if (guardado != null) web.restoreState(guardado);
        else web.loadUrl(URL);
    }

    @Override
    protected void onSaveInstanceState(Bundle estado) {
        super.onSaveInstanceState(estado);
        web.saveState(estado);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent datos) {
        if (req == ELEGIR_FOTO && fotoCallback != null) {
            fotoCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, datos));
            fotoCallback = null;
            return;
        }
        super.onActivityResult(req, res, datos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** Guarda el PNG en la galería, en Imágenes/Calistenia. */
    private void guardar(String b64, String nombre) throws Exception {
        byte[] datos = Base64.decode(b64, Base64.DEFAULT);
        String limpio = nombre.replaceAll("[^A-Za-z0-9._-]", "-");
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, limpio);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Calistenia");
            Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new Exception("sin acceso a la galería");
            try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(datos); }
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Calistenia");
            dir.mkdirs();
            File f = new File(dir, limpio);
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(datos); }
            MediaScannerConnection.scanFile(this, new String[]{f.getAbsolutePath()}, new String[]{"image/png"}, null);
        }
    }

    /** window.CalisteniaAndroid.compartirPng(base64, nombre, texto) / guardarPng(base64, nombre) */
    private class Puente {
        @JavascriptInterface
        public void compartirPng(String b64, String nombre, String texto) {
            runOnUiThread(() -> {
                try {
                    File dir = new File(getCacheDir(), "compartir");
                    dir.mkdirs();
                    File f = new File(dir, nombre.replaceAll("[^A-Za-z0-9._-]", "-"));
                    try (FileOutputStream o = new FileOutputStream(f)) {
                        o.write(Base64.decode(b64, Base64.DEFAULT));
                    }
                    Uri u = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".archivos", f);
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("image/png");
                    i.putExtra(Intent.EXTRA_STREAM, u);
                    i.putExtra(Intent.EXTRA_TEXT, texto);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "Compartir tarjeta"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "No se pudo compartir la tarjeta", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void guardarPng(String b64, String nombre) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 8);
                    Toast.makeText(MainActivity.this, "Da el permiso y vuelve a tocar Guardar", Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    guardar(b64, nombre);
                    Toast.makeText(MainActivity.this, "✅ Guardada en Galería › Calistenia", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "No se pudo guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
