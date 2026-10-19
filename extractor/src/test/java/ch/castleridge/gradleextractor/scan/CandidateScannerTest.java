/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CandidateScannerTest {

  @TempDir
  Path workspace;

  @Test
  void nestedSettingsUnderOuterSettingsAreDropped() throws IOException {
    Path outer = workspace.resolve("root");
    Path nested = outer.resolve("fixture");
    touch(outer.resolve("settings.gradle"));
    touch(nested.resolve("settings.gradle"));
    touch(nested.resolve("build.gradle"));

    Set<Path> found = CandidateScanner.scan(workspace);

    assertEquals(Set.of(outer.toAbsolutePath().normalize()), found);
  }

  @Test
  void buildOnlyUnderSettingsRootIsDropped() throws IOException {
    Path outer = workspace.resolve("root");
    Path sub = outer.resolve("sub");
    touch(outer.resolve("settings.gradle"));
    touch(outer.resolve("build.gradle"));
    touch(sub.resolve("build.gradle"));

    Set<Path> found = CandidateScanner.scan(workspace);

    assertEquals(Set.of(outer.toAbsolutePath().normalize()), found);
  }

  @Test
  void siblingSettingsRootsAreBothKept() throws IOException {
    Path a = workspace.resolve("a");
    Path b = workspace.resolve("b");
    touch(a.resolve("settings.gradle"));
    touch(b.resolve("settings.gradle"));

    Set<Path> found = CandidateScanner.scan(workspace);

    assertEquals(
        Set.of(a.toAbsolutePath().normalize(), b.toAbsolutePath().normalize()),
        found);
  }

  @Test
  void standaloneBuildGradleWithoutSettingsIsKept() throws IOException {
    Path alone = workspace.resolve("alone");
    touch(alone.resolve("build.gradle"));

    Set<Path> found = CandidateScanner.scan(workspace);

    assertEquals(Set.of(alone.toAbsolutePath().normalize()), found);
  }

  @Test
  void filterToImportRootsDropsNestedSettings() throws IOException {
    Path outer = workspace.resolve("root").toAbsolutePath().normalize();
    Path nested = outer.resolve("nested").toAbsolutePath().normalize();
    Files.createDirectories(nested);
    touch(outer.resolve("settings.gradle"));
    touch(nested.resolve("settings.gradle"));

    List<Path> filtered = CandidateScanner.filterToImportRoots(List.of(outer, nested));

    assertEquals(List.of(outer), filtered);
  }

  private static void touch(Path file) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, "");
  }
}
