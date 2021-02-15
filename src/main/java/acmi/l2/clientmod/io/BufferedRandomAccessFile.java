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

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

public class BufferedRandomAccessFile implements RandomAccess {
    private final RandomAccessMemory memory;
    private final RandomAccessFile file;
    private final boolean readOnly;

    public BufferedRandomAccessFile(File f, boolean readOnly, Charset charset) {
        String name = f.getName().substring(0, f.getName().lastIndexOf('.'));
        this.readOnly = readOnly;

        file = new RandomAccessFile(f, readOnly, charset);
        byte[] data = new byte[(int) f.length() - (file.getCryptVersion() != 0 ? 28 : 0)];
        file.readFully(data);
        file.setPosition(0);

        memory = new RandomAccessMemory(name, data, charset);

        if (readOnly) {
            file.close();
        }
    }

    private BufferedRandomAccessFile(RandomAccessMemory memory, RandomAccessFile file, boolean readOnly) {
        this.memory = memory;
        this.file = file;
        this.readOnly = readOnly;
    }

    @Override
    public String getName() {
        return memory.getName();
    }

    @Override
    public Charset getCharset() {
        return memory.getCharset();
    }

    @Override
    public int getPosition() throws UncheckedIOException {
        return memory.getPosition();
    }

    @Override
    public void setPosition(int position) throws UncheckedIOException {
        if (!readOnly) {
            file.setPosition(position);
        }
        memory.setPosition(position);
    }

    @Override
    public void trimToPosition() throws UncheckedIOException {
        if (!readOnly) {
            file.trimToPosition();
        }
        memory.trimToPosition();
    }

    @Override
    public RandomAccess openNewSession(boolean readOnly) throws UncheckedIOException {
        if (this.readOnly == readOnly) {
            return this;
        } else if (readOnly) {
            return new BufferedRandomAccessFile(memory, file, true);
        } else {
            return new BufferedRandomAccessFile(memory, file.openNewSession(false), false);
        }
    }

    @Override
    public void close() throws UncheckedIOException {
        if (!readOnly) {
            file.close();
        }
        memory.close();
    }

    @Override
    public int readUnsignedByte() throws UncheckedIOException {
        return memory.readUnsignedByte();
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws UncheckedIOException {
        memory.readFully(b, off, len);
    }

    @Override
    public void writeByte(int b) throws UncheckedIOException {
        if (!readOnly) {
            file.writeByte(b);
        }
        memory.writeByte(b);
    }

    @Override
    public void writeBytes(byte[] b, int off, int len) throws UncheckedIOException {
        if (!readOnly) {
            file.writeBytes(b, off, len);
        }
        memory.writeBytes(b, off, len);
    }
}
