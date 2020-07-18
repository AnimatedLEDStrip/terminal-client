# AnimatedLEDStrip Terminal Client

The AnimatedLEDStrip Terminal Client allows a connection via a terminal.
You can view information about the strip, view information about running animations, end currently running animations, and more.

## Installation

To install, run

```bash
curl -s https://animatedledstrip.github.io/install/install-terminal.sh | sudo bash
```

This will install the terminal, which can be run with `ledterminal`.

## Usage

*Commands can be shortened as much as you like as long as they still indicate a unique command.*

To run a command, type it in and press enter.
It will then be passed along to the server (if it isn't a command defined by the terminal).

To connect to a server, run `terminal connect [IP [PORT]]`.

To view what commands are possible, run `help`.
If you are connected to a server, it will show help for the terminal as well as for the server.
