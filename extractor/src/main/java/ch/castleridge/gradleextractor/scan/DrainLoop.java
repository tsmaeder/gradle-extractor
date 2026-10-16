/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.scan;

import java.nio.file.Path;
import java.util.Set;

import ch.castleridge.gradleextractor.mbt.MbtAggregator;
import ch.castleridge.gradleextractor.mbt.MbtDocument;

/**
 * Drains a todo set of Gradle project directories build-by-build, shortest path first.
 */
public final class DrainLoop {

  @FunctionalInterface
  public interface BuildImporter {
    /**
     * Imports one Gradle build rooted at {@code projectDir} and returns the partial mbt document
     * plus every project directory that belongs to that build.
     */
    ImportResult importBuild(Path projectDir) throws Exception;
  }

  public record ImportResult(MbtDocument document, Set<Path> coveredProjectDirs) {}

  private DrainLoop() {}

  public static MbtAggregator run(Set<Path> todo, BuildImporter importer) throws Exception {
    MbtAggregator aggregator = new MbtAggregator();
    while (!todo.isEmpty()) {
      Path next = CandidateScanner.takeShortest(todo);
      if (next == null) {
        break;
      }
      ImportResult result = importer.importBuild(next);
      aggregator.add(result.document());
      for (Path covered : result.coveredProjectDirs()) {
        todo.remove(covered.toAbsolutePath().normalize());
      }
      todo.remove(next.toAbsolutePath().normalize());
    }
    return aggregator;
  }
}
