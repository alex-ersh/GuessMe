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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.support.design.widget.Snackbar;
import android.util.Log;

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
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

public class ImagePairProducer {
    private static final String TAG = "ImagePairProducer";

    private static final int IMAGE_PAIR_AMOUNT = 3;
    private Vector<String> mFilenameArray = new Vector<>();
    private LinkedList<ImagePair> mImagePairArray = new LinkedList<>();
    private JSch mJsch;
    private Session mSession;
    private ChannelSftp mSftp;
    private Boolean mIsConnected = false;
    private int mCurId1 = -1;
    private int mCurId2 = -1;
    private boolean mRunning = false;
    private AsyncTask<Void, Void, Boolean> mFetchAsyncTask;
    private Context mContext;
    private static ImagePairProducer mInstance;
    private Random m_Rand;

    public static ImagePairProducer getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new ImagePairProducer(context);
        }
        return mInstance;
    }

    private ImagePairProducer(Context context) {
        mContext = context;
    }

    public void startImageFetch() {
        if (mRunning) {
            return;
        }

        m_Rand = new Random();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            mFetchAsyncTask = new FetchImagePairsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        else
            mFetchAsyncTask = new FetchImagePairsTask().execute();
    }

    public void stopImageFetch() {
        mRunning = false;
        mFetchAsyncTask.cancel(true);
    }

    public ImagePair getNextImagePair() {
        synchronized (this) {
            if (!mImagePairArray.isEmpty()) {
                return mImagePairArray.remove();
            }
        }

        return null;
    }

    public class ImagePair {
        private static final String TAG = "ImagePair";

        private Bitmap mImage1;
        private Bitmap mImage2;
        private Date mDate1;
        private Date mDate2;

        public Date getDateFirst() {
            return mDate1;
        }

        public Date getDateSecond() {
            return mDate2;
        }

        public void setDateFirst(Date date) {
            mDate1 = date;
        }

        public void setDateSecond(Date date) {
            mDate2 = date;
        }

        public Bitmap getImageFirst() {
            return mImage1;
        }

        public Bitmap getImageSecond() {
            return mImage2;
        }

        public void setImageFirst(Bitmap image) {
            mImage1 = image;
        }

        public void setImageSecond(Bitmap image) {
            mImage2 = image;
        }
    }

    private class IdPair {
        private int mId1;
        private int mId2;

        IdPair(int id1, int id2) {
            mId1 = id1;
            mId2 = id2;
        }

        int getIdFirst() {
            return mId1;
        }

        int getIdSecond() {
            return mId2;
        }
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
        mFilenameArray.clear();
        mImagePairArray.clear();
        mCurId1 = -1;
        mCurId2 = -1;
    }

    private void connect() throws JSchException, SftpException {
        mIsConnected = false;
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
                //Log.d(TAG, name);
                mFilenameArray.add(name);
            }
        }

        //Log.i(TAG, mContext.getString(R.string.regular_files_on_server) + mFilenameArray.size());
        mIsConnected = true;
    }

    private IdPair calcNextIdPair() throws RuntimeException {
        if (!mIsConnected) {
            throw new RuntimeException(mContext.getString(R.string.err_not_connected));
        }

        if (mFilenameArray.size() < 3) {
            throw new RuntimeException(mContext.getString(R.string.err_no_images_names));
        }

        final int MAX_TRIES = 100;
        int tries = 0;
        int val;

        while (true) {
            tries++;
            if (tries > MAX_TRIES) {
                throw new RuntimeException(mContext.getString(R.string.err_rand_gen_tries));
            }
            val = m_Rand.nextInt(mFilenameArray.size());
            if (val == mCurId1) { continue; }
            mCurId1 = val;

            while (true) {
                tries++;
                if (tries > MAX_TRIES) {
                    throw new RuntimeException(mContext.getString(R.string.err_rand_gen_tries));
                }
                val = m_Rand.nextInt(mFilenameArray.size());
                if ((val == mCurId2) || (val == mCurId1)) { continue; }
                mCurId2 = val;
                break;
            }

            break;
        }

        return new IdPair(mCurId1, mCurId2);
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

    private class FetchImagePairsTask extends AsyncTask<Void, Void, Boolean> {
        private static final String TAG = "FetchImagePairsTask";

        @Override
        protected Boolean doInBackground(Void... params) {
            mRunning = true;

            try {
                while (mRunning) {
                    if (isCancelled()) {
                        return false;
                    }

                    if (!mIsConnected) {
                        connect();
                    }

                    IdPair idPair = calcNextIdPair();
                    ImagePair imagePair = new ImagePair();
                    ByteArrayOutputStream imgRaw = new ByteArrayOutputStream();

                    try {
                        if (isCancelled()) {
                            return false;
                        }

                        mSftp.get(mFilenameArray.get(idPair.getIdFirst()), imgRaw);
                        byte[] barray = imgRaw.toByteArray();
                        imagePair.setImageFirst(BitmapFactory.decodeByteArray(barray, 0, barray.length));
                        if (imagePair.getImageFirst() == null) {
                            throw new RuntimeException(mContext.getString(R.string.err_decode_img)
                                    + " " + mFilenameArray.get(idPair.getIdFirst()));
                        }
                        imagePair.setDateFirst(getExifDate(barray));
                        if (imagePair.getDateFirst() == null) {
                            throw new RuntimeException(mContext.getString(R.string.err_no_exif)
                                    + " " + mFilenameArray.get(idPair.getIdFirst()));
                        }

                        if (isCancelled()) {
                            return false;
                        }

                        imgRaw.reset();
                        mSftp.get(mFilenameArray.get(idPair.getIdSecond()), imgRaw);
                        barray = imgRaw.toByteArray();
                        imagePair.setImageSecond(BitmapFactory.decodeByteArray(barray, 0, barray.length));
                        if (imagePair.getImageSecond() == null) {
                            throw new RuntimeException(mContext.getString(R.string.err_decode_img)
                                    + " " + mFilenameArray.get(idPair.getIdSecond()));
                        }
                        imagePair.setDateSecond(getExifDate(barray));
                        if (imagePair.getDateSecond() == null) {
                            throw new RuntimeException(mContext.getString(R.string.err_no_exif)
                                    + " " + mFilenameArray.get(idPair.getIdSecond()));
                        }
                    } catch (RuntimeException e) {
                        Log.e(TAG, e.getMessage());
                        continue;
                    }

                    //Log.d(TAG, "Fetched new Image pair: " + mFilenameArray.get(idPair.getIdFirst())
                    //    + ", " + mFilenameArray.get(idPair.getIdSecond()));

                    while (mRunning) {
                        if (isCancelled()) {
                            return false;
                        }

                        synchronized (this) {
                            if (mImagePairArray.size() < IMAGE_PAIR_AMOUNT) {
                                mImagePairArray.add(imagePair);
                                //Log.d(TAG, "Added new pair to array");
                                break;
                            }
                        }
                        Thread.sleep(250);
                    }
                }
            } catch (SftpException e) {
                Log.e(TAG, e.getMessage());
                return false;
            } catch (JSchException e) {
                Log.e(TAG, e.getMessage());
                return false;
            } catch (ImageProcessingException e) {
                Log.e(TAG, e.getMessage());
                return false;
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                return false;
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage());
                return false;
            }

            return true;
        }

        @Override
        protected void onCancelled(Boolean result) {
            closeSftp();
            mRunning = false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                //Snackbar.make(findViewById(R.id.image_view_1), mContext.getString(R.string.err_fetch_thread), Snackbar.LENGTH_SHORT).show();
            }

            closeSftp();
            mRunning = false;
        }
    }
}
