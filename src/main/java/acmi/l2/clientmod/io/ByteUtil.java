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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public class ByteUtil {
    public static byte[] compactIntToByteArray(int v) {
        boolean negative = v < 0;
        v = Math.abs(v);
        int[] bytes = new int[]{
                (v) & 0b00111111,
                (v >> 6) & 0b01111111,
                (v >> 6 + 7) & 0b01111111,
                (v >> 6 + 7 + 7) & 0b01111111,
                (v >> 6 + 7 + 7 + 7) & 0b01111111
        };

        if (negative) bytes[0] |= 0b10000000;

        int lastInd = 4;
        while (lastInd > 0 && bytes[lastInd] == 0) {
            lastInd--;
        }

        byte[] res = new byte[lastInd + 1];

        for (int i = 0; i <= lastInd; i++) {
            if (i != lastInd)
                bytes[i] |= i == 0 ? 0b01000000 : 0b10000000;
            res[i] = (byte) bytes[i];
        }
        return res;
    }

    public static UUID uuidFromBytes(byte[] uuidBytes) {
        return UUID.fromString(String.format(
                "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
                uuidBytes[3], uuidBytes[2], uuidBytes[1], uuidBytes[0],
                uuidBytes[5], uuidBytes[4],
                uuidBytes[7], uuidBytes[6],
                uuidBytes[8], uuidBytes[9],
                uuidBytes[10], uuidBytes[11], uuidBytes[12], uuidBytes[13], uuidBytes[14], uuidBytes[15]
        ));
    }

    public static byte[] uuidToBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt((int) (uuid.getMostSignificantBits() >> 32));
        buffer.putShort((short) (uuid.getMostSignificantBits() >> 16));
        buffer.putShort((short) uuid.getMostSignificantBits());
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putLong(uuid.getLeastSignificantBits());
        return bytes;
    }
}
