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
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static acmi.l2.clientmod.io.ByteUtil.compactIntToByteArray;

public interface DataOutput {
    void write(int b) throws UncheckedIOException;

    default void write(byte[] b) throws UncheckedIOException {
        write(b, 0, b.length);
    }

    default void write(byte[] b, int off, int len) throws UncheckedIOException {
        if ((off | len | (b.length - (len + off)) | (off + len)) < 0)
            throw new IndexOutOfBoundsException();

        for (int i = 0; i < len; i++) {
            write(b[off + i]);
        }
    }

    default void writeByte(int val) throws UncheckedIOException {
        write(val);
    }

    default void writeShort(int val) throws UncheckedIOException {
        write((val) & 0xFF);
        write((val >>> 8) & 0xFF);
    }

    default void writeInt(int val) throws UncheckedIOException {
        write((val) & 0xFF);
        write((val >>> 8) & 0xFF);
        write((val >>> 16) & 0xFF);
        write((val >>> 24) & 0xFF);
    }

    default void writeCompactInt(int val) throws UncheckedIOException {
        write(compactIntToByteArray(val));
    }

    default void writeLong(long val) throws UncheckedIOException {
        write((int) val);
        write((int) (val >> 8));
        write((int) (val >> 16));
        write((int) (val >> 24));
        write((int) (val >> 32));
        write((int) (val >> 40));
        write((int) (val >> 48));
        write((int) (val >> 56));
    }

    default void writeFloat(float val) throws UncheckedIOException {
        writeInt(Float.floatToIntBits(val));
    }

    Charset getCharset();

    default void writeBytes(String s) throws UncheckedIOException {
        if (s == null || s.isEmpty())
            writeCompactInt(0);
        else {
            byte[] strBytes = (s + '\0').getBytes(getCharset());
            writeCompactInt(strBytes.length);
            write(strBytes);
        }
    }

    default void writeChars(String s) throws UncheckedIOException {
        if (s == null || s.isEmpty())
            writeCompactInt(0);
        else {
            byte[] strBytes = (s + '\0').getBytes(Charset.forName("utf-16le"));
            writeCompactInt(-strBytes.length);
            write(strBytes);
        }
    }

    default void writeLine(String s) throws UncheckedIOException {
        if (s == null || s.isEmpty())
            writeCompactInt(0);
        else if (getCharset() != null && getCharset().canEncode() && getCharset().newEncoder().canEncode(s)) {
            writeBytes(s);
        } else {
            writeChars(s);
        }
    }

    default void writeUTF(String s) throws UncheckedIOException {
        if (s == null || s.isEmpty())
            writeInt(0);
        else {
            byte[] strBytes = s.getBytes(Charset.forName("utf-16le"));
            writeInt(strBytes.length);
            write(strBytes);
        }
    }

    default void writeByteArray(byte[] array) throws UncheckedIOException {
        writeCompactInt(array.length);
        write(array);
    }

    int getPosition() throws IOException;

    static DataOutput dataOutput(OutputStream outputStream, Charset charset) {
        return dataOutput(outputStream, charset, 0);
    }

    static DataOutput dataOutput(OutputStream outputStream, Charset charset, int position) {
        return new DataOutput() {
            private int pos = position;

            @Override
            public Charset getCharset() {
                return charset;
            }

            @Override
            public int getPosition() {
                return pos;
            }

            protected void incCount(int value) {
                int temp = pos + value;
                if (temp < 0) {
                    temp = Integer.MAX_VALUE;
                }
                pos = temp;
            }

            @Override
            public void write(int b) throws UncheckedIOException {
                try {
                    outputStream.write(b);

                    incCount(1);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws UncheckedIOException {
                try {
                    outputStream.write(b, off, len);

                    incCount(len);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }

    static DataOutput dataOutput(ByteBuffer buffer, Charset charset) {
        return dataOutput(buffer, charset, 0);
    }

    static DataOutput dataOutput(ByteBuffer buffer, Charset charset, int position) {
        return RandomAccess.randomAccess(buffer, null, charset, position);
    }
}
