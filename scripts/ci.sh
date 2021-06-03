#!/usr/bin/env bash

set -e

export NVM_DIR="$HOME/.nvm"
[[ -s "$NVM_DIR/nvm.sh" ]] && . "$NVM_DIR/nvm.sh"  # This loads nvm

nvm install
npm install -g grunt-cli
npm install -g jspm #@0.16.55

npm install

grunt --stack validate test
sbt compile test assets
grunt --stack bundle
sbt riffRaffUpload



