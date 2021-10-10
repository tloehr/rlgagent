# RLG Agent
This project is part of the RLG (Real Life Gaming) System. Its purpose is to bring known (and hopefully) new game modes to the paintball / airsoft / LARP fields.

Agents do **not know** anything about the current state of a game situation, they completely rely on the command messages sent by the [RLG Commander](https://github.com/tloehr/rlgcommander). The latter keeps track about the states of all the agents during a running game.

They are supposed to run on Raspberry Pi computers with several input and output devices connected to them. Like LED stripes, sirens (switched by relay boards), pushbuttons, LCD Displays etc. But its also possible to run them on a standard desktop computer. In this case, they fire up a Swing GUI to simulate the aforementioned devices on the screen or via the sound card. 

![agent-gui](src/main/resources/docs/agent-gui.png)

We use the [Pi4J](https://pi4j.com/) toolkit to connect the hardware to our Java source code. The whole framework is about to change drastically with the version 2. But for now we stick to Version 1 which still relies on the deprecated [WiringPi](http://wiringpi.com/) project, as it runs very well. Please note, that the Pin numbering used in the config files are named according to the WiringPi scheme. 
## Configs
The agent creates a workspace folder at startup, IF it is missing. The default location is the **home directory of the particular user**.

The configuration is a standard Java properties file.  

![agent-gui](src/main/resources/docs/config-txt.png)

## MQTT
Agents and the Commander communicate through messages handled by [MQTT](https://en.wikipedia.org/wiki/MQTT).

### Topics
Every agent listens to two fixed topics, which are dynamically created like:

1. `<game_id>/cmd/<agent_id>/#`
2. `<game_id>/cmd/all/#`

With 
- `<game_id>` is a unique key for the current game. Every agent and commander must use the same game id to work together.
- `agent_id` Every agent must have a unique id within the running group. Id conflicts are **NOT** detected and must be sorted out by the administrator at setup time, when the agent is first run.

In addition to the two standard topics, a commander can instruct agents to listen to more topics, so it can combine agent to more "logic groups".

### Commands
Commands are MQTT messages received from the commander module. The details are sent in the payload part as JSON Objects.

#### Signals
Signals are LED blinking schemes, siren sounds and buzzer notifications. Signal schemes are lists of **on** and **off** times (in milliseconds) for the specific raspi pin. Every list is preceded by the number of repeats. If a scheme should go on forever (until changed), the repeat number can be replaced by the infinity sign ∞.

The following devices are recognized:
- buzzer
- led_wht
- led_red
- led_ylw
- led_grn
- led_blu
- sir1
- sir2
- sir3

Two "group" devices can also be addressed.
- led_all - All LEDs
- sir_all - All Sirens (Buzzer not included)

Use "0:" or the word "off" to turn off a scheme.

A JSON which turns off the buzzer and then let it sound two times (75 ms) looks like this:

`{"signal": {buzzer: ["off", "2:on,75;off,75"]}}`

If we want to turn off all LEDs and let them blink every second until further notice we would send this:

`{"signal": {led_all: ["off", "∞:on,1000;off,1000"]}}`

We can combine several pin scheme commands in one signal complex: 

`{"signal": {led_all: ["off", "∞:on,1000;off,1000"], {buzzer: ["off", "2:on,75;off,75"]}}}`

#### Displays

##### Line Display
Agents can handle LCD displays driven by the [Hitachi HD44780](https://en.wikipedia.org/wiki/Hitachi_HD44780_LCD_controller) controller chip. LCDs with line/col dimensions of 16x2 and 20x4 are supported.

To change the display output, use the following command syntax: `{"line_display": ['Rot: 123','Blau: 90']}'`

##### Matrix Displays
Matrix displays based on stripes of WS2812 LEDs are planned but not yet implemented.

### Events
