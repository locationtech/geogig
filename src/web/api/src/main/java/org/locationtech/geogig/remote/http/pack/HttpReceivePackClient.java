package org.locationtech.geogig.remote.http.pack;

import java.util.List;

import org.locationtech.geogig.remote.http.HttpRemoteRepo;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.pack.ReceivePackOp;

public class HttpReceivePackClient extends ReceivePackOp {

    private HttpRemoteRepo remote;

    public HttpReceivePackClient(HttpRemoteRepo remote) {
        this.remote = remote;
    }

    public @Override List<RefDiff> call() {
        throw new UnsupportedOperationException();
    }
}
