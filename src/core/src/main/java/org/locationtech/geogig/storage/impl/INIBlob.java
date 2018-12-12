/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Optional;

/**
 * Simple implementation of an INI parser and serializer that operates on byte arrays.
 */
public abstract class INIBlob {

    /**
     * Content of the blob
     */
    private List<Entry> data = null;

    public abstract byte[] iniBytes() throws IOException;

    public abstract void setBytes(byte[] bytes) throws IOException;

    public synchronized Optional<String> get(String section, String key) throws IOException {
        if (section == null || section.length() == 0) {
            throw new IllegalArgumentException("Section name required");
        }
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("Key required");
        }
        checkReload();
        for (Entry e : data) {
            Optional<String> optVal = e.get(section, key);
            if (optVal.isPresent())
                return optVal;
        }
        return Optional.absent();
    }

    public synchronized Map<String, String> getAll() throws IOException {
        checkReload();
        Map<String, String> m = new HashMap<String, String>();
        for (Entry e : data) {
            if (e instanceof Section) {
                Section s = (Section) e;
                for (KeyAndValue kv : s.getValues()) {
                    m.put(s.getHeader() + "." + kv.getKey(), kv.getValue());
                }
            }
        }
        return m;
    }

    public synchronized List<String> listSubsections(String section) throws IOException {
        if (section == null || section.length() == 0) {
            throw new IllegalArgumentException("Section name required");
        }
        checkReload();
        List<String> results = new ArrayList<String>();
        for (Entry e : data) {
            if (e instanceof Section) {
                Section s = (Section) e;
                if (s.getHeader().startsWith(section + ".")) {
                    results.add(s.getHeader().substring(section.length() + 1));
                }
            }
        }
        return results;
    }

    public synchronized Map<String, String> getSection(String section) throws IOException {
        if (section == null || section.length() == 0) {
            throw new IllegalArgumentException("Section name required");
        }
        checkReload();
        for (Entry e : data) {
            if (e instanceof Section) {
                Section s = (Section) e;
                if (s.getHeader().equals(section)) {
                    Map<String, String> values = new HashMap<String, String>();
                    for (KeyAndValue kv : s.getValues()) {
                        values.put(kv.getKey(), kv.getValue());
                    }
                    return values;
                }
            }
        }
        return new HashMap<String, String>();
    }

    public synchronized void set(String section, String key, String value) throws IOException {
        if (section == null || section.length() == 0) {
            throw new IllegalArgumentException("Section name required");
        }
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("Key required");
        }
        checkReload();
        boolean written = false;
        for (Entry e : data) {
            written = e.set(section, key, value);
            if (written) {
                break;
            }
        }
        if (!written) { // didn't add to an existing section, time to add a new section.
            List<KeyAndValue> kvs = new ArrayList<KeyAndValue>();
            kvs.add(new KeyAndValue(key, value));
            data.add(new Section(section, kvs));
        }
        write();
    }

    public synchronized void removeSection(String section) throws IOException {
        if (section == null || section.length() == 0) {
            throw new IllegalArgumentException("Section name required");
        }
        checkReload();
        boolean written = false;
        Iterator<Entry> iter = data.iterator();
        while (iter.hasNext()) {
            Entry e = iter.next();
            if (e instanceof Section && ((Section) e).getHeader().equals(section)) {
                iter.remove();
                written = true;
                break;
            }
        }
        if (written) {
            write();
        } else {
            throw new NoSuchElementException("No such section");
        }
    }

    public synchronized void remove(String section, String key) throws IOException {
        if (section == null || section.length() == 0) {
            throw new IllegalArgumentException("Section name required");
        }
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("Section name required");
        }
        checkReload();
        boolean written = false;
        for (Entry e : data) {

            written |= e.unset(section, key);
        }
        if (written) {
            write();
        }
    }

    private final static class KeyAndValue {
        private String key, value;

        public KeyAndValue(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return this.key;
        }

        public String getValue() {
            return this.value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    private static abstract class Entry {
        public abstract void write(PrintWriter w);

        public Optional<String> get(String section, String key) {
            // No-op
            return Optional.absent();
        }

        public boolean set(String section, String key, String value) {
            // No-op
            return false;
        }

        public boolean unset(String section, String key) {
            return false;
        }
    }

    private static class Section extends Entry {
        private String header;

        private List<KeyAndValue> values;

        public Section(String header, List<KeyAndValue> values) {
            this.header = header;
            this.values = values;
        }

        public String getHeader() {
            return this.header;
        }

        public List<KeyAndValue> getValues() {
            return Collections.unmodifiableList(values);
        }

        @Override
        public Optional<String> get(String section, String key) {
            if (!header.equals(section)) {
                return Optional.absent();
            } else {
                for (KeyAndValue kv : values) {
                    if (kv.getKey().equals(key)) {
                        return Optional.of(kv.getValue());
                    }
                }
                return Optional.absent();
            }
        }

        @Override
        public boolean set(String section, String key, String value) {
            if (!header.equals(section)) {
                return false;
            } else {
                for (KeyAndValue kv : values) {
                    if (kv.getKey().equals(key)) {
                        kv.setValue(value);
                        return true;
                    }
                }

                values.add(new KeyAndValue(key, value));
                return true;
            }
        }

        @Override
        public boolean unset(String section, String key) {
            if (!header.equals(section)) {
                return false;
            } else {
                boolean modified = false;
                Iterator<KeyAndValue> viterator = values.iterator();
                while (viterator.hasNext()) {
                    if (viterator.next().getKey().equals(key)) {
                        viterator.remove();
                        modified = true;
                    }
                }
                return modified;
            }
        }

        public void write(PrintWriter w) {
            w.println("[" + header.replaceAll("\\.", "\\\\") + "]");
            for (KeyAndValue kv : values) {
                w.println(kv.getKey() + " = " + kv.getValue());
            }
        }

        @Override
        public String toString() {
            StringBuffer buff = new StringBuffer();
            buff.append("[" + header + "]");
            for (KeyAndValue kv : values) {
                buff.append("[" + kv.getKey() + " : " + kv.getValue() + "]");
            }
            return buff.toString();
        }
    }

    private static class Blanks extends Entry {
        private int nBlanks;

        public Blanks(int nBlanks) {
            this.nBlanks = nBlanks;
        }

        public void write(PrintWriter w) {
            for (int i = nBlanks; i > 0; i--) {
                w.print(" ");
            }
            w.println();
        }
    }

    private static class Comment extends Entry {
        private String content;

        public Comment(String content) {
            this.content = content;
        }

        public void write(PrintWriter w) {
            w.println("#" + content);
        }
    }

    private void checkReload() throws IOException {
        if (data == null || needsReload()) {
            reload(iniBytes());
        }
    }

    public boolean needsReload() {
        return false;
    }

    // Note. If you're tweaking these be careful, throwing an exception in a
    // static initializer prevents the class from being loaded entirely.
    private static Pattern SECTION_HEADER = Pattern
            .compile("^\\p{Space}*\\[([^\\[\\]]+)]\\p{Space}*$");

    private static Pattern KEY_VALUE = Pattern
            .compile("^\\p{Space}*([^=\\p{Space}]+)\\p{Space}*=\\p{Space}*(.*)\\p{Space}*$");

    private static Pattern BLANK = Pattern.compile("^(\\p{Space}*)$");

    private static Pattern COMMENT = Pattern.compile("^\\p{Space}*#(.*)$");

    private void reload(byte[] ini) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(ini)));
            String sectionName = null;
            List<Entry> results = new ArrayList<Entry>();
            List<KeyAndValue> kvs = new ArrayList<KeyAndValue>();
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m;
                if ((m = SECTION_HEADER.matcher(line)).matches()) {
                    String header = m.group(1);
                    if (sectionName != null) {
                        results.add(new Section(sectionName, kvs));
                        kvs = new ArrayList<KeyAndValue>();
                    }
                    sectionName = header.replaceAll("\\\\", ".");
                } else if ((m = KEY_VALUE.matcher(line)).matches()) {
                    if (sectionName != null) { // if we haven't encountered a section name yet,
                                               // ignore the values
                        String key = m.group(1);
                        String value = m.group(2);
                        kvs.add(new KeyAndValue(key, value));
                    }
                } else if ((m = BLANK.matcher(line)).matches()) {
                    String blanks = m.group(1);
                    results.add(new Blanks(blanks.length()));
                } else if ((m = COMMENT.matcher(line)).matches()) {
                    String comment = m.group(1);
                    results.add(new Comment(comment));
                }
                // If no pattern matches we have an invalid .ini blob but we just drop those lines.
            }
            if (sectionName != null) {
                results.add(new Section(sectionName, kvs));
            }
            data = results;
        } catch (IOException e) {
            data = new ArrayList<Entry>();
        } catch (RuntimeException e) {
            data = new ArrayList<Entry>();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private void write() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(stream)));
        try {
            for (Entry e : data) {
                e.write(writer);
            }
        } finally {
            writer.flush();
            writer.close();
        }
        setBytes(stream.toByteArray());
    }
}
