package com.example.ersh.guessme;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

public class WelcomeActivity extends AppCompatActivity {
    private static final String TAG = "WelcomeActivity";

    TextView mWelcomeTextView1;
    TextView mWelcomeTextView2;
    TextView mWelcomeTextView3;
    boolean mWelcomeDone = false;
    static final long ANIM_DURATION = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        ImagePairProducer.getInstance(getApplicationContext()).startImageFetch();
        mWelcomeTextView1 = (TextView) findViewById(R.id.welcome_textview1);
        mWelcomeTextView2 = (TextView) findViewById(R.id.welcome_textview2);
        mWelcomeTextView3 = (TextView) findViewById(R.id.welcome_textview3);
        Animation anim1 = AnimationUtils.loadAnimation(WelcomeActivity.this, android.R.anim.slide_in_left);
        anim1.setDuration(ANIM_DURATION);
        Animation anim2 = AnimationUtils.loadAnimation(WelcomeActivity.this, android.R.anim.slide_in_left);
        anim2.setDuration(ANIM_DURATION);
        anim2.setStartOffset(800);
        mWelcomeTextView1.setText(getString(R.string.welcome_1_part1));
        mWelcomeTextView1.startAnimation(anim1);
        mWelcomeTextView2.setText(getString(R.string.welcome_1_part2));
        mWelcomeTextView2.startAnimation(anim2);
    }

    public void onContinue(View v) {
        if (!mWelcomeDone) {
            Animation anim1 = AnimationUtils.loadAnimation(WelcomeActivity.this, android.R.anim.fade_in);
            anim1.setDuration(ANIM_DURATION);
            anim1.setFillAfter(true);
            anim1.setStartOffset(200);
            Animation anim2 = AnimationUtils.loadAnimation(WelcomeActivity.this, android.R.anim.fade_in);
            anim2.setDuration(ANIM_DURATION);
            anim2.setFillAfter(true);
            anim2.setStartOffset(700);
            Animation anim3 = AnimationUtils.loadAnimation(WelcomeActivity.this, android.R.anim.fade_in);
            anim3.setDuration(ANIM_DURATION);
            anim3.setFillAfter(true);
            anim3.setStartOffset(1200);
            mWelcomeTextView1.setText(getString(R.string.welcome_2_part1));
            mWelcomeTextView1.startAnimation(anim1);
            mWelcomeTextView2.setText(getString(R.string.welcome_2_part2));
            mWelcomeTextView2.startAnimation(anim2);
            mWelcomeTextView3.setText(getString(R.string.welcome_2_part3));
            mWelcomeTextView3.startAnimation(anim3);
            mWelcomeDone = true;
            return;
        }

        Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
