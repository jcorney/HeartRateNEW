package com.boomgaarden_corney.android.heartrate;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class HeartRateMainActivity extends Activity implements
		SensorEventListener {

	private final String DEBUG_TAG = "DEBUG_HEARTRATE";
	private final String SERVER_URL = "http://54.86.68.241/heartrate.php";

	private TextView txtResults;
	private SensorManager sensorManager;

	private String errorMsg;

	private float heartrateAccuracy;
	private float heartrateValue0;
	private float heartrateValue1;
	private float heartrateValue2;
	private float heartrateMaxRange = 0;
	private float heartratePower = 0;
	private float heartrateResolution = 0;
	private int  heartrateSensorType;
	private int numHeartRateChanges = 0;
	private int heartrateVersion = 0;
	private int heartrateHashCode = 0;
	private Sensor mHeartRate;
	private long heartrateTimeStamp;
	private String heartrateVendor;

	private List<NameValuePair> paramsDevice = new ArrayList<NameValuePair>();
	private List<NameValuePair> paramsErrorMsg = new ArrayList<NameValuePair>();
	private List<NameValuePair> paramsHeartRate = new ArrayList<NameValuePair>();
	private List<NameValuePair> paramsSensor = new ArrayList<NameValuePair>();


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_heart_rate_main);

		txtResults = (TextView) this.findViewById(R.id.txtResults);

		// Setup HeartRate Manager and Provider
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mHeartRate = sensorManager
				.getDefaultSensor(Sensor.TYPE_HEART_RATE);
		

		setDeviceData();
		showDeviceData();
		sendDeviceData();

		if (mHeartRate == null){
			setErrorMsg("No HeartRate Detected");
			showErrorMsg();
			sendErrorMsg();
		} else{
			setSensorData();
			showSensorData();
			sendSensorData();
		}
		

	}

	/* Request HeartRate updates at startup */
	@Override
	protected void onResume() {
		super.onResume();
		sensorManager.registerListener(this, mHeartRate, SensorManager.SENSOR_DELAY_NORMAL);

	}

	/* Remove the HeartRatelistener updates when Activity is paused */
	@Override
	protected void onPause() {
		super.onPause();
		sensorManager.unregisterListener(this);
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (mHeartRate != null) {
			if ((event.sensor.getType() == mHeartRate.getType()) && numHeartRateChanges < 10) {
				
				numHeartRateChanges++;
				setHeartRateData(event);
				showHeartRateData();
				sendHeartRateData();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.heart_rate_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private String buildPostRequest(List<NameValuePair> params)
			throws UnsupportedEncodingException {
		StringBuilder result = new StringBuilder();
		boolean first = true;

		for (NameValuePair pair : params) {
			if (first)
				first = false;
			else
				result.append("&");

			result.append(URLEncoder.encode(pair.getName(), "UTF-8"));
			result.append("=");
			result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
		}

		return result.toString();
	}

	private String sendHttpRequest(String myURL, String postParameters)
			throws IOException {

		URL url = new URL(myURL);

		// Setup Connection
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setReadTimeout(10000); /* in milliseconds */
		conn.setConnectTimeout(15000); /* in milliseconds */
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);

		// Setup POST query params and write to stream
		OutputStream ostream = conn.getOutputStream();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				ostream, "UTF-8"));

		if (postParameters.equals("DEVICE")) {
			writer.write(buildPostRequest(paramsDevice));
		} else if (postParameters.equals("HEARTRATE")) {
			writer.write(buildPostRequest(paramsHeartRate));
			paramsHeartRate = new ArrayList<NameValuePair>();
		} else if (postParameters.equals("ERROR_MSG")) {
			writer.write(buildPostRequest(paramsErrorMsg));
			paramsErrorMsg = new ArrayList<NameValuePair>();
		}  else if (postParameters.equals("SENSOR")) {
			writer.write(buildPostRequest(paramsSensor));
			paramsSensor = new ArrayList<NameValuePair>();
		}

		writer.flush();
		writer.close();
		ostream.close();

		// Connect and Log response
		conn.connect();
		int response = conn.getResponseCode();
		Log.d(DEBUG_TAG, "The response is: " + response);

		conn.disconnect();
		
		return String.valueOf(response);

	}

	private class SendHttpRequestTask extends AsyncTask<String, Void, String> {

		// @params come from SendHttpRequestTask.execute() call
		@Override
		protected String doInBackground(String... params) {
			// params comes from the execute() call: params[0] is the url,
			// params[1] is type POST
			// request to send - i.e. whether to send Device or HeartRate
			// parameters.
			try {
				return sendHttpRequest(params[0], params[1]);
			} catch (IOException e) {
				setErrorMsg("Unable to retrieve web page. URL may be invalid.");
				showErrorMsg();
				return errorMsg;
			}
		}
	}

	private void setDeviceData() {
		paramsDevice.add(new BasicNameValuePair("Device", Build.DEVICE));
		paramsDevice.add(new BasicNameValuePair("Brand", Build.BRAND));
		paramsDevice.add(new BasicNameValuePair("Manufacturer",
				Build.MANUFACTURER));
		paramsDevice.add(new BasicNameValuePair("Model", Build.MODEL));
		paramsDevice.add(new BasicNameValuePair("Product", Build.PRODUCT));
		paramsDevice.add(new BasicNameValuePair("Board", Build.BOARD));
		paramsDevice.add(new BasicNameValuePair("Android API", String
				.valueOf(Build.VERSION.SDK_INT)));
	}

	private void setErrorMsg(String error) {
		errorMsg = error;
		paramsErrorMsg.add(new BasicNameValuePair("Error", errorMsg));
	}

	private void setHeartRateData(SensorEvent heartrate) {
		heartrateAccuracy = heartrate.accuracy;
		heartrateSensorType = heartrate.sensor.getType();
		heartrateTimeStamp = heartrate.timestamp;
		heartrateValue0 = heartrate.values[0];
		heartrateValue1 = heartrate.values[1];
		heartrateValue2 = heartrate.values[2];
		heartrateHashCode = heartrate.hashCode();

		paramsHeartRate.add(new BasicNameValuePair("HeartRate Update Count",
				String.valueOf(numHeartRateChanges)));
		paramsHeartRate.add(new BasicNameValuePair("Accuracy", String
				.valueOf(heartrateAccuracy)));
		paramsHeartRate.add(new BasicNameValuePair("Sensor Type", String
				.valueOf(heartrateSensorType)));
		paramsHeartRate.add(new BasicNameValuePair("Time Stamp", String
				.valueOf(heartrateTimeStamp)));
		paramsHeartRate.add(new BasicNameValuePair(
				"Value 0 Acceleration minus Gx on the x axis", String
						.valueOf(heartrateValue0)));
		paramsHeartRate.add(new BasicNameValuePair(
				"Value 1 Acceleration minus Gy on the y axis", String
						.valueOf(heartrateValue1)));
		paramsHeartRate.add(new BasicNameValuePair(
				"Value 2 Acceleration minus Gz on the z axis", String
						.valueOf(heartrateValue2)));
		paramsHeartRate.add(new BasicNameValuePair(
				"Hash Code Value", String
						.valueOf(heartrateHashCode)));
	}
	
	private void setSensorData() {
		heartrateMaxRange = mHeartRate.getMaximumRange();
		heartratePower = mHeartRate.getPower();
		heartrateResolution = mHeartRate.getResolution();
		heartrateVendor = mHeartRate.getVendor();
		heartrateVersion = mHeartRate.getVersion();		
		
		paramsSensor.add(new BasicNameValuePair("Max Range", String
						.valueOf(heartrateMaxRange)));
		paramsSensor.add(new BasicNameValuePair("Power", String
				.valueOf(heartratePower)));
		paramsSensor.add(new BasicNameValuePair("Resolution", String
				.valueOf(heartrateResolution)));
		paramsSensor.add(new BasicNameValuePair("Vendor", String
				.valueOf(heartrateVendor)));
		paramsSensor.add(new BasicNameValuePair("Version", String
				.valueOf(heartrateVersion)));
	}

	private void showDeviceData() {
		// Display and store (for sending via HTTP POST query) device
		// information
		txtResults.append("Device: " + Build.DEVICE + "\n");
		txtResults.append("Brand: " + Build.BRAND + "\n");
		txtResults.append("Manufacturer: " + Build.MANUFACTURER + "\n");
		txtResults.append("Model: " + Build.MODEL + "\n");
		txtResults.append("Product: " + Build.PRODUCT + "\n");
		txtResults.append("Board: " + Build.BOARD + "\n");
		txtResults.append("Android API: "
				+ String.valueOf(Build.VERSION.SDK_INT) + "\n");

		txtResults.append("\n");

	}

	private void showErrorMsg() {
		Log.d(DEBUG_TAG, errorMsg);
		txtResults.append(errorMsg + "\n");
	}

	private void showHeartRateData() {
		StringBuilder results = new StringBuilder();

		results.append("HeartRate Update Count: "
				+ String.valueOf(numHeartRateChanges) + "\n");
		results.append("HeartRate Accuracy: " + String.valueOf(heartrateAccuracy) + "\n");
		results.append("HeartRate Sensor Type: " + String.valueOf(heartrateSensorType) + "\n");
		results.append("HeartRate Time Stamp: " + String.valueOf(heartrateTimeStamp) + "\n");
		results.append("HeartRate Vaule 0 (X axis): " + String.valueOf(heartrateValue0) + "\n");
		results.append("HeartRate Vaule 1 (Y axis): " + String.valueOf(heartrateValue1) + "\n");
		results.append("HeartRate Vaule 2 (Z axis): " + String.valueOf(heartrateValue2) + "\n");
		results.append("HeartRate Hash Code " + String.valueOf(heartrateHashCode) + "\n");
		
		txtResults.append(new String(results));
		txtResults.append("\n");
	}
	
	private void showSensorData() {
		StringBuilder results = new StringBuilder();
		
		results.append("Max Range: " + String.valueOf(heartrateMaxRange) + "\n");
		results.append("Power: " + String.valueOf(heartratePower) + "\n");
		results.append("Resolution: " + String.valueOf(heartrateResolution) + "\n");
		results.append("Vendor: " + String.valueOf(heartrateVendor) + "\n");
		results.append("Version: " + String.valueOf(heartrateVersion) + "\n");
		
		txtResults.append(new String(results));
		txtResults.append("\n");
	}

	private void sendDeviceData() {
		ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectMgr.getActiveNetworkInfo();

		// Verify network connectivity is working; if not add note to TextView
		// and Logcat file
		if (networkInfo != null && networkInfo.isConnected()) {
			// Send HTTP POST request to server which will include POST
			// parameters with HeartRate info
			new SendHttpRequestTask().execute(SERVER_URL, "DEVICE");
		} else {
			setErrorMsg("No Network Connectivity");
			showErrorMsg();
		}
	}

	private void sendErrorMsg() {
		ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectMgr.getActiveNetworkInfo();

		// Verify network connectivity is working; if not add note to TextView
		// and Logcat file
		if (networkInfo != null && networkInfo.isConnected()) {
			// Send HTTP POST request to server which will include POST
			// parameters with HeartRate info
			new SendHttpRequestTask().execute(SERVER_URL, "ERROR_MSG");
		} else {
			setErrorMsg("No Network Connectivity");
			showErrorMsg();
		}
	}

	private void sendHeartRateData() {
		ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectMgr.getActiveNetworkInfo();

		// Verify network connectivity is working; if not add note to TextView
		// and Logcat file
		if (networkInfo != null && networkInfo.isConnected()) {
			// Send HTTP POST request to server which will include POST
			// parameters with HeartRate info
			new SendHttpRequestTask().execute(SERVER_URL, "HEARTRATE");
		} else {
			setErrorMsg("No Network Connectivity");
			showErrorMsg();
		}
	}

	private void sendSensorData() {
		ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectMgr.getActiveNetworkInfo();

		// Verify network connectivity is working; if not add note to TextView
		// and Logcat file
		if (networkInfo != null && networkInfo.isConnected()) {
			// Send HTTP POST request to server which will include POST
			// parameters with HeartRate info
			new SendHttpRequestTask().execute(SERVER_URL, "SENSOR");
		} else {
			setErrorMsg("No Network Connectivity");
			showErrorMsg();
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

}
