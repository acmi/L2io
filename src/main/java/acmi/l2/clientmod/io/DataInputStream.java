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

import java.io.*;
import java.nio.charset.Charset;

public class DataInputStream extends FilterInputStream implements DataInput {
    private final Charset charset;
    private int position;

    public DataInputStream(InputStream in, Charset charset) {
        super(in);
        this.charset = charset;
    }

    public DataInputStream(InputStream in, Charset charset, int position) {
        this(in, charset);
        this.position = position;
    }

    @Override
    public Charset getCharset() {
        return charset;
    }

    @Override
    public int getPosition() {
        return position;
    }

    private void incCount(int value) {
        int temp = position + value;
        if (temp < 0) {
            temp = Integer.MAX_VALUE;
        }
        position = temp;
    }

    @Override
    public int read() throws IOException {
        int tmp = in.read();
        if (tmp >= 0) {
            incCount(1);
        }
        return tmp;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int tmp = in.read(b, off, len);
        if (tmp >= 0) {
            incCount(tmp);
        }
        return tmp;
    }

    @Override
    public int readUnsignedByte() throws UncheckedIOException {
        try {
            int tmp = read();
            if (tmp < 0) {
                throw new EOFException();
            }
            return tmp;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws UncheckedIOException {
        if (len < 0) {
            throw new IndexOutOfBoundsException();
        }

        try {
            int n = 0;
            while (n < len) {
                int count = read(b, off + n, len - n);
                if (count < 0) {
                    throw new EOFException();
                }
                n += count;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
