#!/bin/sh
set -e

cd "$(dirname "$0")/.."
./gradlew :core:installJvmDist
exec ./core/build/install/core-jvm/bin/core
