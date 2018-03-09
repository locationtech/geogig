package org.locationtech.geogig.remote.http.pack;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.locationtech.geogig.remotes.pack.PackBuilder;
import org.locationtech.geogig.remotes.pack.PackRequest;
import org.locationtech.geogig.remotes.pack.ReceivePackOp;
import org.locationtech.geogig.remotes.pack.SendPackOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.CommandFactory;
import org.locationtech.geogig.repository.Repository;

/**
 * 
 *
 */
public class HttpSendPackServer extends SendPackOp implements CommandFactory {

    private StreamingPackWriter target;

    protected @Override PackBuilder getPackBuilder() {
        return target;
    }

    public HttpSendPackServer setInput(InputStream reqStream) {
        PackRequest packRequest;
        try {
            packRequest = new PackRequestIO().read(reqStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        super.setRequest(packRequest);
        return this;
    }

    public HttpSendPackServer setOutput(OutputStream target) {
        Repository localRepo = repository();
        DataOutputStream out = new DataOutputStream(target);
        this.target = new StreamingPackWriter(localRepo, out);
        super.setTarget(this);
        return this;
    }

    public @Override <T extends AbstractGeoGigOp<?>> T command(Class<T> commandClass) {
        if (ReceivePackOp.class.equals(commandClass)) {
            HttpReceivePackServer cmd;
            cmd = super.command(HttpReceivePackServer.class).setTarget(target);
            return commandClass.cast(cmd);
        }
        return super.command(commandClass);
    }
}
