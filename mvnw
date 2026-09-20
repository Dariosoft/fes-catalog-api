#!/bin/sh
set -eu
MAVEN_VERSION=3.9.16
MAVEN_HOME="${HOME}/.m2/wrapper/apache-maven-${MAVEN_VERSION}"
if [ ! -x "${MAVEN_HOME}/bin/mvn" ]; then
  mkdir -p "${HOME}/.m2/wrapper"
  curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" | tar -xz -C "${HOME}/.m2/wrapper"
fi
exec "${MAVEN_HOME}/bin/mvn" "$@"
