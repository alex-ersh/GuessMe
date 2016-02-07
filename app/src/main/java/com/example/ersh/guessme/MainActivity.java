/*
The MIT License (MIT)

Copyright (c) 2016 alex-ersh

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

package com.example.ersh.guessme;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import static com.example.ersh.guessme.R.drawable.img_date_textview_correct;
import static com.example.ersh.guessme.R.drawable.img_date_textview_wrong;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    ImageView mImageView1;
    ImageView mImageView2;
    TextView mScoreTextView;
    TextView mImgDateTextView1;
    TextView mImgDateTextView2;
    View mImgAnswerStatusView1;
    View mImgAnswerStatusView2;
    View mActiveImgAnswerStatusView;
    ProgressBar mCenterProgressBarView;

    Animation mFadeInAnim;
    Animation mFadeOutAnim;

    private long mLastClickTime = 0;
    int mCurrentScore = 0;
    Boolean mImgChoosingPending = false;
    ImagePairProducer mImageProducer;
    ImagePairProducer.ImagePair mCurImagePair;
    AsyncTask<Void, Void, Boolean> mGetImageAsyncTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mImageView1 = (ImageView) findViewById(R.id.image_view_1);
        mImageView2 = (ImageView) findViewById(R.id.image_view_2);
        mScoreTextView = (TextView) findViewById(R.id.score_textview);
        mImgDateTextView1 = (TextView) findViewById(R.id.image1_date_textview);
        mImgDateTextView2 = (TextView) findViewById(R.id.image2_date_textview);
        mImgAnswerStatusView1 = findViewById(R.id.image1_answer_status_view);
        mImgAnswerStatusView2 = findViewById(R.id.image2_answer_status_view);
        mCenterProgressBarView = (ProgressBar) findViewById(R.id.center_progress_bar_view);

        mImgDateTextView1.setVisibility(View.INVISIBLE);
        mImgDateTextView2.setVisibility(View.INVISIBLE);
        mImgAnswerStatusView1.setVisibility(View.INVISIBLE);
        mImgAnswerStatusView2.setVisibility(View.INVISIBLE);

        mFadeInAnim = AnimationUtils.loadAnimation(this, R.anim.fadein);
        mFadeOutAnim = AnimationUtils.loadAnimation(this, R.anim.fadeout);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mImageProducer = ImagePairProducer.getInstance(getApplicationContext());
        mImageProducer.startImageFetch();
        onNextPair(null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mImageProducer.stopImageFetch();
        if (mGetImageAsyncTask != null) {
            mGetImageAsyncTask.cancel(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public void onNextPair(View v) {
        // mis-clicking prevention
        if (SystemClock.elapsedRealtime() - mLastClickTime < 300){
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();

        if (mImgChoosingPending) {
            if (v == mImageView1) {
                if (mCurImagePair.getDateFirst().before(mCurImagePair.getDateSecond())) {
                    mImgAnswerStatusView1.setBackgroundResource(R.drawable.tick);
                    mImgDateTextView1.setBackgroundResource(img_date_textview_correct);
                    mImgDateTextView2.setBackgroundResource(img_date_textview_wrong);
                    mCurrentScore++;
                } else {
                    mImgAnswerStatusView1.setBackgroundResource(R.drawable.cross);
                    mImgDateTextView1.setBackgroundResource(img_date_textview_wrong);
                    mImgDateTextView2.setBackgroundResource(img_date_textview_correct);
                    mCurrentScore = 0;
                }

                mActiveImgAnswerStatusView = mImgAnswerStatusView1;
                mImgAnswerStatusView2.clearAnimation();
            } else if (v == mImageView2) {
                if (mCurImagePair.getDateSecond().before(mCurImagePair.getDateFirst())) {
                    mImgAnswerStatusView2.setBackgroundResource(R.drawable.tick);
                    mImgDateTextView1.setBackgroundResource(img_date_textview_wrong);
                    mImgDateTextView2.setBackgroundResource(img_date_textview_correct);
                    mCurrentScore++;
                } else {
                    mImgAnswerStatusView2.setBackgroundResource(R.drawable.cross);
                    mImgDateTextView1.setBackgroundResource(img_date_textview_correct);
                    mImgDateTextView2.setBackgroundResource(img_date_textview_wrong);
                    mCurrentScore = 0;
                }

                mActiveImgAnswerStatusView = mImgAnswerStatusView2;
                mImgAnswerStatusView1.clearAnimation();
            }

            mActiveImgAnswerStatusView.startAnimation(mFadeOutAnim);

            SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US);
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            mImgDateTextView1.setText(formatter.format(mCurImagePair.getDateFirst()));
            mImgDateTextView1.startAnimation(mFadeOutAnim);
            mImgDateTextView2.setText(formatter.format(mCurImagePair.getDateSecond()));
            mImgDateTextView2.startAnimation(mFadeOutAnim);
            mScoreTextView.setText(String.valueOf(mCurrentScore));
            mImgChoosingPending = false;
            return;
        }

        mImgDateTextView1.startAnimation(mFadeInAnim);
        mImgDateTextView2.startAnimation(mFadeInAnim);
        if (mActiveImgAnswerStatusView != null) {
            mActiveImgAnswerStatusView.startAnimation(mFadeInAnim);
        }
        mImageView1.startAnimation(mFadeInAnim);
        mImageView2.startAnimation(mFadeInAnim);
        mCenterProgressBarView.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            mGetImageAsyncTask = new GetImageTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        else
            mGetImageAsyncTask = new GetImageTask().execute();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class GetImageTask extends AsyncTask<Void, Void, Boolean> {
        private static final String TAG = "GetImageTask";

        @Override
        protected Boolean doInBackground(Void... params) {
            int tries = 0;
            try {
                while (tries++ < 20) {
                    mCurImagePair = mImageProducer.getNextImagePair();
                    if (mCurImagePair != null) {
                        //Log.d(TAG, "Got image pair");
                        return true;
                    }
                    Thread.sleep(250);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage());
            }

            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                Snackbar.make(findViewById(R.id.image_view_1), getString(R.string.err_get_image_pair), Snackbar.LENGTH_SHORT).show();
            }
            else {
                mImageView1.setImageBitmap(mCurImagePair.getImageFirst());
                mImageView1.startAnimation(mFadeOutAnim);
                mImageView2.setImageBitmap(mCurImagePair.getImageSecond());
                mImageView2.startAnimation(mFadeOutAnim);
                mImgChoosingPending = true;
            }

            mCenterProgressBarView.setVisibility(View.INVISIBLE);
        }
    }
}
