#!/bin/sh
APP_HOME=$(cd "$(dirname "$0")" && pwd)
exec /home/user/gradle-8.2/bin/gradle "$@"
