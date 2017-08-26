/*
 * =================================================
 * Mockifer License
 * =================================================
 *
 * MIT License
 *
 * Copyright (c) 2017 Marcel Braghetto
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package mockifer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static android.content.Context.MODE_PRIVATE;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class Mockifer {
    private static String CONTENT_PATH = "";
    private static int SERVER_PORT = 8503;
    private static final String TAG = "MOCKIFER";
    private static final String SHARED_PREF = "mockifer";
    private static final String PREF_LAST_MODIFIED = "mockifer-last-modified";
    private static final String ZIP_FILE_NAME = "mockifer-js.zip";

    private Mockifer() {
    }

    static {
        System.loadLibrary("mockifer");
    }

    /**
     * Install Mockifer with the default settings - access via http://localhost:8503
     *
     * @param application to associate with Mockifer.
     */
    public static void install(@NonNull Application application) {
        install(application, SERVER_PORT);
    }

    /**
     * Install Mockifer with the given port number to listen on - access via http://localhost:YOUR_PORT_NUMBER
     *
     * @param application to associate with Mockifer.
     * @param port        to listen on.
     */
    @SuppressLint("ApplySharedPref")
    public static void install(@NonNull Application application, int port) {
        CONTENT_PATH = application.getFilesDir().getAbsolutePath() + "/mockifer-js";
        SERVER_PORT = port;

        boolean needsRebuild = false;

        try {
            InputStream inputStream = application.getAssets().open("mockifer-js/mockifer.properties");
            Properties properties = new Properties();
            properties.load(inputStream);
            long propertiesLastModified = Long.valueOf(properties.getProperty("last-build-time", "0"));

            SharedPreferences sharedPreferences = application.getSharedPreferences(SHARED_PREF, MODE_PRIVATE);
            long savedLastModified = sharedPreferences.getLong(PREF_LAST_MODIFIED, 0L);

            // We really only want to go through the process of copying assets to private
            // storage when absolutely needed - in our case only if the mockifer assets
            // in the app have a different build time to when we performed this operation
            // last time. This means the asset file copying only happens once unless the
            // mockifer-js content is changed between builds.
            if (propertiesLastModified != savedLastModified) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putLong(PREF_LAST_MODIFIED, propertiesLastModified);
                editor.commit();
                needsRebuild = true;
            }
        } catch (IOException | NumberFormatException e) {
            needsRebuild = true;
        }

        if (needsRebuild) {
            Log.d(TAG, "New content detected, performing data sync, be patient!");
            long now = System.currentTimeMillis();

            try {
                File mockiferDirectory = new File(CONTENT_PATH);

                if (mockiferDirectory.exists()) {
                    delete(mockiferDirectory);
                }

                String internalZipFilePath = application.getFilesDir().getAbsolutePath() + "/" + ZIP_FILE_NAME;
                String targetUnzipPath = application.getFilesDir().getAbsolutePath() + "/mockifer-js";

                // We copy the 'mockifer-js.zip' file from the assets directory to internal storage.
                copyAssetFile(application, "mockifer-js/" + ZIP_FILE_NAME, internalZipFilePath);

                // Then inflate the zip file directly inside internal storage.
                unzip(internalZipFilePath, targetUnzipPath);

                // Then clean up the copied fip file
                delete(new File(internalZipFilePath));
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Fatal - could not unpack content!");
                return;
            }

            Log.d(TAG, "New content sync completed in " + (System.currentTimeMillis() - now) + "ms");
        }

        application.registerActivityLifecycleCallbacks(new MockiferLifecycle());
    }

    /**
     * Reset the Mockifer server which will restore it to a clean state.
     */
    public static void reset() {
        nativeResetServer();
    }

    /**
     * Set the global response delay to apply to all requests to simulate network timings.
     * The 'reset' method will also clear this value.
     *
     * @param responseDelayMillis to wait before responding to each request.
     */
    public static void setGlobalResponseDelay(int responseDelayMillis) {
        nativeSetGlobalResponseDelay(Math.abs(responseDelayMillis));
    }

    /**
     * Clear any active mocks that have been pushed - other server state will remain intact.
     */
    public static void clearActiveMocks() {
        nativeClearActiveMocks();
    }

    /**
     * Push a collection of mock route identifiers to be evaluated when matching request routes.
     *
     * Each added mock route id will be executed once by default before being consumed.
     *
     * @param mockRouteIds to push to evaluate for incoming requests.
     */
    public static void pushMocks(@NonNull String... mockRouteIds) {
        for (String mockRouteId : mockRouteIds) {
            pushMock(mockRouteId, 1);
        }
    }

    /**
     * Push a single mock route id to be evaluated when matching request routes. The mock route id
     * will be executed once by default before being consumed.
     *
     * @param mockRouteId to push to evaluate for incoming requests.
     */
    public static void pushMock(@NonNull String mockRouteId) {
        pushMock(mockRouteId, 1);
    }

    /**
     * Push a single mock id to be evaluated when matching request routes along with how many times
     * it can be executed before being consumed.
     *
     * @param mockRouteId to push to evaluate for incoming requests.
     * @param times       to execute the mock before it is considered to have been consumed.
     */
    public static void pushMock(@NonNull String mockRouteId, int times) {
        nativePushMock(mockRouteId, times);
    }

    //region Native C/C++ methods
    private static native void nativeStartServer(String contentPath, String port);

    private static native void nativeStopServer();

    private static native void nativeResetServer();

    private static native void nativeSetGlobalResponseDelay(int delay);

    private static native void nativePushMock(String mockRouteId, int times);

    private static native void nativeClearActiveMocks();
    //endregion

    //region Lifecycle management
    private static class MockiferLifecycle implements Application.ActivityLifecycleCallbacks {
        private AtomicInteger activityCount = new AtomicInteger(0);

        private void killServerIfNeeded() {
            new Handler().postDelayed(new Runnable() {
                @Override public void run() {
                    int count = activityCount.get();
                    if (count <= 0) {
                        nativeStopServer();
                    }
                }
            }, 1000L * 60L * 5L);
        }

        @Override public void onActivityCreated(Activity activity, Bundle bundle) {
            int count = activityCount.incrementAndGet();

            if (count <= 0) {
                return;
            }

            if (CONTENT_PATH == null || CONTENT_PATH.length() == 0) {
                return;
            }

            nativeStartServer(CONTENT_PATH, String.valueOf(SERVER_PORT));
        }

        @Override public void onActivityStarted(Activity activity) {
        }

        @Override public void onActivityResumed(Activity activity) {

        }

        @Override public void onActivityPaused(Activity activity) {

        }

        @Override public void onActivityStopped(Activity activity) {
        }

        @Override public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

        }

        @Override public void onActivityDestroyed(Activity activity) {
            activityCount.decrementAndGet();
            killServerIfNeeded();
        }
    }
    //endregion

    //region File management
    private static void delete(File target) {
        File[] fileList = target.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                delete(file);
            }
        }
        target.delete();
    }

    @SuppressLint("ApplySharedPref")
    private static void copyAssetFile(Context context, String assetFilePath, String destinationFilePath) throws IOException {
        InputStream in = context.getApplicationContext().getAssets().open(assetFilePath);
        OutputStream out = new FileOutputStream(destinationFilePath);

        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    private static void unzip(String zipFilePath, String destinationPath) throws IOException {
        int size;
        final int BUFFER_SIZE = 1024;
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            if (!destinationPath.endsWith(File.separator)) {
                destinationPath += File.separator;
            }
            File destinationFile = new File(destinationPath);
            if (!destinationFile.isDirectory()) {
                destinationFile.mkdirs();
            }

            ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFilePath), BUFFER_SIZE));

            try {
                ZipEntry zipEntry;
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    String path = destinationPath + zipEntry.getName();
                    File unzipFile = new File(path);

                    if (zipEntry.isDirectory()) {
                        if (!unzipFile.isDirectory()) {
                            unzipFile.mkdirs();
                        }
                    } else {
                        File parentDirectory = unzipFile.getParentFile();
                        if (parentDirectory != null) {
                            if (!parentDirectory.isDirectory()) {
                                parentDirectory.mkdirs();
                            }
                        }

                        FileOutputStream fileOutputStream = new FileOutputStream(unzipFile, false);
                        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream, BUFFER_SIZE);
                        try {
                            while ((size = zipInputStream.read(buffer, 0, BUFFER_SIZE)) != -1) {
                                bufferedOutputStream.write(buffer, 0, size);
                            }

                            zipInputStream.closeEntry();
                        } finally {
                            bufferedOutputStream.flush();
                            bufferedOutputStream.close();
                        }
                    }
                }
            } finally {
                zipInputStream.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Fatal - could not unzip content!", e);
        }
    }
    //endregion
}
