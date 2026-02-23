#!/bin/bash

path=$(pwd)
cd ../../WebstormProjects/ultunnel-front || exit

npm install
npm run build

if [ -d "./dist/" ]; then
    cp -r "./dist/"* "$path/src/main/resources/static"
else
    echo "Coping error" >&2
    exit 1
fi