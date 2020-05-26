
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HttpService {
	HttpURLConnection conn = null;
	final int BUFFER_SIZE = 4096;
	private static final int TIME_OUT = 60 * 1000; // connect server time out
	private static final String CHARSET = "utf-8";
	private static final String PREFIX = "--"; // prefix
	private static final String BOUNDARY = UUID.randomUUID().toString(); // Boundary markers randomly generated
	private static final String CONTENT_TYPE = "multipart/form-data"; // Content Type
	private static final String LINE_END = "\r\n"; // new line
	// Building SSL Trust
	TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
	} };

	public HttpService() {
		try {
			HttpsURLConnection.setDefaultHostnameVerifier(new NullHostNameVerifier());
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, this.trustAllCerts, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (KeyManagementException | NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}
	
	public class NullHostNameVerifier implements HostnameVerifier {
        /*
         * (non-Javadoc)
         * 
         * @see javax.net.ssl.HostnameVerifier#verify(java.lang.String,
         * javax.net.ssl.SSLSession)
         */
		@Override
		public boolean verify(String arg0, SSLSession arg1) {
			return true;
		}
    }

	public String runTestSuite(String address, Map<String, File> fileParams) throws IOException {
		URL url = new URL(address);
		conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		conn.setReadTimeout(TIME_OUT);
		conn.setConnectTimeout(TIME_OUT);
		conn.setDoOutput(true);
		conn.setDoInput(true);
		conn.setUseCaches(false);// Post request cannot use cache
		// Set the request headers
		conn.setRequestProperty("Connection", "Keep-Alive");
		conn.setRequestProperty("Charset", "UTF-8");
		conn.setRequestProperty("Content-Type", CONTENT_TYPE + ";boundary=" + BOUNDARY);
		
		DataOutputStream dos = null;
		try {
			dos = new DataOutputStream(conn.getOutputStream());
		} catch (IOException e) {
			throw e;
		}
		StringBuilder fileSb = new StringBuilder();
		for (Map.Entry<String, File> fileEntry : fileParams.entrySet()) {
			fileSb.append(PREFIX).append(BOUNDARY).append(LINE_END)
					.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileEntry.getKey() + "\""
							+ LINE_END)
					.append("Content-Transfer-Encoding: 8bit" + LINE_END).append(LINE_END);
			dos.writeBytes(fileSb.toString());
			dos.flush();
			InputStream is = new FileInputStream(fileEntry.getValue());
			byte[] buffer = new byte[1024];
			int len = 0;
			while ((len = is.read(buffer)) != -1) {
				dos.write(buffer, 0, len);
			}
			is.close();
			dos.writeBytes(LINE_END);
		}
		// End sign requested
		dos.writeBytes(PREFIX + BOUNDARY + PREFIX + LINE_END);
		dos.flush();
		dos.close();

		StringBuilder response = new StringBuilder();
		// Read the server to return information
		if (conn.getResponseCode() == 200) {
			InputStream in = conn.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			String line = null;

			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
			reader.close();
		}
		conn.disconnect();
		return response.toString();
	}

	public String getState(String address, final Map<String, String> params) throws IOException {
		StringBuilder response = new StringBuilder();
		HttpURLConnection conn = null;
		String dataParams = getDataString(params);

		URL url = new URL(address + dataParams);
		conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		
		
		int responseCode = HttpURLConnection.HTTP_OK;
		try {
			responseCode = conn.getResponseCode();
		} catch (IOException e) {
			throw e;
		}

		if (responseCode == HttpURLConnection.HTTP_OK) {
			String line;
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			while ((line = br.readLine()) != null) {
				response.append(line);
			}
		}
		conn.disconnect();
		return response.toString();
	}
	
	public void download(String address, final Map<String, String> strParams, String filePath) throws IOException {
		String dataParams = getDataString(strParams);
		URL url = new URL(address + dataParams);
		
		HttpURLConnection conn = null;
		conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setReadTimeout(TIME_OUT);
		conn.setConnectTimeout(TIME_OUT);
		
		int responseCode = conn.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_OK) { // success
			InputStream inputStream = conn.getInputStream();
			
			// opens an output stream to save into file
			FileOutputStream fileOutputStream = new FileOutputStream(filePath);
			int bytesRead = -1;
			byte[] buffer = new byte[BUFFER_SIZE];
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				fileOutputStream.write(buffer, 0, bytesRead);
			}

			fileOutputStream.close();
			inputStream.close();
			System.out.println("File downloaded");
		} else {
			System.out.println("GET request not worked");
		}
		conn.disconnect();
	}

	String getDataString(Map<String, String> params) throws IOException  {
		final String UTF_8 = "UTF-8";
		StringBuilder result = new StringBuilder();
		boolean isFirst = true;
		for (Map.Entry<String, String> entry : params.entrySet()) {
			if (isFirst) {
				isFirst = false;
				result.append("?");
			} else {
				result.append("&");
			}
			result.append(URLEncoder.encode(entry.getKey(), UTF_8));
			result.append("=");
			result.append(URLEncoder.encode(entry.getValue(), UTF_8));
		}
		return result.toString();
	}
}
