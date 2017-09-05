/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.web.api.ParameterSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;

public class TestParams extends MultiMapParams {

    public TestParams(ArrayListMultimap<String, String> options) {
        super(options);
    }

    public static ParameterSet of(@Nullable final String... kvp) {
        Preconditions.checkArgument(kvp == null || kvp.length % 2 == 0);

        TestParams params = new TestParams(null);
        for (int i = 0; kvp != null && i < kvp.length; i += 2) {
            params.options.put(kvp[i], kvp[i + 1]);
        }
        params.setFileUpload();
        return params;
    }

    private void setFileUpload() {
        String fileUploadContents = getFirstValue("mockFileUpload");
        if (fileUploadContents != null) {
            // build a temp file with the mockFileUpload contents
            try {
                File tempFile = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
                tempFile.deleteOnExit();
                try (FileOutputStream fos = new FileOutputStream(tempFile);
                    InputStream bais = new ByteArrayInputStream(fileUploadContents.getBytes(
                        StandardCharsets.UTF_8))) {
                    IOUtils.copy(bais, fos);
                    fos.flush();
                }
                this.uploadedFile = tempFile;
            } catch (IOException ioe) {
                // eat it
            }
        }
    }

    public void setFileUpload(File upload) {
        this.uploadedFile = upload;
    }
}
