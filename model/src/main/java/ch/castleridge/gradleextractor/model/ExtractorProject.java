/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.model;

import java.io.Serializable;
import java.util.List;

/**
 * One Gradle project within an {@link ExtractorModel}.
 */
public interface ExtractorProject extends Serializable {

  String getName();

  /** Gradle project path, e.g. {@code :} or {@code :app}. */
  String getPath();

  /** Absolute project directory. */
  String getProjectDir();

  String getGroup();

  String getVersion();

  /** Absolute Java home when known; otherwise {@code null}. */
  String getJavaHome();

  List<ExtractorSourceSet> getSourceSets();
}
