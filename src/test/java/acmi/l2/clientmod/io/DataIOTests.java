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

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class DataIOTests {
    @Test
    public void skip() {
        ByteBuffer buffer = ByteBuffer.allocate(0x10000);
        DataInput dataInput = DataInput.dataInput(buffer, null);
        dataInput.skip(0x4321);
        dataInput.skip(0x4321);
        assertEquals(0x8642, dataInput.getPosition());
    }

    @Test
    public void uByte() {
        ByteBuffer buffer = ByteBuffer.allocate(1);

        for (int v : new int[]{0, 127, 255}) {
            buffer.clear();
            DataOutput.dataOutput(buffer, null).writeByte(v);
            buffer.flip();
            assertEquals(v, DataInput.dataInput(buffer, null).readUnsignedByte());
        }
    }

    @Test
    public void uShort() {
        ByteBuffer buffer = ByteBuffer.allocate(2);

        for (int v : new int[]{0, 255, 256, 65535}) {
            buffer.clear();
            DataOutput.dataOutput(buffer, null).writeShort(v);
            buffer.flip();
            assertEquals(v, DataInput.dataInput(buffer, null).readUnsignedShort());
        }
    }

    @Test
    public void integer() {
        ByteBuffer buffer = ByteBuffer.allocate(4);

        for (int v : new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE}) {
            buffer.clear();
            DataOutput.dataOutput(buffer, null).writeInt(v);
            buffer.flip();
            assertEquals(v, DataInput.dataInput(buffer, null).readInt());
        }
    }

    @Test
    public void compactInt() {
        ByteBuffer buffer = ByteBuffer.allocate(5);

        for (int v : new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE}) {
            buffer.clear();
            DataOutput.dataOutput(buffer, null).writeCompactInt(v);
            buffer.flip();
            assertEquals(v, DataInput.dataInput(buffer, null).readCompactInt());
        }
    }

    @Test
    public void longint() {
        ByteBuffer buffer = ByteBuffer.allocate(8);

        for (long v : new long[]{Long.MIN_VALUE, 0, Long.MAX_VALUE}) {
            buffer.clear();
            DataOutput.dataOutput(buffer, null).writeLong(v);
            buffer.flip();
            assertEquals(v, DataInput.dataInput(buffer, null).readLong());
        }
    }

    @Test
    public void floatingPoint() {
        ByteBuffer buffer = ByteBuffer.allocate(4);

        for (float v : new float[]{Float.MIN_VALUE, Float.MIN_NORMAL, Float.MAX_VALUE, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 0.0f}) {
            buffer.clear();
            DataOutput.dataOutput(buffer, null).writeFloat(v);
            buffer.flip();
            assertEquals(v, DataInput.dataInput(buffer, null).readFloat(), 0.000001);
        }
    }

    @Test
    public void line() {
        ByteBuffer buffer = ByteBuffer.allocate(20);

        buffer.clear();
        DataOutput.dataOutput(buffer, UnrealPackage.getDefaultCharset()).writeLine(null);
        buffer.flip();
        assertEquals("", DataInput.dataInput(buffer, UnrealPackage.getDefaultCharset()).readLine());

        for (String v : new String[]{
                "",
                "ascii",
                "русский",
                "한국어"
        }) {
            buffer.clear();
            DataOutput.dataOutput(buffer, UnrealPackage.getDefaultCharset()).writeLine(v);
            buffer.flip();
            assertEquals(v, DataInput.dataInput(buffer, UnrealPackage.getDefaultCharset()).readLine());
        }
    }

    @Test
    public void utf() {
        ByteBuffer buffer = ByteBuffer.allocate(20);

        buffer.clear();
        DataOutput.dataOutput(buffer, null).writeUTF(null);
        buffer.flip();
        assertEquals("", DataInput.dataInput(buffer, null).readUTF());

        for (String v : new String[]{
                "",
                "ascii",
                "русский",
                "한국어"
        }) {
            buffer.clear();
            DataOutput.dataOutput(buffer, null).writeUTF(v);
            buffer.flip();
            assertEquals(v, DataInput.dataInput(buffer, null).readUTF());
        }
    }

    @Test
    public void byteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(4);

        byte[] array = new byte[]{-128, 0, 127};
        DataOutput.dataOutput(buffer, null).writeByteArray(array);
        buffer.flip();
        assertArrayEquals(array, DataInput.dataInput(buffer, null).readByteArray());
    }
}
