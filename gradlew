#!/bin/sh
APP_HOME=$(cd "$(dirname "$0")" && pwd)
exec /sandboxdata/workspace/file/DshAndroid/gradle-8.2/bin/gradle "$@"
