/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.fs;

import java.io.File;
import java.io.IOException;

import org.locationtech.geogig.storage.impl.INIBlob;

import com.google.common.io.Files;

/**
 * Simple implementation of an INI file parser and serializer
 */
public abstract class INIFile extends INIBlob {
    /**
     * Timestamp the ini file at latest read
     */
    private long timestamp = 0;


    public abstract File iniFile();

    @Override
    public byte[] iniBytes() throws IOException {
        File file = iniFile();
        if (file.exists()) {
            return Files.toByteArray(file);
        } else {
            return null;
        }
    }

    @Override
    public void setBytes(byte[] bytes) throws IOException {
        File file = iniFile();
        Files.write(bytes, file);
    }

    @Override
    public boolean needsReload() {
        File ini = iniFile();
        long currentTime = ini.lastModified();
        if (currentTime > timestamp) {
            timestamp = currentTime;
            return true;
        }
        return false;
    }

    public static INIFile forFile(final File iniFile) {
        return new INIFile() {

            private final File file = iniFile;

            @Override
            public File iniFile() {
                return file;
            }
        };
    }
}
