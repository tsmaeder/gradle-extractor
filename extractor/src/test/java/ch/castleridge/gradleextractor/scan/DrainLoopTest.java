/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.castleridge.gradleextractor.mbt.MbtAggregator;
import ch.castleridge.gradleextractor.mbt.MbtDocument;
import ch.castleridge.gradleextractor.mbt.MbtNamespace;

class DrainLoopTest {

  @TempDir
  Path workspace;

  @Test
  void removesCoveredDirsIncludingNestedIncludeBuildRoots() throws Exception {
    Path root = workspace.resolve("root").toAbsolutePath().normalize();
    Path included = workspace.resolve("root/build-logic").toAbsolutePath().normalize();
    List<Path> imported = new ArrayList<>();

    Set<Path> todo = new LinkedHashSet<>();
    todo.add(root);
    todo.add(included);

    MbtAggregator aggregator = DrainLoop.run(todo, projectDir -> {
      imported.add(projectDir);
      MbtDocument doc = new MbtDocument();
      MbtNamespace ns = new MbtNamespace();
      ns.sources.add(projectDir.getFileName().toString());
      doc.namespaces.put(projectDir.getFileName().toString(), ns);
      // Parent composite maps include-build projects and reports them covered.
      return new DrainLoop.ImportResult(doc, Set.of(root, included));
    });

    assertEquals(List.of(root), imported);
    assertTrue(aggregator.toDocument().namespaces.containsKey("root"));
    assertTrue(todo.isEmpty());
  }
}
