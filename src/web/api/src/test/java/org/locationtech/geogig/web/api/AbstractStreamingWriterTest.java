/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;

/**
 *
 */
public abstract class AbstractStreamingWriterTest {

    protected static final String CDATA1 = "\n" +
            "    Since this is a CDATA section\n" +
            "    I can use all sorts of reserved characters\n" +
            "    like > < \" and &\n" +
            "    or write things like\n" +
            "    <foo></bar>\n" +
            "    but my document is still well formed!\n";

    protected static final String CDATA2 = "\n" +
            "    This is another CDATA section\n";

    private StringWriter sink;
    private StreamingWriter writer;

    protected abstract StreamingWriter createWriter(Writer sink);

    @Before
    public void before() {
        // create the output sink
        sink = new StringWriter(256);
        // create the JSON stream
        writer = createWriter(sink);
        // start the doc
        writer.writeStartDocument();
    }

    @After
    public void after() {
        try {
            writer.flush();
            writer.close();
            sink.close();
        } catch (Exception ex) {
            // ignore
        } finally {
            writer = null;
            sink = null;
        }
    }

    private void closeDocument() throws IOException {
        // close the document
        writer.writeEndDocument();
        // flush the writer
        writer.flush();
    }

    private void verify(final String[] paths, final String value) throws IOException {
        verifyInternal(paths, value, sink.toString());
    }

    private void verifyAttribute(final String[] paths, final String value) throws IOException {
        verifyAttributeInternal(paths, value, sink.toString());
    }

    private void verifyArray(final String[] paths, final String[] values) throws IOException {
        verifyArrayInternal(paths, values, sink.toString());
    }

    private void verifyArrayElement(final String[] paths, final String value) throws IOException {
        verifyArrayElementInternal(paths, value, sink.toString());
    }

    protected abstract void verifyInternal(final String[] paths, final String value,
            final String actualBuffer) throws IOException;

    protected abstract void verifyAttributeInternal(final String[] paths, final String value,
            final String actualBuffer) throws IOException;

    protected abstract void verifyArrayInternal(final String[] paths, final String[] values,
            final String actualBuffer) throws IOException;

    protected abstract void verifyArrayElementInternal(final String[] paths, final String value,
            final String actualBuffer) throws IOException;

    @Test
    public void testWriteSingleStringElement() throws IOException {
        writer.writeElement("stringElement", "StringValue");
        closeDocument();
        verify(new String[]{"stringElement"}, "StringValue");
    }

    @Test
    public void testWriteCompositeElement() throws IOException {
        writer.writeStartElement("compositeElement");
        writer.writeElement("stringElement", "StringValue");
        writer.writeEndElement();
        closeDocument();
        verify(new String[]{"compositeElement", "stringElement"}, "StringValue");
    }

    @Test
    public void testWriteEmptyArray() throws IOException {
        writer.writeStartElement("root");
        writer.writeStartArray("simpleArray");
        writer.writeEndArray();
        writer.writeEndElement();
        closeDocument();
        verifyArray(new String[]{"root", "simpleArray"}, new String[]{});
    }

    @Test
    public void testWriteArrayElements() throws IOException {
        writer.writeStartElement("root");
        writer.writeStartArray("simpleArray");
        writer.writeArrayElement("simpleArray", "StringValue1");
        writer.writeArrayElement("simpleArray", "StringValue2");
        writer.writeArrayElement("simpleArray", "StringValue3");
        writer.writeEndArray();
        writer.writeEndElement();
        closeDocument();
        verifyArray(new String[]{"root", "simpleArray"},
                new String[]{"StringValue1", "StringValue2", "StringValue3"});
    }

    @Test
    public void testWriteNullObjectId() throws IOException {
        // verify that a series of zeros is interpreted as a String, not an int
        writer.writeElement("objectId", ObjectId.NULL);
        closeDocument();
        verify(new String[]{"objectId"}, "0000000000000000000000000000000000000000");
    }

    @Test
    public void testWriteInteger() throws IOException {
        writer.writeElement("integerElement", Integer.valueOf(17));
        closeDocument();
        verify(new String[]{"integerElement"}, "17");
    }

    @Test
    public void testWriteLong() throws IOException {
        writer.writeElement("longElement", Long.valueOf("1234567890987"));
        closeDocument();
        verify(new String[]{"longElement"}, "1234567890987");
    }

    @Test
    public void testWriteFloat() throws IOException {
        writer.writeElement("floatElement", Float.valueOf("1234.56"));
        closeDocument();
        verify(new String[]{"floatElement"}, "1234.56");
    }

    @Test
    public void testWriteDouble() throws IOException {
        writer.writeElement("doubleElement", Double.valueOf("12345343.55331"));
        closeDocument();
        verify(new String[]{"doubleElement"}, "12345343.55331");
    }

    @Test
    public void testWriteBigInteger() throws IOException {
        writer.writeElement("bigIntegerElement", BigInteger.TEN);
        closeDocument();
        verify(new String[]{"bigIntegerElement"}, "10");
    }

    @Test
    public void testWriteBigDecimal() throws IOException {
        writer.writeElement("bigDecimalElement", new BigDecimal("1.234567890987654321"));
        closeDocument();
        verify(new String[]{"bigDecimalElement"}, "1.234567890987654321");
    }

    @Test
    public void testWriteNull() throws IOException {
        writer.writeElement("nullElement", null);
        closeDocument();
        verify(new String[]{"nullElement"}, null);
    }

    @Test
    public void testWriteNullObjectIdInArray() throws IOException {
        // verify that a series of zeros is interpreted as a String, not an int
        writer.writeStartElement("root");
        writer.writeStartArray("objectIds");
        writer.writeArrayElement("objectIds", ObjectId.NULL);
        writer.writeEndArray();
        writer.writeEndElement();
        closeDocument();
        verifyArray(new String[]{"root", "objectIds"}, new String[]{"0000000000000000000000000000000000000000"});
    }

    @Test
    public void testWriteIntegerInArray() throws IOException {
        writer.writeStartElement("root");
        writer.writeStartArray("integerArrayElements");
        writer.writeArrayElement("integerArrayElements", Integer.valueOf(17));
        writer.writeArrayElement("integerArrayElements", Integer.valueOf(18));
        writer.writeEndArray();
        writer.writeEndElement();
        closeDocument();
        verifyArray(new String[]{"root", "integerArrayElements"}, new String[]{"17", "18"});
    }

    @Test
    public void testWriteLongInArray() throws IOException {
        writer.writeStartElement("root");
        writer.writeStartArray("longArrayElements");
        writer.writeArrayElement("longArrayElements", Long.valueOf("1234567890987"));
        writer.writeArrayElement("longArrayElements", Long.valueOf("1234567890988"));
        writer.writeEndArray();
        writer.writeEndElement();
        closeDocument();
        verifyArray(new String[]{"root", "longArrayElements"}, new String[]{"1234567890987", "1234567890988"});
    }

    @Test
    public void testWriteFloatInArray() throws IOException {
        writer.writeStartElement("root");
        writer.writeStartArray("floatArrayElements");
        writer.writeArrayElement("floatArrayElements", Float.valueOf("1234.56"));
        writer.writeArrayElement("floatArrayElements", Float.valueOf("9876.54"));
        writer.writeEndArray();
        writer.writeEndElement();
        closeDocument();
        verifyArray(new String[]{"root", "floatArrayElements"}, new String[]{"1234.56", "9876.54"});
    }

    @Test
    public void testWriteDoubleInArray() throws IOException {
        writer.writeStartElement("root");
        writer.writeStartArray("doubleArrayElements");
        writer.writeArrayElement("doubleArrayElements", Double.valueOf("12345343.55331"));
        writer.writeArrayElement("doubleArrayElements", Double.valueOf("98765432.12345"));
        writer.writeEndArray();
        writer.writeEndElement();
        closeDocument();
        verifyArray(new String[]{"root", "doubleArrayElements"}, new String[]{"12345343.55331", "98765432.12345"});
    }

    @Test
    public void testWriteBigIntegerInArray() throws IOException {
        writer.writeStartElement("root");
        writer.writeStartArray("bigIntegerArrayElements");
        writer.writeArrayElement("bigIntegerArrayElements", new BigInteger("1234567890"));
        writer.writeArrayElement("bigIntegerArrayElements", new BigInteger("9876543210"));
        writer.writeEndArray();
        writer.writeEndElement();
        closeDocument();
        verifyArray(new String[]{"root", "bigIntegerArrayElements"}, new String[]{"1234567890", "9876543210"});
    }

    @Test
    public void testWriteBigDecimalInArray() throws IOException {
        writer.writeStartElement("root");
        writer.writeStartArray("bigDecimalElements");
        writer.writeArrayElement("bigDecimalElements", new BigDecimal("1.234567890987654321"));
        writer.writeArrayElement("bigDecimalElements", new BigDecimal("98.76543210"));
        writer.writeEndArray();
        writer.writeEndElement();
        closeDocument();
        verifyArray(new String[]{"root", "bigDecimalElements"}, new String[]{"1.234567890987654321", "98.76543210"});
    }

    @Test
    public void testWriteNullInArray() throws IOException {
        writer.writeStartElement("root");
        writer.writeStartArray("nullArrayElements");
        writer.writeArrayElement("nullArrayElements", null);
        writer.writeArrayElement("nullArrayElements", new Object() {
            @Override
            public String toString() {
                return null;
            }
        });
        writer.writeEndArray();
        writer.writeEndElement();
        closeDocument();
        verifyArray(new String[]{"root", "nullArrayElements"}, new String[]{null, null});
    }

    @Test
    public void testWriteBoolean() throws IOException {
        writer.writeStartElement("root");
        writer.writeElement("booleanElement1", Boolean.TRUE);
        writer.writeElement("booleanElement2", Boolean.FALSE);
        writer.writeElement("booleanElement3", "true");
        writer.writeElement("booleanElement4", "false");
        writer.writeElement("fakeBooleanElement5", "True");
        writer.writeElement("fakeBooleanElement6", "False");
        writer.writeElement("fakeBooleanElement7", "TRUe");
        writer.writeElement("fakeBooleanElement8", "FAlSE");
        writer.writeEndElement();
        closeDocument();
        verify(new String[]{"root", "booleanElement1"}, "true");
        verify(new String[]{"root", "booleanElement2"}, "false");
        verify(new String[]{"root", "booleanElement3"}, "true");
        verify(new String[]{"root", "booleanElement4"}, "false");
        verify(new String[]{"root", "fakeBooleanElement5"}, "True");
        verify(new String[]{"root", "fakeBooleanElement6"}, "False");
        verify(new String[]{"root", "fakeBooleanElement7"}, "TRUe");
        verify(new String[]{"root", "fakeBooleanElement8"}, "FAlSE");
    }

    @Test
    public void testWriteBooleanInArray() throws IOException {
        writer.writeStartElement("root");
        writer.writeStartArray("booleans");
        writer.writeArrayElement("booleans", Boolean.TRUE);
        writer.writeArrayElement("booleans", Boolean.FALSE);
        writer.writeArrayElement("booleans", "true");
        writer.writeArrayElement("booleans", "false");
        writer.writeArrayElement("booleans", "True");
        writer.writeArrayElement("booleans", "False");
        writer.writeArrayElement("booleans", "TRUe");
        writer.writeArrayElement("booleans", "FAlSE");
        writer.writeEndArray();
        writer.writeEndElement();
        closeDocument();
        verifyArray(new String[]{"root", "booleans"},
                new String[]{"true", "false", "true", "false", "True", "False", "TRUe", "FAlSE"});
    }

    @Test
    public void testWriteCDataElement() throws IOException {
        writer.writeCDataElement("CDataElement", CDATA1);
        closeDocument();
        verify(new String[]{"CDataElement"}, CDATA1);
    }

    @Test
    public void testWriteCDataArrayElement() throws IOException {
        writer.writeStartElement("root");
        writer.writeStartArray("CDataElements");
        writer.writeCDataArrayElement("CDataElements", CDATA1);
        writer.writeCDataArrayElement("CDataElements", CDATA2);
        writer.writeEndArray();
        writer.writeEndElement();
        closeDocument();
        verifyArray(new String[]{"root", "CDataElements"}, new String[]{CDATA1, CDATA2});
    }

    @Test
    public void testWriteCDataNullElement() throws IOException {
        writer.writeStartElement("root");
        writer.writeCDataElement("CDataElement", null);
        writer.writeEndElement();
        closeDocument();
        verify(new String[]{"root", "CDataElement"}, null);
    }

    @Test
    public void testWriteAttribute() throws IOException {
        writer.writeStartElement("root");
        writer.writeStartElement("attributeElement");
        writer.writeAttribute("attribute1", "value1");
        writer.writeAttribute("attribute2", "value2");
        writer.writeEndElement();
        writer.writeEndElement();
        closeDocument();
        verifyAttribute(new String[]{"root", "attributeElement", "attribute1"}, "value1");
        verifyAttribute(new String[]{"root", "attributeElement", "attribute2"}, "value2");
    }

    @Test
    public void testWriteStartArrayElement() throws IOException {
        writer.writeStartElement("root");
        writer.writeStartArray("arrayElements");
        writer.writeStartArrayElement("arrayElements");
        writer.writeElement("arrayElement1", "value1");
        writer.writeEndArrayElement();
        writer.writeStartArrayElement("arrayElements");
        writer.writeElement("arrayElement2", "value2");
        writer.writeEndArrayElement();
        writer.writeEndArray();
        writer.writeEndElement();
        closeDocument();
        verifyArrayElement(new String[]{"root", "arrayElements", "arrayElement1"}, "value1");
        verifyArrayElement(new String[]{"root", "arrayElements", "arrayElement2"}, "value2");
    }
}
