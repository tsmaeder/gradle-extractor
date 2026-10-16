/*
 * Copyright 2026 by Castle Ridge Software GmbH
 */
package ch.castleridge.gradleextractor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import ch.castleridge.gradleextractor.map.ProjectMapper;
import ch.castleridge.gradleextractor.mbt.MbtAggregator;
import ch.castleridge.gradleextractor.mbt.MbtDocument;
import ch.castleridge.gradleextractor.mbt.MbtJsonWriter;
import ch.castleridge.gradleextractor.model.ExtractorModel;
import ch.castleridge.gradleextractor.scan.CandidateScanner;
import ch.castleridge.gradleextractor.scan.DrainLoop;
import ch.castleridge.gradleextractor.scan.OutputUpToDate;
import ch.castleridge.gradleextractor.tapi.GradleModelFetcher;
import ch.castleridge.gradleextractor.tapi.PluginJar;

/**
 * Entry point for the Gradle workspace extractor.
 *
 * <pre>
 * java -jar gradle-extractor.jar &lt;directory&gt; [--output &lt;file&gt;] [--force]
 * </pre>
 */
public final class Main {

  private static final String DEFAULT_OUTPUT = ".javals/mbt.json.gradle";

  private Main() {}

  public static void main(String[] args) {
    int code = run(args);
    if (code != 0) {
      System.exit(code);
    }
  }

  static int run(String[] args) {
    ParsedArgs parsed = parseArgs(args);
    if (parsed == null) {
      return 1;
    }

    Path directory = Path.of(parsed.directory).toAbsolutePath().normalize();
    if (!Files.isDirectory(directory)) {
      System.err.println("Not a readable directory: " + directory);
      return 1;
    }

    Path output = resolveOutput(directory, parsed.output);

    try {
      Set<Path> todo = CandidateScanner.scan(directory);
      if (todo.isEmpty()) {
        if (Files.isRegularFile(output)) {
          Files.delete(output);
          System.out.println(
              "No Gradle projects found under " + directory + "; removed stale " + output);
        } else {
          System.out.println("No Gradle projects found under " + directory);
        }
        return 0;
      }

      List<Path> upToDateInputs = CandidateScanner.markerFiles(todo);
      if (!parsed.force && OutputUpToDate.isUpToDate(output, upToDateInputs)) {
        System.out.println("Up to date: " + output);
        return 0;
      }

      Path pluginJar = PluginJar.materialize();
      try (GradleModelFetcher fetcher = new GradleModelFetcher(pluginJar)) {
        MbtAggregator aggregator = DrainLoop.run(todo, projectDir -> {
          ExtractorModel model = fetcher.fetch(projectDir);
          MbtDocument partial = ProjectMapper.toMbt(directory, model);
          Set<Path> covered = ProjectMapper.coveredProjectDirs(model);
          return new DrainLoop.ImportResult(partial, covered);
        });
        Path written = MbtJsonWriter.write(output, aggregator.toDocument());
        System.out.println("Wrote " + written);
      }
      return 0;
    } catch (Exception e) {
      System.err.println("Gradle import failed: " + e.getMessage());
      e.printStackTrace(System.err);
      return 1;
    }
  }

  static Path resolveOutput(Path directory, String outputArg) {
    Path output = Path.of(outputArg != null ? outputArg : DEFAULT_OUTPUT);
    if (!output.isAbsolute()) {
      output = directory.resolve(output);
    }
    return output.toAbsolutePath().normalize();
  }

  private static ParsedArgs parseArgs(String[] args) {
    String directory = null;
    String output = null;
    boolean force = false;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if ("--output".equals(arg) || "-o".equals(arg)) {
        if (i + 1 >= args.length) {
          System.err.println("Missing value for " + arg);
          printUsage();
          return null;
        }
        output = args[++i];
      } else if ("--force".equals(arg) || "-f".equals(arg)) {
        force = true;
      } else if (arg.startsWith("-")) {
        System.err.println("Unknown option: " + arg);
        printUsage();
        return null;
      } else if (directory == null) {
        directory = arg;
      } else {
        System.err.println("Unexpected argument: " + arg);
        printUsage();
        return null;
      }
    }

    if (directory == null) {
      printUsage();
      return null;
    }

    return new ParsedArgs(directory, output, force);
  }

  private static void printUsage() {
    System.err.println(
        "Usage: java -jar gradle-extractor.jar <directory> [--output <file>] [--force]");
  }

  private record ParsedArgs(String directory, String output, boolean force) {}
}
