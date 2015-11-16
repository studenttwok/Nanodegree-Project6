package com.example.android.sunshine.app.sync;


import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

// Ref: http://android-wear-docs.readthedocs.org/en/latest/sync.html

public class AndroidWearService extends IntentService {
    private final String LOG_TAG = AndroidWearService.class.getSimpleName();
    private GoogleApiClient mGoogleApiClient;


    public AndroidWearService() {
        super("AndroidWearService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "AndroidWearService onCreate()");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(LOG_TAG, "onHandleIntent()");

        final String min = intent.getStringExtra("desc");
        final String max = intent.getStringExtra("max");
        final Asset img = intent.getParcelableExtra("asset");

        Log.d(LOG_TAG, img.toString());

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(LOG_TAG, "onConnected: " + connectionHint);
                        // Now you can use the Data Layer API

                        Log.d(LOG_TAG, "Google API Ready");

                        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/weatherSync");
                        putDataMapReq.getDataMap().putString("min", min);
                        putDataMapReq.getDataMap().putString("max", max);
                        putDataMapReq.getDataMap().putAsset("img", img);
                        putDataMapReq.getDataMap().putLong("ts", System.currentTimeMillis());

                        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
                        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                            @Override
                            public void onResult(DataApi.DataItemResult dataItemResult) {
                                Log.d(LOG_TAG, "Result: " + dataItemResult.getStatus().toString());
                            }
                        });


                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.d(LOG_TAG, "onConnectionSuspended: " + cause);

                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d(LOG_TAG, "onConnectionFailed: " + result);

                    }
                })
                        // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();


    }
}
