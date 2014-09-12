package org.omnirom.music.app;

import android.app.Application;
import android.net.http.HttpResponseCache;
import android.util.Log;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.acra.sender.HttpSender;
import org.omnirom.music.api.echonest.AutoMixManager;
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.ProviderAggregator;

import java.io.File;
import java.io.IOException;

@ReportsCrashes(
        formKey = "",
        httpMethod = HttpSender.Method.PUT,
        reportType = HttpSender.Type.JSON,
        formUri = "http://nuclear.ouverta.fr/om-acra/_design/acra-storage/_update/report",
        formUriBasicAuthLogin = "reporter-omnimusic-alpha",
        formUriBasicAuthPassword = "4lqh4mu51c1337"

        // Dialog during DEBUG, TOAST on prod
        /*mode = ReportingInteractionMode.TOAST,
        resToastText = R.string.crash_toast_text, // optional, displayed as soon as the crash occurs, before collecting data which can take a few seconds
        resDialogText = R.string.crash_dialog_text,
        resDialogIcon = android.R.drawable.ic_dialog_info, //optional. default is a warning sign
        resDialogTitle = R.string.crash_dialog_title, // optional. default is your application name
        resDialogCommentPrompt = R.string.crash_dialog_comment_prompt, // optional. when defined, adds a user text field input with this text resource as a label
        resDialogOkToast = R.string.crash_dialog_ok_toast // optional. displays a Toast message when the user accepts to send a report.*/
)
public class OmniMusic extends Application {
    private static final String TAG = "OmniMusic";

    @Override
    public void onCreate() {
        super.onCreate();

        ACRA.init(this);

        // Setup the plugins system
        ProviderAggregator.getDefault().setContext(getApplicationContext());
        PluginsLookup.getDefault().initialize(getApplicationContext());

        // Setup network cache
        /**
         * Note about the cache and EchoNest: The HTTP cache would sometimes cache request
         * we didn't want (such as status query for Taste Profile update). We're using
         * a hacked jEN library that doesn't cache these requests.
         */
        try {
            final File httpCacheDir = new File(getCacheDir(), "http");
            final long httpCacheSize = 100 * 1024 * 1024; // 100 MiB
            final HttpResponseCache cache = HttpResponseCache.install(httpCacheDir, httpCacheSize);

            Log.i(TAG, "HTTP Cache size: " + cache.size() / 1024 / 1024 + "MB");
        } catch (IOException e) {
            Log.w(TAG, "HTTP response cache installation failed", e);
        }

        // Setup image cache
        AlbumArtCache.getDefault().initialize(getApplicationContext());
        ImageCache.getDefault().initialize(getApplicationContext());

        // Setup Automix system
        AutoMixManager.getDefault().initialize(getApplicationContext());
    }


}
