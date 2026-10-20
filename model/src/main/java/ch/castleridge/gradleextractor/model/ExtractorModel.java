/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.model;

import java.io.Serializable;
import java.util.List;

/**
 * Root Tooling API model for one Gradle build connection.
 */
public interface ExtractorModel extends Serializable {

  List<ExtractorProject> getProjects();

  /**
   * Absolute filesystem paths covered by this build (project dirs and build files) so the
   * extractor can remove them from the workspace todo set. Includes paths from included
   * builds mapped as part of the same composite.
   */
  List<String> getCoveredPaths();
}
