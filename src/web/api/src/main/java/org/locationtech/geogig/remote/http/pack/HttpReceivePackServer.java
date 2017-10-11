package org.locationtech.geogig.remote.http.pack;

import static com.google.common.base.Preconditions.checkNotNull;

import org.locationtech.geogig.remotes.pack.PackProcessor;
import org.locationtech.geogig.remotes.pack.ReceivePackOp;

public class HttpReceivePackServer extends ReceivePackOp {

    private StreamingPackWriter target;

    public HttpReceivePackServer setTarget(StreamingPackWriter target) {
        this.target = target;
        return this;
    }

    protected @Override PackProcessor getPackProcessor() {
        checkNotNull(target, "no target output stream was provided");
        return target;
    }
}
