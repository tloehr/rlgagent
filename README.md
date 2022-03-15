RLG Agent
=========

* [Configs](#configs)
* [MQTT](#mqtt)
* [Commands](#commands)
   * [Initializing an agent](#initializing-an-agent)
   * [Signals](#signals)
      * [Standard Signal Schemes](#standard-signal-schemes)
   * [Paged Displays](#paged-displays)
      * [Setting the page content](#setting-the-page-content)
      * [Variables and template expressions](#variables-and-template-expressions)
         * [Preset variables](#preset-variables)
         * [Dynamic variables](#dynamic-variables)
         * [Timers](#timers)
      * [Adding a page](#adding-a-page)
      * [Deleting a page](#deleting-a-page)
   * [Matrix Displays (WIP - not implemented)](#matrix-displays-wip---not-implemented)
   * [Additional Subscriptions](#additional-subscriptions)
   * [Events](#events)
---
# Preface
The purpose of the **RLGS** (Real Life Gaming System) is to realize games for **tactical sports** like paintball, airsoft, Nerf or Laser Tag. RLGS adapts well known multiplayer modes from games like Battlefield, Call of Duty, FarCry, Planetside 2 oder Counterstrike to be played in real life.

The RLGS concept consists of two basic elements: the [commander](https://github.com/tloehr/rlgcommander) and one or more agents (this project).

Agents can produce optical and acoustical signals and detect events (currently only the press of a button). They do **not know anything** about why they are flashing LEDs, sounding sirens or why somebody presses their buttons. They completely rely on the commander to tell them what to do. The commander is the only one who keeps track about the game situation.

# Hard- and Software
Agents are supposed to run on Raspberry Pi computers with several input and output devices connected to them. Like LED stripes, sirens (switched by relay boards), push buttons, LCDs etc. But it is also possible to run them on a standard desktop computers (Mac, Windows, Linux). In this case, they start up a Swing GUI to simulate the aforementioned devices on the screen or via the sound card. 

![agent-gui](src/main/resources/docs/agent-gui.png)

We use the [Pi4J](https://pi4j.com/) framework to connect the hardware to our Java source code. The whole framework is about to change drastically with the version 2. But for now we stick to Version 1, which still relies on the now deprecated [WiringPi](http://wiringpi.com/) project, as it runs very well. Please note, that the Pin numbering used in the config files are named according to the WiringPi scheme.

WiringPi is not available in Raspbian anymore or as sourcecode in https://git.drogon.net/?p=wiringPi;a=summary
Until we are moving on to pi4j 2.0 (which based on pigpio), we stick with a source code mirror for WiringPi on GitHub. Which works very well for us.

## Configs
The agent creates a workspace folder startup (if missing). The default location is the **home directory of the particular user**. Inside this folder resides the **config.txt** file, which is a standard Java properties file.  

```
#Settings rlgagent
#Sun Mar 13 20:19:11 CET 2022
btn01=GPIO 3
btn02=GPIO 4
buzzer=GPIO 26
lcd_cols=20
lcd_i2c_address=0x27
lcd_rows=4
led_blu=GPIO 22
led_grn=GPIO 21
led_red=GPIO 1
led_wht=GPIO 2
led_ylw=GPIO 5
loglevel=DEBUG
mcp23017_i2c_address=0x20
mqtt_broker=localhost
mqtt_clean_session=true
mqtt_max_inflight=1000
mqtt_port=1883
mqtt_qos=2
mqtt_reconnect=true
mqtt_retained=true
mqtt_root=rlg
mqtt_timeout=10
myid=ag01
sir1=GPIO 7
sir2=GPIO 0
sir3=GPIO 6
uuid=d0cc8d4e-7a10-4762-8241-cfa79a932653
wifi_cmd=iwconfig wlan0
```

# MQTT
Agents and the Commander communicate through messages handled by the [MQTT](https://en.wikipedia.org/wiki/MQTT) protocol.

Every agent listens to a fixed topic channel:

`rlg/cmd/<agent_id>/#`

With 
- `<game_id>` is a unique key for the current game. Every agent and commander must use the same game id to work together. We will use this prefix just in case we are sharing a MQTT broker with others.
- `agent_id` Every agent must have a unique id within the running group. Id conflicts are **NOT** detected and must be sorted out by the administrator at setup time, when the agent is first run. Set the id in the config.txt file of the agent (e.g. myid=ag01)

In addition to these two default channels, a commander can instruct agents to listen to more topics, so it can combine agent into functional groups like "sirens", "leds", "spawns" etc. These groups are purely defined by the game mode in the commander. Again, the agent itself doesn't have any notion about these topics. It simply listens as being told.

# Commands
Commands are messages received from the commander (obviously). Most of the commands contain parameters as JSON objects stored in the payloads.

## Initializing an agent
When the agent receives the init command it will:
- unsubscribe from additional topics (not the base ones mentioned [above](#topics))
- remove all pages from the [display](#paged-displays), besides **"page0"**
- stops all signals (LEDs, buzzers, sirens)

```
{ "init": ""}
```
There is no payload for the init command.

## Signals
Signals are "on/off" schemes for specified pins of the Raspberry Pi. We use a PCB hat, to connect 12V LED Stripes, relay driven 12V sirens and a 12 V buzzers to send out notifications.

Signal schemes are lists of **on** and **off** times (in milliseconds) for the specific raspi pin. Every list is preceded by the number of repeats. If a scheme should go on forever (until changed), the repeat_count can be replaced by the infinity sign ∞ (in fact, there is no infinity, it is Long.MAX_VALUE, but for our purpose this would take forever). A repeat_count of 0, turns off the signal. Like so: "0:" or the word "off" (which is also understood).

The syntax of the scheme is:

```
<repeat_count>:[on|off],<period_in_ms>;[on|off],<period_in_ms>
```

The agent abstracts devices from their GPIO counterparts on the Raspi. The DEVICE-to-GPIO assignment is stored in the config.txt and can be changed, if You don't use our [PCB](https://easyeda.com/tloehr/rlg-mainboard-v11_copy).

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

Three hardcoded device groups can also be addressed:
- all - All pins.
- led_all - All LEDs
- sir_all - All Sirens (Buzzer not included)

A JSON which turns off the buzzer and then makes it sound two times (75 ms) looks like this:

`{"signal": {buzzer: "off", "2:on,75;off,75"}}`

If we want to let all LEDs blink every second (until further notice), we would send this:

`{"signal": {led_all: "∞:on,1000;off,1000"}}`

We can combine multiple commands into one message. The following example makes all agent repeatedly flash all of their LEDs shortly and then pause for 2,5 seconds. The attached display shows in page 0 the default text. 

```
topic: g1/cmd/all
{
    "signal": {"led_all":"∞:on,250;off,2500"},
    "page_content": {
        "page0": [
                    "Waiting for a game",
                    "cmdr 1.0.1.55",
                    "agnt ${agversion}.${agbuild}",
                    "RLGS2 @flashheart.de"
                 ]
    }
}
```

You may have noted, that there are some template expressions in the display string like ${agversion}. You will find some detailed explanations in the [Displays](#paged-displays) section of this document.

### Standard Signal Schemes
By default, an agent recognizes some standard schemes which are translated locally.

Single signals
- **very_long** &#8594; "1:on,5000;off,1"
- **long** &#8594; "1:on,2500;off,1"
- **medium** &#8594; "1:on,1000;off,1"
- **short** &#8594; "1:on,500;off,1"

Recurring signals
- **slow** &#8594; "∞:on,2000;off,1000"
- **normal** &#8594; "∞:on,1000;off,1000"
- **fast** &#8594; "∞:on,500;off,500"
- **very_fast** &#8594; "∞:on,250;off,250"

Repeated Signals (used mainly for the buzzer - hence the naming)
- **single_buzz** &#8594; "1:on,75;off,75"
- **double_buzz** &#8594; "2:on,75;off,75"
- **triple_buzz** &#8594; "3:on,75;off,75"

## Paged Displays
Agents can handle LCDs driven by the [Hitachi HD44780](https://en.wikipedia.org/wiki/Hitachi_HD44780_LCD_controller) controller chip. LCDs with line/col dimensions of 16x2 and 20x4 are supported. As You can see in the [JavaDoc for MyLCD](https://github.com/tloehr/rlgagent/blob/main/src/main/java/de/flashheart/rlgagent/hardware/abstraction/MyLCD.java), we organize the display output in pages which cycle in order by their addition. Refer to the MyLCD class for more details. Every page is identified by a string handle. Please note that there is always a starting page called **"page0"**, which cannot be removed.

### Setting the page content
Example display command:
```
topic: g1/cmd/all
{
    "page_content": {
        "page0": [
            "FarCry Assault/Rush",
            "Bombtime: 00:15",
            "Respawn-Time: 00:12",
            "Spielzeit: 01:40"
        ],
        "page1": [
            "line1",
            "agnt ${agversion}.${agbuild}",
            "line3",
            "line4"
        ]
    }
}
```

The content of multiple pages can be set with one message. Content exceeding the supported display dimension (e.g. 20x4) will be ignored. Superfluous lines are discarded, exceeding lines are truncated. 

### Variables and template expressions
Template expressions are replaced with their corresponding values. These values can be prefixed by the agent or set dynamically by the commander. [Timers](#timers) are a special case of values. In the above example we used template expressions already.

#### Preset variables
- **wifi** &#8594; the current Wi-Fi signal strength
- **agversion** current software version of the agent
- **agbuild** current software build of the agent
- **agbdate** current software builddate of the agent 

#### Dynamic variables
The commander can set any variable to a specific value to fill out the page templates on the display. The following message:
                                                                                                                    
```
{
    "vars": {
        "var1": "winners",
        "var2": "loosers"
    }
}
```

combined with a display line: `"we have ${var1} and ${var2}"` will result in `"we have winners and loosers"`

#### Timers
Timers are also variables, but they have to be [Long](https://docs.oracle.com/javase/8/docs/api/java/lang/Long.html) values. The agent interprets those values as **remaining time in seconds** and starts to count them down after reception. The corresponding template is replaced by the time in the format `"hh:ss"` and disappears when the time **reaches zero**. 

```
{
    "timers": {
        "remaining": "61"
    }
}
```

The above message will start a timer at 1 minute 1 second. The following Display line: `"timer: ${remaining}` will show up on the LCD as `timer: 01:01` - and counting 

### Adding a page
We can add additional pages to the display output. If the page already exists, the command will be ignored.

```
{
    "add_pages": [
            "score_page",
            "spawn_page"
    ]
}
```

Added pages are removed by the "init" command.

### Deleting a page

```
{
    "del_pages": [
            "score_page",
            "spawn_page"
    ]
}
```

## Matrix Displays (WIP - not implemented)

Matrix displays based on stripes of WS2812 LEDs are planned but not yet implemented.

## Additional Subscriptions
In order to reduce the amount of messages sent in a short time period, we can order agents to subscribe to additional functional channels, like "sirens", "leds" etc.

`{"subscribe_to": "sirens"}`
`{"subscribe_to": "leds"}`

The name of the groups is not preset. The commander can choose the group names as needed.


# Events
The agent currently sends 2 different types of events.
1. Notifications about pressed buttons. The agent handles two buttons, but only button 1 is in use. The second button was implemented for later usage. Example: `{"button_pressed":"btn01"}`
2. Status reports about the current state of the agent, to be considered by the commander module. example:
```
{
    "status":{
        "gameid":"g1",
        "has_rfid":false,
        "agentid":"ag01",
        "wifi":3,
        "has_line_display":true,
        "has_sirens":false,
        "has_sound":false,
        "has_leds":true,
        "has_matrix_display":false,
        "timestamp":"2021-10-11T15:30:49.345767+02:00[Europe/Berlin]"
    }
}
``` 
