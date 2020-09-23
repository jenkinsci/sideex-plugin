package sideex;

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
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.net.MalformedURLException;

public class SideeXWebServiceClientAPI {
	private String baseURL;
	private String hostname;
	static final int BUFFER_SIZE = 4096;
	private static final int TIME_OUT = 60 * 1000; // connect server time out
	private static final String CHARSET = "utf-8";
	private static final String PREFIX = "--"; // prefix
	private static final String BOUNDARY = UUID.randomUUID().toString(); // Boundary markers randomly generated
	private static final String CONTENT_TYPE = "multipart/form-data"; // Content Type
	private static final String LINE_END = "\r\n"; // new line
	private HTTPSHostNameVerifier httpsHostNameVerifier;
	private TrustManager[] trustAllCerts;
	static HostnameVerifier defaultHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
	static SSLSocketFactory defaultSSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();

	public SideeXWebServiceClientAPI(String baseURL, ProtocolType protocolType) throws MalformedURLException {
		this.baseURL = baseURL;
		try {
			this.hostname = new URL(this.baseURL).getHost();
		} catch (MalformedURLException e) {
			throw e;
		}
		if (protocolType == ProtocolType.HTTPS_DISABLE) {
			setHTTPSDisable();
		} else if (protocolType == ProtocolType.HTTPS_ENABLE) {
			httpsHostNameVerifier = new HTTPSHostNameVerifier(hostname);// Building SSL Trust
		}
	}
	
	public static void setHTTPSToDefault() {
		HttpsURLConnection.setDefaultHostnameVerifier(defaultHostnameVerifier);
		HttpsURLConnection.setDefaultSSLSocketFactory(defaultSSLSocketFactory);
	}
	
	public void setHTTPSDisable() {
		this.trustAllCerts = new TrustManager[] { new X509TrustManager() {
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
		try {
			HttpsURLConnection.setDefaultHostnameVerifier(new NullHostNameVerifier());
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, this.trustAllCerts, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (KeyManagementException | NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}

	private static class HTTPSHostNameVerifier implements HostnameVerifier {
		private String hostname;

		public HTTPSHostNameVerifier(String hostname) {
			this.hostname = hostname;
		}

		@Override
		public boolean verify(String hostname, SSLSession session) {
			if (this.hostname.equals(hostname)) {
				return true;
			} else {
				HostnameVerifier hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
				return hostnameVerifier.verify(hostname, session);
			}
		}
	}

	void setCertificate(String caFile) throws Exception {
		try {
			CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
			InputStream caFileInputStream = new FileInputStream(caFile);
			InputStream caInput = caFileInputStream;
			final Certificate ca;
			try {
				ca = certificateFactory.generateCertificate(caInput);
			} finally {
				caInput.close();
			}
			// Create an SSLContext that uses our TrustManager
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] { new X509TrustManager() {
				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

				}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
					for (X509Certificate cert : chain) {
						// Make sure that it hasn't expired.
						cert.checkValidity();

						// Verify the certificate's public key chain.
						try {
							cert.verify(((X509Certificate) ca).getPublicKey());
						} catch (InvalidKeyException | CertificateException | NoSuchAlgorithmException
								| NoSuchProviderException | SignatureException e) {
							throw new CertificateException(e);
						}
					}
				}

				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
				}
			} }, null);

			HttpsURLConnection.setDefaultHostnameVerifier(httpsHostNameVerifier);
			HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
		} catch (Exception e) {
			throw e;
		}
	}

	private static class NullHostNameVerifier implements HostnameVerifier {
		@Override
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	}

	public String runTestSuite(Map<String, File> file) throws IOException {
		URL url = new URL(this.baseURL + "sideex-webservice/runTestSuites");
		HttpURLConnection conn = null;
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
		for (Map.Entry<String, File> fileEntry : file.entrySet()) {
			fileSb.append(PREFIX).append(BOUNDARY).append(LINE_END).append(
					"Content-Disposition: form-data; name=\"file\"; filename=\"" + fileEntry.getKey() + "\"" + LINE_END)
					.append("Content-Transfer-Encoding: 8bit" + LINE_END).append(LINE_END);
			dos.writeBytes(fileSb.toString());
			dos.flush();
			InputStream is = null;
			is = new FileInputStream(fileEntry.getValue());
			try {
				byte[] buffer = new byte[1024];
				int len = 0;
				while ((len = is.read(buffer)) != -1) {
					dos.write(buffer, 0, len);
				}
				dos.writeBytes(LINE_END);
			} finally {
				if(is != null)
					is.close();
			}
		}
		// End sign requested
		dos.writeBytes(PREFIX + BOUNDARY + PREFIX + LINE_END);
		dos.flush();
		dos.close();

		StringBuilder response = new StringBuilder();
		// Read the server to return information
		if (conn.getResponseCode() == 200) {
			InputStream in = conn.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			String line = null;

			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
			reader.close();
		}
		conn.disconnect();
		return response.toString();
	}

	public String getState(String token) throws IOException {
		StringBuilder response = new StringBuilder();
		HttpURLConnection conn = null;
		Map<String, String> params = new HashMap<String, String>();
		params.put("token", token);
		String dataParams = getDataString(params);

		URL url = new URL(this.baseURL + "sideex-webservice/getState" + dataParams);
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
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
			while ((line = br.readLine()) != null) {
				response.append(line);
			}
			br.close();
		}
		conn.disconnect();
		return response.toString();
	}

	public void download(final Map<String, String> formData, String filePath, int option) throws IOException {
		String tempBaseURL = this.baseURL;
		if (option == 0) {
			tempBaseURL = tempBaseURL + "sideex-webservice/downloadReports";
		} else {
			tempBaseURL = tempBaseURL + "sideex-webservice/downloadLogs";
		}

		String dataParams = getDataString(formData);
		URL url = new URL(tempBaseURL + dataParams);

		HttpURLConnection conn = null;
		conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setReadTimeout(TIME_OUT);
		conn.setConnectTimeout(TIME_OUT);

		int responseCode = conn.getResponseCode();
		InputStream inputStream = null;
		FileOutputStream fileOutputStream = null;
		if (responseCode == HttpURLConnection.HTTP_OK) { // success
			try {
				inputStream = conn.getInputStream();

				// opens an output stream to save into file
				fileOutputStream = new FileOutputStream(filePath);
				int bytesRead = -1;
				byte[] buffer = new byte[BUFFER_SIZE];
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					fileOutputStream.write(buffer, 0, bytesRead);
				}

				System.out.println("File downloaded");
			} finally {
				if(fileOutputStream != null)
					fileOutputStream.close();
				if(inputStream != null)
					inputStream.close();
			}
		} else {
			System.out.println("GET request not worked");
		}

		conn.disconnect();
	}

	public String deleteJob(String token) throws IOException {
		URL url = new URL(this.baseURL + "sideex-webservice/deleteJob");
		HttpURLConnection conn = null;
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

		Map<String, String> strParams = new HashMap<String, String>();
		strParams.put("token", token);
		DataOutputStream dos = null;
		StringBuilder response = new StringBuilder();
		try {
			dos = new DataOutputStream(conn.getOutputStream());
			dos.writeBytes(getStrParams(strParams).toString());
			// End sign requested
			dos.writeBytes(PREFIX + BOUNDARY + PREFIX + LINE_END);
			dos.flush();
			dos.close();

			// Read the server to return information
			if (conn.getResponseCode() == 200) {
				InputStream in = conn.getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
				String line = null;

				while ((line = reader.readLine()) != null) {
					response.append(line);
				}
				reader.close();
			}
			conn.disconnect();
		} catch (IOException e) {
			throw e;
		}

		return response.toString();
	}

	private String getDataString(Map<String, String> params) throws IOException {
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

	private static StringBuilder getStrParams(Map<String, String> strParams) {
		StringBuilder strSb = new StringBuilder();
		for (Map.Entry<String, String> entry : strParams.entrySet()) {
			strSb.append(PREFIX).append(BOUNDARY).append(LINE_END)
					.append("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"" + LINE_END)
					.append("Content-Type: text/plain; charset=" + CHARSET + LINE_END)
					.append("Content-Transfer-Encoding: 8bit" + LINE_END).append(LINE_END)
					.append(entry.getValue()).append(LINE_END);
		}
		return strSb;
	}
}