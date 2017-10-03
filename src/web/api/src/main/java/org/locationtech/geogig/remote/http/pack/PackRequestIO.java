package org.locationtech.geogig.remote.http.pack;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.locationtech.geogig.remotes.pack.PackRequest;
import org.locationtech.geogig.remotes.pack.RefRequest;

public class PackRequestIO {

    final byte[] delimiter = { 'p', 'a', 'c', 'k', 'r', 'e', 'q' };

    private RefRequestIO refIO = new RefRequestIO();

    public void write(PackRequest request, OutputStream out) throws IOException {
        DataOutputStream dout = new DataOutputStream(out);
        dout.write(delimiter);
        dout.writeInt(request.getMaxDepth().or(0));
        writeRequests(dout, request.getRefs());

        // final int maxDepth = request.getMaxDepth().or(0);
        // final RepositoryFilter sparseFilter = request.getSparseFilter().orNull();
        dout.write(delimiter);
        dout.flush();
    }

    public PackRequest read(InputStream in) throws IOException {
        DataInputStream din = new DataInputStream(in);
        require(din, delimiter);
        final int depth = din.readInt();

        List<RefRequest> readRequests = readRequests(din);
        require(din, delimiter);

        PackRequest req = new PackRequest();
        req.maxDepth(depth);
        readRequests.forEach((r) -> req.addRef(r));
        return req;
    }

    private void require(DataInputStream in, byte[] bytes) throws IOException {
        byte[] buff = new byte[bytes.length];
        in.readFully(buff);
        boolean equals = Arrays.equals(bytes, buff);
        if (!equals) {
            throw new IllegalStateException(String.format("expected %s, got %s",
                    Arrays.toString(bytes), Arrays.toString(buff)));
        }
    }

    void writeRequests(DataOutputStream out, List<RefRequest> refs) throws IOException {
        out.writeInt(refs.size());
        for (RefRequest r : refs) {
            refIO.write(out, r);
        }
    }

    List<RefRequest> readRequests(DataInputStream in) throws IOException {
        final int size = in.readInt();
        List<RefRequest> refs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            RefRequest r = refIO.read(in);
            refs.add(r);
        }
        return refs;
    }
}
