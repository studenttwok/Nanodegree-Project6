/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunShineWatchFace extends CanvasWatchFaceService {
    private final String LOG_TAG = SunShineWatchFace.class.getSimpleName();

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }


    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBlackPaint;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mSmallTextPaint;
        Paint mSeparator;
        Paint mImgPaint;

        Rect mDestRct;
        boolean mAmbient;

        Time mTime;

        float mXOffset;
        float mYOffset;
        float mLineSpacing;
        float mTempSpacing;

        //String desc = "";
        String max = "";
        String min = "";
        Bitmap img = null;

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d yyyy", Locale.ENGLISH);

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;


        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunShineWatchFace.this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();


        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(LOG_TAG, "onDataChanged");

            for (DataEvent event : dataEventBuffer) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    // DataItem changed
                    DataItem item = event.getDataItem();
                    if (item.getUri().getPath().compareTo("/weatherSync") == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        max = dataMap.getString("max");
                        min = dataMap.getString("min");
                        final Asset imgAsset = dataMap.getAsset("img");
                        Thread thread = new Thread() {
                            @Override
                            public void run() {
                                super.run();
                                img = loadBitmapFromAsset(imgAsset);
                            }
                        };
                        thread.start();
                    }
                } else if (event.getType() == DataEvent.TYPE_DELETED) {
                    // DataItem deleted
                }
            }

            invalidate();
        }


        @Override
        public void onConnected(Bundle bundle) {
            Log.d(LOG_TAG, "onConnected()");
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);

            // check if we need to send the request to get the weather info actively
            if (img == null) {
                // send request
//                PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/weatherRequest");
//
//                PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
//                Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
//                    @Override
//                    public void onResult(DataApi.DataItemResult dataItemResult) {
//                        Log.d(LOG_TAG, "Result: " + dataItemResult.getStatus().toString());
//                    }
//                });

                Thread mThread = new Thread() {
                    @Override
                    public void run() {
                        super.run();

                        String path = "/weatherSync";
                        String message = "";

                        //NodeApi.GetLocalNodeResult nodes = Wearable.NodeApi.getLocalNode(mGoogleApiClient).await();
                        NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

                        if (nodes.getNodes().size() > 0) {
                            Node node = nodes.getNodes().get(0);
                            Log.v(LOG_TAG, "Activity Node is : " + node.getId() + " - " + node.getDisplayName());
                            MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), path, message.getBytes()).await();
                            if (result.getStatus().isSuccess()) {
                                Log.v(LOG_TAG, "Activity Message: {" + message + "} sent to: " + node.getDisplayName());
                            } else {
                                // Log an error
                                Log.v(LOG_TAG, "ERROR: failed to send Activity Message");
                            }

                            Log.d(LOG_TAG, "send the /weatherRequest out");
                        }


                    }
                };
                mThread.start();

            }
        }


        @Override
        public void onConnectionSuspended(int i) {
            Log.d(LOG_TAG, "onConnectionSuspended()");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d(LOG_TAG, "onConnectionFailed()");

        }

        public Bitmap loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            ConnectionResult result = mGoogleApiClient.blockingConnect(5000, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                return null;
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset).await().getInputStream();
            //mGoogleApiClient.disconnect();

            if (assetInputStream == null) {
                Log.w(LOG_TAG, "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunShineWatchFace.this)
                    //.setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                            //.setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_PERSISTENT)
                    .setStatusBarGravity(Gravity.RIGHT | Gravity.TOP)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunShineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mLineSpacing = resources.getDimension(R.dimen.line_spacing);

            mTempSpacing = resources.getDimension(R.dimen.temp_spacing);

            mBlackPaint = new Paint();
            mBlackPaint.setColor(resources.getColor(R.color.black));

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text), resources.getDimension(R.dimen.text_size));

            mSmallTextPaint = new Paint();
            mSmallTextPaint = createTextPaint(resources.getColor(R.color.digital_text_dim), resources.getDimension(R.dimen.small_text_size));

            mSeparator = new Paint();
            mSeparator.setStrokeWidth(resources.getDimension(R.dimen.separator_width));
            mSeparator.setColor(resources.getColor(R.color.separator));

            mImgPaint = new Paint();

            mTime = new Time();

            mGoogleApiClient.connect();

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            Wearable.DataApi.removeListener(mGoogleApiClient, Engine.this);
            mGoogleApiClient.disconnect();
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, float textSize) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextSize(textSize);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunShineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunShineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunShineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mSmallTextPaint.setAntiAlias(!inAmbientMode);

                    mSmallTextPaint.setColor(getResources().getColor(R.color.digital_text_dim));
                } else {

                    mSmallTextPaint.setColor(getResources().getColor(R.color.white));
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (mAmbient) {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBlackPaint);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);

            // Draw date
            String result = sdf.format(Calendar.getInstance().getTime());
            canvas.drawText(result, mXOffset, mYOffset + mLineSpacing, mSmallTextPaint);


            if (mAmbient) {
                //canvas.drawLine(startX, startY, stopX, startY, mSeparator);
            } else {
                int lineLength = canvas.getWidth() / 4;
                int startX = (int) ((canvas.getWidth() / 4) + mXOffset);
                int startY = (int) ((2 * (canvas.getHeight() / 4)));
                int stopX = startX + (canvas.getWidth() / 4);
                canvas.drawLine(startX, startY, stopX, startY, mSeparator);

                if (img != null) {
                    // draw the asset
                    canvas.drawBitmap(img, mXOffset + mXOffset, mYOffset + mLineSpacing + mLineSpacing, null);
                }

                // write desc
                canvas.drawText(min, mXOffset+ mTempSpacing + mTempSpacing + mTempSpacing + mTempSpacing + mTempSpacing + mTempSpacing, mYOffset + mLineSpacing + mLineSpacing + mLineSpacing + (mLineSpacing / 2), mTextPaint);
                canvas.drawText(max, mXOffset + mTempSpacing + mTempSpacing + mTempSpacing + mTempSpacing + mTempSpacing + mTempSpacing, mYOffset + mLineSpacing + mLineSpacing + mLineSpacing + mLineSpacing + mLineSpacing + (mLineSpacing / 2), mTextPaint);


            }



        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunShineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunShineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunShineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
