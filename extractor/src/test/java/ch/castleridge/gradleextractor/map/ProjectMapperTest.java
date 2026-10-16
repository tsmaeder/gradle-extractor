/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.castleridge.gradleextractor.mbt.MbtDocument;
import ch.castleridge.gradleextractor.mbt.MbtNamespace;
import ch.castleridge.gradleextractor.model.DefaultExtractorModel;
import ch.castleridge.gradleextractor.model.DefaultExtractorProject;
import ch.castleridge.gradleextractor.model.DefaultExtractorSourceSet;

class ProjectMapperTest {

  @TempDir
  Path workspace;

  @Test
  void multiProjectMainDependsOnLibOutputDir() throws IOException {
    Path libOut = workspace.resolve("lib/build/classes/java/main");
    Path appOut = workspace.resolve("app/build/classes/java/main");
    Path externalJar = workspace.resolve("caches/guava-33.0.jar");
    Files.createDirectories(externalJar.getParent());
    Files.writeString(externalJar, "jar");

    DefaultExtractorProject lib = project("lib", ":lib", workspace.resolve("lib"),
        sourceSet("main", List.of(), List.of(libOut.toString())));
    DefaultExtractorProject app = project("app", ":app", workspace.resolve("app"),
        sourceSet("main",
            List.of(libOut.toString(), externalJar.toString()),
            List.of(appOut.toString())));

    MbtDocument doc = ProjectMapper.toMbt(workspace, model(lib, app));

    String libId = "ex:lib:1.0";
    String appId = "ex:app:1.0";
    MbtNamespace appNs = doc.namespaces.get(appId);
    assertTrue(appNs.dependsOn.contains(libId), () -> "dependsOn=" + appNs.dependsOn);
    assertFalse(appNs.dependsOn.contains(appId));
    assertTrue(appNs.dependencyModules.contains("guava-33.0"));
    assertFalse(appNs.dependencyModules.stream().anyMatch(id -> id.contains("lib")));
  }

  @Test
  void testSourceSetDependsOnOwnMainFromClasspath() {
    Path mainOut = workspace.resolve("lib/build/classes/java/main");
    Path testOut = workspace.resolve("lib/build/classes/java/test");

    DefaultExtractorProject lib = project("lib", ":lib", workspace.resolve("lib"),
        sourceSet("main", List.of(), List.of(mainOut.toString())),
        sourceSet("test", List.of(mainOut.toString()), List.of(testOut.toString())));

    MbtDocument doc = ProjectMapper.toMbt(workspace, model(lib));
    String mainId = "ex:lib:1.0";
    String testId = mainId + ":test";

    assertTrue(doc.namespaces.get(testId).dependsOn.contains(mainId));
    assertFalse(doc.namespaces.get(testId).dependsOn.contains(testId));
  }

  @Test
  void testSourceSetDependsOnOwnMainWhenUnbuilt() {
    DefaultExtractorProject lib = project("lib", ":lib", workspace.resolve("lib"),
        sourceSet("main", List.of(), List.of()),
        sourceSet("test", List.of(), List.of()));

    MbtDocument doc = ProjectMapper.toMbt(workspace, model(lib));
    String mainId = "ex:lib:1.0";
    assertTrue(doc.namespaces.get(mainId + ":test").dependsOn.contains(mainId));
  }

  @Test
  void transitiveModulesOnClasspathBecomeDependsOn() {
    Path libOut = workspace.resolve("lib/build/classes/java/main");
    Path transitiveOut = workspace.resolve("transitive/build/classes/java/main");
    Path appOut = workspace.resolve("app/build/classes/java/main");

    DefaultExtractorProject transitive = project("transitive", ":transitive",
        workspace.resolve("transitive"),
        sourceSet("main", List.of(), List.of(transitiveOut.toString())));
    DefaultExtractorProject lib = project("lib", ":lib", workspace.resolve("lib"),
        sourceSet("main", List.of(transitiveOut.toString()), List.of(libOut.toString())));
    DefaultExtractorProject app = project("app", ":app", workspace.resolve("app"),
        sourceSet("main",
            List.of(libOut.toString(), transitiveOut.toString()),
            List.of(appOut.toString())));

    MbtDocument doc = ProjectMapper.toMbt(workspace, model(transitive, lib, app));
    MbtNamespace appNs = doc.namespaces.get("ex:app:1.0");
    assertTrue(appNs.dependsOn.contains("ex:lib:1.0"));
    assertTrue(appNs.dependsOn.contains("ex:transitive:1.0"));
  }

  @Test
  void moduleOnlyOnOtherProjectClasspathIsNotDependsOn() {
    Path hiddenOut = workspace.resolve("hidden/build/classes/java/main");
    Path libOut = workspace.resolve("lib/build/classes/java/main");
    Path appOut = workspace.resolve("app/build/classes/java/main");

    DefaultExtractorProject hidden = project("hidden", ":hidden", workspace.resolve("hidden"),
        sourceSet("main", List.of(), List.of(hiddenOut.toString())));
    DefaultExtractorProject lib = project("lib", ":lib", workspace.resolve("lib"),
        sourceSet("main", List.of(hiddenOut.toString()), List.of(libOut.toString())));
    DefaultExtractorProject app = project("app", ":app", workspace.resolve("app"),
        sourceSet("main", List.of(libOut.toString()), List.of(appOut.toString())));

    MbtDocument doc = ProjectMapper.toMbt(workspace, model(hidden, lib, app));
    assertFalse(doc.namespaces.get("ex:app:1.0").dependsOn.contains("ex:hidden:1.0"));
    assertTrue(doc.namespaces.get("ex:lib:1.0").dependsOn.contains("ex:hidden:1.0"));
  }

  @Test
  void packagedJarUnderProjectDirMapsToMainNamespace() {
    Path libJar = workspace.resolve("lib/build/libs/lib-1.0.jar");
    Path appOut = workspace.resolve("app/build/classes/java/main");

    DefaultExtractorProject lib = project("lib", ":lib", workspace.resolve("lib"),
        sourceSet("main", List.of(),
            List.of(workspace.resolve("lib/build/classes/java/main").toString())));
    DefaultExtractorProject app = project("app", ":app", workspace.resolve("app"),
        sourceSet("main", List.of(libJar.toString()), List.of(appOut.toString())));

    MbtDocument doc = ProjectMapper.toMbt(workspace, model(lib, app));
    assertTrue(doc.namespaces.get("ex:app:1.0").dependsOn.contains("ex:lib:1.0"));
    assertEquals(0, doc.namespaces.get("ex:app:1.0").dependencyModules.size());
  }

  private static DefaultExtractorModel model(DefaultExtractorProject... projects) {
    return new DefaultExtractorModel(List.of(projects), List.of());
  }

  private static DefaultExtractorProject project(
      String name, String path, Path projectDir, DefaultExtractorSourceSet... sourceSets) {
    DefaultExtractorProject p = new DefaultExtractorProject();
    p.setName(name);
    p.setPath(path);
    p.setProjectDir(projectDir.toAbsolutePath().normalize().toString());
    p.setGroup("ex");
    p.setVersion("1.0");
    p.setJavaHome("/jdk");
    p.setSourceSets(List.of(sourceSets));
    return p;
  }

  private static DefaultExtractorSourceSet sourceSet(
      String name, List<String> compileClasspath, List<String> outputDirs) {
    DefaultExtractorSourceSet ss = new DefaultExtractorSourceSet();
    ss.setName(name);
    ss.setSourceDirs(List.of());
    ss.setCompileClasspath(compileClasspath);
    ss.setJavacOptions(List.of());
    ss.setOutputDirs(outputDirs);
    return ss;
  }
}
