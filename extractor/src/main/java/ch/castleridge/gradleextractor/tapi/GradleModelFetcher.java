/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.tapi;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import ch.castleridge.gradleextractor.model.ExtractorModel;

/**
 * Fetches {@link ExtractorModel} for a Gradle project directory via the Tooling API.
 */
public final class GradleModelFetcher implements Closeable {

  private final Path pluginJar;
  private final Path initScript;

  public GradleModelFetcher(Path pluginJar) throws IOException {
    this.pluginJar = pluginJar;
    this.initScript = InitScript.write(pluginJar);
  }

  public ExtractorModel fetch(Path projectDir) {
    File daemonJdk = DaemonJvmResolver.resolve(projectDir);
    GradleConnector connector = GradleConnector.newConnector()
        .forProjectDirectory(projectDir.toFile())
        .useBuildDistribution();
    try (ProjectConnection connection = connector.connect()) {
      return connection.model(ExtractorModel.class)
          .withArguments(
              "--init-script", initScript.toString(),
              // Model fetch does not benefit from the task build cache; disabling it
              // avoids "Build cache controller already set" with some settings scripts.
              "--no-build-cache")
          .setJavaHome(daemonJdk)
          .get();
    }
  }

  @Override
  public void close() {
    // temp files are deleteOnExit; pluginJar retained for reuse across builds
  }

  Path pluginJar() {
    return pluginJar;
  }
}
