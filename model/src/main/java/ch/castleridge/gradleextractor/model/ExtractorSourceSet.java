/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

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

  /**
   * Absolute binary jar path to absolute sources jar path for external
   * dependencies that resolved a sources artifact. Missing sources are omitted.
   */
  Map<String, String> getDependencySources();
}
