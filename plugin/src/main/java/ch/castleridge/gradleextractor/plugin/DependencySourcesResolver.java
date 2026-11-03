/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.plugin;

import java.io.File;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;

/**
 * Optionally resolves {@code -sources.jar} artifacts for external dependencies
 * on a source set's compile classpath.
 *
 * <p>Prefers {@link org.gradle.api.artifacts.query.ArtifactResolutionQuery}
 * (downloads published sources reliably), then falls back to a lenient
 * documentation variant reselection view. Sources that resolve to the same
 * file as the binary jar are ignored.
 */
final class DependencySourcesResolver {

  private DependencySourcesResolver() {}

  /**
   * Returns absolute normalized binary jar path to absolute normalized sources
   * jar path for dependencies that resolved a distinct sources artifact.
   * Failures are ignored.
   */
  static Map<String, String> resolve(Project project, SourceSet sourceSet) {
    Map<String, String> result = new LinkedHashMap<>();
    try {
      String configName = sourceSet.getCompileClasspathConfigurationName();
      Configuration compileClasspath = project.getConfigurations().findByName(configName);
      if (compileClasspath == null) {
        return result;
      }

      Map<ComponentIdentifier, File> jarsByComponent = new LinkedHashMap<>();
      for (ResolvedArtifactResult artifact : compileClasspath.getIncoming().getArtifacts()) {
        File file = artifact.getFile();
        if (file != null && file.isFile() && file.getName().endsWith(".jar")) {
          jarsByComponent.putIfAbsent(artifact.getId().getComponentIdentifier(), file);
        }
      }
      if (jarsByComponent.isEmpty()) {
        return result;
      }

      putDistinctSources(result, jarsByComponent, resolveViaArtifactQuery(project, jarsByComponent.keySet()));
      putDistinctSources(result, jarsByComponent, resolveViaArtifactView(project, compileClasspath));
    } catch (Exception ignored) {
      // Sources jars are optional.
    }
    return result;
  }

  private static Map<ComponentIdentifier, File> resolveViaArtifactQuery(
      Project project, Set<ComponentIdentifier> components) {
    Map<ComponentIdentifier, File> sourcesByComponent = new LinkedHashMap<>();
    try {
      for (org.gradle.api.artifacts.result.ComponentArtifactsResult component :
          project.getDependencies()
              .createArtifactResolutionQuery()
              .forComponents(components)
              .withArtifacts(JvmLibrary.class, SourcesArtifact.class)
              .execute()
              .getResolvedComponents()) {
        for (ArtifactResult artifactResult : component.getArtifacts(SourcesArtifact.class)) {
          if (!(artifactResult instanceof ResolvedArtifactResult)) {
            continue;
          }
          File sourcesFile = ((ResolvedArtifactResult) artifactResult).getFile();
          if (sourcesFile != null && sourcesFile.isFile()) {
            sourcesByComponent.putIfAbsent(component.getId(), sourcesFile);
            break;
          }
        }
      }
    } catch (Exception ignored) {
      // Fall back to artifact view.
    }
    return sourcesByComponent;
  }

  private static Map<ComponentIdentifier, File> resolveViaArtifactView(
      Project project, Configuration compileClasspath) {
    Map<ComponentIdentifier, File> sourcesByComponent = new LinkedHashMap<>();
    try {
      ObjectFactory objects = project.getObjects();
      for (ResolvedArtifactResult sourcesArtifact : compileClasspath.getIncoming()
          .artifactView(view -> {
            view.withVariantReselection();
            view.setLenient(true);
            view.attributes(attrs -> {
              attrs.attribute(
                  Category.CATEGORY_ATTRIBUTE,
                  objects.named(Category.class, Category.DOCUMENTATION));
              attrs.attribute(
                  DocsType.DOCS_TYPE_ATTRIBUTE,
                  objects.named(DocsType.class, DocsType.SOURCES));
            });
          })
          .getArtifacts()) {
        File sourcesFile = sourcesArtifact.getFile();
        if (sourcesFile == null || !sourcesFile.isFile()) {
          continue;
        }
        sourcesByComponent.putIfAbsent(
            sourcesArtifact.getId().getComponentIdentifier(), sourcesFile);
      }
    } catch (Exception ignored) {
      // Sources jars are optional.
    }
    return sourcesByComponent;
  }

  private static void putDistinctSources(
      Map<String, String> result,
      Map<ComponentIdentifier, File> jarsByComponent,
      Map<ComponentIdentifier, File> sourcesByComponent) {
    for (Map.Entry<ComponentIdentifier, File> e : sourcesByComponent.entrySet()) {
      File jar = jarsByComponent.get(e.getKey());
      if (jar == null) {
        continue;
      }
      String jarPath = normalizePath(jar);
      String sourcesPath = normalizePath(e.getValue());
      if (jarPath == null || sourcesPath == null || jarPath.equals(sourcesPath)) {
        continue;
      }
      result.putIfAbsent(jarPath, sourcesPath);
    }
  }

  /** Absolute normalized path string, or {@code null} when {@code file} is null. */
  static String normalizePath(File file) {
    if (file == null) {
      return null;
    }
    return Paths.get(file.getAbsolutePath()).toAbsolutePath().normalize().toString();
  }
}
