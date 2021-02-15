/*
 * Copyright (c) 2021 acmi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package acmi.l2.clientmod.io;

import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static acmi.l2.clientmod.io.ByteUtil.compactIntToByteArray;
import static java.nio.charset.StandardCharsets.UTF_16LE;

public interface DataOutput {
    void writeByte(int b) throws UncheckedIOException;

    default void writeBytes(byte[] b) throws UncheckedIOException {
        writeBytes(b, 0, b.length);
    }

    default void writeBytes(byte[] b, int off, int len) throws UncheckedIOException {
        if ((off | len | (b.length - (len + off)) | (off + len)) < 0) {
            throw new IndexOutOfBoundsException();
        }

        for (int i = 0; i < len; i++) {
            writeByte(b[off + i]);
        }
    }

    default void writeShort(int val) throws UncheckedIOException {
        writeByte((val) & 0xFF);
        writeByte((val >>> 8) & 0xFF);
    }

    default void writeInt(int val) throws UncheckedIOException {
        writeByte((val) & 0xFF);
        writeByte((val >>> 8) & 0xFF);
        writeByte((val >>> 16) & 0xFF);
        writeByte((val >>> 24) & 0xFF);
    }

    default void writeCompactInt(int val) throws UncheckedIOException {
        writeBytes(compactIntToByteArray(val));
    }

    default void writeLong(long val) throws UncheckedIOException {
        writeByte((int) val);
        writeByte((int) (val >> 8));
        writeByte((int) (val >> 16));
        writeByte((int) (val >> 24));
        writeByte((int) (val >> 32));
        writeByte((int) (val >> 40));
        writeByte((int) (val >> 48));
        writeByte((int) (val >> 56));
    }

    default void writeFloat(float val) throws UncheckedIOException {
        writeInt(Float.floatToIntBits(val));
    }

    Charset getCharset();

    default void writeBytes(String s) throws UncheckedIOException {
        if (s == null || s.isEmpty()) {
            writeCompactInt(0);
        } else {
            byte[] strBytes = (s + '\0').getBytes(getCharset());
            writeCompactInt(strBytes.length);
            writeBytes(strBytes);
        }
    }

    default void writeChars(String s) throws UncheckedIOException {
        if (s == null || s.isEmpty()) {
            writeCompactInt(0);
        } else {
            byte[] strBytes = (s + '\0').getBytes(UTF_16LE);
            writeCompactInt(-strBytes.length);
            writeBytes(strBytes);
        }
    }

    default void writeLine(String s) throws UncheckedIOException {
        if (s == null || s.isEmpty()) {
            writeCompactInt(0);
        } else if (getCharset() != null && getCharset().canEncode() && getCharset().newEncoder().canEncode(s)) {
            writeBytes(s);
        } else {
            writeChars(s);
        }
    }

    default void writeUTF(String s) throws UncheckedIOException {
        if (s == null || s.isEmpty()) {
            writeInt(0);
        } else {
            byte[] strBytes = s.getBytes(UTF_16LE);
            writeInt(strBytes.length);
            writeBytes(strBytes);
        }
    }

    default void writeByteArray(byte[] array) throws UncheckedIOException {
        writeCompactInt(array.length);
        writeBytes(array);
    }

    int getPosition() throws UncheckedIOException;

    static DataOutput dataOutput(OutputStream outputStream, Charset charset) {
        return dataOutput(outputStream, charset, 0);
    }

    static DataOutput dataOutput(OutputStream outputStream, Charset charset, int position) {
        return new DataOutputStream(outputStream, charset, position);
    }

    static DataOutput dataOutput(ByteBuffer buffer, Charset charset) {
        return dataOutput(buffer, charset, 0);
    }

    static DataOutput dataOutput(ByteBuffer buffer, Charset charset, int position) {
        return RandomAccess.randomAccess(buffer, null, charset, position);
    }
}
