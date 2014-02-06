# Dodedodo Android
The dodedodo app starts, stops and connects [AIM modules](http://dobots.github.io/aim/).

## For users
First create a [dodedodo](http://www.dodedodo.com/) account, then login on this app. You can then use the website to drag and drop modules on your phone and connect them. Each modules is a seperate app you will have to install, the dodedodo app will ask you to do this.
The dodedodo app will keep on running in the background, you will see a notification while it is running.
If things seem broken, try stopping all modules (also check running services) and then restart the dodedodo app.

## For developers
To make your own module, there are several ways to go:
* Use the [AIM tools](http://dobots.github.io/aim/), this is handy when your module needs no UI and only uses header-only libraries.
* Use the [AIM android library](https://github.com/dobots/android_aim_library), best when you want a GUI or when the app requires android specific code.
* Comply to the AIM android protocol, as described below.

#### Deployment
A module needs a deployment file that describes the module to dodedodo. This module should be located at "module_dir/aim-core/aim_deployment.json". It should contain the following:
```json
{
	"name":"MyModule",
	"type":"background",
	"version":"1",
	"category":"Image processing",
	"description":"short description",
	"long_description":"longer description goes here",
	"supported_middleware":[ "android" ],
	"supported_devices":[ "android" ],
	"enable":"true",
	"android": {
		"package":"org.dobots.mymodule",
		"url":"https://play.google.com/store/apps/details?id=org.dobots.mymodule"
	},
	"ports": [
		{
			"name":"bmp",
			"dir":"in",
			"type":"intarray",
			"middleware":"default"
		},
		{
			"name":"jpg",
			"dir":"out",
			"type":"string",
			"middleware":"default"
		}
	]
}
```

Attribute | Description
---|---
type | Either "background" or "UI". If type is UI, it means the module requires a UI to work.
version | A positive integer number, increase it each time you update your module.
supported_middleware | Since you're making an android app, only android is supported.
supported_devices | Since you're making an android app, only android is supported.
enable | Either "true" or "false". If set to true, this module will be added to the dodedodo website.
ports | A list of input and output ports of your module.
port name | Should be lower case
port dir | Either "in" or "out"
port type | Can be "string", "int", "intarray", "float" or "floatarray"
port middleware | Should be "default"


Here are some examples: [PictureSelectModule](https://github.com/vliedel/aim_modules/blob/master/PictureSelectModule/aim-core/aim_deployment.json) [BmpToJpgModule](https://github.com/vliedel/aim_modules/blob/master/BmpToJpgModule/aim-core/aim_deployment.json)


#### Starting
A module will be started by dodedodo by calling an intent with action "ACTION_RUN" in category "CATEGORY_DEFAULT". This means your AndroidManifest.xml should contain:
```
</intent-filter>
	<action android:name="android.intent.action.RUN" />
	<category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```
in the service or activity that should be started.

When a user clicks on your module in the module list, dodedodo will call an intent with action "ACTION_MAIN" in category "CATEGORY_DEFAULT". This means your AndroidManifest.xml should contain:
```
<intent-filter>
	<action android:name="android.intent.action.MAIN" />
	<category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```
in the activity of the module.

##### On create / On StartCommand
A module that's started as activity will get the module id *on create*, while background module will get it *onStartCommand*. You can get it by reading *intent.getIntExtra("id")*

##### Binding

When the module is started, it should bind to the dodedodo service like so:
```java

Intent intent = new Intent();
intent.setClassName("org.dobots.dodedodo", "org.dobots.dodedodo.MsgService");
bindService(intent, mMsgServiceConnection, Context.BIND_AUTO_CREATE);
```
where mMsgServiceConnection is an instance of ServiceConnection.

Modules communicate with the dodedodo app via [android messengers](http://developer.android.com/reference/android/os/Messenger.html). Your module should have a messenger to which dodedodo can send commands and a seperate messenger for each input port.

These messengers are registered when connected to dodedodo as done in the function *onServiceConnected* in [AimConnectionHelper](https://github.com/dobots/android_aim_library/blob/master/src/org/dobots/aim/AimConnectionHelper.java).


#### Stopping

Dodedodo will send a *MSG_STOP* when the module should stop. On destroy, the module should unregister with a *MSG_UNREGISTER* and unbind from dodedodo. See function *unbindFromMsgService* in [AimConnectionHelper](https://github.com/dobots/android_aim_library/blob/master/src/org/dobots/aim/AimConnectionHelper.java).

#### Heartbeat

Each second the module should send a *MSG_PONG* to dodedodo, to indicate the module is still working. See *HeartBeatTimerTask* in [AimConnectionHelper](https://github.com/dobots/android_aim_library/blob/master/src/org/dobots/aim/AimConnectionHelper.java).

#### Connecting ports

Dodedodo will send a *MSG_SET_MESSENGER* to a module when connecting an output port. The message contains a messenger to which the output port can send its messages and the name of the output port. See *IncomingMsgHandler* in [AimConnectionHelper](https://github.com/dobots/android_aim_library/blob/master/src/org/dobots/aim/AimConnectionHelper.java).

#### Data

Each messenger of an input port has a message handler to read incoming data. A data message should be of type *MSG_PORT_DATA* and contain a bundle which contains the datatype (*DATATYPE_...*) and the data. See [AimUtils](https://github.com/dobots/android_aim_library/blob/master/src/org/dobots/aim/AimUtils.java).


## Copyrights
The copyrights (2013) belong to:

- License: LGPL v.3
- Author: Bart van Vliet
- Almende B.V., http://www.almende.com and DO bots B.V., http://www.dobots.nl
- Rotterdam, The Netherlands

[![Analytics](https://ga-beacon.appspot.com/UA-46821459-1/dobots/Dodedodo_Android)](https://github.com/dobots/Dodedodo_Android)
