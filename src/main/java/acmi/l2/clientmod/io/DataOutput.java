/*
 * Copyright (c) 2016 acmi
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

import java.io.IOException;
import java.nio.charset.Charset;

import static acmi.l2.clientmod.io.ByteUtil.compactIntToByteArray;

public interface DataOutput {
    void write(int b) throws IOException;

    default void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    default void write(byte[] b, int off, int len) throws IOException {
        if ((off | len | (b.length - (len + off)) | (off + len)) < 0)
            throw new IndexOutOfBoundsException();

        for (int i = 0; i < len; i++) {
            write(b[off + i]);
        }
    }

    default void writeByte(int val) throws IOException {
        write(val);
    }

    default void writeShort(int val) throws IOException {
        write((val) & 0xFF);
        write((val >>> 8) & 0xFF);
    }

    default void writeInt(int val) throws IOException {
        write((val) & 0xFF);
        write((val >>> 8) & 0xFF);
        write((val >>> 16) & 0xFF);
        write((val >>> 24) & 0xFF);
    }

    default void writeCompactInt(int val) throws IOException {
        write(compactIntToByteArray(val));
    }

    default void writeLong(long val) throws IOException {
        write((int) val);
        write((int) (val >> 8));
        write((int) (val >> 16));
        write((int) (val >> 24));
        write((int) (val >> 32));
        write((int) (val >> 40));
        write((int) (val >> 48));
        write((int) (val >> 56));
    }

    default void writeFloat(float val) throws IOException {
        writeInt(Float.floatToIntBits(val));
    }

    Charset getCharset();

    default void writeBytes(String s) throws IOException {
        byte[] strBytes = (s + '\0').getBytes(getCharset());
        writeCompactInt(strBytes.length);
        write(strBytes);
    }

    default void writeChars(String s) throws IOException {
        byte[] strBytes = (s + '\0').getBytes("utf-16le");
        writeCompactInt(-strBytes.length);
        write(strBytes);
    }

    default void writeLine(String s) throws IOException {
        try {
            if (getCharset().newEncoder().canEncode(s)) {
                writeBytes(s);
                return;
            }
        } catch (NullPointerException | UnsupportedOperationException ignore) {
        }

        writeChars(s);
    }

    default void writeUTF(String s) throws IOException {
        byte[] strBytes = s.getBytes("utf-16le");
        writeInt(strBytes.length);
        write(strBytes);
    }

    default void writeByteArray(byte[] array) throws IOException {
        writeCompactInt(array.length);
        write(array);
    }

    int getPosition() throws IOException;
}
