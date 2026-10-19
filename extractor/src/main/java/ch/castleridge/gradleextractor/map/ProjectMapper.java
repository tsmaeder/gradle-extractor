/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.map;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
 *
 * <p>{@code dependsOn} is derived from each source set's resolved compile
 * classpath (workspace module outputs), matching mavenimporter: flat,
 * classpath-accurate namespace ids. Non-main source sets always depend on
 * the same project's main namespace.
 */
public final class ProjectMapper {

  private ProjectMapper() {}

  public static MbtDocument toMbt(Path workspaceRoot, ExtractorModel model) {
    MbtDocument document = new MbtDocument();
    Map<String, String> pathToNamespaceId = new LinkedHashMap<>();
    Map<String, MbtDependencyModule> depsById = new LinkedHashMap<>();
    Map<Path, String> outputDirToNamespace = new LinkedHashMap<>();
    List<ProjectDirEntry> projectDirs = new ArrayList<>();

    for (ExtractorProject project : model.getProjects()) {
      Path projectDir = Path.of(project.getProjectDir()).toAbsolutePath().normalize();
      projectDirs.add(new ProjectDirEntry(projectDir, project));
      for (ExtractorSourceSet sourceSet : project.getSourceSets()) {
        String nsId = namespaceId(project, sourceSet);
        pathToNamespaceId.put(projectKey(project.getPath(), sourceSet.getName()), nsId);
        for (String outputDir : sourceSet.getOutputDirs()) {
          if (outputDir == null || outputDir.isBlank()) {
            continue;
          }
          Path out = Path.of(outputDir).toAbsolutePath().normalize();
          outputDirToNamespace.put(out, nsId);
        }
      }
    }
    projectDirs.sort(Comparator.comparingInt((ProjectDirEntry e) -> e.dir.getNameCount()).reversed());

    for (ExtractorProject project : model.getProjects()) {
      String mainNsId = pathToNamespaceId.get(projectKey(project.getPath(), "main"));
      for (ExtractorSourceSet sourceSet : project.getSourceSets()) {
        String selfNsId = namespaceId(project, sourceSet);
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

        Map<String, String> dependencySources = sourceSet.getDependencySources() != null
            ? sourceSet.getDependencySources()
            : Map.of();
        LinkedHashSet<String> dependsOn = new LinkedHashSet<>();
        for (String cpEntry : sourceSet.getCompileClasspath()) {
          if (cpEntry == null || cpEntry.isBlank()) {
            continue;
          }
          Path entry = Path.of(cpEntry).toAbsolutePath().normalize();
          String workspaceNs = namespaceForClasspathEntry(entry, outputDirToNamespace, projectDirs);
          if (workspaceNs != null) {
            if (!workspaceNs.equals(selfNsId)) {
              dependsOn.add(workspaceNs);
            }
            continue;
          }
          if (Files.isRegularFile(entry) && cpEntry.endsWith(".jar")) {
            String id = jarId(entry);
            String sourcesPath = lookupSources(dependencySources, cpEntry, entry);
            String sourcesUri = sourcesPath != null ? toFileUri(Path.of(sourcesPath)) : null;
            putDependencyModule(depsById, id, toFileUri(entry), sourcesUri);
            ns.dependencyModules.add(id);
          }
        }

        if (!"main".equals(sourceSet.getName()) && mainNsId != null) {
          dependsOn.add(mainNsId);
        }
        ns.dependsOn.addAll(dependsOn);
        document.namespaces.put(selfNsId, ns);
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

  /**
   * Maps a classpath entry to a workspace namespace id, or {@code null} if it is
   * not under any known project output / project directory.
   */
  static String namespaceForClasspathEntry(
      Path entry,
      Map<Path, String> outputDirToNamespace,
      List<ProjectDirEntry> projectDirsLongestFirst) {
    String fromOutput = matchOutputDir(entry, outputDirToNamespace);
    if (fromOutput != null) {
      return fromOutput;
    }
    for (ProjectDirEntry projectDir : projectDirsLongestFirst) {
      if (entry.startsWith(projectDir.dir)) {
        return namespaceId(projectDir.project, "main");
      }
    }
    return null;
  }

  private static String matchOutputDir(Path entry, Map<Path, String> outputDirToNamespace) {
    String best = null;
    int bestCount = -1;
    for (Map.Entry<Path, String> e : outputDirToNamespace.entrySet()) {
      Path out = e.getKey();
      if (entry.equals(out) || entry.startsWith(out)) {
        int count = out.getNameCount();
        if (count > bestCount) {
          bestCount = count;
          best = e.getValue();
        }
      }
    }
    return best;
  }

  private static String namespaceId(ExtractorProject project, ExtractorSourceSet sourceSet) {
    return namespaceId(project, sourceSet.getName());
  }

  private static String namespaceId(ExtractorProject project, String sourceSetName) {
    String group = project.getGroup() != null && !project.getGroup().isBlank()
        ? project.getGroup()
        : project.getName();
    String version = project.getVersion() != null ? project.getVersion() : "unspecified";
    String base = group + ":" + project.getName() + ":" + version;
    if ("main".equals(sourceSetName)) {
      return base;
    }
    return base + ":" + sourceSetName;
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

  private static String jarId(Path jar) {
    String name = jar.getFileName().toString();
    if (name.endsWith(".jar")) {
      name = name.substring(0, name.length() - 4);
    }
    return name;
  }

  private static String lookupSources(
      Map<String, String> dependencySources, String cpEntry, Path entry) {
    String sources = dependencySources.get(cpEntry);
    if (sources != null) {
      return sources;
    }
    return dependencySources.get(entry.toString());
  }

  private static void putDependencyModule(
      Map<String, MbtDependencyModule> depsById, String id, String jarUri, String sourcesUri) {
    MbtDependencyModule existing = depsById.get(id);
    if (existing == null) {
      depsById.put(id, new MbtDependencyModule(id, jarUri, sourcesUri));
      return;
    }
    if (existing.sources == null && sourcesUri != null) {
      depsById.put(id, new MbtDependencyModule(id, jarUri, sourcesUri));
    }
  }

  private static String toFileUri(Path path) {
    URI uri = path.toAbsolutePath().normalize().toUri();
    return uri.toString();
  }

  record ProjectDirEntry(Path dir, ExtractorProject project) {}
}
