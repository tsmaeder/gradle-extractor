/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.model;

import java.io.Serializable;
import java.util.List;

/**
 * One source set (e.g. {@code main}, {@code test}) within an {@link ExtractorProject}.
 */
public interface ExtractorSourceSet extends Serializable {

  String getName();

  /** Absolute source directories that exist. */
  List<String> getSourceDirs();

  /** Absolute compile-classpath jar (and class directory) paths. */
  List<String> getCompileClasspath();

  List<String> getJavacOptions();

  /**
   * Absolute class-output directories for this source set (from
   * {@code SourceSetOutput.getClassesDirs()}), used to map classpath entries
   * back to workspace namespaces.
   */
  List<String> getOutputDirs();
}
