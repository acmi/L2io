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

import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ByteUtilTests {
    @Test
    public void compactInt() {
        assertArrayEquals(new byte[]{(byte) 0b00000000}, ByteUtil.compactIntToByteArray(0));
        assertArrayEquals(new byte[]{(byte) 0b00000001}, ByteUtil.compactIntToByteArray(1));
        assertArrayEquals(new byte[]{(byte) 0b10000001}, ByteUtil.compactIntToByteArray(-1));
        assertArrayEquals(new byte[]{(byte) 0b01000000, (byte) 0b00000001}, ByteUtil.compactIntToByteArray(64));
        assertArrayEquals(new byte[]{(byte) 0b11000000, (byte) 0b00000001}, ByteUtil.compactIntToByteArray(-64));
        assertArrayEquals(new byte[]{(byte) 0b01000000, (byte) 0b10000000, (byte) 0b00000001}, ByteUtil.compactIntToByteArray(8192));
        assertArrayEquals(new byte[]{(byte) 0b11000000, (byte) 0b10000000, (byte) 0b00000001}, ByteUtil.compactIntToByteArray(-8192));
    }

    @Test
    public void uuid() {
        UUID uuid = UUID.randomUUID();
        byte[] uuidBytes = ByteUtil.uuidToBytes(uuid);
        assertEquals(uuid, ByteUtil.uuidFromBytes(uuidBytes));
    }
}
