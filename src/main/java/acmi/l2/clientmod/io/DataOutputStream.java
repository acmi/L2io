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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

public class DataOutputStream extends FilterOutputStream implements DataOutput {
    private final Charset charset;
    private int position;

    public DataOutputStream(OutputStream out, Charset charset) {
        super(out);
        this.charset = charset;
    }

    public DataOutputStream(OutputStream out, Charset charset, int position) {
        this(out, charset);
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

    protected void incCount(int value) {
        int temp = position + value;
        if (temp < 0) {
            temp = Integer.MAX_VALUE;
        }
        position = temp;
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);

        incCount(1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);

        incCount(len);
    }

    @Override
    public void writeByte(int b) throws UncheckedIOException {
        try {
            write(b);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeBytes(byte[] b, int off, int len) throws UncheckedIOException {
        try {
            write(b, off, len);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
