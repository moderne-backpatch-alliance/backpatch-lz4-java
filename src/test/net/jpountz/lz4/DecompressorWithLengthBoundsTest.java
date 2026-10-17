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

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Upstream's {@code OutOfBoundsTest} cases for {@link LZ4DecompressorWithLength}
 * (yawkat/lz4-java 6492ce5 and b383b9e), moved from JUnit Jupiter parameterized tests to JUnit 4.
 * The constructors with an explicit maximum are reached reflectively so that this class also
 * compiles against 1.8.1 without the fix.
 */
public class DecompressorWithLengthBoundsTest {

  private static LZ4DecompressorWithLength withMaximum(Object decompressor, int maxDecompressedLength) {
    final Class<?> type = decompressor instanceof LZ4FastDecompressor ? LZ4FastDecompressor.class : LZ4SafeDecompressor.class;
    try {
      return LZ4DecompressorWithLength.class.getConstructor(type, int.class).newInstance(decompressor, maxDecompressedLength);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new AssertionError(e);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("no constructor with an explicit maximum", e);
    }
  }

  private static List<LZ4DecompressorWithLength> decompressorsWithLength() {
    LZ4Factory factory = LZ4Factory.safeInstance();
    return Arrays.asList(
      new LZ4DecompressorWithLength(factory.fastDecompressor()),
      new LZ4DecompressorWithLength(factory.safeDecompressor())
    );
  }

  private static List<LZ4DecompressorWithLength> limitedDecompressorsWithLength() {
    LZ4Factory factory = LZ4Factory.safeInstance();
    return Arrays.asList(
      withMaximum(factory.fastDecompressor(), 15),
      withMaximum(factory.safeDecompressor(), 15)
    );
  }

  @Test
  public void impossibleDeclaredLength() {
    for (final LZ4DecompressorWithLength decompressor : decompressorsWithLength()) {
      byte[] input = {
        0, 0, 0x10, 0, // 1 MiB decompressed length
        0 // empty LZ4 block
      };
      LZ4Exception exception = assertThrows(LZ4Exception.class, () -> decompressor.decompress(input));
      assertEquals("Decompressed length 1048576 exceeds maximum compression ratio of 255 for compressed length 1",
        exception.getMessage());

      Arrays.fill(input, 0, 4, (byte) 0xff); // negative decompressed length
      exception = assertThrows(LZ4Exception.class, () -> decompressor.decompress(input));
      assertEquals("Invalid decompressed length: -1", exception.getMessage());

      int declaredLength = 64 * 1024 * 1024 + 1;
      byte[] defaultLimitInput = new byte[4 + (declaredLength + 254) / 255];
      defaultLimitInput[0] = (byte) declaredLength;
      defaultLimitInput[1] = (byte) (declaredLength >>> 8);
      defaultLimitInput[2] = (byte) (declaredLength >>> 16);
      defaultLimitInput[3] = (byte) (declaredLength >>> 24);
      exception = assertThrows(LZ4Exception.class, () -> decompressor.decompress(defaultLimitInput));
      assertEquals("Decompressed length 67108865 exceeds configured maximum 67108864", exception.getMessage());
    }
  }

  @Test
  public void destinationBufferLimitsDecompressedLength() {
    for (final LZ4DecompressorWithLength decompressor : decompressorsWithLength()) {
      LZ4Factory factory = LZ4Factory.safeInstance();
      byte[] compressed = new LZ4CompressorWithLength(factory.fastCompressor()).compress(new byte[16]);

      byte[] destination = new byte[16];
      LZ4Exception exception = assertThrows(LZ4Exception.class,
        () -> decompressor.decompress(compressed, 0, destination, 1));
      assertEquals("Decompressed length 16 exceeds destination length 15", exception.getMessage());

      ByteBuffer movingSrc = ByteBuffer.wrap(compressed);
      ByteBuffer movingDest = ByteBuffer.allocate(15);
      exception = assertThrows(LZ4Exception.class,
        () -> decompressor.decompress(movingSrc, movingDest));
      assertEquals("Decompressed length 16 exceeds destination length 15", exception.getMessage());
      assertEquals(0, movingSrc.position());
      assertEquals(0, movingDest.position());

      ByteBuffer indexedSrc = ByteBuffer.wrap(compressed);
      ByteBuffer indexedDest = ByteBuffer.allocate(16);
      exception = assertThrows(LZ4Exception.class,
        () -> decompressor.decompress(indexedSrc, 0, indexedDest, 1));
      assertEquals("Decompressed length 16 exceeds destination length 15", exception.getMessage());
      assertEquals(0, indexedSrc.position());
      assertEquals(0, indexedDest.position());
    }
  }

  @Test
  public void configuredMaximumOnlyLimitsAllocatedOutput() {
    for (final LZ4DecompressorWithLength decompressor : limitedDecompressorsWithLength()) {
      LZ4Factory factory = LZ4Factory.safeInstance();
      byte[] compressed = new LZ4CompressorWithLength(factory.fastCompressor()).compress(new byte[16]);

      LZ4Exception exception = assertThrows(LZ4Exception.class, () -> decompressor.decompress(compressed));
      assertEquals("Decompressed length 16 exceeds configured maximum 15", exception.getMessage());

      byte[] destination = new byte[16];
      decompressor.decompress(compressed, destination);
      assertArrayEquals(new byte[16], destination);
    }
  }

  @Test
  public void configuredMaximumValidation() {
    LZ4Factory factory = LZ4Factory.safeInstance();
    assertThrows(IllegalArgumentException.class,
      () -> withMaximum(factory.fastDecompressor(), -1));
    assertThrows(IllegalArgumentException.class,
      () -> withMaximum(factory.safeDecompressor(), -1));

    byte[] compressed = new LZ4CompressorWithLength(factory.fastCompressor()).compress(new byte[0]);
    assertArrayEquals(new byte[0],
      withMaximum(factory.fastDecompressor(), 0).decompress(compressed));
    assertArrayEquals(new byte[0],
      withMaximum(factory.safeDecompressor(), 0).decompress(compressed));

    compressed = new LZ4CompressorWithLength(factory.fastCompressor()).compress(new byte[16]);
    assertArrayEquals(new byte[16],
      withMaximum(factory.fastDecompressor(), 16).decompress(compressed));
    assertArrayEquals(new byte[16],
      withMaximum(factory.safeDecompressor(), 16).decompress(compressed));
  }
}
