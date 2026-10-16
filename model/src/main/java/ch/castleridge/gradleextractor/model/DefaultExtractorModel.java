/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default serializable {@link ExtractorModel}.
 */
public final class DefaultExtractorModel implements ExtractorModel {
  private static final long serialVersionUID = 1L;

  private List<ExtractorProject> projects = new ArrayList<>();
  private List<String> coveredPaths = new ArrayList<>();

  public DefaultExtractorModel() {}

  public DefaultExtractorModel(List<ExtractorProject> projects, List<String> coveredPaths) {
    this.projects = projects != null ? new ArrayList<>(projects) : new ArrayList<>();
    this.coveredPaths = coveredPaths != null ? new ArrayList<>(coveredPaths) : new ArrayList<>();
  }

  @Override
  public List<ExtractorProject> getProjects() {
    return projects;
  }

  public void setProjects(List<ExtractorProject> projects) {
    this.projects = projects != null ? projects : new ArrayList<>();
  }

  @Override
  public List<String> getCoveredPaths() {
    return coveredPaths;
  }

  public void setCoveredPaths(List<String> coveredPaths) {
    this.coveredPaths = coveredPaths != null ? coveredPaths : new ArrayList<>();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DefaultExtractorModel other)) {
      return false;
    }
    return Objects.equals(projects, other.projects) && Objects.equals(coveredPaths, other.coveredPaths);
  }

  @Override
  public int hashCode() {
    return Objects.hash(projects, coveredPaths);
  }
}
