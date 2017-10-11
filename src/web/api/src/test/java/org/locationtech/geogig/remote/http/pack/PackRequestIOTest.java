package org.locationtech.geogig.remote.http.pack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.hashString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.remotes.pack.PackRequest;
import org.locationtech.geogig.remotes.pack.RefRequest;

public class PackRequestIOTest {

    ByteArrayOutputStream out;

    PackRequestIO requestIO;

    PackRequest request;

    public @Before void before() {
        out = new ByteArrayOutputStream();
        requestIO = new PackRequestIO();
        request = new PackRequest();
    }

    public @Test void testEncodeDecodeEmpty() throws IOException {
        testEncodeDecode(request);
    }

    public @Test void testEncodeDecodeRefRequests() throws IOException {
        request.addRef(RefRequest.create("refs/heads/master", hashString("1"), null));
        request.addRef(RefRequest.create("refs/tags/t1", hashString("2"), null));
        request.addRef(RefRequest.create("HEAD", hashString("3"), hashString("2")));
        testEncodeDecode(request);
    }

    public @Test void testEncodeDecodeDepth() throws IOException {
        request.maxDepth(10);
        testEncodeDecode(request);
    }

    private void testEncodeDecode(PackRequest request) throws IOException {
        requestIO.write(request, out);
        PackRequest read = requestIO.read(new ByteArrayInputStream(out.toByteArray()));
        assertNotNull(read);
        assertEquals(request, read);
    }

}
