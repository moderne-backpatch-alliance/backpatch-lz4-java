package net.jpountz.lz4;

/*
 * Copyright 2025 Jonas Konrad and the lz4-java contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static net.jpountz.lz4.LZ4Constants.MIN_MATCH;
import static org.junit.Assert.assertArrayEquals;

@RunWith(Parameterized.class)
public class CopyBeyondOutputTest {
  @Parameterized.Parameters(name = "{index}: {0} dec={1} len={2}")
  public static Collection<Object[]> parameters() {
    LZ4Factory[] factories = {
      LZ4Factory.fastestInstance(),
      LZ4Factory.fastestJavaInstance(),
      LZ4Factory.nativeInstance(),
      LZ4Factory.safeInstance(),
      LZ4Factory.unsafeInsecureInstance()
    };
    List<Object> decompressors = new ArrayList<>();
    for (LZ4Factory factory : factories) {
      decompressors.add(factory.fastDecompressor());
    }
    for (LZ4Factory factory : factories) {
      decompressors.add(factory.safeDecompressor());
    }
    List<Object[]> parameters = new ArrayList<>();
    for (Object decompressor : decompressors) {
      for (int dec = 0; dec < 14; dec++) {
        for (int len = dec; len < 14; len++) {
          parameters.add(new Object[]{decompressor, (byte) dec, len});
        }
      }
    }
    return parameters;
  }

  @Parameterized.Parameter(0)
  public Object decompressor;

  @Parameterized.Parameter(1)
  public byte dec;

  @Parameterized.Parameter(2)
  public int len;

  @Test
  public void copyBeyondOutput() {
    byte[] compressed = {
      // padding frame (14 bytes)
      (byte) 0xe0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      // copy len bytes (+ 4 MIN_MATCH) from -dec
      (byte) len, dec, 0,
      // padding frame (12 bytes)
      (byte) 0xc0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    byte[] output = new byte[14 + MIN_MATCH + len + MIN_MATCH + 12];
    Arrays.fill(output, (byte) 0x77);

    if (decompressor instanceof LZ4FastDecompressor) {
      ((LZ4FastDecompressor) decompressor).decompress(compressed, output);
    } else {
      ((LZ4SafeDecompressor) decompressor).decompress(compressed, output);
    }

    assertArrayEquals(new byte[output.length], output); // should be all zero
  }
}
