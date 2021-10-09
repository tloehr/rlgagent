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

