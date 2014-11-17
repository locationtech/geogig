/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.rest.repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.WritableByteChannel;

import org.restlet.Context;
import org.restlet.data.ClientInfo;
import org.restlet.data.Encoding;
import org.restlet.resource.Representation;
import org.restlet.util.ByteUtils;

import com.noelios.restlet.application.EncodeRepresentation;
import com.noelios.restlet.application.Encoder;

/**
 * Extends {@link Encoder} in order to overcome the bug that leaves a dangling non-daemon thread for
 * each response written product of {@link EncodeRepresentation#getStream()} (see
 * {@link ByteUtils#getStream(Representation)}.
 * 
 * <p>
 * To do so, we forbid calls to {@link EncodeRepresentation#getStream()} and instead override
 * {@link EncodeRepresentation#write(WritableByteChannel)}.
 * 
 * <p>
 * NOTE: this class can be removed if/when we upgrade to a restlet version > 2.x which seems to have
 * fixed this issue.
 */
public class FixedEncoder extends Encoder {

    public FixedEncoder(Context context) {
        super(context);
    }

    @Override
    public Representation encode(ClientInfo client, Representation representation) {
        Representation result = representation;
        Encoding bestEncoding = getBestEncoding(client);

        if (bestEncoding != null && !Encoding.IDENTITY.equals(bestEncoding)) {
            result = new FixedEncoderRepresentation(bestEncoding, representation);
        }

        return result;
    }

    private static final class FixedEncoderRepresentation extends EncodeRepresentation {

        public FixedEncoderRepresentation(Encoding encoding, Representation wrappedRepresentation) {
            super(encoding, wrappedRepresentation);
        }

        @Override
        public InputStream getStream() throws IOException {
            throw new UnsupportedOperationException(
                    "shouldn't be called, we implement write(WBC) without calling this method");
        }

        @Override
        public void write(WritableByteChannel writableChannel) throws IOException {
            if (canEncode()) {
                OutputStream stream;
                stream = org.restlet.util.ByteUtils.getStream(writableChannel);
                write(stream);
            } else {
                getWrappedRepresentation().write(writableChannel);
            }
        }
    }
}
