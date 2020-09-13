# Installation
You need to install all the drivers for the devices you need in *"Drivers Code"* by adding a *"New Driver"* and import the code.

Device | Import URL
--- | ---
Shelly 1 | https://raw.githubusercontent.com/zenblom/Hubitat/master/drivers/Shelly/Shelly_1.groovy
~~Shelly 1 Garage~~ | ~~https://raw.githubusercontent.com/zenblom/Hubitat/master/drivers/Shelly/Shelly_1_Garage.groovy~~
~~Shelly 2.5~~ | ~~https://raw.githubusercontent.com/zenblom/Hubitat/master/drivers/Shelly/Shelly_2_5.groovy~~
~~Shelly 2.5~~ | ~~https://raw.githubusercontent.com/zenblom/Hubitat/master/drivers/Shelly/Shelly_2_5_Verisure.groovy~~
~~Shelly Dimmer~~ | ~~https://raw.githubusercontent.com/zenblom/Hubitat/master/drivers/Shelly/Shelly_Dimmer.groovy~~
~~Shelly Door/Window~~ | ~~https://raw.githubusercontent.com/zenblom/Hubitat/master/drivers/Shelly/Shelly_Door_Window.groovy~~
~~Shelly Duo~~ | ~~https://raw.githubusercontent.com/zenblom/Hubitat/master/drivers/Shelly/Shelly_Duo.groovy~~
~~Shelly H&T~~ | ~~https://raw.githubusercontent.com/zenblom/Hubitat/master/drivers/Shelly/Shelly_H_T.groovy~~
~~Shelly Plug (S)~~ | ~~https://raw.githubusercontent.com/zenblom/Hubitat/master/drivers/Shelly/Shelly_Plug.groovy~~

After this you only create a Virtual device and set device type to **Shelly x**. When this is done you need to configure the device with the actual IP address of the physical Shelly device and press update. This will trigger multiple local REST API calls to the Shelly device and add Hubitat IP & port on the Action URLs so we can get real-time update of the state when changed, either digital or physical.

*Please note that all other settings needs to be done in Shelly app or web ui*

# Current status of drivers

Type | Progress
--- | ---
Shelly 1 | On / off
Shelly 1 Garage | Use a shelly 1 to control Garage Door with contact wired to SW
Shelly 2.5 | ***Under development (not working)***
Shelly 2.5 | Use a Shelly 2.5 to locally get Verisure Alarm status from Smart plugs. ***Under development (not working)***
Shelly Dimmer | On / off, set level (only state change when digital), 2nd button input will trigger a button push
Shelly Door/Window | ***Under development (not working)***
Shelly Duo | On / off, set level + set colour temperature (only state change when digital)
Shelly H&T | ***Under development (not working)***
Shelly Plug (S) | On / off
