/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.tapi;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import ch.castleridge.gradleextractor.model.ExtractorModel;

/**
 * Fetches {@link ExtractorModel} for a Gradle project directory via the Tooling API.
 *
 * <p>Included builds often lack their own wrapper; the nearest ancestor
 * {@code gradle/wrapper/gradle-wrapper.properties} supplies the distribution.
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
        .forProjectDirectory(projectDir.toFile());
    configureDistribution(connector, projectDir);
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

  /**
   * Uses the project wrapper when present; otherwise the nearest ancestor wrapper's
   * {@code distributionUrl}. Falls back to {@code useBuildDistribution()} only when
   * no wrapper properties are found.
   */
  static void configureDistribution(GradleConnector connector, Path projectDir) {
    Path wrapperRoot = findWrapperRoot(projectDir);
    if (wrapperRoot == null) {
      connector.useBuildDistribution();
      return;
    }
    if (wrapperRoot.equals(projectDir.toAbsolutePath().normalize())) {
      connector.useBuildDistribution();
      return;
    }
    URI distribution = readDistributionUri(wrapperRoot);
    if (distribution != null) {
      connector.useDistribution(distribution);
    } else {
      connector.useBuildDistribution();
    }
  }

  static Path findWrapperRoot(Path start) {
    return DaemonJvmResolver.findWrapperRoot(start);
  }

  static URI readDistributionUri(Path wrapperRoot) {
    Path props = wrapperRoot.resolve("gradle/wrapper/gradle-wrapper.properties");
    if (!Files.isRegularFile(props)) {
      return null;
    }
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(props)) {
      p.load(in);
    } catch (IOException e) {
      return null;
    }
    String url = p.getProperty("distributionUrl", "");
    if (url.isBlank()) {
      return null;
    }
    // Properties files escape ':' as '\:'
    url = url.replace("\\:", ":");
    try {
      return URI.create(url);
    } catch (IllegalArgumentException e) {
      return null;
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
