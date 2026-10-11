package io.github.drcesarcantu.calistenia;

import android.app.Activity;
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

    /** window.CalisteniaAndroid.compartirPng(base64, nombre, texto) */
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
    }
}
