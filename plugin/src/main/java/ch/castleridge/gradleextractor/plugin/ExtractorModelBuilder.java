/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import ch.castleridge.gradleextractor.model.DefaultExtractorModel;
import ch.castleridge.gradleextractor.model.DefaultExtractorProject;
import ch.castleridge.gradleextractor.model.DefaultExtractorSourceSet;
import ch.castleridge.gradleextractor.model.ExtractorModel;
import ch.castleridge.gradleextractor.model.ExtractorProject;
import ch.castleridge.gradleextractor.model.ExtractorSourceSet;

/**
 * Builds {@link ExtractorModel} for the connected Gradle build (all projects).
 */
public final class ExtractorModelBuilder implements ToolingModelBuilder {

  @Override
  public boolean canBuild(String modelName) {
    return ExtractorModel.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(String modelName, Project project) {
    Project root = project.getRootProject();
    List<ExtractorProject> projects = new ArrayList<>();
    Set<String> covered = new LinkedHashSet<>();

    for (Project p : root.getAllprojects()) {
      // Gradle 9+ requires the project's exclusive lock when resolving configurations.
      // ToolingModelBuilder only locks the queried project, so lock each subproject
      // before mapping (same pattern as EclipseModelBuilder).
      projects.add(mapProjectLocked(p));
      addCovered(covered, p);
    }

    for (org.gradle.api.initialization.IncludedBuild included : root.getGradle().getIncludedBuilds()) {
      File includedRoot = included.getProjectDir();
      covered.add(includedRoot.getAbsolutePath());
      addBuildFiles(covered, includedRoot);
    }

    return new DefaultExtractorModel(projects, new ArrayList<>(covered));
  }

  private static void addCovered(Set<String> covered, Project p) {
    File projectDir = p.getProjectDir();
    covered.add(projectDir.getAbsolutePath());
    addBuildFiles(covered, projectDir);
    File buildFile = p.getBuildFile();
    if (buildFile != null && buildFile.exists()) {
      covered.add(buildFile.getAbsolutePath());
    }
  }

  private static final String[] BUILD_FILE_NAMES = {
      "settings.gradle",
      "settings.gradle.kts",
      "build.gradle",
      "build.gradle.kts"
  };

  private static void addBuildFiles(Set<String> covered, File projectDir) {
    for (String name : BUILD_FILE_NAMES) {
      File f = new File(projectDir, name);
      if (f.isFile()) {
        covered.add(f.getAbsolutePath());
      }
    }
  }

  private static ExtractorProject mapProjectLocked(Project p) {
    return ((ProjectInternal) p).getOwner().fromMutableState(ExtractorModelBuilder::mapProject);
  }

  private static ExtractorProject mapProject(Project p) {
    DefaultExtractorProject out = new DefaultExtractorProject();
    out.setName(p.getName());
    out.setPath(p.getPath());
    out.setProjectDir(p.getProjectDir().getAbsolutePath());
    Object group = p.getGroup();
    out.setGroup(group != null ? group.toString() : "");
    Object version = p.getVersion();
    out.setVersion(version != null ? version.toString() : "unspecified");
    out.setJavaHome(resolveJavaHome(p));
    out.setSourceSets(mapSourceSets(p));
    return out;
  }

  private static String resolveJavaHome(Project p) {
    try {
      JavaPluginExtension java = p.getExtensions().findByType(JavaPluginExtension.class);
      if (java == null) {
        return System.getProperty("java.home");
      }
      JavaToolchainSpec toolchain = java.getToolchain();
      if (toolchain == null || !toolchain.getLanguageVersion().isPresent()) {
        return System.getProperty("java.home");
      }
      JavaToolchainService service = p.getExtensions().findByType(JavaToolchainService.class);
      if (service == null) {
        return System.getProperty("java.home");
      }
      File home = service.launcherFor(toolchain).get().getMetadata().getInstallationPath().getAsFile();
      return home.getAbsolutePath();
    } catch (Exception e) {
      return System.getProperty("java.home");
    }
  }

  private static List<ExtractorSourceSet> mapSourceSets(Project p) {
    List<ExtractorSourceSet> result = new ArrayList<>();
    SourceSetContainer container = findSourceSets(p);
    if (container == null) {
      return result;
    }
    for (SourceSet sourceSet : container) {
      result.add(mapSourceSet(p, sourceSet));
    }
    return result;
  }

  private static SourceSetContainer findSourceSets(Project p) {
    JavaPluginExtension java = p.getExtensions().findByType(JavaPluginExtension.class);
    if (java != null) {
      return java.getSourceSets();
    }
    Object legacy = p.getExtensions().findByName("sourceSets");
    if (legacy instanceof SourceSetContainer) {
      return (SourceSetContainer) legacy;
    }
    return null;
  }

  private static ExtractorSourceSet mapSourceSet(Project p, SourceSet sourceSet) {
    DefaultExtractorSourceSet out = new DefaultExtractorSourceSet();
    out.setName(sourceSet.getName());

    List<String> sources = new ArrayList<>();
    for (File dir : sourceSet.getAllJava().getSrcDirs()) {
      if (dir.isDirectory()) {
        sources.add(dir.getAbsolutePath());
      }
    }
    out.setSourceDirs(sources);

    List<String> classpath = new ArrayList<>();
    for (File file : sourceSet.getCompileClasspath()) {
      classpath.add(file.getAbsolutePath());
    }
    out.setCompileClasspath(classpath);

    List<String> outputs = new ArrayList<>();
    for (File dir : sourceSet.getOutput().getClassesDirs().getFiles()) {
      if (dir != null) {
        outputs.add(dir.getAbsolutePath());
      }
    }
    out.setOutputDirs(outputs);

    out.setJavacOptions(resolveJavacOptions(p, sourceSet));
    return out;
  }

  private static List<String> resolveJavacOptions(Project p, SourceSet sourceSet) {
    List<String> options = new ArrayList<>();
    String taskName = sourceSet.getCompileJavaTaskName();
    Object task = p.getTasks().findByName(taskName);
    if (task instanceof JavaCompile) {
      CompileOptions compileOptions = ((JavaCompile) task).getOptions();
      if (compileOptions.getCompilerArgs() != null) {
        options.addAll(compileOptions.getCompilerArgs());
      }
    }
    return options;
  }
}
