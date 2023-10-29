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

    String rtspUrl = "";
    int rtspVideoWidth = 1280;
    int rtspVideoHeight = 720;
    String recordingOutputDir = "IPCameraRecorder";

    RtspRecorder rtspRecorder;

    Button btnStartRecording;
    Button btnStopRecording;
    Button btnRecordToNewFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartRecording = (Button) findViewById(R.id.btnStartRecording);
        btnStopRecording = (Button) findViewById(R.id.btnStopRecording);
        btnRecordToNewFile = (Button) findViewById(R.id.btnRecordToNewFile);

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

        rtspRecorder = new RtspRecorder();
    }

    @Override
    protected void onResume() {
        super.onResume();

        rtspRecorder.startStreaming(rtspUrl);
    }

    @Override
    protected void onPause() {
        super.onPause();

        rtspRecorder.stopStreaming();
    }
}