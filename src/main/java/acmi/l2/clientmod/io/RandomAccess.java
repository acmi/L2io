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
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;

public interface RandomAccess extends DataInput, DataOutput, AutoCloseable {
    String getName();

    void setPosition(int position) throws UncheckedIOException;

    void trimToPosition() throws UncheckedIOException;

    RandomAccess openNewSession(boolean readOnly) throws UncheckedIOException;

    void close() throws UncheckedIOException;

    static RandomAccess randomAccess(ByteBuffer buffer, String name, Charset charset, int position) {
        return new RandomAccess() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Charset getCharset() {
                return charset;
            }

            @Override
            public int getPosition() {
                return position + buffer.position();
            }

            @Override
            public void setPosition(int pos) throws IllegalArgumentException {
                buffer.position(pos - position);
            }

            @Override
            public void trimToPosition() {
                buffer.limit(buffer.position());
            }

            @Override
            public int readUnsignedByte() throws UncheckedIOException {
                try {
                    return buffer.get() & 0xff;
                } catch (BufferUnderflowException e) {
                    throw new UncheckedIOException(new IOException(e));
                }
            }

            @Override
            public void write(int b) throws UncheckedIOException {
                try {
                    buffer.put((byte) b);
                } catch (BufferOverflowException | ReadOnlyBufferException e) {
                    throw new UncheckedIOException(new IOException(e));
                }
            }

            @Override
            public RandomAccess openNewSession(boolean readOnly) throws UncheckedIOException {
                return this;
            }

            @Override
            public void close() {
            }
        };
    }
}
