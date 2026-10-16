/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor.plugin;

import javax.inject.Inject;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * Injected via init script; registers {@link ExtractorModelBuilder}.
 */
public final class ExtractorPlugin implements Plugin<Project> {

  private final ToolingModelBuilderRegistry registry;

  @Inject
  public ExtractorPlugin(ToolingModelBuilderRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void apply(Project project) {
    registry.register(new ExtractorModelBuilder());
  }
}
