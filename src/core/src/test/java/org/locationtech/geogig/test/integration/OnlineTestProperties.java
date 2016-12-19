/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import java.io.File;
import java.io.IOException;

import org.locationtech.geogig.storage.fs.INIFile;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

public class OnlineTestProperties {
    private String propertiesFileName;

    private String[] defaults;

    private class SectionOptionPair {
        String section;

        String option;

        public SectionOptionPair(String key) {
            final int index = key.indexOf('.');

            if (index == -1) {
                throw new RuntimeException("Section.key invalid!");
            }

            section = key.substring(0, index);
            option = key.substring(index + 1);

            if (section.length() == 0 || option.length() == 0) {
                throw new RuntimeException("Section.key invalid!");
            }
        }
    }

    /**
     * @param propertiesFileName name of the .properties file to place/use under $HOME (e.g.
     *        .geogig-mongo-test.properties, .geogig-pg-test.properties, etc)
     */
    public OnlineTestProperties(final String propertiesFileName, final String... defaultsKvp) {
        this.propertiesFileName = propertiesFileName;
        this.defaults = defaultsKvp;
    }

    private File config() {
        File f = new File(System.getProperty("user.home"), propertiesFileName);
        try {
            if (!f.exists()) {
                f.createNewFile();

                // Populate the file with default values
                if (defaults != null) {
                    for (int i = 0; i < defaults.length; i += 2) {
                        String k = defaults[i];
                        String v = defaults[i + 1];
                        put(k, v);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot write to the home directory.");
        }
        return f;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> c) {
        if (key == null) {
            throw new RuntimeException("Section.key not provided to get.");
        }

        File configFile = config();

        final SectionOptionPair pair = new SectionOptionPair(key);
        try {
            final INIFile ini = INIFile.forFile(configFile);
            Optional<String> val = ini.get(pair.section, pair.option);

            if (!val.isPresent())
                return Optional.absent();
            String value = val.get();
            if (Strings.isNullOrEmpty(value))
                return Optional.absent();

            if (int.class.equals(c) || Integer.class.equals(c)) {
                return Optional.of((T) Integer.valueOf(value));
            } else if (Boolean.class.equals(c)) {
                return Optional.of((T) Boolean.valueOf(value));
            } else {
                return Optional.of(c.cast(value));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Config location invalid.");
        }
    }

    public void put(String key, String value) {
        final SectionOptionPair pair = new SectionOptionPair(key);

        File configFile = config();

        try {
            final INIFile ini = INIFile.forFile(configFile);
            ini.set(pair.section, pair.option, value);
        } catch (Exception e) {
            throw new RuntimeException("Config location invalid.");
        }
    }
}
