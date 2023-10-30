package com.nuuneoi.rtsplab;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alexvas.rtsp.RtspClient;
import com.alexvas.utils.NetUtils;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class RtspRecorder {

    interface RtspListener {
        void onRtspVideoDataReceived(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo);
        void onRtspAudioDataReceived(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo);
    }

    private static boolean DEBUG = false;
    private static String TAG = "RtspRecorder";

    MediaMuxer mediaMuxer;
    RtspThread rtspThread;

    boolean isStreaming = false;
    Boolean isRecording = false;

    boolean isNewFileRequested = false;

    String rtspUrl;

    private byte[] sps;
    private byte[] pps;

    String videoMimeType;
    int videoWidth;
    int videoHeight;

    private int audioSampleRateHz;
    private int audioChannels;

    private String recordingOutputFilePath;
    private String recordingOutputDir;

    private RtspListener mRtspListener;

    public void setRtspListener(RtspListener listener) {
        mRtspListener = listener;
    }

    public void startStreaming(String url) {
        isStreaming = true;

        rtspUrl = url;

        rtspThread = new RtspThread();
        rtspThread.start();
    }

    public void stopStreaming() {
        stopRecording();

        rtspThread.stopAsync();
        rtspThread = null;

        isStreaming = false;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public void startRecording(String outputDir, String mimeType, int width, int height) {
        recordingOutputDir = outputDir;

        videoMimeType = mimeType;
        videoWidth = width;
        videoHeight = height;

        synchronized (isRecording) {
            isRecording = true;
        }
    }

    public void stopRecording() {
        synchronized (isRecording) {
            isRecording = false;
            isNewFileRequested = false;
            releaseMuxer();
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public String getRecordingFilePath() {
        return recordingOutputFilePath;
    }

    public void requestNewFile() {
        isNewFileRequested = true;
    }

    private void initializeMuxer() {
        if (mediaMuxer != null)
            return;

        MediaFormat videoTrackFormat = MediaFormat.createVideoFormat(videoMimeType, videoWidth, videoHeight);
        videoTrackFormat.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
        videoTrackFormat.setByteBuffer("csd-1", ByteBuffer.wrap(pps));

        String audioMimeType = MediaFormat.MIMETYPE_AUDIO_AAC;
        MediaFormat audioTrackFormat = MediaFormat.createAudioFormat(audioMimeType, audioSampleRateHz, audioChannels);

        try {
            recordingOutputFilePath = getCaptureFile(Environment.DIRECTORY_MOVIES, ".mp4").toString();
            mediaMuxer = new MediaMuxer(recordingOutputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mediaMuxer.addTrack(videoTrackFormat);
            mediaMuxer.addTrack(audioTrackFormat);
            mediaMuxer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void releaseMuxer() {
        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
        }
    }

    public byte[] getSps() {
        return sps;
    }

    public byte[] getPps() {
        return pps;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public int getAudioSampleRateHz() {
        return audioSampleRateHz;
    }

    public int getAudioChannels() {
        return audioChannels;
    }

    private RtspClient.RtspClientListener rtspClientListener = new RtspClient.RtspClientListener() {
        private String TAG = "RtspClientListener";

        @Override
        public void onRtspConnecting() {
            if (DEBUG) Log.d(TAG, "onRtspConnecting");
        }

        @Override
        public void onRtspConnected(@NonNull RtspClient.SdpInfo sdpInfo) {
            if (DEBUG) Log.d(TAG, "onRtspConnected");

            sps = sdpInfo.videoTrack.sps;
            pps = sdpInfo.videoTrack.pps;

            audioSampleRateHz = sdpInfo.audioTrack.sampleRateHz;
            audioChannels = sdpInfo.audioTrack.channels;
        }

        @Override
        public void onRtspVideoNalUnitReceived(@NonNull byte[] data, int offset, int length, long timestamp) {
            if (DEBUG) Log.d(TAG, "onRtspVideoNalUnitReceived");

            boolean isKeyFrame = (data[4] & 0x1f) == 5;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.presentationTimeUs = timestamp;
            bufferInfo.flags = isKeyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
            bufferInfo.size = length;
            ByteBuffer encodedData = ByteBuffer.wrap(data, offset, length);

            synchronized (isRecording) {
                if (isRecording) {
                    // Smooth record to the new file
                    if (isNewFileRequested && isKeyFrame) {
                        releaseMuxer();
                        isNewFileRequested = false;
                    }

                    if (mediaMuxer == null)
                        initializeMuxer();

                    mediaMuxer.writeSampleData(0, encodedData, bufferInfo);
                }
            }

            if (mRtspListener != null)
                mRtspListener.onRtspVideoDataReceived(encodedData, bufferInfo);
        }

        @Override
        public void onRtspAudioSampleReceived(@NonNull byte[] data, int offset, int length, long timestamp) {
            if (DEBUG) Log.d(TAG, "onRtspAudioSampleReceived");

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.presentationTimeUs = timestamp;
            bufferInfo.size = length;
            ByteBuffer encodedData = ByteBuffer.wrap(data, offset, length);

            synchronized (isRecording) {
                if (isRecording) {
                    if (mediaMuxer == null)
                        initializeMuxer();

                    mediaMuxer.writeSampleData(1, encodedData, bufferInfo);
                }
            }

            if (mRtspListener != null)
                mRtspListener.onRtspAudioDataReceived(encodedData, bufferInfo);
        }

        @Override
        public void onRtspDisconnecting() {
            if (DEBUG) Log.d(TAG, "onRtspDisconnecting");
        }

        @Override
        public void onRtspDisconnected() {
            if (DEBUG) Log.d(TAG, "onRtspDisconnected");

            stopRecording();
        }

        @Override
        public void onRtspFailedUnauthorized() {
            if (DEBUG) Log.d(TAG, "onRtspFailedUnauthorized");
        }

        @Override
        public void onRtspFailed(@Nullable String message) {
            if (DEBUG) Log.d(TAG, "onRtspFailed");
        }
    };

    class RtspThread extends Thread {
        private String TAG = "RtspThread";
        private AtomicBoolean rtspStopped = new AtomicBoolean(false);

        @Override
        public void run() {
            super.run();
            initializeRtsp();
        }

        public void stopAsync() {
            rtspStopped.set(true);
            // Wake up sleep() code
            interrupt();
        }

        private void initializeRtsp() {
            if (DEBUG) Log.d(TAG, "Starting");
            Uri uri = Uri.parse(rtspUrl);
            Socket socket = null;
            try {
                if (DEBUG) Log.d(TAG, "Creating Socket");
                socket = NetUtils.createSocketAndConnect(uri.getHost(), uri.getPort(), 5000);

                if (DEBUG) Log.d(TAG, "Creating RTSP Client");
                RtspClient rtspClient = new RtspClient.Builder(socket, uri.toString(), rtspStopped, rtspClientListener)
                        .requestVideo(true)
                        .requestAudio(true)
                        .withDebug(false)
                        .withUserAgent("RTSP Client")
                        .build();
                if (DEBUG) Log.d(TAG, "Executing");
                rtspClient.execute();

                if (DEBUG) Log.d(TAG, "Closing Socket");
                NetUtils.closeSocket(socket);
            } catch (IOException e) {
                e.printStackTrace();
                if (DEBUG) Log.e(TAG, e.getMessage());
            }
        }
    };

    private static final SimpleDateFormat mDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);

    private final File getCaptureFile(final String type, final String ext) {
        final File dir = new File(Environment.getExternalStoragePublicDirectory(type), recordingOutputDir);
        if (DEBUG) Log.d(TAG, "path=" + dir.toString());
        dir.mkdirs();
        if (dir.canWrite()) {
            return new File(dir, getDateTimeString() + ext);
        }
        return null;
    }

    private final String getDateTimeString() {
        final GregorianCalendar now = new GregorianCalendar();
        return mDateTimeFormat.format(now.getTime());
    }
}
