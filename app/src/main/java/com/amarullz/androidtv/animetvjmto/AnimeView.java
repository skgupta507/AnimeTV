package com.amarullz.androidtv.animetvjmto;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Map;

public class AnimeView extends WebViewClient {
  private final Activity activity;
  public WebView webView;
  public AnimeApi aApi;

  public String playerInjectString;

  public static boolean USE_WEB_VIEW_ASSETS=false;

  @SuppressLint("SetJavaScriptEnabled")
  public AnimeView(Activity mainActivity) {
    activity = mainActivity;
    WebView.setWebContentsDebuggingEnabled(true);
    webView = activity.findViewById(R.id.webview);
    webView.requestFocus();
    webView.setBackgroundColor(0xffffffff);
    WebSettings webSettings = webView.getSettings();
    webSettings.setJavaScriptEnabled(true);
    webSettings.setMediaPlaybackRequiresUserGesture(false);
    webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
    webSettings.setSafeBrowsingEnabled(false);
    webSettings.setSupportMultipleWindows(false);
    webView.addJavascriptInterface(new JSViewApi(), "_JSAPI");
    webView.setWebViewClient(this);
    webView.setWebChromeClient(new WebChromeClient() {
      @Override public Bitmap getDefaultVideoPoster() {
        final Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawARGB(255, 0, 0, 0);
        return bitmap;
      }
    });
    webView.setVerticalScrollBarEnabled(false);

    aApi=new AnimeApi(activity);
    playerInjectString=aApi.assetsString("inject/view_player.html");
    webView.loadUrl("https://9anime.to/__view/main.html");
  }

  @Override
  public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
    String url = request.getUrl().toString();
    return !url.startsWith("https://9anime.to/");
  }

  @Override
  public WebResourceResponse shouldInterceptRequest(final WebView view,
                                                    WebResourceRequest request) {
    Uri uri = request.getUrl();
    String url = uri.toString();
    String host = uri.getHost();
    String accept = request.getRequestHeaders().get("Accept");
    if (host==null||accept==null) return aApi.badRequest;
    if (host.contains("9anime.to")) {
      String path=uri.getPath();
      if (path.startsWith("/__view/")){
        if (USE_WEB_VIEW_ASSETS){
          if (!path.endsWith(".woff2") && !path.endsWith(".ttf")&& !accept.startsWith("image/")) {
            /* dev web */
            try {
              Log.d("ATVLOG", "VIEW GET " + url + " = " + accept);
              String newurl = url.replace("https://9anime.to", "http://192.168.100.245");
              HttpURLConnection conn = aApi.initQuic(newurl, request.getMethod());
              for (Map.Entry<String, String> entry :
                      request.getRequestHeaders().entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
              }
              String[] cType = aApi.parseContentType(conn.getContentType());
              ByteArrayOutputStream buffer = aApi.getBody(conn, null);
              InputStream stream = new ByteArrayInputStream(buffer.toByteArray());
              return new WebResourceResponse(cType[0], cType[1], stream);
            } catch (Exception ignored) {}
            return aApi.badRequest;
          }
        }
        return aApi.assetsRequest(uri.getPath().substring(3));
      }
      try {
        HttpURLConnection conn = aApi.initQuic(url, request.getMethod());
        for (Map.Entry<String, String> entry :
            request.getRequestHeaders().entrySet()) {
          conn.setRequestProperty(entry.getKey(), entry.getValue());
        }
        String[] cType = aApi.parseContentType(conn.getContentType());
        ByteArrayOutputStream buffer = aApi.getBody(conn, null);
        InputStream stream = new ByteArrayInputStream(buffer.toByteArray());
        return new WebResourceResponse(cType[0], cType[1], stream);
      } catch (Exception ignored) {}
      return aApi.badRequest;
    }
    else if (host.contains("vizcloud.co")||host.contains("mcloud.to")){
      if (accept.startsWith("text/html")) {
        Log.d("ATVLOG","VIEW PLAYER REQ = "+url);
        try {
          HttpURLConnection conn = aApi.initQuic(url, request.getMethod());
          for (Map.Entry<String, String> entry :
              request.getRequestHeaders().entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
          }
          String[] cType = aApi.parseContentType(conn.getContentType());
          ByteArrayOutputStream buffer = aApi.getBody(conn, null);
          aApi.injectString(buffer, playerInjectString);
          InputStream stream = new ByteArrayInputStream(buffer.toByteArray());
          Log.d("ATVLOG","VIEW PLAYER REQ FINISH");
          return new WebResourceResponse(cType[0], cType[1], stream);
        } catch (Exception ignored) {}
        return aApi.badRequest;
      }
    }
    return super.shouldInterceptRequest(view, request);
  }

  public void getViewCallback(String d,int u){
    webView.evaluateJavascript("__GETVIEWCB("+d+","+u+");",null);
  }

  public class JSViewApi{

    @JavascriptInterface
    public boolean getview(String url, int zid) {
      if (aApi.resData.status==1) return false;
      AsyncTask.execute(() -> activity.runOnUiThread(() -> aApi.getData(url, result -> getViewCallback(result.Text,zid))));
      return true;
    }
    @JavascriptInterface
    public void clearView() {
      aApi.cleanWebView();
    }

    @JavascriptInterface
    public void tapEmulate(float x, float y) {
      AsyncTask.execute(() -> simulateClick(x,y));
    }

    @JavascriptInterface
    public void appQuit() {
      activity.finish();
    }

    @JavascriptInterface
    public void getmp4vid(String url) {
      AsyncTask.execute(() -> {
        final String out=aApi.getMp4Video(url);
        activity.runOnUiThread(() -> webView.evaluateJavascript("__MP4CB("+out+");",null));
      });
    }
  }

  @Override
  public void onPageFinished(WebView view, String url) {
    webView.setVisibility(View.VISIBLE);
  }

  private void simulateClick(float xx, float yy) {
    int x=(int) ((webView.getMeasuredWidth()*xx)/100.0);
    int y=(int) ((webView.getMeasuredHeight()*yy)/100.0);
    Log.d("VLOG","TAP: ("+x+", "+y+") -> "+xx+"%, "+yy+"%");
    long downTime = SystemClock.uptimeMillis();
    long eventTime = SystemClock.uptimeMillis() + 150;
    int metaState = 0;
    MotionEvent me = MotionEvent.obtain(
        downTime,
        eventTime,
        MotionEvent.ACTION_DOWN,
        x,
        y,
        metaState
    );
    webView.dispatchTouchEvent(me);
    me = MotionEvent.obtain(
        downTime,
        eventTime,
        MotionEvent.ACTION_UP,
        x,
        y,
        metaState
    );
    webView.dispatchTouchEvent(me);
  }
}
