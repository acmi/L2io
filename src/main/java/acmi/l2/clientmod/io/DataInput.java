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

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static java.nio.charset.StandardCharsets.UTF_16LE;

public interface DataInput {
    default void skip(int n) throws UncheckedIOException {
        if (n <= 0) {
            return;
        }

        for (byte[] skipBuffer = new byte[0x1000]; n > skipBuffer.length; n -= skipBuffer.length) {
            readFully(skipBuffer);
        }
        readFully(new byte[n]);
    }

    default void readFully(byte[] b) throws UncheckedIOException {
        readFully(b, 0, b.length);
    }

    default void readFully(byte[] b, int off, int len) throws UncheckedIOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        for (int i = 0; i < len; i++) {
            b[off + i] = (byte) readUnsignedByte();
        }
    }

    int readUnsignedByte() throws UncheckedIOException;

    default int readUnsignedShort() throws UncheckedIOException {
        int ch1 = readUnsignedByte();
        int ch2 = readUnsignedByte();
        return ch1 + (ch2 << 8);
    }

    default int readInt() throws UncheckedIOException {
        int ch1 = readUnsignedByte();
        int ch2 = readUnsignedByte();
        int ch3 = readUnsignedByte();
        int ch4 = readUnsignedByte();
        return (ch1 + (ch2 << 8) + (ch3 << 16) + (ch4 << 24));
    }

    default int readCompactInt() throws UncheckedIOException {
        return ByteUtil.compactIntFromBytes(this::readUnsignedByte);
    }

    default long readLong() throws UncheckedIOException {
        return ((((long) readUnsignedByte())) |
                (((long) readUnsignedByte()) << 8) |
                (((long) readUnsignedByte()) << 16) |
                (((long) readUnsignedByte()) << 24) |
                (((long) readUnsignedByte()) << 32) |
                (((long) readUnsignedByte()) << 40) |
                (((long) readUnsignedByte()) << 48) |
                (((long) readUnsignedByte()) << 56));
    }

    default float readFloat() throws UncheckedIOException {
        return Float.intBitsToFloat(readInt());
    }

    Charset getCharset();

    default String readLine() throws UncheckedIOException {
        int len = readCompactInt();

        if (len == 0) {
            return "";
        }

        byte[] bytes = new byte[len > 0 ? len : -2 * len];
        readFully(bytes);
        return new String(bytes, 0, bytes.length - (len > 0 ? 1 : 2), len > 0 && getCharset() != null ? getCharset() : UTF_16LE);
    }

    default String readUTF() throws UncheckedIOException {
        int len = readInt();

        if (len < 0) {
            throw new IllegalStateException("Invalid string length: " + len);
        }

        if (len == 0) {
            return "";
        }

        byte[] bytes = new byte[len];
        readFully(bytes);
        return new String(bytes, UTF_16LE);
    }

    default byte[] readByteArray() throws UncheckedIOException {
        int len = readCompactInt();

        if (len < 0) {
            throw new IllegalStateException("Invalid array length: " + len);
        }

        byte[] array = new byte[len];
        readFully(array);
        return array;
    }

    int getPosition() throws UncheckedIOException;

    static DataInput dataInput(InputStream inputStream, Charset charset) {
        return dataInput(inputStream, charset, 0);
    }

    static DataInput dataInput(InputStream inputStream, Charset charset, int position) {
        return new DataInputStream(inputStream, charset, position);
    }

    static DataInput dataInput(ByteBuffer buffer, Charset charset) {
        return dataInput(buffer, charset, 0);
    }

    static DataInput dataInput(ByteBuffer buffer, Charset charset, int position) {
        return RandomAccess.randomAccess(buffer, null, charset, position);
    }
}
