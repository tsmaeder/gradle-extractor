/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.tapi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks a JDK home suitable for running the Gradle daemon for a given project.
 *
 * <p>The Tooling API client may run on a newer JVM (e.g. 25) than the project's Gradle
 * distribution supports. The wrapper only pins the Gradle version, not the daemon JDK.
 * When present, {@code gradle/gradle-daemon-jvm.properties} {@code toolchainVersion} is honored.
 */
public final class DaemonJvmResolver {

  static final String ENV_OVERRIDE = "GRADLE_EXTRACTOR_JAVA_HOME";

  private static final Pattern DISTRIBUTION_VERSION =
      Pattern.compile("gradle-(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-|$)");

  private static final Pattern VERSION_IN_PATH =
      Pattern.compile("(?i)(?:jdk|jre)[_-]?(\\d+)");

  /** (minMajor, minMinor, maxJava) — first row where gradle >= that version wins. */
  private static final int[][] MAX_JAVA_BY_GRADLE = {
      {9, 1, 25},
      {9, 0, 24},
      {8, 14, 24},
      {8, 10, 23},
      {8, 8, 22},
      {8, 5, 21},
      {8, 3, 20},
      {7, 6, 19},
      {7, 5, 18},
      {7, 3, 17},
      {7, 0, 16},
      {6, 7, 15},
      {6, 0, 14},
      {5, 0, 11},
      {0, 0, 8}
  };

  private DaemonJvmResolver() {}

  /**
   * Resolves a daemon JDK for {@code projectDir}, or throws if none is compatible.
   */
  public static File resolve(Path projectDir) {
    Optional<JdkInstallation> override = fromEnv(ENV_OVERRIDE);
    Optional<JdkInstallation> javaHome = fromPath(Path.of(System.getProperty("java.home", "")));
    Optional<JdkInstallation> javaHomeEnv = fromEnv("JAVA_HOME");
    return resolve(projectDir, collectCandidates(), override, javaHome, javaHomeEnv);
  }

  /**
   * Resolves using explicit candidates and preferred homes (for tests).
   * When {@code override} is present it is used like {@code GRADLE_EXTRACTOR_JAVA_HOME}.
   */
  static File resolve(
      Path projectDir,
      List<JdkInstallation> candidates,
      Optional<JdkInstallation> override,
      Optional<JdkInstallation> javaHome,
      Optional<JdkInstallation> javaHomeEnv) {
    Path wrapperRoot = findWrapperRoot(projectDir);
    Path versionRoot = wrapperRoot != null ? wrapperRoot : projectDir;
    GradleVersion gradle = readWrapperVersion(versionRoot).orElse(new GradleVersion(8, 0, 0));
    int minJava = minJavaFor(gradle);
    int maxJava = maxJavaFor(gradle);

    if (override.isPresent()) {
      JdkInstallation jdk = override.get();
      if (jdk.major >= minJava && jdk.major <= maxJava) {
        return jdk.home.toFile();
      }
      throw new IllegalStateException(
          ENV_OVERRIDE + "=" + jdk.home + " is Java " + jdk.major
              + ", but Gradle " + gradle
              + " requires a daemon JVM between " + minJava + " and " + maxJava);
    }

    Optional<Integer> pinned = readDaemonJvmToolchainVersion(versionRoot);
    if (pinned.isPresent()) {
      int required = pinned.get();
      if (required < minJava || required > maxJava) {
        throw new IllegalStateException(
            "gradle/gradle-daemon-jvm.properties toolchainVersion=" + required
                + " is outside the daemon JVM range " + minJava + "–" + maxJava
                + " for Gradle " + gradle);
      }
      return findExact(candidates, required)
          .orElseThrow(() -> new IllegalStateException(
              "gradle/gradle-daemon-jvm.properties requires Java " + required
                  + ", but no matching JDK was found. Install JDK " + required
                  + " or set " + ENV_OVERRIDE + "."))
          .home
          .toFile();
    }

    return selectBest(candidates, minJava, maxJava, javaHome, javaHomeEnv)
        .orElseThrow(() -> new IllegalStateException(
            "No JDK in range " + minJava + "–" + maxJava
                + " found for Gradle " + gradle
                + ". Install a compatible JDK or set " + ENV_OVERRIDE + "."))
        .home
        .toFile();
  }

  /**
   * Nearest directory (self or ancestor) with {@code gradle/wrapper/gradle-wrapper.properties}.
   * Included builds typically inherit the consumer's wrapper.
   */
  static Path findWrapperRoot(Path start) {
    Path dir = start.toAbsolutePath().normalize();
    while (dir != null) {
      if (Files.isRegularFile(dir.resolve("gradle/wrapper/gradle-wrapper.properties"))) {
        return dir;
      }
      dir = dir.getParent();
    }
    return null;
  }

  static Optional<GradleVersion> readWrapperVersion(Path projectDir) {
    Path props = projectDir.resolve("gradle/wrapper/gradle-wrapper.properties");
    if (!Files.isRegularFile(props)) {
      return Optional.empty();
    }
    Properties p = new Properties();
    try (var in = Files.newInputStream(props)) {
      p.load(in);
    } catch (IOException e) {
      return Optional.empty();
    }
    String url = p.getProperty("distributionUrl", "");
    Matcher m = DISTRIBUTION_VERSION.matcher(url);
    if (!m.find()) {
      return Optional.empty();
    }
    int major = Integer.parseInt(m.group(1));
    int minor = Integer.parseInt(m.group(2));
    int patch = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
    return Optional.of(new GradleVersion(major, minor, patch));
  }

  /**
   * Reads {@code toolchainVersion} from {@code gradle/gradle-daemon-jvm.properties}, if present.
   */
  static Optional<Integer> readDaemonJvmToolchainVersion(Path projectDir) {
    Path props = projectDir.resolve("gradle/gradle-daemon-jvm.properties");
    if (!Files.isRegularFile(props)) {
      return Optional.empty();
    }
    Properties p = new Properties();
    try (var in = Files.newInputStream(props)) {
      p.load(in);
    } catch (IOException e) {
      return Optional.empty();
    }
    String raw = p.getProperty("toolchainVersion");
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Integer.parseInt(raw.trim()));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  static int minJavaFor(GradleVersion g) {
    return g.major >= 9 ? 17 : 8;
  }

  static int maxJavaFor(GradleVersion g) {
    for (int[] row : MAX_JAVA_BY_GRADLE) {
      if (g.major > row[0] || (g.major == row[0] && g.minor >= row[1])) {
        return row[2];
      }
    }
    return 8;
  }

  /**
   * Selects a daemon JDK when no {@code toolchainVersion} pin is present.
   *
   * <p>Preference: {@code java.home} if in range, then {@code JAVA_HOME} if in range, then highest
   * major in range among {@code candidates}.
   */
  static Optional<JdkInstallation> selectBest(
      List<JdkInstallation> candidates,
      int minJava,
      int maxJava,
      Optional<JdkInstallation> javaHome,
      Optional<JdkInstallation> javaHomeEnv) {
    if (javaHome.isPresent()) {
      JdkInstallation j = javaHome.get();
      if (j.major >= minJava && j.major <= maxJava) {
        return javaHome;
      }
    }
    if (javaHomeEnv.isPresent()) {
      JdkInstallation j = javaHomeEnv.get();
      if (j.major >= minJava && j.major <= maxJava) {
        return javaHomeEnv;
      }
    }
    return candidates.stream()
        .filter(j -> j.major >= minJava && j.major <= maxJava)
        .max(Comparator.comparingInt((JdkInstallation j) -> j.major)
            .thenComparing(j -> j.home.toString()));
  }

  static Optional<JdkInstallation> findExact(List<JdkInstallation> candidates, int major) {
    return candidates.stream()
        .filter(j -> j.major == major)
        .max(Comparator.comparing(j -> j.home.toString()));
  }

  static List<JdkInstallation> collectCandidates() {
    List<JdkInstallation> candidates = new ArrayList<>();
    addIfPresent(candidates, fromPath(Path.of(System.getProperty("java.home", ""))));
    String javaHomeEnv = System.getenv("JAVA_HOME");
    if (javaHomeEnv != null && !javaHomeEnv.isBlank()) {
      addIfPresent(candidates, fromPath(Path.of(javaHomeEnv)));
    }
    for (Path root : installRoots()) {
      if (!Files.isDirectory(root)) {
        continue;
      }
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
        for (Path child : stream) {
          addIfPresent(candidates, fromPath(child));
        }
      } catch (IOException ignored) {
        // skip unreadable roots
      }
    }
    return candidates;
  }

  private static Optional<JdkInstallation> fromEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return fromPath(Path.of(value));
  }

  private static void addIfPresent(List<JdkInstallation> out, Optional<JdkInstallation> jdk) {
    jdk.ifPresent(j -> {
      for (JdkInstallation existing : out) {
        if (existing.home.equals(j.home)) {
          return;
        }
      }
      out.add(j);
    });
  }

  static Optional<JdkInstallation> fromPath(Path home) {
    if (home == null || home.toString().isBlank()) {
      return Optional.empty();
    }
    Path normalized = home.toAbsolutePath().normalize();
    // java.home may point at a JRE subdirectory
    if (!Files.isRegularFile(normalized.resolve("release"))
        && Files.isRegularFile(normalized.resolve("..").normalize().resolve("release"))) {
      normalized = normalized.resolve("..").normalize();
    }
    if (!Files.isDirectory(normalized)) {
      return Optional.empty();
    }
    Path javaBin = normalized.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
    if (!Files.isRegularFile(javaBin)) {
      return Optional.empty();
    }
    Optional<Integer> fromRelease = readMajorFromRelease(normalized.resolve("release"));
    if (fromRelease.isPresent()) {
      return Optional.of(new JdkInstallation(normalized, fromRelease.get()));
    }
    Matcher m = VERSION_IN_PATH.matcher(normalized.getFileName().toString());
    if (m.find()) {
      return Optional.of(new JdkInstallation(normalized, Integer.parseInt(m.group(1))));
    }
    return Optional.empty();
  }

  private static Optional<Integer> readMajorFromRelease(Path releaseFile) {
    if (!Files.isRegularFile(releaseFile)) {
      return Optional.empty();
    }
    try {
      for (String line : Files.readAllLines(releaseFile, StandardCharsets.UTF_8)) {
        if (line.startsWith("JAVA_VERSION=")) {
          String raw = line.substring("JAVA_VERSION=".length()).trim();
          if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1);
          }
          // "1.8.0_402" or "17.0.16" or "25.0.2"
          if (raw.startsWith("1.")) {
            int second = raw.indexOf('.', 2);
            String minor = second > 0 ? raw.substring(2, second) : raw.substring(2);
            return Optional.of(Integer.parseInt(minor));
          }
          int dot = raw.indexOf('.');
          String major = dot > 0 ? raw.substring(0, dot) : raw;
          return Optional.of(Integer.parseInt(major));
        }
      }
    } catch (IOException | NumberFormatException ignored) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private static List<Path> installRoots() {
    List<Path> roots = new ArrayList<>();
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      String pf = System.getenv("ProgramFiles");
      if (pf != null) {
        roots.add(Path.of(pf, "Eclipse Adoptium"));
        roots.add(Path.of(pf, "Java"));
        roots.add(Path.of(pf, "Microsoft"));
        roots.add(Path.of(pf, "Azul"));
        roots.add(Path.of(pf, "Amazon Corretto"));
        roots.add(Path.of(pf, "BellSoft"));
      }
      String pf86 = System.getenv("ProgramFiles(x86)");
      if (pf86 != null) {
        roots.add(Path.of(pf86, "Eclipse Adoptium"));
        roots.add(Path.of(pf86, "Java"));
      }
    } else if (os.contains("mac")) {
      roots.add(Path.of("/Library/Java/JavaVirtualMachines"));
      String home = System.getProperty("user.home", "");
      if (!home.isBlank()) {
        roots.add(Path.of(home, "Library/Java/JavaVirtualMachines"));
      }
    } else {
      roots.add(Path.of("/usr/lib/jvm"));
      roots.add(Path.of("/usr/java"));
      roots.add(Path.of("/opt/java"));
    }
    return roots;
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  record GradleVersion(int major, int minor, int patch) {
    @Override
    public String toString() {
      return major + "." + minor + "." + patch;
    }
  }

  record JdkInstallation(Path home, int major) {}
}
