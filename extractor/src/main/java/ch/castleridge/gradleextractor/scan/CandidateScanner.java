/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.scan;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Recursively finds Gradle project directories and orders them by path segment count.
 */
public final class CandidateScanner {

  private CandidateScanner() {}

  public static Set<Path> scan(Path root) throws IOException {
    List<Path> found = new ArrayList<>();
    Files.walkFileTree(root, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
        if (name.equals(".git")
            || name.equals("build")
            || name.equals(".gradle")
            || name.equals("node_modules")
            || name.equals("src")
            || name.equals("target")) {
          return FileVisitResult.SKIP_SUBTREE;
        }
        if (isGradleProjectDir(dir)) {
          found.add(dir.toAbsolutePath().normalize());
        }
        return FileVisitResult.CONTINUE;
      }
    });
    found.sort(Comparator
        .comparingInt(CandidateScanner::pathSegmentCount)
        .thenComparing(Path::toString));
    return new LinkedHashSet<>(filterNestedBuildOnly(found));
  }

  /**
   * Keeps settings-based roots and standalone single-project builds. Drops
   * {@code build.gradle(.kts)}-only directories that sit under another settings
   * root — those are subprojects, not separate Tooling API entry points.
   */
  static List<Path> filterNestedBuildOnly(List<Path> found) {
    List<Path> settingsRoots = new ArrayList<>();
    for (Path dir : found) {
      if (hasSettings(dir)) {
        settingsRoots.add(dir);
      }
    }
    List<Path> filtered = new ArrayList<>();
    for (Path dir : found) {
      if (hasSettings(dir)) {
        filtered.add(dir);
        continue;
      }
      boolean underSettingsRoot = false;
      for (Path root : settingsRoots) {
        if (dir.startsWith(root) && !dir.equals(root)) {
          underSettingsRoot = true;
          break;
        }
      }
      if (!underSettingsRoot) {
        filtered.add(dir);
      }
    }
    return filtered;
  }

  static boolean isGradleProjectDir(Path dir) {
    return hasSettings(dir)
        || Files.isRegularFile(dir.resolve("build.gradle"))
        || Files.isRegularFile(dir.resolve("build.gradle.kts"));
  }

  static boolean hasSettings(Path dir) {
    return Files.isRegularFile(dir.resolve("settings.gradle"))
        || Files.isRegularFile(dir.resolve("settings.gradle.kts"));
  }

  static int pathSegmentCount(Path path) {
    return path.getNameCount();
  }

  /**
   * Returns the candidate with the fewest path segments, or {@code null} if empty.
   */
  public static Path takeShortest(Set<Path> todo) {
    return todo.stream()
        .min(Comparator.comparingInt(CandidateScanner::pathSegmentCount).thenComparing(Path::toString))
        .orElse(null);
  }

  /** Build / settings files under the given project directories (for up-to-date checks). */
  public static List<Path> markerFiles(Set<Path> projectDirs) {
    List<Path> markers = new ArrayList<>();
    for (Path dir : projectDirs) {
      for (String name : List.of(
          "settings.gradle",
          "settings.gradle.kts",
          "build.gradle",
          "build.gradle.kts")) {
        Path marker = dir.resolve(name);
        if (Files.isRegularFile(marker)) {
          markers.add(marker.toAbsolutePath().normalize());
        }
      }
    }
    return markers;
  }
}
