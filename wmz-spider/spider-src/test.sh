#!/usr/bin/env bash
# 桌面联调：编译 Test 并跑指定蜘蛛
# 用法: bash test.sh csp_AppYsV2 '' home
set -e
cd "$(dirname "$0")"
bash build.sh > /dev/null
mkdir -p build/test
javac -encoding UTF-8 -nowarn -cp "build/stubs;build/classes" -d build/test test/Test.java
java -Dfile.encoding=UTF-8 -cp "build/stubs;build/classes;build/test" Test "$@"
