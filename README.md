# Karoo Powerbar Extension

> [!WARNING]  
> Only tested on Karoo 3

Simple karoo extension that shows an overlay power bar at the bottom of the screen. For Karoo 2 and Karoo 3 devices
running Karoo OS version 1.524.2003 and later.

![Powerbar](powerbar0.png)
![Settings](powerbar1.png)

## Usage

Install the app and start it from the main menu. You will be asked to grant it permission to show 
it on top of other apps (i. e. the karoo ride app). You can select one of the following data sources:

- Heart rate
- Power
- Average power over the last 3 seconds
- Average power over the last 10 seconds

Subsequently, the power bar will be shown at the bottom of the screen. The bar will be filled according
to your current power output / heart rate and will change color depending on the intensity, as setup
in your power / heart rate zones.

## Installation

Currently, Hammerhead has not yet released an on-device app store that allows users to easily install 
extensions like this. Until it is available, you can sideload the app.

1. Download the apk from the [releases page](https://github.com/timklge/karoo-powerbar/releases) (or build it from source)
2. Set up your Karoo for sideloading. DC Rainmaker has a great [step-by-step guide](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html).
3. Install the app by running `adb install app-release.apk`.

## Credits

- Icons by [boxicons.com](https://boxicons.com) (MIT-licensed).

## Links

[karoo-ext source](https://github.com/hammerheadnav/karoo-ext)
