package dev.copydirectlink;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ResultReceiver;

import java.util.function.Consumer;

import rikka.shizuku.Shizuku;

final class ShizukuLinkReader implements ServiceConnection {
    private final Shizuku.UserServiceArgs service;
    private final Handler handler;
    private final Consumer<String> result;
    private boolean reading = true;

    ShizukuLinkReader(Context context, Handler handler, Consumer<String> result) {
        service = new Shizuku.UserServiceArgs(new ComponentName(context, ChromeAssistReader.class))
                .processNameSuffix("chrome_address").daemon(false).version(1);
        this.handler = handler;
        this.result = result;
    }

    static boolean authorized() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }

    void start() {
        try {
            Shizuku.bindUserService(service, this);
        } catch (IllegalStateException | SecurityException unavailable) {
            finish(null);
        }
    }

    void close() {
        if (!reading) {
            return;
        }
        reading = false;
        if (Shizuku.pingBinder()) {
            try {
                Shizuku.unbindUserService(service, this, true);
            } catch (IllegalStateException | SecurityException unavailable) {
                // Shizuku can stop or lose permission between the connection check and unbind.
            }
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        if (!reading) {
            return;
        }
        var address = new ResultReceiver(handler) {
            @Override
            protected void onReceiveResult(int code, Bundle data) {
                finish(data == null ? null : data.getString(ChromeAssistReader.URL));
            }
        };
        var parcel = Parcel.obtain();
        try {
            parcel.writeInterfaceToken(ChromeAssistReader.DESCRIPTOR);
            parcel.writeTypedObject(address, 0);
            if (!binder.transact(ChromeAssistReader.READ_URL, parcel, null, IBinder.FLAG_ONEWAY)) {
                finish(null);
            }
        } catch (RemoteException unavailable) {
            finish(null);
        } finally {
            parcel.recycle();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        finish(null);
    }

    private void finish(String url) {
        if (reading) {
            result.accept(url);
        }
    }
}
