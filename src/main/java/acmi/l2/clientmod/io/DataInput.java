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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public interface DataInput {
    int read() throws UncheckedIOException;

    default void skip(int n) throws UncheckedIOException {
        if (n <= 0)
            return;

        for (byte[] skipBuffer = new byte[0x1000]; n > skipBuffer.length; n -= skipBuffer.length) {
            readFully(skipBuffer);
        }
        readFully(new byte[n]);
    }

    default void readFully(byte[] b) throws UncheckedIOException {
        readFully(b, 0, b.length);
    }

    default void readFully(byte b[], int off, int len) throws UncheckedIOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        for (int i = 0; i < len; i++)
            b[off + i] = (byte) readUnsignedByte();
    }

    default int readUnsignedByte() throws UncheckedIOException {
        int b = read();
        if (b < 0)
            throw new UncheckedIOException(new EOFException());
        return b;
    }

    default int readUnsignedShort() throws UncheckedIOException {
        int ch1 = read();
        int ch2 = read();
        if ((ch1 | ch2) < 0)
            throw new UncheckedIOException(new EOFException());
        return ch1 + (ch2 << 8);
    }

    default int readInt() throws UncheckedIOException {
        int ch1 = read();
        int ch2 = read();
        int ch3 = read();
        int ch4 = read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new UncheckedIOException(new EOFException());
        return (ch1 + (ch2 << 8) + (ch3 << 16) + (ch4 << 24));
    }

    default int readCompactInt() throws UncheckedIOException {
        int output = 0;
        boolean signed = false;
        for (int i = 0; i < 5; i++) {
            int x = readUnsignedByte();
            if (i == 0) {
                if ((x & 0x80) > 0)
                    signed = true;
                output |= (x & 0x3F);
                if ((x & 0x40) == 0)
                    break;
            } else if (i == 4) {
                output |= (x & 0x1F) << (6 + (3 * 7));
            } else {
                output |= (x & 0x7F) << (6 + ((i - 1) * 7));
                if ((x & 0x80) == 0)
                    break;
            }
        }
        if (signed) {
            if (output == 0)
                return Integer.MIN_VALUE;
            else
                return -output;
        } else {
            return output;
        }
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

        if (len == 0)
            return "";

        byte[] bytes = new byte[len > 0 ? len : -2 * len];
        readFully(bytes);
        return new String(bytes, 0, bytes.length - (len > 0 ? 1 : 2), len > 0 && getCharset() != null ? getCharset() : Charset.forName("utf-16le"));
    }

    default String readUTF() throws UncheckedIOException {
        int len = readInt();

        if (len < 0)
            throw new IllegalStateException("Invalid string length: " + len);

        if (len == 0)
            return "";

        byte[] bytes = new byte[2 * len];
        readFully(bytes);
        return new String(bytes, Charset.forName("utf-16le"));
    }

    default byte[] readByteArray() throws UncheckedIOException {
        int len = readCompactInt();

        if (len < 0)
            throw new IllegalStateException("Invalid array length: " + len);

        byte[] array = new byte[len];
        readFully(array);
        return array;
    }

    int getPosition() throws UncheckedIOException;

    static DataInput dataInput(InputStream inputStream, Charset charset) {
        return dataInput(inputStream, 0, charset);
    }

    static DataInput dataInput(InputStream inputStream, int position, Charset charset) {
        return new DataInput() {
            private int pos = position;

            @Override
            public Charset getCharset() {
                return charset;
            }

            @Override
            public int getPosition() {
                return pos;
            }

            private void incCount(int value) {
                int temp = pos + value;
                if (temp < 0) {
                    temp = Integer.MAX_VALUE;
                }
                pos = temp;
            }

            @Override
            public int read() throws UncheckedIOException {
                try {
                    int tmp = inputStream.read();
                    if (tmp >= 0)
                        incCount(1);
                    return tmp;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void readFully(byte[] b, int off, int len) throws UncheckedIOException {
                if (len < 0)
                    throw new IndexOutOfBoundsException();

                try {
                    int n = 0;
                    while (n < len) {
                        int count = inputStream.read(b, off + n, len - n);
                        if (count < 0)
                            throw new EOFException();
                        n += count;

                        incCount(count);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }

    static DataInput dataInput(ByteBuffer buffer, Charset charset) {
        return dataInput(buffer, 0, charset);
    }

    static DataInput dataInput(ByteBuffer buffer, int position, Charset charset) {
        return new DataInput() {
            @Override
            public int read() throws UncheckedIOException {
                if (buffer.position() >= buffer.limit())
                    return -1;

                return buffer.get() & 0xff;
            }

            @Override
            public Charset getCharset() {
                return charset;
            }

            @Override
            public int getPosition() throws UncheckedIOException {
                return position + buffer.position();
            }
        };
    }
}
