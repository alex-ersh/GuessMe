/*
The MIT License (MIT)

Copyright (c) 2017 alex-ersh

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package xyz.ershweb.guessme;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.ExecutionException;

public class WelcomeActivity extends AppCompatActivity {
    private static final String TAG = "WelcomeActivity";
    // Very bad implementation due to animation problems.
    // 5 views instead of 3.
    TextView mWelcomeTextView1_1;
    TextView mWelcomeTextView2_1;

    TextView mWelcomeTextView1_2;
    TextView mWelcomeTextView2_2;
    TextView mWelcomeTextView3;
    ProgressBar mLoggingProgressBarView;
    boolean mWelcomeDone = false;
    boolean mLoggedIn = false;
    boolean mLoggingIn = false;
    String mLastHost;
    String mLastUsername;
    AlertDialog mLoginDialog;
    LoginTask mLoginTask;
    static final long ANIM_DURATION = 1000;

    private void promptLogin() {
        @SuppressLint("InflateParams")
        final View login_dlg = LayoutInflater.from(WelcomeActivity.this).inflate(R.layout.login_dialog, null);
        final EditText host_view = (EditText) login_dlg.findViewById(R.id.login_host_edittext);
        final EditText username_view = (EditText) login_dlg.findViewById(R.id.login_u_edittext);
        final EditText password_view = (EditText) login_dlg.findViewById(R.id.login_p_edittext);
        mLoginDialog = new AlertDialog.Builder(WelcomeActivity.this)
                .setTitle(getString(R.string.login_dlg_title))
                .setView(login_dlg)
                .setPositiveButton(getString(R.string.ok_button), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        mLastHost = host_view.getText().toString();
                        mLastUsername = username_view.getText().toString();
                        String password = password_view.getText().toString();
                        startLoggingIn(mLastHost, mLastUsername, password);
                    }
                })
                .create();
        mLoginDialog.show();
        host_view.setText(mLastHost);
        username_view.setText(mLastUsername);
    }

    private void startLoggingIn(String host, String username, String password) {
        mLoggingProgressBarView.setVisibility(View.VISIBLE);
        mLoggingIn = true;
        mLoginTask = new LoginTask();
        mLoginTask.execute(host, username, password);
    }

    private void tryLogin() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        mLastHost = sharedPref.getString(getString(R.string.saved_img_host), "");
        mLastUsername = sharedPref.getString(getString(R.string.saved_u), "");
        String saved_password = sharedPref.getString(getString(R.string.saved_p), "");
        if (mLastHost.isEmpty()) {
            promptLogin();
        } else {
            startLoggingIn(mLastHost, mLastUsername, saved_password);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        mWelcomeTextView1_1 = (TextView) findViewById(R.id.welcome_textview1_1);
        mWelcomeTextView2_1 = (TextView) findViewById(R.id.welcome_textview2_1);
        mWelcomeTextView1_2 = (TextView) findViewById(R.id.welcome_textview1_2);
        mWelcomeTextView2_2 = (TextView) findViewById(R.id.welcome_textview2_2);
        mWelcomeTextView3 = (TextView) findViewById(R.id.welcome_textview3);
        mLoggingProgressBarView = (ProgressBar) findViewById(R.id.logging_progress_bar_view);
        mLoggingProgressBarView.setVisibility(View.INVISIBLE);
        tryLogin();
    }

    @Override
    protected void onPause() {
        if (mLoginDialog != null) {
            mLoginDialog.dismiss();
        }
        if (mLoginTask != null) {
            try {
                mLoginTask.get();
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, e.getMessage());
            }
        }

        super.onPause();
    }

    private void startAnimation() {
        Animation anim1 = AnimationUtils.loadAnimation(WelcomeActivity.this, android.R.anim.slide_in_left);
        anim1.setDuration(ANIM_DURATION);
        Animation anim2 = AnimationUtils.loadAnimation(WelcomeActivity.this, android.R.anim.slide_in_left);
        anim2.setDuration(ANIM_DURATION);
        anim2.setStartOffset(800);
        mWelcomeTextView1_1.setText(getString(R.string.welcome_1_part1));
        mWelcomeTextView1_1.startAnimation(anim1);
        mWelcomeTextView2_1.setText(getString(R.string.welcome_1_part2));
        mWelcomeTextView2_1.startAnimation(anim2);
    }

    public void onContinue(View v) {
        if (mLoggingIn) {
            return;
        }

        if (!mLoggedIn) {
            tryLogin();
            return;
        }

        if (!mWelcomeDone) {
            mWelcomeTextView1_1.clearAnimation();
            mWelcomeTextView2_1.clearAnimation();
            mWelcomeTextView1_1.setVisibility(View.GONE);
            mWelcomeTextView2_1.setVisibility(View.GONE);

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
            mWelcomeTextView1_2.setText(getString(R.string.welcome_2_part1));
            mWelcomeTextView1_2.startAnimation(anim1);
            mWelcomeTextView2_2.setText(getString(R.string.welcome_2_part2));
            mWelcomeTextView2_2.startAnimation(anim2);
            mWelcomeTextView3.setText(getString(R.string.welcome_2_part3));
            mWelcomeTextView3.startAnimation(anim3);
            mWelcomeDone = true;
            return;
        }

        Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private class LoginTask extends AsyncTask<String, Void, ImagePairProducer.CredentialsCheckStatus> {
        String mHost;
        String mUsername;
        String mPassword;

        @Override
        protected ImagePairProducer.CredentialsCheckStatus doInBackground(String... params) {
            mHost = params[0];
            mUsername = params[1];
            mPassword = params[2];
            ImagePairProducer imp = ImagePairProducer.getInstance(getResources());
            return imp.setAuth(mHost, mUsername, mPassword);
        }

        @Override
        protected void onPostExecute(ImagePairProducer.CredentialsCheckStatus result) {
            mLoggingIn = false;
            mLoggedIn = false;
            mLoggingProgressBarView.setVisibility(View.INVISIBLE);

            if (result == ImagePairProducer.CredentialsCheckStatus.OK) {
                mLoggedIn = true;
                SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(getString(R.string.saved_img_host), mHost);
                editor.putString(getString(R.string.saved_u), mUsername);
                editor.putString(getString(R.string.saved_p), mPassword);
                editor.apply();
                ImagePairProducer.getInstance(getResources()).startImageFetch();
                startAnimation();
            } else if (result == ImagePairProducer.CredentialsCheckStatus.WRONG_CREDENTIALS) {
                promptLogin();
            } else {
                Snackbar.make(mWelcomeTextView1_1,
                        getString(R.string.connect_fail), Snackbar.LENGTH_SHORT).show();
            }
        }
    }
}
