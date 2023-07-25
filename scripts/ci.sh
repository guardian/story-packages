#!/usr/bin/env bash

set -e

export NVM_DIR="$HOME/.nvm"
[[ -s "$NVM_DIR/nvm.sh" ]] && . "$NVM_DIR/nvm.sh"  # This loads nvm

nvm install
npm install -g grunt-cli
npm install -g jspm@0.16.55

npm install
jspm config registries.github.auth ${JSPM_GITHUB_AUTH_SECRET}
jspm config registries.github.remote https://github.jspm.io
jspm config registries.github.handler jspm-github
jspm registry export github
jspm install

grunt --stack validate test
grunt --stack bundle
sbt compile test assets
sbt riffRaffUpload
