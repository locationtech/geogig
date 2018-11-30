/* Copyright (c) 2014-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.internal;

import java.io.IOException;
import java.io.OutputStream;

import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;
import com.google.common.io.CountingOutputStream;

public class ObjectFunnels {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectFunnels.class);

    public static ObjectFunnel newFunnel(OutputStream out, RevObjectSerializer serializer) {
        return new DirectFunnel(out, serializer);
    }

    public static ObjectFunnel newFunnel(final Supplier<OutputStream> outputFactory,
            final RevObjectSerializer serializer, final int byteSoftLimit) {

        return new SizeLimitingFunnel(outputFactory, serializer, byteSoftLimit);
    }

    private static class DirectFunnel implements ObjectFunnel {

        private OutputStream out;

        private RevObjectSerializer serializer;

        public DirectFunnel(OutputStream out, RevObjectSerializer serializer) {
            this.out = out;
            this.serializer = serializer;
        }

        @Override
        public void funnel(RevObject object) throws IOException {
            out.write(object.getId().getRawValue());
            serializer.write(object, out);
        }

        @Override
        public void close() throws IOException {
            OutputStream out = this.out;
            this.out = null;
            if (out != null) {
                out.close();
            }
        }
    }

    private static class SizeLimitingFunnel implements ObjectFunnel {

        private Supplier<OutputStream> outputFactory;

        private final RevObjectSerializer serializer;

        private final int byteSoftLimit;

        private CountingOutputStream currentTarget;

        public SizeLimitingFunnel(Supplier<OutputStream> outputFactory,
                RevObjectSerializer serializer, final int byteSoftLimit) {
            this.outputFactory = outputFactory;
            this.serializer = serializer;
            this.byteSoftLimit = byteSoftLimit;
        }

        @Override
        public void funnel(RevObject object) throws IOException {
            OutputStream out = getCurrentTarget();
            out.write(object.getId().getRawValue());
            serializer.write(object, out);
            out.flush();
        }

        private OutputStream getCurrentTarget() throws IOException {
            if (currentTarget == null) {
                currentTarget = new CountingOutputStream(outputFactory.get());
            } else if (currentTarget.getCount() >= byteSoftLimit) {
                LOGGER.info(
                        String.format("Closing stream and opening a new one, reached %,d bytes.\n",
                                currentTarget.getCount()));
                currentTarget.close();
                currentTarget = new CountingOutputStream(outputFactory.get());
            }

            return currentTarget;
        }

        @Override
        public void close() throws IOException {
            OutputStream currentTarget = this.currentTarget;
            this.currentTarget = null;
            if (currentTarget != null) {
                currentTarget.close();
            }
            outputFactory = null;
        }

    }
}
