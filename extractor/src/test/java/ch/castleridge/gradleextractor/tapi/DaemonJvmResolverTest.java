/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.tapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.castleridge.gradleextractor.tapi.DaemonJvmResolver.GradleVersion;
import ch.castleridge.gradleextractor.tapi.DaemonJvmResolver.JdkInstallation;

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

    Optional<JdkInstallation> found = DaemonJvmResolver.fromPath(jdk);
    assertTrue(found.isPresent());
    assertEquals(21, found.get().major());
  }

  @Test
  void readsDaemonJvmToolchainVersion() throws Exception {
    Path gradleDir = temp.resolve("gradle");
    Files.createDirectories(gradleDir);
    Files.writeString(gradleDir.resolve("gradle-daemon-jvm.properties"), "toolchainVersion=17\n");

    Optional<Integer> version = DaemonJvmResolver.readDaemonJvmToolchainVersion(temp);
    assertTrue(version.isPresent());
    assertEquals(17, version.get());
  }

  @Test
  void selectBestPrefersJavaHomeWhenInRange() {
    JdkInstallation j17 = new JdkInstallation(Path.of("/jdk-17"), 17);
    JdkInstallation j25 = new JdkInstallation(Path.of("/jdk-25"), 25);

    Optional<JdkInstallation> selected = DaemonJvmResolver.selectBest(
        List.of(j17, j25),
        17,
        25,
        Optional.of(j17),
        Optional.of(j25));

    assertTrue(selected.isPresent());
    assertEquals(17, selected.get().major());
  }

  @Test
  void selectBestFallsBackToJavaHomeEnvThenHighest() {
    JdkInstallation j17 = new JdkInstallation(Path.of("/jdk-17"), 17);
    JdkInstallation j21 = new JdkInstallation(Path.of("/jdk-21"), 21);
    JdkInstallation j25 = new JdkInstallation(Path.of("/jdk-25"), 25);

    Optional<JdkInstallation> fromEnv = DaemonJvmResolver.selectBest(
        List.of(j17, j21, j25),
        17,
        25,
        Optional.empty(),
        Optional.of(j21));
    assertTrue(fromEnv.isPresent());
    assertEquals(21, fromEnv.get().major());

    Optional<JdkInstallation> highest = DaemonJvmResolver.selectBest(
        List.of(j17, j21, j25),
        17,
        25,
        Optional.empty(),
        Optional.empty());
    assertTrue(highest.isPresent());
    assertEquals(25, highest.get().major());
  }

  @Test
  void resolveHonorsDaemonJvmPinOverHigherJdks() throws Exception {
    writeWrapper(temp, "9.5.0");
    writeDaemonJvmPin(temp, 17);

    JdkInstallation j17 = new JdkInstallation(Path.of("/jdk-17"), 17);
    JdkInstallation j25 = new JdkInstallation(Path.of("/jdk-25"), 25);

    File selected = DaemonJvmResolver.resolve(
        temp,
        List.of(j17, j25),
        Optional.empty(),
        Optional.of(j25),
        Optional.of(j25));

    assertEquals(j17.home().toFile(), selected);
  }

  @Test
  void resolveFailsWhenPinnedJdkMissing() throws Exception {
    writeWrapper(temp, "9.5.0");
    writeDaemonJvmPin(temp, 17);

    JdkInstallation j25 = new JdkInstallation(Path.of("/jdk-25"), 25);

    IllegalStateException ex = assertThrows(
        IllegalStateException.class,
        () -> DaemonJvmResolver.resolve(
            temp,
            List.of(j25),
            Optional.empty(),
            Optional.of(j25),
            Optional.empty()));

    assertTrue(ex.getMessage().contains("requires Java 17"));
    assertTrue(ex.getMessage().contains("no matching JDK"));
  }

  @Test
  void resolveFailsWhenPinOutsideGradleRange() throws Exception {
    writeWrapper(temp, "9.5.0");
    writeDaemonJvmPin(temp, 11);

    JdkInstallation j11 = new JdkInstallation(Path.of("/jdk-11"), 11);
    JdkInstallation j17 = new JdkInstallation(Path.of("/jdk-17"), 17);

    IllegalStateException ex = assertThrows(
        IllegalStateException.class,
        () -> DaemonJvmResolver.resolve(
            temp,
            List.of(j11, j17),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()));

    assertTrue(ex.getMessage().contains("toolchainVersion=11"));
    assertTrue(ex.getMessage().contains("outside the daemon JVM range"));
  }

  @Test
  void resolveWithoutPinPrefersJavaHome() throws Exception {
    writeWrapper(temp, "9.5.0");

    JdkInstallation j17 = new JdkInstallation(Path.of("/jdk-17"), 17);
    JdkInstallation j25 = new JdkInstallation(Path.of("/jdk-25"), 25);

    File selected = DaemonJvmResolver.resolve(
        temp,
        List.of(j17, j25),
        Optional.empty(),
        Optional.of(j17),
        Optional.of(j25));

    assertEquals(j17.home().toFile(), selected);
  }

  private static void writeWrapper(Path projectDir, String version) throws Exception {
    Path wrapperDir = projectDir.resolve("gradle/wrapper");
    Files.createDirectories(wrapperDir);
    Files.writeString(
        wrapperDir.resolve("gradle-wrapper.properties"),
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-"
            + version
            + "-bin.zip\n");
  }

  private static void writeDaemonJvmPin(Path projectDir, int major) throws Exception {
    Path gradleDir = projectDir.resolve("gradle");
    Files.createDirectories(gradleDir);
    Files.writeString(
        gradleDir.resolve("gradle-daemon-jvm.properties"),
        "toolchainVersion=" + major + "\n");
  }

  private static boolean windows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }
}
