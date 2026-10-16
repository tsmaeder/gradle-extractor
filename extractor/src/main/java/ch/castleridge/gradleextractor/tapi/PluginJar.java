/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.tapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Materializes the embedded {@code gradle-extractor-plugin.jar} resource to a temp file.
 */
public final class PluginJar {

  private static final String RESOURCE = "/gradle-extractor-plugin.jar";

  private PluginJar() {}

  public static Path materialize() throws IOException {
    try (InputStream in = PluginJar.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IOException("Missing embedded resource " + RESOURCE);
      }
      Path temp = Files.createTempFile("gradle-extractor-plugin-", ".jar");
      temp.toFile().deleteOnExit();
      try (OutputStream out = Files.newOutputStream(temp)) {
        in.transferTo(out);
      }
      return temp.toAbsolutePath().normalize();
    }
  }
}
