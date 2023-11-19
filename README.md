RLG Agent
=========

---
# Preface
The purpose of the **RLGS** (Real Life Gaming System) is to organize games for **tactical sports** like paintball, airsoft, Nerf or Laser Tag.

The RLGS concept consists of two basic elements: the [commander](https://github.com/tloehr/rlgcommander) and one or more
**agents** (this project).

Agents can produce optical and acoustical signals and detect events (currently only the press of a button). They do **not know anything** about the current game state. They are just doing as they are told by the commander. The commander is the only instanceto keep track about the game
situation.

The concept of an agent contains the following in- and output devices.

- 5 light signals, colored in "white, red, yelllow, green, blue"
- 4 Sirens (or relays)
- a buzzer for local player feedback
- 2 Buttons for player interaction (currently only 1 Button is used)
- and a 20x4 [Hitachi HD44780](https://en.wikipedia.org/wiki/Hitachi_HD44780_LCD_controller) based text LCD.

An agent **does not have to be fully equipped** with every device possible. Nevertheless, the software understands all signals, even if the addressed device is not connected. In this
case the signal is accepted but ignored.

# Installation
The agent is written in Java and therefor available on nearly any platform. There are ready-made packages for Windows, Mac, Linux and Raspbian. The Raspbian version is only available as a service or demonized version. The Linux version is distributed via a package repository at flashheart.de. Refer to the [download page](https://www.flashheart.de/doku.php/de:downloads#linux) for more information.

# Hard- and Software

The Agent-Software usually runs on a Raspberry Pi computer with several input and output devices connected to them. Like LED stripes, sirens (switched by relay boards), push buttons, LCDs etc. But it is also possible to run them on a standard desktop computers (Mac, Windows, Desktop Linux). In this case, they start up with a graphical user interface to simulate the aforementioned devices on the screen or via the sound card.

![agent-gui](src/main/resources/docs/agent-gui.gif)

We use the [Pi4J](https://pi4j.com/) framework to connect the hardware to our Java source code. The whole framework is
about to change drastically with the version 2. But for now we stick to Version 1, which still relies on the now
deprecated [WiringPi](http://wiringpi.com/) project, as it runs very well. Please note, that the Pin numbering used in
the config files are named according to the WiringPi scheme.

WiringPi is not available in Raspbian anymore or as sourcecode. Until we are moving on to pi4j 2.0 (which based on pigpio), we stick with a source code mirror for WiringPi on GitHub. Which works very well for us. A stable deb version of WiringPi is also available via our APT repository.

There is also a standard PCB which works best for a Raspberry Pi setup. You can get Your
own [PCBs here](https://easyeda.com/tloehr/rlg-mainboard-v11_copy).

# Workspace

Upon start the agent software creates a workspace folder (if missing). The folder's location **has to be specified via a -D argument** on the java command line.

`java -jar -Dworkspace=/home/pi/rlgagent`

The standard installation packages contain this setting in the `rlgagent.vmoptions` file located in the installation folder.

* Linux: `/opt/rlgagent` or `/opt/rlagentd`. The latter for an installation as a service or deamon.
* Mac: `/Applications/rlgagent`

The workspace folder contains the `config.txt` file, the log file directory and the mqtt persistence folder. It usually looks something like this:

```
├── ag30-026bae8b-2b63-4578-ab87-240046d7d3bb-tcplocalhost1883
├── audio
│   ├── announce
│   │   ├── countdown.mp3
│   │   ├── defeat.mp3
│   │   ├── doublekill.mp3
│   │   ├── enemydoublekill.mp3
│   │   ├── enemypentakill.mp3
│   │   ├── enemyquadrakill.mp3
│   │   ├── enemyspree.mp3
│   │   ├── enemytriplekill.mp3
│   │   ├── firstblood.mp3
│   │   ├── overtime.mp3
│   │   ├── pentakill.mp3
│   │   ├── quadrakill.mp3
│   │   ├── selfdestruct.mp3
│   │   ├── shutdown.mp3
│   │   ├── spree.mp3
│   │   ├── triplekill.mp3
│   │   ├── victory.mp3
│   │   └── welcome.mp3
│   ├── intro
│   │   └── bf3.mp3

│   └── pause
│       ├── aquatic-ambiance.mp3
│       ├── dire-docks.mp3
│       ├── frosty-frolics.mp3
│       ├── snes-waterworld.mp3
│       └── stickerbush-symphony.mp3
├── config.txt
└── logs
    ├── 2023-07-16.rlgagent.log.gz
    ├── 2023-08-04.rlgagent.log.gz
    └── rlgagent.log
```

## Logfiles

Logfiles are stored in the **logs** subdirectory. The agent archives a log file from a previous day on startup (see
above). The current file is always named `rlgagent.log`

## Configs

The **config.txt** is a standard Java properties file. If it is missing either single entries or completely, it will be
created and filled up with the following default settings:

```properties
#Settings rlgagent v1.5b6
#Wed Jun 21 14:52:29 CEST 2023
#
# UI settings
#
selected_tab=0
frameHeight0=350
frameHeight1=607
frameHeight2=420
frameLocationX=2795
frameLocationY=126
frameWidth0=239
frameWidth1=125
frameWidth2=192
#
# hardware settings and addresses
# defaults to the RLGAgent PCB
#
blu=GPIO 22
btn01=GPIO 3
btn02=GPIO 4
button_debounce=200
buzzer=GPIO 26
grn=GPIO 21
lcd_cols=20
lcd_i2c_address=0x27
lcd_rows=4
led_blu=GPIO 22
led_grn=GPIO 21
led_red=GPIO 1
led_wht=GPIO 2
led_ylw=GPIO 5
red=GPIO 1
wht=GPIO 2
ylw=GPIO 5
sir1=GPIO 7
sir2=GPIO 0
sir3=GPIO 6
sir4=GPIO 23
# Only if a port expander is used.
mcp23017_i2c_address=0x20
#
# multimedia settings
#
mpg321_bin=
mpg321_options=
#
# MQTT settings
#
mqtt_broker=localhost
mqtt_clean_session=true
mqtt_max_inflight=1000
mqtt_port=1883
mqtt_qos=1
mqtt_reconnect=true
mqtt_retained=true
mqtt_root=rlg
mqtt_timeout=10
#
# network settings
#
network_monitor_interval_in_seconds=10
networking_monitor_disconnect_after_failed_pings=3
ping_timeout=500
ping_tries=5
ip_address_cmd=ip a s wlan1|grep -i 'inet '|awk '{print $2}'
trigger_on_high_sir1=true
trigger_on_high_sir2=true
trigger_on_high_sir3=true
trigger_on_high_sir4=true
wifi_cmd=iwconfig wlan1
#
# MISC settings
#
myid=ag01
# can be increased to TRACE or decreases to INFO or OFF
loglevel=DEBUG
status_interval_in_seconds=60
# line will be recreated with a random value, if missing in the config file
uuid=b2135176-d4e8-4250-823f-761291fa79b3
```

- `loglevel` the verbosity of the log file. possible values: **OFF, DEBUG, TRACE, INFO, ERROR, WARN**
- `uuid` a unique id which is used as part of the client id to connect to the MQTT broker. Will be created on startup,
  if missing. To get a new uuid on next startup, simply delete this line. **NEEDS TO BE UNIQUE WITHIN THE RLGS SETUP**
- `myid` the agent name to be used. The default is **ag01**. **NEEDS TO BE UNIQUE WITHIN THE RLGS SETUP**
- `led_wht, led_red, led_ylw, led_grn, led_blu, sir1, sir2, sir3, sir4, btn01, btn02, buzzer` the Raspi GPIO pin for the
  corresponding devices (Wiring Pi numbering scheme). Default sets the assignment to the standard
  agent [PCB](https://easyeda.com/tloehr/rlg-mainboard-v11_copy).
- `lcd_cols, lcd_rows` dimensions for the LCD.
- `lcd_i2c_address` address on the i2c bus for the connected LCD
- `mcp23017_i2c_address` if a MCP23017 port extender is used, this is the address to find it on the i2c bus
- `mqtt_broker` Space separated list of brokers. The agent tries to connect to the entries in this list - one by one. If
  the connection breaks during the game, the agent keeps trying to reconnect again.
- `mqtt_clean_session` settings for paho client
    - [see here](https://www.eclipse.org/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttConnectOptions.html#setCleanSession-boolean-)
- `mqtt_clean_session` settings for paho client
    - [see here](https://www.eclipse.org/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttConnectOptions.html#setCleanSession-boolean-)
- `mqtt_port` port address for the broker
- `mqtt_qos` [quality of service](https://en.wikipedia.org/wiki/MQTT#Quality_of_service) to be used for the event
  messages sent by this agent from 0 to 2. Where 0 is at most once, 1 at least once, 2 exactly once.
- `mqtt_max_inflight` the maximum number of messages in transit and not yet delivered.
- `mqtt_reconnect` settings for paho client
    - [see here](https://www.eclipse.org/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttConnectOptions.html#setAutomaticReconnect-boolean-)
- `mqtt_retained` sets whether the event messages from this agent should
  be [retained](https://www.hivemq.com/blog/mqtt-essentials-part-8-retained-messages/).
- `mqtt_root` the root element of the message topics to subscribe and send to. Keep the defaults.
- `mqtt_timeout` settings for paho client - [see here](settings for paho client
    - [see here](https://www.eclipse.org/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttConnectOptions.html#setConnectionTimeout-int-)
- `wifi_cmd` only for Raspberry Pis. command line to be execute every 5 seconds. The results are parsed and sent to the
  commander via a status event message.
- `frameHeight[0-2], frameWidth[0-2], frameLocation¢[X|Y]` **Only for UI usage.** Dimensions and upper left corner position of the agent's desktop window. Dimension 0-2 are different for the full, flag and siren tab.
- `selected_tab` **Only for UI usage.** Last selected tab number.
- `ip_address_cmd` shell command line that will retrieve the current ip address of the agent. Will be used in the status message to the commander.
- `status_interval_in_seconds` the interval in seconds when the agent sends a status message to the commander.
- 
# Messaging

The commander and the agents communicate via a [MQTT](https://en.wikipedia.org/wiki/MQTT) broker.

Every agent has its own command and event channel.

- **inbound command channel:** `/<mqtt_root>/cmd/<agent_id>/#` will provide orders from the commander to the agent
- **outbound event channel:** `/<mqtt_root>/evt/<agent_id>/#` will be used to send information about what happened to
  the agent. Like button presses or network connectivity.

Where

- `mqtt_root`
- `agent_id`

are both defined in **configs.txt**

**EXAMPLE:** for a default installation these two channels are

- **inbound command channel:** `/rlg/cmd/ag01`. Commands (sub-channels) can be: **acoustic, visual, play, paged, timers, vars**.
  Example: a command to switch off all LEDs would be sent to `/rlg/cmd/ag01/visual` with this JSON
  payload `{"led_all":"off"}`
- **outbound event channel:** `/rlg/evt/ag01`. Events (sub-channels) can be: **btn01, btn02, state**.

# Commands

Every command is sent to its own sub-topic below the agent's command channel. Signals to ag01 are either sent
to `/rlg/cmd/ag01/visual` or `/rlg/cmd/ag01/acoustic`, LCD content to `/rlg/cmd/ag01/paged` etc. The examples below are written with these default
settings in mind.

## Signals

Topics: `/rlg/cmd/ag01/visual` or `/rlg/cmd/ag01/acoustic`

Signals are sent out by the agent optically (LEDs) or acoustically (Sirens, Buzzer). In the end, they all reach the same pin handler, but the acoustic topic is not **retained**. The latter config ensures, that a siren is not repeating the last signal if it is reconnecting on the field. This would confuse the players.

### Schemes

Signal schemes are defined as JSON objects. The following scheme turns off all LEDs first, then set the blue LED to flash 5 times quickly and the red LED 3 times a bit slower. In the **scheme** array all values are times in milliseconds. Negative means **PIN on LOW**, positive means **PIN on HIGH**.

```json
{
  "led_all": "off",
  "blu": {
    "repeat": 5,
    "scheme": [
      50,
      -50
    ]
  },
  "red": {
    "repeat": 3,
    "scheme": [
      1000,
      -500
    ]
  }
}
```

A negative **repeat** value stands for **forever** (until overwritten by a new command). In fact is **Long.MAX_VALUE**, but for our purpose this would take forever). To simply turn off a PIN, we can send the "off" value as a scheme.

The **available device keys** are:
* wht, red, ylw, grn, blu
* sir1, sir2, sir3, sir4
* buzzer


### Devices

The agent abstracts devices from their GPIO counterparts on the Raspi. The following devices are
recognized: `led_wht, led_red, led_ylw, led_grn, led_blu, sir1, sir2, sir3, btn01, btn02, buzzer`. "sir" stands for
siren. So the meaning of this list should be pretty obvious.

There are 3 device groups:

- `led_all` → `led_wht, led_red, led_ylw, led_grn, led_blu`
- `sir_all` → `sir1, sir2, sir3`

**Examples:**

A signal which causes the agent to buzz two times (75 ms) would have a payload like this:

```json
{
  "buzzer": {
    "repeat" : 2,
    "scheme" : [75,-75]        
  }
}
```

If we want all LEDs to blink every second (until further notice), we would send this:

```json
{
  "led_all": {
    "repeat": -1,
    "scheme": [
      1000,
      -1000
    ]
  }
}
```

or in short (using a standard signal scheme - see below):

```json
{
  "led_all": "normal"
}
```

We can combine multiple payloads into one message. Also for the other commands, not only signals.

```json
{
  "led_all": {
    "repeat": -1,
    "scheme": [
      250,
      -2500
    ]
  },
  "sir1": "long"
}
```

Even though this may be a bad example, because usually we are not combining acoustic and visual commands.

Devices (like LEDs or sirens) connected to these pins via a MOSFET transistors or Relays are switched on and off accordingly.

### Standard signal schemes

By default, an agent recognizes some standard schemes which are translated locally. In fact, the commander makes extensive use of these "macros", as they cover most of its needs. See the file [scheme_macros.json](src/main/resources/scheme_macros.json) for more details.

### Dynamic signal schemes
In contrast to static schemes, the agents can also handle dynamic signalling. We can show the remaining time as a progress bar running from 0% to 100%.

#### Progress
If we want to use the progress function to show a timer value, all LEDs are needed. 
```json
{
  "progress": "remaining"
}
```

## Audio Output
Topic: `/rlg/cmd/ag01/play`

The commander can instruct agents to play MP3 sound files via the `mpg321` player. There are 5 different "sound channels" handled by the agent.

- music
- voice1
- voice2
- sound1
- sound2

The agent doesn't care what type of sound is played via the channels. It's a logical separation to clear up things. The sound files must be located in the agents `audio` subfolder.

```json
{
  "channel": "music",
  "subpath":"intro",
  "soundfile": "sirius"
}
```

## Display Pages

Topic: `/rlg/cmd/ag01/paged`

Agents can handle LCDs driven by the [Hitachi HD44780](https://en.wikipedia.org/wiki/Hitachi_HD44780_LCD_controller)
controller chip. LCDs with line/col should have a text screen dimension of 20x4. As You can see in
the [JavaDoc for MyLCD](https://github.com/tloehr/rlgagent/blob/main/src/main/java/de/flashheart/rlgagent/hardware/abstraction/MyLCD.java), the display output is organized in pages, which cycle in order by their addition. Refer to the MyLCD class for more details.

Every screen page is identified by a string handle. Please note that there is always a starting page called **`page0`**, which cannot be removed.

### Setting the page content

The following payload will set the content of 2 pages. A new page will be created automatically when needed. It is also automatically removed, when this new page is missing from a later page content command.

```json
{
  "page1": [
    "   >>> BLUE  <<<   ",
    "${blue_l1}",
    "${blue_l2}",
    "Red->${red_tickets}:${blue_tickets}<-Blue"
  ],
  "page0": [
    "   >>> RED   <<<   ",
    "${red_l1}",
    "${red_l2}",
    "Red->${red_tickets}:${blue_tickets}<-Blue"
  ]
}
```

Text which exceeds the supported display dimension (e.g. 20x4) will be truncated.

As You can see, we used template expressions in the last example like "${blue_l1}". These expressions refer to a
variable [(see the next section)](#variables-and-template-expressions) and will be replaced by the bound variable
content. The variable content is updated every time the page is displayed.

There also timer variables which are always counted down, even when the page is currently displayed. So You can display
a running timer, even when there is only one page to be displayed.  [(see timers)](#timers)

### Variables and template expressions

Topic: `/rlg/cmd/ag01/vars`

Template expressions are replaced with their corresponding values. These values can be prefixed by the agent or set
dynamically by the commander. [Timers](#timers) are a special case of values. In the above example we used template
expressions already.

You may have noted, that there are some template expressions in the display string like ${agversion}. You will find some
detailed explanations in the [Displays](#paged-displays) section of this document.

#### Preset variables

- **wifi** → the current Wi-Fi signal strength
- **ssid** → ssid of the Wi-Fi connected to.
- **agversion** → current software version of the agent
- **agbuild** → current software build of the agent
- **agbdate** → current software build-date of the agent

#### Dynamic variables

The commander can set any variable to a specific value to fill out the page templates on the display, as described
above. Example message as generated by the Conquest class.

```json
{
  "red_l2": "",
  "blue_tickets": "250",
  "blue_l1": "",
  "red_l1": "",
  "blue_l2": "",
  "red_tickets": "250"
}
```

#### Timers

Topic: `/rlg/cmd/ag01/timers`

Timers are also variables, but they have to be [Long](https://docs.oracle.com/javase/8/docs/api/java/lang/Long.html)
values. The agent interprets those values as **remaining time in seconds** and starts to count them down after
reception. The timer template is replaced by the time in the format ``hh:ss`` and disappears when the time **reaches
zero**.

```json
{
  "remaining": 61
}
```

The above message will start a timer at 1 minute 1 second. A display line:

```json
{
  "timer": "${remaining}"
}
```

will show up on the LCD as `timer: 01:01` - and counting

# Events

Events are something that happens to or on the agent. They are reported to the commander.

## Buttons

Topic: `/rlg/evt/ag01/btn01` or `/rlg/evt/ag01/btn02`

The use of a button is divided into two separate events:

1. The first one reporting that the button is **pressed down**: `{"button":"down"}`
2. the second one when the button is **released again**: `{"button":"up"}`

## Status

Topic: `/rlg/evt/ag01/status`

Every **60 seconds** an agent reports its current status to the commander. Very important to tell, whether all agents
are working correctly during a match.

```json
{
  "mqtt-broker": "localhost",
  "netmonitor_cycle": 96,
  "wifi": "PERFECT",
  "mqtt_connect_tries": 1,
  "essid": "!DESKTOP!",
  "last_ping": "16.03.22, 15:06:27",
  "link": "--",
  "freq": "--",
  "ping_max": "0.044",
  "bitrate": "--",
  "version": "1.0.1.387",
  "ap": "!DESKTOP!",
  "txpower": "--",
  "ping_success": "ok",
  "powermgt": "--",
  "ping_loss": "0%",
  "ping_min": "0.044",
  "ping_avg": "0.044",
  "signal": "-30",
  "ping_host": "localhost",
  "timestamp": "2022-03-16T15:06:27.107081+01:00[Europe/Berlin]"
}
``` 
# Other people's material
<a href="https://www.flaticon.com/free-icons/buzzer" title="buzzer icons">Buzzer icons created by Freepik - Flaticon</a>

<a href="https://www.flaticon.com/de/kostenlose-icons/volumen" title="volumen Icons">Volumen Icons erstellt von apien - Flaticon</a>
