package dev.copydirectlink;

import android.app.assist.AssistContent;
import android.content.ComponentName;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.util.Log;

import java.lang.reflect.Proxy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Loaded by Shizuku in the shell process. Returns only Chrome's current address. */
public final class ChromeAssistReader extends Binder {
    static final String DESCRIPTOR = "dev.copydirectlink.ChromeAssistReader";
    static final int READ_URL = FIRST_CALL_TRANSACTION;
    static final String URL = "url";
    private static final int DESTROY = 16777115;
    private static final String ASSIST_RECEIVER = "android.app.IAssistDataReceiver";
    private static final String ASSIST_PACKAGE = "android.intent.extra.ASSIST_PACKAGE";
    private static final String CUSTOM_TAB_PACKAGE = "org.chromium.chrome.browser.customtabs.";

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == DESTROY) {
            System.exit(0);
        }
        if (code != READ_URL) {
            return super.onTransact(code, data, reply, flags);
        }
        data.enforceInterface(DESCRIPTOR);
        var receiver = data.readTypedObject(ResultReceiver.CREATOR);
        if (receiver == null) {
            return false;
        }
        long identity = Binder.clearCallingIdentity();
        try {
            var result = new Bundle();
            result.putString(URL, readChromeUrl());
            receiver.send(0, result);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return true;
    }

    private static String readChromeUrl() {
        try {
            var managerType = Class.forName("android.app.IActivityTaskManager");
            var manager = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            var task = managerType.getMethod("getFocusedRootTaskInfo").invoke(manager);
            if (task == null || !(task.getClass().getField("topActivity").get(task) instanceof ComponentName top)
                    || !CopyLinkService.CHROME.equals(top.getPackageName())
                    || !top.getClassName().startsWith(CUSTOM_TAB_PACKAGE)) {
                return null;
            }

            var receiverType = Class.forName(ASSIST_RECEIVER);
            var received = new ArrayBlockingQueue<Bundle>(1);
            var callback = new Binder() {
                @Override
                protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                    if (code != FIRST_CALL_TRANSACTION) {
                        return false;
                    }
                    data.enforceInterface(ASSIST_RECEIVER);
                    var result = data.readTypedObject(Bundle.CREATOR);
                    if (result != null) {
                        received.offer(result);
                    }
                    return true;
                }
            };
            var receiver = Proxy.newProxyInstance(receiverType.getClassLoader(), new Class<?>[]{receiverType},
                    (proxy, method, arguments) -> callback);
            // Android 17 requires a specific activity or task for assist requests.
            Object requested;
            if (Build.VERSION.SDK_INT >= 37) {
                int taskId = task.getClass().getField("taskId").getInt(task);
                var request = managerType.getMethod("requestAssistDataForTask", receiverType, int.class,
                        String.class, String.class, boolean.class);
                requested = request.invoke(manager, receiver, taskId, "com.android.shell", null, false);
            } else {
                int contentOnly = Class.forName("android.app.ActivityManager")
                        .getField("ASSIST_CONTEXT_CONTENT").getInt(null);
                var request = managerType.getMethod("requestAssistContextExtras", int.class, receiverType,
                        Bundle.class, IBinder.class, boolean.class, boolean.class);
                requested = request.invoke(manager, contentOnly, receiver, null, null, true, false);
            }
            if (!Boolean.TRUE.equals(requested)) {
                return null;
            }
            return urlFromAssist(received.poll(2, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            Log.w("CopyDirectLink", "Unable to request Chrome's address", unavailable);
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static String urlFromAssist(Bundle result) {
        var metadata = result == null ? null : result.getBundle("data");
        if (metadata == null || !CopyLinkService.CHROME.equals(metadata.getString(ASSIST_PACKAGE))) {
            return null;
        }
        if (!(result.getParcelable("content") instanceof AssistContent content) || !content.isAppProvidedWebUri()) {
            return null;
        }
        var url = content.getWebUri();
        return url == null ? null : url.toString();
    }
}
