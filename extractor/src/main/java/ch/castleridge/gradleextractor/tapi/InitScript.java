/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.tapi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a temporary Gradle init script that applies {@code ExtractorPlugin}.
 */
public final class InitScript {

  private InitScript() {}

  public static Path write(Path pluginJar) throws IOException {
    String pluginPath = pluginJar.toAbsolutePath().normalize().toString().replace("\\", "/");
    String content = """
        initscript {
          dependencies {
            classpath files('%s')
          }
        }
        gradle.lifecycle.beforeProject { project ->
          project.apply plugin: ch.castleridge.gradleextractor.plugin.ExtractorPlugin
        }
        """.formatted(pluginPath);
    Path temp = Files.createTempFile("gradle-extractor-init-", ".gradle");
    temp.toFile().deleteOnExit();
    Files.writeString(temp, content, StandardCharsets.UTF_8);
    return temp.toAbsolutePath().normalize();
  }
}
