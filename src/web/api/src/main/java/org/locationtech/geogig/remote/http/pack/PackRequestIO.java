package org.locationtech.geogig.remote.http.pack;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.remotes.pack.PackRequest;
import org.locationtech.geogig.remotes.pack.RefRequest;

import com.google.common.base.Preconditions;

class PackRequestIO {

    final byte[] delimiter = { 'p', 'a', 'c', 'k', 'r', 'e', 'q' };

    public void write(PackRequest request, OutputStream out) throws IOException {
        DataOutputStream dout = new DataOutputStream(out);
        dout.write(delimiter);

        writeRequests(dout, request.getRefs());

        // final int maxDepth = request.getMaxDepth().or(0);
        // final RepositoryFilter sparseFilter = request.getSparseFilter().orNull();
        dout.write(delimiter);
        dout.flush();
    }

    public PackRequest read(InputStream in) throws IOException {
        DataInputStream din = new DataInputStream(in);
        require(din, delimiter);
        List<RefRequest> readRequests = readRequests(din);
        require(din, delimiter);

        PackRequest req = new PackRequest();
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
            writeRefReq(out, r);
        }
    }

    List<RefRequest> readRequests(DataInputStream in) throws IOException {
        final int size = in.readInt();
        List<RefRequest> refs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            RefRequest r = readRequest(in);
            refs.add(r);
        }
        return refs;
    }

    private void writeRefReq(DataOutputStream out, RefRequest r) throws IOException {
        String name = r.name;
        ObjectId want = r.want;
        ObjectId have = r.have.orNull();

        out.writeUTF(name);
        writeId(out, want);
        writeId(out, have);
    }

    private RefRequest readRequest(DataInputStream in) throws IOException {
        String name = in.readUTF();
        final ObjectId want = readId(in);
        final @Nullable ObjectId have = readId(in);
        return RefRequest.create(name, want, have);
    }

    void writeId(DataOutputStream out, @Nullable ObjectId id) throws IOException {
        out.writeByte(id == null ? 0 : 1);
        if (id != null) {
            id.writeTo(out);
        }
    }

    @Nullable
    ObjectId readId(DataInputStream in) throws IOException {
        final int present = in.readByte() & 0xFF;
        Preconditions.checkArgument(present == 0 || present == 1);
        ObjectId id = null;
        if (1 == present) {
            id = ObjectId.readFrom(in);
        }
        return id;
    }
}
