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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NativeExtractionTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  /** Loads the bundled library in a JVM of its own, then lists the temporary directory while the library is loaded. */
  public static final class LoadAndList {
    public static void main(String[] args) {
      Native.load();
      if (!Native.isLoaded()) {
        throw new AssertionError("not loaded");
      }
      for (String name : new File(System.getProperty("java.io.tmpdir")).list()) {
        System.out.println(name);
      }
    }
  }

  @Test
  public void bundledLibraryIsWrittenToTheFileCreateTempFileReserved() throws Exception {
    File tmpdir = folder.newFolder("tmpdir");
    String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    ProcessBuilder pb = new ProcessBuilder(java, "-cp", classpath,
        "-Djava.io.tmpdir=" + tmpdir.getAbsolutePath(), LoadAndList.class.getName());
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    Process process = pb.start();
    List<String> names = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        names.add(line);
      }
    }
    assertEquals(0, process.waitFor());

    // The only file is the one File.createTempFile created, with no lock file whose name the library path is derived from.
    assertEquals(names.toString(), 1, names.size());
    assertTrue(names.toString(), names.get(0).startsWith("liblz4-java-"));
    assertFalse(names.toString(), names.get(0).endsWith(".lck"));
  }
}
