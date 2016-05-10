#!/bin/bash

set -euo pipefail

function installTravisTools {
  mkdir ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v28 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}

installTravisTools

case "$TEST" in

ci)
  SONAR_PROJECT_VERSION=`maven_expression "project.version"`

  # Do not deploy a SNAPSHOT version but the release version related to this build
  set_maven_build_version $TRAVIS_BUILD_NUMBER

  # the profile "deploy-sonarsource" is defined in parent pom v28+
  mvn deploy \
      -Pdeploy-sonarsource \
      -B -e -V
  ;;
*)
  echo "Unexpected TEST mode: $TEST"
  exit 1
  ;;

esac
