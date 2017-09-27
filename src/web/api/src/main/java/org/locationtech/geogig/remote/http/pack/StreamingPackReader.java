package org.locationtech.geogig.remote.http.pack;

import java.io.DataInputStream;
import java.util.List;

import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.pack.Pack;
import org.locationtech.geogig.remotes.pack.PackProcessor;
import org.locationtech.geogig.repository.ProgressListener;

public class StreamingPackReader implements Pack {

    private DataInputStream in;

    public StreamingPackReader(DataInputStream dataInput) {
        this.in = dataInput;
    }

    @Override
    public List<RefDiff> applyTo(PackProcessor target, ProgressListener progress) {
        // TODO Auto-generated method stub
        return null;
    }

}
