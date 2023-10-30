package com.nuuneoi.rtsplab;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.alexvas.rtsp.RtspClient;
import com.alexvas.utils.NetUtils;
import com.pedro.rtmp.utils.ConnectCheckerRtmp;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    String rtspSourceUrl = "";
    String rtmpBroadcastUrl = "";

    int rtspVideoWidth = 1280;
    int rtspVideoHeight = 720;
    String recordingOutputDir = "IPCameraRecorder";

    RtspRecorder rtspRecorder;
    RtmpStreamer rtmpStreamer;

    Button btnStartRecording;
    Button btnStopRecording;
    Button btnRecordToNewFile;

    Button btnStartStreaming;
    Button btnStopStreaming;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartRecording = (Button) findViewById(R.id.btnStartRecording);
        btnStopRecording = (Button) findViewById(R.id.btnStopRecording);
        btnRecordToNewFile = (Button) findViewById(R.id.btnRecordToNewFile);

        btnStartStreaming = (Button) findViewById(R.id.btnStartStreaming);
        btnStopStreaming = (Button) findViewById(R.id.btnStopStreaming);

        btnStartRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rtspRecorder.startRecording(recordingOutputDir, "video/avc", rtspVideoWidth, rtspVideoHeight);
                btnStartRecording.setVisibility(View.GONE);
                btnStopRecording.setVisibility(View.VISIBLE);
                btnRecordToNewFile.setVisibility(View.VISIBLE);
            }
        });

        btnStopRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rtspRecorder.stopRecording();
                btnStartRecording.setVisibility(View.VISIBLE);
                btnStopRecording.setVisibility(View.GONE);
                btnRecordToNewFile.setVisibility(View.GONE);
            }
        });

        btnRecordToNewFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rtspRecorder.requestNewFile();
            }
        });

        btnStartStreaming.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnStartStreaming.setVisibility(View.GONE);
                btnStopStreaming.setVisibility(View.VISIBLE);
                rtmpStreamer.startStreaming(rtmpBroadcastUrl, false);
            }
        });

        btnStopStreaming.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnStartStreaming.setVisibility(View.VISIBLE);
                btnStopStreaming.setVisibility(View.GONE);
                rtmpStreamer.stopStreaming();
            }
        });

        rtspRecorder = new RtspRecorder();
        rtmpStreamer = new RtmpStreamer();
        rtmpStreamer.setConnectCheckerRtmpListener(rtmpConnectionListner);
        rtmpStreamer.setInputSource(rtspRecorder);
    }

    @Override
    protected void onResume() {
        super.onResume();

        rtspRecorder.startStreaming(rtspSourceUrl);
    }

    @Override
    protected void onPause() {
        super.onPause();

        rtspRecorder.stopStreaming();
    }

    ConnectCheckerRtmp rtmpConnectionListner = new ConnectCheckerRtmp() {
        @Override
        public void onConnectionStartedRtmp(@NonNull String s) {

        }

        @Override
        public void onConnectionSuccessRtmp() {

        }

        @Override
        public void onConnectionFailedRtmp(@NonNull String s) {
            btnStartStreaming.setVisibility(View.VISIBLE);
            btnStopStreaming.setVisibility(View.GONE);
            rtmpStreamer.stopStreaming();
        }

        @Override
        public void onNewBitrateRtmp(long l) {

        }

        @Override
        public void onDisconnectRtmp() {
            btnStartStreaming.setVisibility(View.VISIBLE);
            btnStopStreaming.setVisibility(View.GONE);
            rtmpStreamer.stopStreaming();
        }

        @Override
        public void onAuthErrorRtmp() {

        }

        @Override
        public void onAuthSuccessRtmp() {

        }
    };
}