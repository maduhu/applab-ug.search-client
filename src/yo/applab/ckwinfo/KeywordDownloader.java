/**
 * Copyright (C) 2010 Grameen Foundation
Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
 */

package yo.applab.ckwinfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import android.os.Handler;
import android.util.Log;

/**
 * Provides access to network resources including updating keywords and
 * retrieveing search results.
 */
public class KeywordDownloader implements Runnable {
	/** for debugging purposes in adb logcat */
	private final String DEBUG_TAG = "Connect";

	/** Application handler to which notifications can be sent */
	private Handler applicationHandler;

	private HttpURLConnection httpConnection;

	private boolean canceled;
	private int responseContentLength;
	private int bufferedSize;

	public KeywordDownloader(Handler handler) {
		this.applicationHandler = handler;
		canceled = false;
	}

	@Override
	public void run() {
		boolean error = false;
		InputStream inputStream = null;
		StringBuffer stringBuffer = new StringBuffer();
		try {
			try {
				Log.d(DEBUG_TAG, "Connecting...");
				inputStream = openHttpConnection(Global.URL);
				int byteIntegerValue = 0;

				if (inputStream != null && !canceled) {
					Log.d(DEBUG_TAG, "Reading socket...");
					while ((byteIntegerValue = inputStream.read()) != -1) {
						stringBuffer.append((char) byteIntegerValue);

						if (canceled) {
							httpConnection.disconnect();
							break;
						}
					}
					bufferedSize = stringBuffer.length();
					Log.d(DEBUG_TAG, "BUFFERED: " + bufferedSize);
					Global.data = stringBuffer.toString();
					inputStream.close();
				}

			} catch (IOException e) {
				Global.cancelConnection = false;
				Log.d(DEBUG_TAG, "Exception2: " + e.toString());
				error = true;
				if (!canceled) {
					// Notify handler: connection failure
					applicationHandler.sendEmptyMessage(Global.CONNECTION_ERROR);
				}
			}
			Global.cancelConnection = false;
			if (!error && !canceled) {
				// Notify handler: connection success
				applicationHandler.sendEmptyMessage(Global.CONNECTION_SUCCESS);
				Log.d(DEBUG_TAG, "Download complete...");
			}
		} finally {
			httpConnection.disconnect();
		}
	}

	public int tryCancel() {
		// TODO OKP-1#CFR-29, An alternative would be to have an enum return, or
		// a boolean return
		if (Global.cancelConnection) {
			httpConnection.disconnect();
			canceled = true;
			return 0;
		} else {
			return -1;
		}
	}

	private InputStream openHttpConnection(String urlString) throws IOException {
		InputStream inputStream = null;
		Global.cancelConnection = false;
		int response = -1;
		URL url = new URL(urlString);
		URLConnection conn = url.openConnection();

		if (!(conn instanceof HttpURLConnection)) {
			throw new IOException("Not an HTTP connection");
		}

		try {
			httpConnection = (HttpURLConnection) conn;
			httpConnection.setAllowUserInteraction(false);
			httpConnection.setInstanceFollowRedirects(true);
			httpConnection.setConnectTimeout(Global.TIMEOUT);
			httpConnection.setReadTimeout(Global.TIMEOUT);
			httpConnection.setRequestMethod("GET");
			httpConnection.connect();
			response = httpConnection.getResponseCode();
			responseContentLength = httpConnection.getContentLength();
			Log.d(DEBUG_TAG, "SIZE: " + responseContentLength);
			Global.cancelConnection = true;
			Log.d(DEBUG_TAG, "RESPONSE CODE: " + Integer.toString(response));
			if (response == HttpURLConnection.HTTP_OK) {
				inputStream = httpConnection.getInputStream();
			}
			if (canceled) {
				httpConnection.disconnect();
			}
		} catch (Exception ex) {
			Log.d(DEBUG_TAG, "Exception: " + ex.toString());
			throw new IOException(ex.toString());
		}
		return inputStream;
	}

}