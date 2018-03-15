package org.locationtech.geogig.remote.http.pack;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.locationtech.geogig.remote.http.HttpRemoteRepo;
import org.locationtech.geogig.remotes.pack.Pack;
import org.locationtech.geogig.remotes.pack.PackRequest;
import org.locationtech.geogig.remotes.pack.SendPackOp;
import org.locationtech.geogig.repository.ProgressListener;

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
        URL commandURL = new URL(remote.getRemoteURL().toString() + "/repo/sendpack");
        return commandURL;
    }

    protected @Override Pack preparePack(PackRequest request) {
        HttpURLConnection connection = null;
        ProgressListener progress = getProgressListener();
        try {
            final URL url = createURL();
            connection = new HttpPostProcessor(url).connect();
            progress.setDescription("Connected to " + remote.getInfo().getName());
            OutputStream out = connection.getOutputStream();
            new PackRequestIO().write(request, out);
            out.flush();
            out.close();

            progress.setDescription("Request sent, awaiting response...");
            final int respCode = connection.getResponseCode();
            if (respCode == HttpURLConnection.HTTP_OK) {
                progress.setDescription("200 OK");
                InputStream input = connection.getInputStream();
                progress.setDescription("processing response...");
                DataInputStream dataInput = new DataInputStream(input);
                return new StreamingPackBuilder(dataInput, progress).build();
            }
            throw new IllegalStateException("Server returned " + respCode);
        } catch (Exception e) {
            remote.closeSafely(connection);
            throw new RuntimeException(e);
        }
    }
}
