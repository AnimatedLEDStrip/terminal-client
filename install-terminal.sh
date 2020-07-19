#!/usr/bin/env bash

#   Copyright (c) 2020 AnimatedLEDStrip
#
#   Permission is hereby granted, free of charge, to any person obtaining a copy
#   of this software and associated documentation files (the "Software"), to deal
#   in the Software without restriction, including without limitation the rights
#   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
#   copies of the Software, and to permit persons to whom the Software is
#   furnished to do so, subject to the following conditions:
#
#   The above copyright notice and this permission notice shall be included in
#   all copies or substantial portions of the Software.
#
#   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
#   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
#   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
#   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
#   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
#   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
#   THE SOFTWARE.

if [ "$EUID" -ne 0 ]
then
  echo "Please run as root"
  exit 1
fi


echo -n "Determining latest version..."

VERSION=$(curl -s https://api.github.com/repos/AnimatedLEDStrip/terminal-client/releases/latest | grep --color="never" -P '"tag_name":' | cut -d '"' -f 4)

if [[ -z "$VERSION" ]]
then
  echo "Could not determine latest version, aborting"
  exit 1
else
  echo "$VERSION"
fi


echo -n "Checking for java..."

if ! command -v java &> /dev/null
then
  echo "not found"
  echo "Please install java"
  exit 1
else
  echo "found"
fi


rm -rf /tmp/ledterminal-download
mkdir /tmp/ledterminal-download
cd /tmp/ledterminal-download


echo -n "Creating /usr/local/leds..."

if [[ -d /usr/local/leds ]]
then
  echo "exists"
else
  install -d /usr/local/leds
  echo "done"
fi


echo -n "Downloading ledterminal..."

wget -q https://github.com/AnimatedLEDStrip/terminal-client/releases/download/${VERSION}/animatedledstrip-terminal-client-${VERSION}.jar

echo "done"


echo -n "Installing ledterminal..."

mv animatedledstrip-terminal-client-${VERSION}.jar /usr/local/leds/ledterminal.jar

wget -q https://raw.githubusercontent.com/AnimatedLEDStrip/terminal-client/master/install/ledterminal.sh

install -m 755 ledterminal.sh /usr/local/leds/ledterminal.sh

chmod 755 /usr/local/leds/ledterminal.sh

ln -f -s /usr/local/leds/ledterminal.sh /usr/bin/ledterminal

echo "done"


rm -rf /tmp/ledterminal-download

echo "AnimatedLEDStrip LED Terminal installed successfully"
