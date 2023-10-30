package com.nuuneoi.rtsplab;

import android.media.MediaCodec;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import com.alexvas.rtsp.RtspClient;
import com.pedro.rtmp.rtmp.RtmpClient;
import com.pedro.rtmp.rtmp.VideoCodec;
import com.pedro.rtmp.utils.ConnectCheckerRtmp;

import java.io.IOException;
import java.nio.ByteBuffer;

public class RtmpStreamer implements RtspRecorder.RtspListener {
    private static final String TAG = "RtmpStreamer";

    ConnectCheckerRtmp mConnectCheckerRtmpListener;

    RtspRecorder mInputSourceRtspRecorder;

    RtmpClient rtmpClient;

    Boolean isStreaming = false;
    Boolean isVideoInfoSet = false;
    Boolean isAudioInfoSet = false;

    boolean isOnlyVideo = false;

    public void setInputSource(RtspRecorder rtspRecorder) {
        if (isStreaming)
            throw new RuntimeException("Cannot set input source once started");

        if (mInputSourceRtspRecorder != null)
            mInputSourceRtspRecorder.setRtspListener(null);

        mInputSourceRtspRecorder = rtspRecorder;
        mInputSourceRtspRecorder.setRtspListener(this);
    }

    public void setConnectCheckerRtmpListener(ConnectCheckerRtmp listener) {
        mConnectCheckerRtmpListener = listener;
    }

    public void startStreaming(String rtmpUrl, boolean onlyVideo) {
        if (isStreaming)
            return;

        isVideoInfoSet = false;
        isAudioInfoSet = false;

        isOnlyVideo = onlyVideo;

        rtmpClient = new RtmpClient(connectCheckerRtp);
        rtmpClient.setVideoCodec(VideoCodec.H264);
        rtmpClient.setVideoResolution(mInputSourceRtspRecorder.getVideoWidth(), mInputSourceRtspRecorder.getVideoHeight());
        rtmpClient.setFps(30);
        rtmpClient.setOnlyVideo(isOnlyVideo);
        rtmpClient.connect(rtmpUrl);

        isStreaming = true;
    }

    public void stopStreaming() {
        if (!isStreaming)
            return;

        rtmpClient.disconnect();
        rtmpClient = null;

        isVideoInfoSet = false;
        isAudioInfoSet = false;
        isStreaming = false;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    // RtspRecorder.RtspListener

    @Override
    public void onRtspVideoDataReceived(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) {
        Log.d(TAG, "onRtspVideoNalUnitReceived");

        synchronized (isStreaming) {
            if (isStreaming) {
                synchronized (isVideoInfoSet) {
                    if (!isVideoInfoSet && mInputSourceRtspRecorder.getSps() != null) {
                        rtmpClient.setVideoInfo(
                                ByteBuffer.wrap(mInputSourceRtspRecorder.getSps(), 0, mInputSourceRtspRecorder.getSps().length),
                                ByteBuffer.wrap(mInputSourceRtspRecorder.getPps(), 0, mInputSourceRtspRecorder.getPps().length),
                                null
                        );
                        isVideoInfoSet = true;
                    }
                }

                rtmpClient.sendVideo(byteBuf, bufferInfo);
            }
        }
    }

    @Override
    public void onRtspAudioDataReceived(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) {
        Log.d(TAG, "onRtspAudioSampleReceived");

        if (isOnlyVideo)
            return;

        synchronized (isStreaming) {
            if (isStreaming) {
                synchronized (isAudioInfoSet) {
                    if (!isAudioInfoSet && mInputSourceRtspRecorder.getAudioSampleRateHz() > 0) {
                        rtmpClient.setAudioInfo(
                            mInputSourceRtspRecorder.getAudioSampleRateHz(),
                            mInputSourceRtspRecorder.getAudioChannels() == 2
                        );
                        isAudioInfoSet = true;
                    }
                }

                rtmpClient.sendAudio(byteBuf, bufferInfo);
            }
        }
    }

    // Connect Checker

    ConnectCheckerRtmp connectCheckerRtp = new ConnectCheckerRtmp() {
        @Override
        public void onConnectionStartedRtmp(@NonNull String s) {
            Log.d(TAG, "onConnectionStartedRtmp");
            if (mConnectCheckerRtmpListener != null)
                mConnectCheckerRtmpListener.onConnectionStartedRtmp(s);
        }

        @Override
        public void onConnectionSuccessRtmp() {
            Log.d(TAG, "onConnectionSuccessRtmp");
            if (mConnectCheckerRtmpListener != null)
                mConnectCheckerRtmpListener.onConnectionSuccessRtmp();
        }

        @Override
        public void onConnectionFailedRtmp(@NonNull String s) {
            Log.d(TAG, "onConnectionFailedRtmp");
            if (mConnectCheckerRtmpListener != null)
                mConnectCheckerRtmpListener.onConnectionFailedRtmp(s);
        }

        @Override
        public void onNewBitrateRtmp(long l) {
            Log.d(TAG, "onNewBitrateRtmp");
            if (mConnectCheckerRtmpListener != null)
                mConnectCheckerRtmpListener.onNewBitrateRtmp(l);
        }

        @Override
        public void onDisconnectRtmp() {
            Log.d(TAG, "onDisconnectRtmp");
            if (mConnectCheckerRtmpListener != null)
                mConnectCheckerRtmpListener.onDisconnectRtmp();
        }

        @Override
        public void onAuthErrorRtmp() {
            Log.d(TAG, "onAuthErrorRtmp");
            if (mConnectCheckerRtmpListener != null)
                mConnectCheckerRtmpListener.onAuthErrorRtmp();
        }

        @Override
        public void onAuthSuccessRtmp() {
            Log.d(TAG, "onAuthSuccessRtmp");
            if (mConnectCheckerRtmpListener != null)
                mConnectCheckerRtmpListener.onAuthSuccessRtmp();
        }
    };
}
