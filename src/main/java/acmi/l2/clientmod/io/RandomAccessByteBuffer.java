package acmi.l2.clientmod.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;

public class RandomAccessByteBuffer implements RandomAccess {
    private final ByteBuffer buffer;
    private final String name;
    private final Charset charset;
    private final int position;

    public RandomAccessByteBuffer(ByteBuffer buffer, String name, Charset charset, int position) {
        this.buffer = buffer;
        this.name = name;
        this.charset = charset;
        this.position = position;
    }

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
    public void writeByte(int b) throws UncheckedIOException {
        try {
            buffer.put((byte) b);
        } catch (BufferOverflowException | ReadOnlyBufferException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    @Override
    public RandomAccess openNewSession(boolean readOnly) {
        return this;
    }

    @Override
    public void close() {
    }
}
