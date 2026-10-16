/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default serializable {@link ExtractorProject}.
 */
public final class DefaultExtractorProject implements ExtractorProject {
  private static final long serialVersionUID = 1L;

  private String name;
  private String path;
  private String projectDir;
  private String group;
  private String version;
  private String javaHome;
  private List<ExtractorSourceSet> sourceSets = new ArrayList<>();

  public DefaultExtractorProject() {}

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  @Override
  public String getProjectDir() {
    return projectDir;
  }

  public void setProjectDir(String projectDir) {
    this.projectDir = projectDir;
  }

  @Override
  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  @Override
  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @Override
  public String getJavaHome() {
    return javaHome;
  }

  public void setJavaHome(String javaHome) {
    this.javaHome = javaHome;
  }

  @Override
  public List<ExtractorSourceSet> getSourceSets() {
    return sourceSets;
  }

  public void setSourceSets(List<ExtractorSourceSet> sourceSets) {
    this.sourceSets = sourceSets != null ? sourceSets : new ArrayList<>();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DefaultExtractorProject other)) {
      return false;
    }
    return Objects.equals(name, other.name)
        && Objects.equals(path, other.path)
        && Objects.equals(projectDir, other.projectDir)
        && Objects.equals(group, other.group)
        && Objects.equals(version, other.version)
        && Objects.equals(javaHome, other.javaHome)
        && Objects.equals(sourceSets, other.sourceSets);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, path, projectDir, group, version, javaHome, sourceSets);
  }
}
