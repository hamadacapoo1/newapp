package com.mohamed.voicenotes;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** بعد ما الموبايل يفتح: لو في تحميل ناقص بيظهر إشعار تضغط عليه وتكمل. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null || !Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        if (ModelManager.isReady(c) || ModelManager.partialBytes(c) <= 0) return;
        String st = ModelManager.getState(c);
        if (ModelManager.S_DOWNLOADING.equals(st) || ModelManager.S_PAUSED.equals(st)) {
            ModelManager.setState(c, ModelManager.S_PAUSED);
            ModelManager.setUserPaused(c, true);
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(ModelDownloadService.NOTIF_ID, ModelDownloadService.buildPaused(c));
        }
    }
}
