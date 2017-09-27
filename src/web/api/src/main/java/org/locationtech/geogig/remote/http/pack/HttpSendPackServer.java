package org.locationtech.geogig.remote.http.pack;

import java.io.IOException;
import java.io.InputStream;

import org.locationtech.geogig.remotes.pack.PackRequest;
import org.locationtech.geogig.remotes.pack.SendPackOp;

import com.google.common.base.Throwables;

/**
 * 
 *
 */
public class HttpSendPackServer extends SendPackOp {

    public HttpSendPackServer setRequest(InputStream reqStream) {
        PackRequest packRequest;
        try {
            packRequest = new PackRequestIO().read(reqStream);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        super.setRequest(packRequest);
        return this;
    }
}
