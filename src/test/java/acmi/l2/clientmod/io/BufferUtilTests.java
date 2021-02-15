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

import static org.junit.Assert.assertEquals;

@SuppressWarnings("deprecation")
public class BufferUtilTests {
    @Test
    public void compactInt() {
        ByteBuffer buffer = ByteBuffer.allocate(5);

        testCompactInt(buffer, Integer.MIN_VALUE);
        testCompactInt(buffer, 0);
        testCompactInt(buffer, Integer.MAX_VALUE);

        for (int i = 2; i < 32; i += 7) {
            testCompactInt(buffer, 1 << i);
            testCompactInt(buffer, -(1 << i));
        }
    }

    private void testCompactInt(ByteBuffer buffer, int value) {
        buffer.clear();
        BufferUtil.putCompactInt(buffer, value);
        buffer.flip();
        assertEquals(value, BufferUtil.getCompactInt(buffer));
    }

    @Test
    public void string() {
        ByteBuffer buffer = ByteBuffer.allocate(20);

        buffer.clear();
        BufferUtil.putString(buffer, null);
        buffer.flip();
        assertEquals("", BufferUtil.getString(buffer));

        String[] strings = new String[]{
                "",
                "ascii",
                "русский",
                "한국어"
        };

        for (String string : strings) {
            buffer.clear();
            BufferUtil.putString(buffer, string);
            buffer.flip();
            assertEquals(string, BufferUtil.getString(buffer));
        }
    }

    @Test
    public void utf() {
        ByteBuffer buffer = ByteBuffer.allocate(20);

        buffer.clear();
        BufferUtil.putUTF(buffer, null);
        buffer.flip();
        assertEquals("", BufferUtil.getUTF(buffer));

        String[] strings = new String[]{
                "",
                "ascii",
                "русский",
                "한국어"
        };

        for (String string : strings) {
            buffer.clear();
            BufferUtil.putUTF(buffer, string);
            buffer.flip();
            assertEquals(string, BufferUtil.getUTF(buffer));
        }
    }
}
