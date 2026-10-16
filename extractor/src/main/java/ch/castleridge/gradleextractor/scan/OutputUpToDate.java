/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.scan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Decides whether an existing mbt fragment is still fresh relative to scanned inputs.
 */
public final class OutputUpToDate {

  private OutputUpToDate() {}

  /**
   * Returns {@code true} when {@code output} exists as a regular file and no path in
   * {@code inputs} has a last-modified time strictly after the output's.
   */
  public static boolean isUpToDate(Path output, Collection<Path> inputs) throws IOException {
    if (!Files.isRegularFile(output)) {
      return false;
    }
    long outputMtime = Files.getLastModifiedTime(output).toMillis();
    for (Path input : inputs) {
      if (input == null || !Files.exists(input)) {
        continue;
      }
      if (Files.getLastModifiedTime(input).toMillis() > outputMtime) {
        return false;
      }
    }
    return true;
  }
}
