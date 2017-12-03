# mqtt-timer

Publish MQTT timer messages for testing etc.


## Build

```bash
git clone https://github.com/veita/mqtt-timer.git
cd mqtt-timer
./gradlew clean assembleShadowDist
```


## Usage

```
mqtt.timer.MqttTimerMain --uri <uri> [OPTION]...

  --uri <uri>              the server URI, e.g. tcp://localhost:1883
  --user <user name>       user name for authentication
  --pass <password>        password authentication
  --freq <frequency>       supported values are secondly (default),
                           minutely, hourly, or daily, hundredmsly,
                           tenmsly
  --topic <topic name>     an optional topic (default is timer/<frequency>)
  --start-delay <num>      start delay in milliseconds before the
                           connection to the server is established
  --silent                 do not write sent timestamps to stdout
  --help, -h               show this help
```

Example

```bash
java -jar build/libs/mqtt-timer-all.jar --uri tcp://localhost:1883 --topic timer/secondly --freq secondly
```
