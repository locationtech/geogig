package org.locationtech.geogig.remote.http.pack;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.locationtech.geogig.remote.http.HttpRemoteRepo;
import org.locationtech.geogig.remotes.pack.Pack;
import org.locationtech.geogig.remotes.pack.PackRequest;
import org.locationtech.geogig.remotes.pack.SendPackOp;

import com.google.common.base.Throwables;

/**
 * HTTP proxy for a {@link SendPackOp} on the remote repository's {@code <repoURL>/sendpack}
 * endpoint
 *
 */
public class HttpSendPackClient extends SendPackOp {

    private final HttpRemoteRepo remote;

    public HttpSendPackClient(HttpRemoteRepo httpRemoteRepo) {
        this.remote = httpRemoteRepo;
    }

    private URL createURL() throws MalformedURLException {
        URL commandURL = new URL(remote.getRemoteURL().toString() + "/sendpack");
        return commandURL;
    }

    protected @Override Pack preparePack(PackRequest request) {
        HttpURLConnection connection = null;
        try {
            final URL url = createURL();
            connection = new HttpPostProcessor(url).connect();

            OutputStream out = connection.getOutputStream();
            new PackRequestIO().write(request, out);
            out.flush();
            out.close();

            final int respCode = connection.getResponseCode();
            if (respCode == HttpURLConnection.HTTP_OK) {
                InputStream input = connection.getInputStream();
                DataInputStream dataInput = new DataInputStream(input);
                return new StreamingPackReader(dataInput);
            }
            throw new IllegalStateException("Server returned " + respCode);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            remote.closeSafely(connection);
        }
    }
}
