package com.example.android.sunshine.app.sync;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.R;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;


public class AndroidWearableListenerService extends WearableListenerService {
    private final String LOG_TAG = AndroidWearableListenerService.class.getSimpleName();

    private static final String[] FORECAST_COLUMNS = {
            // In this case the id needs to be fully qualified with a table name, since
            // the content provider joins the location & weather tables in the background
            // (both have an _id column)
            // On the one hand, that's annoying.  On the other, you can search the weather table
            // using the location set by the user, which is only in the Location table.
            // So the convenience is worth it.
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.LocationEntry.COLUMN_COORD_LAT,
            WeatherContract.LocationEntry.COLUMN_COORD_LONG
    };

    // These indices are tied to FORECAST_COLUMNS.  If FORECAST_COLUMNS changes, these
    // must change.
    static final int COL_WEATHER_ID = 0;
    static final int COL_WEATHER_DATE = 1;
    static final int COL_WEATHER_DESC = 2;
    static final int COL_WEATHER_MAX_TEMP = 3;
    static final int COL_WEATHER_MIN_TEMP = 4;
    static final int COL_LOCATION_SETTING = 5;
    static final int COL_WEATHER_CONDITION_ID = 6;
    static final int COL_COORD_LAT = 7;
    static final int COL_COORD_LONG = 8;


    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        Log.d(LOG_TAG, "onMessageReceived()");

        // start the sync process
        //SunshineSyncAdapter.syncImmediately(this);
        getWeather();
    }

    private void getWeather() {
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";

        String locationSetting = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(locationSetting, System.currentTimeMillis());
        Cursor data = getApplicationContext().getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null, null, sortOrder);
        if (data.getCount() > 0) {
            // get the first one
            // that is today
            data.moveToPosition(0);

            int weatherId = data.getInt(COL_WEATHER_CONDITION_ID);

            int iconId = Utility.getIconResourceForWeatherCondition(weatherId);

            Bitmap icon = BitmapFactory.decodeResource(getResources(), iconId);
            icon = Bitmap.createScaledBitmap(icon, (int)getResources().getDimension(R.dimen.img_size), (int)getResources().getDimension(R.dimen.img_size), false);

            final String maxTemp = Utility.formatTemperature(this, data.getDouble(COL_WEATHER_MAX_TEMP));
            final String minTemp = Utility.formatTemperature(this, data.getDouble(COL_WEATHER_MIN_TEMP));

            final Asset img = Utility.createAssetFromBitmap(icon);

            data.close();

            // so we send back the data
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle bundle) {
                            PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/weatherSync");
                            putDataMapReq.getDataMap().putString("min", minTemp);
                            putDataMapReq.getDataMap().putString("max", maxTemp);
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
                        public void onConnectionSuspended(int i) {

                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult connectionResult) {

                        }
                    })
                    .build();

            mGoogleApiClient.connect();

        }
    }


}
