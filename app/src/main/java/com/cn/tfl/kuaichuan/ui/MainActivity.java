package com.cn.tfl.kuaichuan.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.cn.tfl.kuaichuan.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void send(View view) {
        Intent intent = new Intent(MainActivity.this, ChooseFileActivity.class);
        startActivity(intent);

    }


    public void receiver(View view) {
        Intent intent = new Intent(MainActivity.this, ReceiverWaitingActivity.class);
        startActivity(intent);
    }
}
