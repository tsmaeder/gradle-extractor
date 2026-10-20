/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.tapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleModelFetcherTest {

  @TempDir
  Path temp;

  @Test
  void readDistributionUriUnescapesColon() throws Exception {
    Path wrapperDir = temp.resolve("gradle/wrapper");
    Files.createDirectories(wrapperDir);
    Files.writeString(
        wrapperDir.resolve("gradle-wrapper.properties"),
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.0-bin.zip\n");

    URI uri = GradleModelFetcher.readDistributionUri(temp);
    assertNotNull(uri);
    assertEquals(
        "https://services.gradle.org/distributions/gradle-9.5.0-bin.zip",
        uri.toString());
  }

  @Test
  void findWrapperRootWalksToAncestor() throws Exception {
    Path wrapperDir = temp.resolve("gradle/wrapper");
    Files.createDirectories(wrapperDir);
    Files.writeString(
        wrapperDir.resolve("gradle-wrapper.properties"),
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.0-bin.zip\n");
    Path included = temp.resolve("build-logic-commons");
    Files.createDirectories(included);

    assertEquals(
        temp.toAbsolutePath().normalize(),
        GradleModelFetcher.findWrapperRoot(included));
  }
}
