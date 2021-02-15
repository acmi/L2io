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

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_16LE;

public class RandomAccessFile implements RandomAccess {
    protected final java.io.RandomAccessFile file;
    private final String packageName;
    private final String path;

    private final int cryptVer;
    protected final int xorKey;
    protected final int startOffset;

    private final Charset charset;

    public RandomAccessFile(File f, boolean readOnly, Charset charset) throws UncheckedIOException {
        try {
            file = new java.io.RandomAccessFile(f, readOnly ? "r" : "rw");
            packageName = f.getName().substring(0, f.getName().lastIndexOf('.'));
            path = f.getPath();

            String l2CryptHeader;
            if (file.length() >= 28 && (l2CryptHeader = getCryptHeader(file)).startsWith("Lineage2Ver")) {
                startOffset = 28;
                cryptVer = Integer.parseInt(l2CryptHeader.substring(11));
                switch (cryptVer) {
                    case 111:
                        xorKey = 0xACACACAC;
                        break;
                    case 121:
                        int xb = getCryptKey(f.getName());
                        xorKey = xb | (xb << 8) | (xb << 16) | (xb << 24);
                        break;
                    default:
                        throw new IOException("Crypt " + cryptVer + " is not supported.");
                }
            } else {
                startOffset = 0;
                cryptVer = 0;
                xorKey = 0;
            }

            this.charset = charset;

            setPosition(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public RandomAccessFile(String path, boolean readOnly, Charset charset) throws UncheckedIOException {
        this(new File(path), readOnly, charset);
    }

    private static String getCryptHeader(java.io.RandomAccessFile file) throws IOException {
        byte[] l2CryptHeaderBytes = new byte[28];
        file.readFully(l2CryptHeaderBytes);
        return new String(l2CryptHeaderBytes, UTF_16LE);
    }

    private static int getCryptKey(String filename) {
        filename = filename.toLowerCase();
        int ind = 0;
        for (int i = 0; i < filename.length(); i++) {
            ind += filename.charAt(i);
        }
        return ind & 0xff;
    }

    @Override
    public String getName() {
        return packageName;
    }

    public String getPath() {
        return path;
    }

    public int getCryptVersion() {
        return cryptVer;
    }

    @Override
    public Charset getCharset() {
        return charset;
    }

    @Override
    public void setPosition(int pos) throws UncheckedIOException {
        try {
            file.seek(pos + startOffset);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int getPosition() throws UncheckedIOException {
        try {
            return (int) file.getFilePointer() - startOffset;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void trimToPosition() throws UncheckedIOException {
        try {
            file.setLength(file.getFilePointer());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void skip(int n) throws UncheckedIOException {
        try {
            file.seek(file.getFilePointer() + n);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    @Override
    public void close() throws UncheckedIOException {
        try {
            file.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int readUnsignedByte() throws UncheckedIOException {
        try {
            if (cryptVer != 0) {
                int b = file.read();
                if (b < 0) {
                    throw new EOFException();
                }

                return (b ^ xorKey) & 0xff;
            } else {
                return file.read();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws UncheckedIOException {
        try {
            file.readFully(b, off, len);

            if (cryptVer != 0) {
                for (int i = 0; i < len; i++) {
                    b[off + i] ^= xorKey;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeByte(int b) throws UncheckedIOException {
        try {
            if (cryptVer != 0) {
                file.write(b ^ xorKey);
            } else {
                file.write(b);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeBytes(byte[] b, int off, int len) throws UncheckedIOException {
        if ((off | len | (b.length - (len + off)) | (off + len)) < 0) {
            throw new IndexOutOfBoundsException();
        }

        try {
            if (cryptVer != 0) {
                byte[] toWrite = Arrays.copyOfRange(b, off, off + len);
                for (int i = 0; i < toWrite.length; i++) {
                    toWrite[i] ^= xorKey;
                }
                file.write(toWrite);
            } else {
                file.write(b, off, len);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public RandomAccessFile openNewSession(boolean readOnly) throws UncheckedIOException {
        return new RandomAccessFile(getPath(), readOnly, getCharset());
    }
}
