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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Date;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

class ImagePairProducer {
    private static final String TAG = "ImagePairHttpProducer";

    private static final int IMAGE_PAIR_AMOUNT = 3;
    private Vector<String> mFilenameArray = new Vector<>();
    private LinkedList<ImagePair> mImagePairArray = new LinkedList<>();
    private int mCurId1 = -1;
    private int mCurId2 = -1;
    private boolean mRunning = false;
    private AsyncTask<Void, Void, Boolean> mFetchAsyncTask;
    private Resources mResources;
    private static ImagePairProducer mInstance;
    private Random m_Rand;
    private SSLContext mSslContext;
    private String mAuthStr;
    private String mHost;

    static ImagePairProducer getInstance(Resources resources) {
        if (mInstance == null) {
            mInstance = new ImagePairProducer(resources);
        }
        return mInstance;
    }

    private ImagePairProducer(Resources resources) {
        mResources = resources;
        mSslContext = GetSSLContext();
    }

    private SSLContext GetSSLContext() {
        // Load CAs from an InputStream
        SSLContext context;
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate ca;
            InputStream caInput = new BufferedInputStream(
                    mResources.openRawResource(R.raw.ershweb_ca));
            ca = cf.generateCertificate(caInput);

            // Create a KeyStore containing our trusted CAs
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            // Create a TrustManager that trusts the CAs in our KeyStore
            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(keyStore);

            // Create an SSLContext that uses our TrustManager
            context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);
        } catch (Exception e) {
            return null;
        }

        return context;
    }

    enum CredentialsCheckStatus {
        CONNECT_ERROR, OK, WRONG_CREDENTIALS
    }

    CredentialsCheckStatus setAuth(String host, String username, String password) {
        mHost = host;
        mAuthStr = "Basic " + Base64.encodeToString((username + ":" + password).getBytes(),
                Base64.NO_WRAP);

        try {
            HttpsURLConnection connection = getConnection(mHost);
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpsURLConnection.HTTP_OK) {
                mAuthStr = null;
                mHost = null;
                if (responseCode == HttpsURLConnection.HTTP_UNAUTHORIZED) {
                    return CredentialsCheckStatus.WRONG_CREDENTIALS;
                } else {
                    return CredentialsCheckStatus.CONNECT_ERROR;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            return CredentialsCheckStatus.CONNECT_ERROR;
        }

        return CredentialsCheckStatus.OK;
    }

    void startImageFetch() {
        if (mRunning) {
            return;
        }

        m_Rand = new Random();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            mFetchAsyncTask = new FetchImagePairsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        else
            mFetchAsyncTask = new FetchImagePairsTask().execute();
    }

    void stopImageFetch() {
        mRunning = false;
        mFetchAsyncTask.cancel(true);
    }

    ImagePair getNextImagePair() {
        synchronized (this) {
            if (!mImagePairArray.isEmpty()) {
                return mImagePairArray.remove();
            }
        }

        return null;
    }

    class ImagePair {
        private Bitmap mImage1;
        private Bitmap mImage2;
        private Date mDate1;
        private Date mDate2;

        Date getDateFirst() {
            return mDate1;
        }

        Date getDateSecond() {
            return mDate2;
        }

        void setDateFirst(Date date) {
            mDate1 = date;
        }

        void setDateSecond(Date date) {
            mDate2 = date;
        }

        Bitmap getImageFirst() {
            return mImage1;
        }

        Bitmap getImageSecond() {
            return mImage2;
        }

        void setImageFirst(Bitmap image) {
            mImage1 = image;
        }

        void setImageSecond(Bitmap image) {
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

    private void clear() {
        mFilenameArray.clear();
        mImagePairArray.clear();
        mCurId1 = -1;
        mCurId2 = -1;
    }

    private HttpsURLConnection getConnection(String url) throws IOException {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(url).openConnection();
            connection.setSSLSocketFactory(mSslContext.getSocketFactory());
            connection.setReadTimeout(2000);
            connection.setConnectTimeout(2000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", mAuthStr);
            connection.setDoInput(true);
            connection.connect();
        } catch (IOException e) {
            if (connection != null) {
                connection.disconnect();
            }
            throw e;
        }
        return connection;
    }

    private byte[] getImage(String imageUrl) throws IOException {
        HttpsURLConnection connection = getConnection(imageUrl);
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpsURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }

        InputStream is = connection.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    private void getImageListFromServer() {
        mFilenameArray.clear();

        try {
            HttpsURLConnection connection = getConnection(mHost);
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpsURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode);
            }

            String regexp = "^" + mHost + "(.+)\\.jpg$";
            Pattern pattern = Pattern.compile(regexp);

            Document doc = Jsoup.parse(connection.getInputStream(), "UTF-8", mHost);
            for (Element element : doc.select("a")) {
                String link = element.attr("abs:href");
                if (pattern.matcher(link).matches()) {
                    mFilenameArray.add(link);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private IdPair calcNextIdPair() throws RuntimeException {
        if (mFilenameArray.size() < 3) {
            throw new RuntimeException(mResources.getString(R.string.err_no_images_names));
        }

        final int MAX_TRIES = 100;
        int tries = 0;
        int val;

        while (true) {
            tries++;
            if (tries > MAX_TRIES) {
                throw new RuntimeException(mResources.getString(R.string.err_rand_gen_tries));
            }
            val = m_Rand.nextInt(mFilenameArray.size());
            if (val == mCurId1) {
                continue;
            }
            mCurId1 = val;

            while (true) {
                tries++;
                if (tries > MAX_TRIES) {
                    throw new RuntimeException(mResources.getString(R.string.err_rand_gen_tries));
                }
                val = m_Rand.nextInt(mFilenameArray.size());
                if ((val == mCurId2) || (val == mCurId1)) {
                    continue;
                }
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
                getImageListFromServer();

                while (mRunning) {
                    if (isCancelled()) {
                        return false;
                    }

                    IdPair idPair = calcNextIdPair();
                    ImagePair imagePair = new ImagePair();

                    try {
                        if (isCancelled()) {
                            return false;
                        }

                        byte[] imgRaw = getImage(mFilenameArray.get(idPair.getIdFirst()));
                        imagePair.setImageFirst(BitmapFactory.decodeByteArray(imgRaw, 0, imgRaw.length));
                        if (imagePair.getImageFirst() == null) {
                            throw new RuntimeException(mResources.getString(R.string.err_decode_img)
                                    + " " + mFilenameArray.get(idPair.getIdFirst()));
                        }
                        imagePair.setDateFirst(getExifDate(imgRaw));
                        if (imagePair.getDateFirst() == null) {
                            throw new RuntimeException(mResources.getString(R.string.err_no_exif)
                                    + " " + mFilenameArray.get(idPair.getIdFirst()));
                        }

                        if (isCancelled()) {
                            return false;
                        }

                        imgRaw = getImage(mFilenameArray.get(idPair.getIdSecond()));
                        imagePair.setImageSecond(BitmapFactory.decodeByteArray(imgRaw, 0, imgRaw.length));
                        if (imagePair.getImageSecond() == null) {
                            throw new RuntimeException(mResources.getString(R.string.err_decode_img)
                                    + " " + mFilenameArray.get(idPair.getIdSecond()));
                        }
                        imagePair.setDateSecond(getExifDate(imgRaw));
                        if (imagePair.getDateSecond() == null) {
                            throw new RuntimeException(mResources.getString(R.string.err_no_exif)
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
            } catch (ImageProcessingException | IOException | InterruptedException | RuntimeException e) {
                Log.e(TAG, e.getMessage());
                return false;
            }

            return true;
        }

        @Override
        protected void onCancelled(Boolean result) {
            clear();
            mRunning = false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            /*if (!result) {
                Snackbar.make(findViewById(R.id.image_view_1), mResourses.getString(
                    R.string.err_fetch_thread), Snackbar.LENGTH_SHORT).show();
            }*/

            clear();
            mRunning = false;
        }
    }
}
