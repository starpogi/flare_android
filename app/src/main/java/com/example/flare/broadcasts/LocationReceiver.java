package com.example.flare.broadcasts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.flare.map.MainActivity;

public class LocationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().matches("android.location.PROVIDERS_CHANGED")) {
            Intent pushIntent = new Intent(context, MainActivity.class);
            intent.putExtra("FLY_OVER", true);
            context.startService(pushIntent);
        }
    }
}
