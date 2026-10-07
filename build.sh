#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Adapted on 2026-10-07 from the GPL-2.0-or-later Modifyworld build script.
# Uses persistent shared JDK 25/Maven 3.9.11 tools without installing them.
# COMMANDBULKLOAD_TOOLS_DIR selects their common directory. Individual JAVA_HOME
# and MAVEN_HOME overrides below take precedence. Maven normally uses ~/.m2.
# Usage: ./build.sh -B -o clean verify (cached dependencies), or ./build.sh -version.
# Explicit arguments replace the default -B clean verify. Mockito is loaded by pom.xml.
set -eu

tools_dir=${COMMANDBULKLOAD_TOOLS_DIR:-"$HOME/.local/share/minecraft-devtools"}
build_java_home=${COMMANDBULKLOAD_JAVA_HOME:-"$tools_dir/jdk-25.0.4.1+1/Contents/Home"}
build_maven_home=${COMMANDBULKLOAD_MAVEN_HOME:-"$tools_dir/apache-maven-3.9.11"}

if [ ! -x "$build_java_home/bin/java" ] || [ ! -x "$build_java_home/bin/javac" ]; then
    echo "JDK not found at $build_java_home. Set COMMANDBULKLOAD_JAVA_HOME to a JDK 25 installation." >&2
    exit 1
fi
if [ ! -x "$build_maven_home/bin/mvn" ]; then
    echo "Maven not found at $build_maven_home. Set COMMANDBULKLOAD_MAVEN_HOME to a Maven 3.9.11 installation." >&2
    exit 1
fi

export JAVA_HOME="$build_java_home"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

"$JAVA_HOME/bin/java" -version
"$build_maven_home/bin/mvn" -version
if [ "$#" -eq 0 ]; then
    set -- -B clean verify
fi
exec "$build_maven_home/bin/mvn" "$@"
