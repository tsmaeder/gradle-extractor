# Agents.md

## Mission

The point of this project is to make an extractor executable jar for projects in gradle project into
mbt.json files. The project is for use in the javals language server (see ..\java-ls).
The idea is to implement a custom model builder and to invoke it via an init script. The approach for
generating workspace mbt.json's is the same as in maven-extractor: find all project candidates
and remove those locations that are part of the build from the todo until none are left under the
workspace directory.

## Rules

Do not preseve backwards compatibility. Remove obsolete paths instead of adding compatilibity layers, fallbacks or migrations.

New files are copyright by Castle Ridge Software and no license

