/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default serializable {@link ExtractorSourceSet}.
 */
public final class DefaultExtractorSourceSet implements ExtractorSourceSet {
  private static final long serialVersionUID = 1L;

  private String name;
  private List<String> sourceDirs = new ArrayList<>();
  private List<String> compileClasspath = new ArrayList<>();
  private List<String> javacOptions = new ArrayList<>();
  private List<String> projectDependencies = new ArrayList<>();

  public DefaultExtractorSourceSet() {}

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public List<String> getSourceDirs() {
    return sourceDirs;
  }

  public void setSourceDirs(List<String> sourceDirs) {
    this.sourceDirs = sourceDirs != null ? sourceDirs : new ArrayList<>();
  }

  @Override
  public List<String> getCompileClasspath() {
    return compileClasspath;
  }

  public void setCompileClasspath(List<String> compileClasspath) {
    this.compileClasspath = compileClasspath != null ? compileClasspath : new ArrayList<>();
  }

  @Override
  public List<String> getJavacOptions() {
    return javacOptions;
  }

  public void setJavacOptions(List<String> javacOptions) {
    this.javacOptions = javacOptions != null ? javacOptions : new ArrayList<>();
  }

  @Override
  public List<String> getProjectDependencies() {
    return projectDependencies;
  }

  public void setProjectDependencies(List<String> projectDependencies) {
    this.projectDependencies = projectDependencies != null ? projectDependencies : new ArrayList<>();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DefaultExtractorSourceSet other)) {
      return false;
    }
    return Objects.equals(name, other.name)
        && Objects.equals(sourceDirs, other.sourceDirs)
        && Objects.equals(compileClasspath, other.compileClasspath)
        && Objects.equals(javacOptions, other.javacOptions)
        && Objects.equals(projectDependencies, other.projectDependencies);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, sourceDirs, compileClasspath, javacOptions, projectDependencies);
  }
}
