#!/usr/bin/env bash

if [ "$EUID" -ne 0 ]
then
  echo "Please run as root"
  exit 1
fi

VERSION=$(curl -s https://api.github.com/repos/AnimatedLEDStrip/terminal-client/releases/latest | grep --color="never" -P '"tag_name":' | cut -d '"' -f 4)

rm -rf /tmp/ledterminal-download
mkdir /tmp/ledterminal-download
cd /tmp/ledterminal-download


echo -n "Creating /usr/local/leds..."

install -d /usr/local/leds

echo "done"


echo -n "Installing ledterminal..."

wget -q https://github.com/AnimatedLEDStrip/terminal-client/releases/download/${VERSION}/animatedledstrip-terminal-client-${VERSION}.jar

mv animatedledstrip-terminal-client-${VERSION}.jar /usr/local/leds/ledterminal.jar

wget -q https://raw.githubusercontent.com/AnimatedLEDStrip/terminal-client/master/install/ledterminal.sh

install -m 755 ledterminal.sh /usr/local/leds/ledterminal.sh

chmod 755 /usr/local/leds/ledterminal.sh

ln -f -s /usr/local/leds/ledterminal.sh /usr/bin/ledterminal

echo "done"


rm -rf /tmp/ledterminal-download

echo "AnimatedLEDStrip LED Terminal installed successfully"
