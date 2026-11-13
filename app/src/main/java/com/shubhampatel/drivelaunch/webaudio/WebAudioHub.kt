package com.shubhampatel.drivelaunch.webaudio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Bridges HTML5 <video>/<audio> playback inside the app's WebView to a MediaSession so the
 * car (Android Auto media UI / in-app Now Playing card) can show audio-only controls.
 * The car never receives video frames from here: only title, artwork, position and commands.
 */
object WebAudioHub {

    data class State(
        val active: Boolean = false,
        val playing: Boolean = false,
        val title: String = "",
        val artist: String = "",
        val site: String = "",
        val artUrl: String = "",
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val updatedAt: Long = 0
    )

    @Volatile var state = State()
        private set
    @Volatile var art: Bitmap? = null
        private set

    val listeners = CopyOnWriteArrayList<() -> Unit>()

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var current: WeakReference<WebView>? = null
    private var artFetchedFor = ""

    fun install(webView: WebView) {
        webView.addJavascriptInterface(Bridge(webView), "OttBridge")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching { WebViewCompat.addDocumentStartJavaScript(webView, HOOK_JS, setOf("*")) }
        }
    }

    /** Fallback for WebViews without document-start scripts; the script is idempotent. */
    fun ensureScript(view: WebView) {
        view.evaluateJavascript(HOOK_JS, null)
    }

    fun command(cmd: String, arg: Long = 0) {
        val w = current?.get() ?: return
        main.post { w.evaluateJavascript("window.__ott&&window.__ott.cmd('$cmd',$arg)", null) }
    }

    /** Pause and drop the now-playing state (used by "Stop & browse" on the car). */
    fun stop() {
        command("pause")
        setState(State())
    }

    internal fun setState(s: State) {
        state = s
        listeners.forEach { runCatching { it() } }
    }

    private class Bridge(webView: WebView) {
        private val ref = WeakReference(webView)

        @JavascriptInterface
        fun onState(json: String) {
            if (json.length > 8000) return
            val w = ref.get() ?: return
            main.post { handle(w, json) }
        }
    }

    private fun handle(w: WebView, json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        val active = o.optBoolean("active", false)
        if (!active) {
            if (current?.get() === w) setState(State())
            return
        }
        current = WeakReference(w)
        val s = State(
            active = true,
            playing = o.optBoolean("playing", false),
            title = o.optString("title").take(200).trim(),
            artist = o.optString("artist").take(200).trim(),
            site = o.optString("site").removePrefix("www.").removePrefix("m.").take(80),
            artUrl = o.optString("art").take(1000),
            positionMs = o.optLong("pos", 0),
            durationMs = o.optLong("dur", 0),
            updatedAt = System.currentTimeMillis()
        )
        val before = state
        setState(s)
        if (s.artUrl != artFetchedFor) fetchArt(s.artUrl)
        if (s.playing && !before.playing) startService(w.context.applicationContext)
    }

    private fun startService(ctx: Context) {
        runCatching {
            ContextCompat.startForegroundService(ctx, Intent(ctx, WebAudioService::class.java))
        }
    }

    private fun fetchArt(url: String) {
        artFetchedFor = url
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            art = null; listeners.forEach { runCatching { it() } }; return
        }
        io.execute {
            val bmp = runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 6000; c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "Mozilla/5.0")
                c.inputStream.use { ins ->
                    val bytes = ins.readNBytes(3_000_000)
                    val bo = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bo)
                    var sample = 1
                    while (bo.outWidth / sample > 1024) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.getOrNull()
            main.post {
                if (artFetchedFor == url) {
                    art = bmp
                    listeners.forEach { runCatching { it() } }
                }
            }
        }
    }

    private const val HOOK_JS = """
(function(){
 if(window.__ottHooked) return; window.__ottHooked=true;
 var handlers={}, cur=null, lastSend=0, lastKey='';
 try{
  Object.defineProperty(document,'hidden',{get:function(){return false},configurable:true});
  Object.defineProperty(document,'visibilityState',{get:function(){return 'visible'},configurable:true});
  document.addEventListener('visibilitychange',function(e){e.stopImmediatePropagation();},true);
 }catch(e){}
 try{
  var ms=navigator.mediaSession;
  if(ms&&ms.setActionHandler){var orig=ms.setActionHandler.bind(ms);
   ms.setActionHandler=function(a,h){handlers[a]=h;try{orig(a,h);}catch(e){}};}
 }catch(e){}
 function isMedia(t){return t&&(t.tagName==='VIDEO'||t.tagName==='AUDIO');}
 function meta(){
  var t='',a='',art='';
  try{var m=navigator.mediaSession&&navigator.mediaSession.metadata;
   if(m){t=m.title||'';a=m.artist||m.album||'';var im=m.artwork;if(im&&im.length){art=im[im.length-1].src||'';}}}catch(e){}
  if(!t){t=document.title||'';}
  if(!art){var og=document.querySelector('meta[property="og:image"]');if(og)art=og.content||'';}
  return {t:t,a:a,art:art};
 }
 function send(el,force){
  var now=Date.now();
  var d=el.duration; if(!isFinite(d)) d=0;
  var m=meta();
  var o={active:true,playing:(!el.paused&&!el.ended),title:m.t,artist:m.a,art:m.art,site:location.hostname,
         pos:Math.round((el.currentTime||0)*1000),dur:Math.round(d*1000)};
  var key=[o.playing,o.title,o.art].join('|');
  if(!force&&key===lastKey&&now-lastSend<1000) return;
  lastKey=key; lastSend=now;
  try{OttBridge.onState(JSON.stringify(o));}catch(e){}
 }
 function onEv(e){
  var t=e.target; if(!isMedia(t)) return;
  if(t.muted||t.volume===0){ if(t===cur&&e.type==='emptied'){cur=null;try{OttBridge.onState('{"active":false}');}catch(x){}} return; }
  if(e.type==='emptied'){ if(t===cur){cur=null;try{OttBridge.onState('{"active":false}');}catch(x){}} return; }
  if(!cur||!document.contains(cur)||cur.paused||cur.ended||e.type==='play'||e.type==='playing'){cur=t;}
  if(t!==cur) return;
  send(t, e.type!=='timeupdate');
 }
 ['play','playing','pause','ended','timeupdate','loadedmetadata','durationchange','seeked','emptied','volumechange'].forEach(function(n){
  document.addEventListener(n,onEv,true);});
 window.addEventListener('pagehide',function(){try{OttBridge.onState('{"active":false}');}catch(x){}});
 window.__ott={cmd:function(c,a){
  var el=(cur&&document.contains(cur))?cur:document.querySelector('video,audio');
  try{
  if(c==='play'){ if(el) el.play(); }
  else if(c==='pause'){ if(el) el.pause(); }
  else if(c==='seek'){ if(el) el.currentTime=a/1000; }
  else if(c==='fwd'){ if(el) el.currentTime=Math.min(el.duration||1e9,el.currentTime+10); }
  else if(c==='rew'){ if(el) el.currentTime=Math.max(0,el.currentTime-10); }
  else if(c==='next'){
   if(handlers.nexttrack){handlers.nexttrack();}
   else{var b=document.querySelector('.ytp-next-button,[data-uia="next-episode-seamless-button"],button[aria-label*="Next" i]');if(b)b.click();}
  }
  else if(c==='prev'){
   if(handlers.previoustrack){handlers.previoustrack();} else if(el){el.currentTime=0;}
  }
  }catch(e){}
 }};
})();
"""
}
