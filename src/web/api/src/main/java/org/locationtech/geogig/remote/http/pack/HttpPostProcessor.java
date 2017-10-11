package org.locationtech.geogig.remote.http.pack;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Helper class to do HTTP POST
 *
 */
public class HttpPostProcessor {

    private URL postRequestURL;

    public HttpPostProcessor(URL postRequestURL) {
        this.postRequestURL = postRequestURL;
    }

    public HttpURLConnection connect() throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) postRequestURL.openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setUseCaches(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setChunkedStreamingMode(4096);
        connection.setRequestProperty("content-length", "-1");
        // connection.setRequestProperty("content-encoding", "gzip");

        return connection;
    }
}
