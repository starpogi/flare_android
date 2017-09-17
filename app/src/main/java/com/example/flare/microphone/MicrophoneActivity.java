package com.example.flare.microphone;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;

import com.example.flare.map.R;

import android.util.Log;

public class MicrophoneActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_microphone);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        Log.d("com.example.flare.mic", ""+ev.getAction());
        return true;
    }
}
