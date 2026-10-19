/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.plugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceSet;

/**
 * Optionally resolves {@code -sources.jar} artifacts for external dependencies
 * on a source set's compile classpath via variant reselection.
 */
final class DependencySourcesResolver {

  private DependencySourcesResolver() {}

  /**
   * Returns absolute binary jar path to absolute sources jar path for
   * dependencies that resolved a sources artifact. Failures are ignored.
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
        File jar = jarsByComponent.get(sourcesArtifact.getId().getComponentIdentifier());
        if (jar != null) {
          result.putIfAbsent(jar.getAbsolutePath(), sourcesFile.getAbsolutePath());
        }
      }
    } catch (Exception ignored) {
      // Sources jars are optional.
    }
    return result;
  }
}
