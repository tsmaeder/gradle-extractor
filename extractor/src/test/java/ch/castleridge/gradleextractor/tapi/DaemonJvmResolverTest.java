/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.tapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.castleridge.gradleextractor.tapi.DaemonJvmResolver.GradleVersion;

class DaemonJvmResolverTest {

  @TempDir
  Path temp;

  @Test
  void readsWrapperVersionFromDistributionUrl() throws Exception {
    Path wrapperDir = temp.resolve("gradle/wrapper");
    Files.createDirectories(wrapperDir);
    Files.writeString(
        wrapperDir.resolve("gradle-wrapper.properties"),
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.2-bin.zip\n");

    Optional<GradleVersion> version = DaemonJvmResolver.readWrapperVersion(temp);
    assertTrue(version.isPresent());
    assertEquals(8, version.get().major());
    assertEquals(14, version.get().minor());
    assertEquals(2, version.get().patch());
  }

  @Test
  void maxJavaForGradle814Is24() {
    assertEquals(24, DaemonJvmResolver.maxJavaFor(new GradleVersion(8, 14, 2)));
  }

  @Test
  void maxJavaForGradle91Includes25() {
    assertEquals(25, DaemonJvmResolver.maxJavaFor(new GradleVersion(9, 1, 0)));
  }

  @Test
  void minJavaForGradle8Is8() {
    assertEquals(8, DaemonJvmResolver.minJavaFor(new GradleVersion(8, 14, 2)));
  }

  @Test
  void minJavaForGradle9Is17() {
    assertEquals(17, DaemonJvmResolver.minJavaFor(new GradleVersion(9, 0, 0)));
  }

  @Test
  void readsMajorFromReleaseFile() throws Exception {
    Path jdk = temp.resolve("jdk-21.0.8");
    Files.createDirectories(jdk.resolve("bin"));
    Files.writeString(jdk.resolve("bin").resolve(windows() ? "java.exe" : "java"), "");
    Files.writeString(jdk.resolve("release"), "JAVA_VERSION=\"21.0.8\"\n");

    Optional<DaemonJvmResolver.JdkInstallation> found = DaemonJvmResolver.fromPath(jdk);
    assertTrue(found.isPresent());
    assertEquals(21, found.get().major());
  }

  private static boolean windows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }
}
