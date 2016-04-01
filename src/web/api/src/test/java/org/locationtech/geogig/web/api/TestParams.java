/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.restlet.util.ByteUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;

public class TestParams implements ParameterSet {

    private ArrayListMultimap<String, String> params = ArrayListMultimap.create();

    private File fileUpload;

    @Override
    @Nullable
    public String getFirstValue(String key) {
        return getFirstValue(key, null);
    }

    @Override
    public String getFirstValue(String key, String defaultValue) {
        String[] array = getValuesArray(key);
        return array.length == 0 ? defaultValue : array[0];
    }

    @Override
    @Nullable
    public String[] getValuesArray(String key) {
        List<String> list = params.get(key);
        return list.toArray(new String[list.size()]);
    }

    public static ParameterSet of(@Nullable final String... kvp) {
        Preconditions.checkArgument(kvp == null || kvp.length % 2 == 0);

        TestParams params = new TestParams();
        for (int i = 0; kvp != null && i < kvp.length; i += 2) {
            params.params.put(kvp[i], kvp[i + 1]);
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
                    ByteUtils.write(bais, fos);
                    fos.flush();
                }
                fileUpload = tempFile;
            } catch (IOException ioe) {
                // eat it
            }
        }
    }

    @Override
    public File getUploadedFile() {
        return this.fileUpload;
    }

    public void setFileUpload(File upload) {
        this.fileUpload = upload;
    }
}
