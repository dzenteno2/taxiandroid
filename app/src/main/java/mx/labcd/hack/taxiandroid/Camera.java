package mx.labcd.hack.taxiandroid;

import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class Camera extends ActionBarActivity
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, OnMapReadyCallback{

    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    private static final int CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE = 200;
    private Uri fileUri;
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    private GoogleApiClient mGoogleApiClient;
    private Location mCurrentLocation;
    private String mLastUpdateTime;
    private LocationRequest mLocationRequest;

    private MapFragment mapFragment;

    private GoogleMap googleMap;

    private static final int userId = 1;
    private static final String taxiMapUrl = "http://taximap-ezentenoj.rhcloud.com/";

    private boolean rideStarted, stopRide, panic;
    private int rideId;

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    protected synchronized void buildGoogleApiClient(){
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        //Create Intent
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);




    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_camera, menu);
        return true;
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

    /** Create a file Uri for saving an image or video */
    private static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {

                rideStarted = false;
                stopRide = false;
                panic = false;

                buildGoogleApiClient();
                createLocationRequest();

                // Image captured and saved to fileUri specified in the Intent

                final Button finishButton = (Button) findViewById(R.id.finishRide);
                finishButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        stopRide = true;
                        new MyAsyncTask().execute("");

                    }
                });

                final Button panicButton = (Button) findViewById(R.id.panic);
                panicButton.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View v) {

                        panic = true;
                        new MyAsyncTask().execute("");

                    }
                });


                mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
                mapFragment.getMapAsync(this);

            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the image capture
            } else {
                // Image capture failed, advise user
            }
        }

        if (requestCode == CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Video captured and saved to fileUri specified in the Intent
                Toast.makeText(this, "Video saved to:\n" +
                        data.getData(), Toast.LENGTH_LONG).show();
            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the video capture
            } else {
                // Video capture failed, advise user
            }
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        // Connected to Google Play services!
        // The good stuff goes here.
        startLocationUpdates();
    }

    protected void startLocationUpdates(){
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this
        );
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the next section.
        Log.d("tag","NO SE PUDO CONECTAR");

    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        if(googleMap != null){
            LatLng latLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());

            googleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("EseTaxi"));


            LatLngBounds.Builder builder = new LatLngBounds.Builder();

            builder.include(latLng);

            LatLngBounds bounds = builder.build();

            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, 0);

            googleMap.moveCamera(cu);
            googleMap.moveCamera(CameraUpdateFactory.zoomTo(14));
        }
        new MyAsyncTask().execute("");

    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;

    }


    private class MyAsyncTask extends AsyncTask<String, Integer, Double> {

        @Override
        protected Double doInBackground(String... params) {
            // TODO Auto-generated method stub
            postData();
            return null;
        }

        protected void onPostExecute(Double result){
            Log.d("tag", "Dont know");
        }
        protected void onProgressUpdate(Integer... progress){

        }

        public void postData() {
            if(stopRide){
                stopRide();
            }
            else {
                if (!rideStarted)
                    startRide();
                else {

                    if (!panic)
                        continueRide();
                    else
                        sendPanic();
                }

            }


        }

        private void startRide(){
            HttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = null;

            String setCoordinate = "setCoordinate.php?";
            String postUrl = taxiMapUrl + setCoordinate;
            postUrl += "user_id=" + String.valueOf(userId);
            postUrl += "&coordinate=" + String.valueOf(mCurrentLocation.getLatitude()) + ",";
            postUrl += "%20" + String.valueOf(mCurrentLocation.getLongitude());

            HttpPost httpPost = new HttpPost(postUrl);

            try{
                response = httpClient.execute(httpPost);
                Log.d("TAG", "terminó");
            }
            catch (ClientProtocolException e){
                e.printStackTrace();
            }
            catch (IOException e){
                e.printStackTrace();
            }

            String responseText = null;
            try{
                responseText = EntityUtils.toString(response.getEntity());
                Log.d("TAG", responseText);
                try{
                    JSONObject json = new JSONObject(responseText);
                    JSONObject jsonResult = json.getJSONObject("result");
                    rideId = jsonResult.getInt("ride_id");
                    rideStarted = true;
                }
                catch(JSONException e){
                    e.printStackTrace();
                }
            }
            catch(IOException e){
                e.printStackTrace();
            }

        }

        private void continueRide(){
            HttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = null;

            String setCoordinate = "setCoordinate.php?";
            String postUrl = taxiMapUrl + setCoordinate;
            postUrl += "user_id=" + String.valueOf(userId);
            postUrl += "&ride_id=" + String.valueOf(rideId);
            postUrl += "&coordinate=" + String.valueOf(mCurrentLocation.getLatitude()) + ",";
            postUrl += "%20" + String.valueOf(mCurrentLocation.getLongitude());

            HttpPost httpPost = new HttpPost(postUrl);

            try{
                response = httpClient.execute(httpPost);
                Log.d("TAG", "terminó");
            }
            catch (ClientProtocolException e){
                e.printStackTrace();
            }
            catch (IOException e){
                e.printStackTrace();
            }


        }

        private void stopRide(){
            HttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = null;

            String setCoordinate = "setCoordinate.php?";
            String postUrl = taxiMapUrl + setCoordinate;
            postUrl += "user_id=" + String.valueOf(userId);
            postUrl += "&ride_id=" + String.valueOf(rideId);
            postUrl += "&end_ride=1";
            postUrl += "&coordinate=" + String.valueOf(mCurrentLocation.getLatitude()) + ",";
            postUrl += "%20" + String.valueOf(mCurrentLocation.getLongitude());

            HttpPost httpPost = new HttpPost(postUrl);

            try{
                response = httpClient.execute(httpPost);
                Log.d("TAG", "terminó");
            }
            catch (ClientProtocolException e){
                e.printStackTrace();
            }
            catch (IOException e){
                e.printStackTrace();
            }


        }

        private void sendPanic(){
            HttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = null;

            String setCoordinate = "setCoordinate.php?";
            String postUrl = taxiMapUrl + setCoordinate;
            postUrl += "user_id=" + String.valueOf(userId);
            postUrl += "&ride_id=" + String.valueOf(rideId);
            postUrl += "&panic=1";
            postUrl += "&coordinate=" + String.valueOf(mCurrentLocation.getLatitude()) + ",";
            postUrl += "%20" + String.valueOf(mCurrentLocation.getLongitude());

            HttpPost httpPost = new HttpPost(postUrl);

            try{
                response = httpClient.execute(httpPost);
                Log.d("TAG", "terminó");
            }
            catch (ClientProtocolException e){
                e.printStackTrace();
            }
            catch (IOException e){
                e.printStackTrace();
            }


        }



    }


}
