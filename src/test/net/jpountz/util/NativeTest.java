package net.jpountz.util;

/*
 * Copyright 2026 Jonas Konrad and the lz4-java contributors.
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class NativeTest {
  private static final long NOW = TimeUnit.DAYS.toMillis(365 * 50);
  private static final long OLD = NOW - TimeUnit.HOURS.toMillis(2);
  private static final long FRESH = NOW - TimeUnit.MINUTES.toMillis(5);

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  // Reflective so that this class still compiles when the fix is reverted for the fail-before control.
  private static void cleanupOldTempLibs(File dir, long now) throws Exception {
    Method m = Native.class.getDeclaredMethod("cleanupOldTempLibs", File.class, long.class);
    m.setAccessible(true);
    m.invoke(null, dir, now);
  }

  private File file(String name, long lastModified) throws IOException {
    File f = new File(folder.getRoot(), name);
    assertTrue(f.createNewFile());
    assertTrue(f.setLastModified(lastModified));
    return f;
  }

  @Test
  public void cleanupOldTempLibs() throws Exception {
    File oldLib = file("liblz4-java-123.so", OLD);
    File oldLock = file("liblz4-java-123.so.lck", OLD);
    File oldLockOnly = file("liblz4-java-456.dll.lck", OLD);
    File freshLib = file("liblz4-java-789.so", FRESH);
    File freshLock = file("liblz4-java-789.so.lck", FRESH);
    File oldOther = file("libother-123.so", OLD);

    cleanupOldTempLibs(folder.getRoot(), NOW);

    assertFalse(oldLib.exists());
    assertFalse(oldLock.exists());
    assertFalse(oldLockOnly.exists());
    assertTrue(freshLib.exists());
    assertTrue(freshLock.exists());
    assertTrue(oldOther.exists());
  }

  @Test
  public void cleanupOldTempLibsMissingDirectory() throws Exception {
    cleanupOldTempLibs(new File(folder.getRoot(), "missing"), NOW);
  }
}
