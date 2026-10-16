/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.map;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import ch.castleridge.gradleextractor.mbt.MbtDependencyModule;
import ch.castleridge.gradleextractor.mbt.MbtDocument;
import ch.castleridge.gradleextractor.mbt.MbtNamespace;
import ch.castleridge.gradleextractor.model.ExtractorModel;
import ch.castleridge.gradleextractor.model.ExtractorProject;
import ch.castleridge.gradleextractor.model.ExtractorSourceSet;

/**
 * Maps an {@link ExtractorModel} to a Metals-schema mbt fragment.
 */
public final class ProjectMapper {

  private ProjectMapper() {}

  public static MbtDocument toMbt(Path workspaceRoot, ExtractorModel model) {
    MbtDocument document = new MbtDocument();
    Map<String, String> pathToNamespaceId = new LinkedHashMap<>();
    Map<String, MbtDependencyModule> depsById = new LinkedHashMap<>();

    for (ExtractorProject project : model.getProjects()) {
      for (ExtractorSourceSet sourceSet : project.getSourceSets()) {
        String nsId = namespaceId(project, sourceSet);
        pathToNamespaceId.put(projectKey(project.getPath(), sourceSet.getName()), nsId);
      }
    }

    Set<String> workspaceProjectPaths = new LinkedHashSet<>();
    for (ExtractorProject project : model.getProjects()) {
      workspaceProjectPaths.add(project.getPath());
    }

    for (ExtractorProject project : model.getProjects()) {
      for (ExtractorSourceSet sourceSet : project.getSourceSets()) {
        MbtNamespace ns = new MbtNamespace();
        ns.javaHome = project.getJavaHome();
        if (sourceSet.getJavacOptions() != null) {
          ns.javacOptions.addAll(sourceSet.getJavacOptions());
        }
        for (String sourceDir : sourceSet.getSourceDirs()) {
          String relative = toWorkspaceRelative(workspaceRoot, sourceDir);
          if (relative != null) {
            ns.sources.add(relative);
          }
        }
        for (String depPath : sourceSet.getProjectDependencies()) {
          String mainKey = projectKey(depPath, "main");
          String depNs = pathToNamespaceId.get(mainKey);
          if (depNs != null && workspaceProjectPaths.contains(depPath)) {
            ns.dependsOn.add(depNs);
          }
        }
        for (String cpEntry : sourceSet.getCompileClasspath()) {
          Path entry = Path.of(cpEntry);
          if (!Files.isRegularFile(entry) || !cpEntry.endsWith(".jar")) {
            continue;
          }
          if (isUnderAnyProjectDir(model, entry)) {
            continue;
          }
          String id = jarId(entry);
          depsById.putIfAbsent(id, new MbtDependencyModule(id, toFileUri(entry), null));
          ns.dependencyModules.add(id);
        }
        document.namespaces.put(namespaceId(project, sourceSet), ns);
      }
    }

    document.dependencyModules.addAll(depsById.values());
    return document;
  }

  public static Set<Path> coveredProjectDirs(ExtractorModel model) {
    Set<Path> covered = new LinkedHashSet<>();
    for (ExtractorProject project : model.getProjects()) {
      if (project.getProjectDir() != null) {
        covered.add(Path.of(project.getProjectDir()).toAbsolutePath().normalize());
      }
    }
    for (String path : model.getCoveredPaths()) {
      Path p = Path.of(path).toAbsolutePath().normalize();
      if (Files.isDirectory(p)) {
        covered.add(p);
      } else if (Files.isRegularFile(p) && p.getParent() != null) {
        covered.add(p.getParent());
      }
    }
    return covered;
  }

  private static String namespaceId(ExtractorProject project, ExtractorSourceSet sourceSet) {
    String group = project.getGroup() != null && !project.getGroup().isBlank()
        ? project.getGroup()
        : project.getName();
    String version = project.getVersion() != null ? project.getVersion() : "unspecified";
    String base = group + ":" + project.getName() + ":" + version;
    if ("main".equals(sourceSet.getName())) {
      return base;
    }
    return base + ":" + sourceSet.getName();
  }

  private static String projectKey(String gradlePath, String sourceSetName) {
    return gradlePath + "::" + sourceSetName;
  }

  private static String toWorkspaceRelative(Path workspaceRoot, String absolute) {
    try {
      Path abs = Path.of(absolute).toAbsolutePath().normalize();
      Path root = workspaceRoot.toAbsolutePath().normalize();
      if (!abs.startsWith(root)) {
        return abs.toString().replace('\\', '/');
      }
      return root.relativize(abs).toString().replace('\\', '/');
    } catch (Exception e) {
      return null;
    }
  }

  private static boolean isUnderAnyProjectDir(ExtractorModel model, Path jar) {
    Path normalized = jar.toAbsolutePath().normalize();
    for (ExtractorProject project : model.getProjects()) {
      Path dir = Path.of(project.getProjectDir()).toAbsolutePath().normalize();
      if (normalized.startsWith(dir)) {
        return true;
      }
    }
    return false;
  }

  private static String jarId(Path jar) {
    String name = jar.getFileName().toString();
    if (name.endsWith(".jar")) {
      name = name.substring(0, name.length() - 4);
    }
    return name;
  }

  private static String toFileUri(Path path) {
    URI uri = path.toAbsolutePath().normalize().toUri();
    return uri.toString();
  }
}
