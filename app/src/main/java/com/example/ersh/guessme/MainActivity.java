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

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Random;
import java.util.Vector;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    ImageView mImageView1;
    ImageView mImageView2;
    ProgressDialog mProgressDialog;
    Bitmap mCurrentImage1;
    Bitmap mCurrentImage2;
    TextView mScoreTextView;

    JSch mJsch;
    Session mSession;
    ChannelSftp mSftp;

    Vector<String> mFilenameArray = new Vector<>();
    Boolean mIsConnected = false;
    int mCurrentId1 = -1;
    int mCurrentId2 = -1;
    Date mCurrentDate1;
    Date mCurrentDate2;
    int mCurrentScore = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mImageView1 = (ImageView) findViewById(R.id.image_view_1);
        mImageView2 = (ImageView) findViewById(R.id.image_view_2);
        mScoreTextView = (TextView) findViewById(R.id.score_textview);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mProgressDialog = ProgressDialog.show(this, getString(R.string.waiting_for_connection),
                "", true, true);
        new ConnectSftpTask().execute();
    }

    @Override
    protected void onStop() {
        super.onStop();

        mFilenameArray.clear();
        mCurrentId1 = -1;
        mCurrentId2 = -1;
        closeSftp();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public void onNextPair(View v) {
        if (v == mImageView1) {
            if (mCurrentDate1.before(mCurrentDate2)) {
                Snackbar.make(mImageView1, getString(R.string.answer_correct), Snackbar.LENGTH_SHORT).show();
                mCurrentScore++;
            }
            else {
                Snackbar.make(mImageView1, getString(R.string.answer_incorrect), Snackbar.LENGTH_SHORT).show();
                mCurrentScore = 0;
            }
        } else if (v == mImageView2) {
            if (mCurrentDate2.before(mCurrentDate1)) {
                Snackbar.make(mImageView1, getString(R.string.answer_correct), Snackbar.LENGTH_SHORT).show();
                mCurrentScore++;
            }
            else {
                Snackbar.make(mImageView1, getString(R.string.answer_incorrect), Snackbar.LENGTH_SHORT).show();
                mCurrentScore = 0;
            }
        }

        mImageView1.setImageResource(android.R.color.transparent);
        mImageView2.setImageResource(android.R.color.transparent);
        mScoreTextView.setText(String.valueOf(mCurrentScore));
        loadNextPair();
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

    private void closeSftp() {
        if (mSftp != null) {
            mSftp.disconnect();
            mSftp = null;
        }

        if (mSession != null) {
            mSession.disconnect();
            mSession = null;
        }

        mJsch = null;
        mIsConnected = false;
    }

    private void loadNextPair() {
        try {
            if (!mIsConnected) {
                throw new RuntimeException(getString(R.string.err_not_connected));
            }

            if (mFilenameArray.size() < 3) {
                throw new RuntimeException(getString(R.string.err_no_images_names));
            }

            Random r = new Random();
            final int MAX_TRIES = 100;
            int tries = 0;
            int val;

            while (true) {
                tries++;
                if (tries > MAX_TRIES) {
                    throw new RuntimeException(getString(R.string.err_rand_gen_tries));
                }
                val = r.nextInt(mFilenameArray.size());
                if (val == mCurrentId1) { continue; }
                mCurrentId1 = val;

                while (true) {
                    tries++;
                    if (tries > MAX_TRIES) {
                        throw new RuntimeException(getString(R.string.err_rand_gen_tries));
                    }
                    val = r.nextInt(mFilenameArray.size());
                    if ((val == mCurrentId2) || (val == mCurrentId1)) { continue; }
                    mCurrentId2 = val;
                    break;
                }

                break;
            }

            new FetchNewImagePairTask().execute(mCurrentId1, mCurrentId2);
        } catch (RuntimeException e) {
            Log.e(TAG, e.getMessage());
            mCurrentId1 = -1;
            mCurrentId2 = -1;
        }
    }

    private class ConnectSftpTask extends AsyncTask<Void, Void, Boolean> {
        private static final String TAG = "ConnectSftpTask";

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                closeSftp();

                mJsch = new JSch();
                mJsch.addIdentity(SftpAccessInfo.SFTP_USER,
                        SftpAccessInfo.SFTP_PRIVATE_KEY.getBytes(), null,
                        SftpAccessInfo.SFTP_PASS.getBytes());
                JSch.setConfig("StrictHostKeyChecking", "no");

                mSession = mJsch.getSession(SftpAccessInfo.SFTP_USER,
                        SftpAccessInfo.SFTP_HOST, SftpAccessInfo.SFTP_PORT);
                mSession.connect();

                mSftp = (ChannelSftp) mSession.openChannel("sftp");
                mSftp.connect();
                @SuppressWarnings("unchecked")
                Vector<ChannelSftp.LsEntry> filelist = mSftp.ls(".");

                mFilenameArray.clear();
                for (ChannelSftp.LsEntry file : filelist) {
                    if (file.getAttrs().isReg())
                    {
                        String name = file.getFilename();
                        Log.d(TAG, name);
                        mFilenameArray.add(name);
                    }
                }

                Log.i(TAG, getString(R.string.regular_files_on_server) + mFilenameArray.size());
                mIsConnected = true;
                return true;
            } catch (SftpException e) {
                Log.e(TAG, e.getMessage());
                closeSftp();
                return false;
            } catch (JSchException e) {
                Log.e(TAG, e.getMessage());
                closeSftp();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            mProgressDialog.dismiss();
            mProgressDialog = null;

            if (result) {
                Snackbar.make(mImageView1, getString(R.string.connect_success), Snackbar.LENGTH_SHORT).show();
                loadNextPair();
            } else {
                Snackbar.make(mImageView1, getString(R.string.connect_fail), Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private class FetchNewImagePairTask extends AsyncTask<Integer, Void, Boolean> {
        private static final String TAG = "FetchNewImagePairTask";

        @Override
        protected Boolean doInBackground(Integer... params) {
            try {
                if (params.length != 2) {
                    throw new RuntimeException(getString(R.string.err_incorrect_input));
                }

                if ((params[0] < 0) || (params[0] >= mFilenameArray.size())
                        || (params[1] < 0) || (params[1] >= mFilenameArray.size())) {
                    throw new RuntimeException(getString(R.string.err_incorrect_input_values));
                }

                if (!mIsConnected) {
                    throw new RuntimeException(getString(R.string.err_not_connected));
                }

                ByteArrayOutputStream imgRaw = new ByteArrayOutputStream();

                mSftp.get(mFilenameArray.get(params[0]), imgRaw);
                byte[] barray = imgRaw.toByteArray();
                mCurrentImage1 = BitmapFactory.decodeByteArray(barray, 0, barray.length);
                if (mCurrentImage1 == null) {
                    throw new RuntimeException(getString(R.string.err_decode_img)
                            + " " + mFilenameArray.get(params[0]));
                }
                mCurrentDate1 = getExifDate(barray);
                if (mCurrentDate1 == null) {
                    throw new RuntimeException(getString(R.string.err_no_exif)
                            + " " + mFilenameArray.get(params[0]));
                }

                imgRaw.reset();
                mSftp.get(mFilenameArray.get(params[1]), imgRaw);
                barray = imgRaw.toByteArray();
                mCurrentImage2 = BitmapFactory.decodeByteArray(barray, 0, barray.length);
                if (mCurrentImage2 == null) {
                    throw new RuntimeException(getString(R.string.err_decode_img)
                            + " " + mFilenameArray.get(params[1]));
                }
                mCurrentDate2 = getExifDate(barray);
                if (mCurrentDate2 == null) {
                    throw new RuntimeException(getString(R.string.err_no_exif)
                            + " " + mFilenameArray.get(params[1]));
                }

                return true;
            } catch (SftpException e) {
                Log.e(TAG, e.getMessage());
                return false;
            } catch (RuntimeException e) {
                Log.e(TAG, e.getMessage());
                return false;
            } catch (ImageProcessingException e) {
                Log.e(TAG, e.getMessage());
                return false;
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                mImageView1.setImageBitmap(mCurrentImage1);
                mImageView2.setImageBitmap(mCurrentImage2);
            }
        }

        private Date getExifDate(byte[] imgRaw) throws ImageProcessingException, IOException {
            Metadata metadata = ImageMetadataReader.readMetadata(
                    new BufferedInputStream(new ByteArrayInputStream(imgRaw)));
            ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (directory == null) {
                return null;
            }
            return directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
        }
    }
}
