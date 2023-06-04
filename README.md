# myenergiIntegration
App and driver using the myenergi API to control their product line via Hubitat

Changelog:
  2023-06-04  Added zappi driver and resolved bugs in director call in the app
  
** NOTE: version 0.2.0 of this driver uses a different driver file to create the eddi driver. The old file has been left in place for backwards compatibility so that HPM doesn't remove it. Theoretically, the device using the old driver shouldn't be updated by future runs of the app so it should continue to function as it has but haven't done a great deal of testing on this due to lack of time

INSTALL AT YOUR OWN RISK: I have done a fair bit of testing on the majority of the function(s) of both drivers but as with everything the real stuff only gets discovered when it hits real world. If you come across any bugs then firstly I apologise and, secondly, I would ask you to raise them with me either via github or on the Hubitat community forum. I make no guarantees that it won't affect your hub operation or casuse something to fail so as always I suggest that you make a backup before installing.

INSTRUCTIONS FOR USE:

1. Install the app driver
2. Install the driver files for the devices that you wish to control (currently EDDI or ZAPPI)
3. Run the app and enter your hub login and password (not the myenergi portal credentials)
4. Select debug setting based on your personal preference and click 'NEXT'
5. The devices connected to your hub should be present in the drop down - select the device(s) that you want to manage
6. Choose a refresh rate for the devices. This defaults to 15 minutes, which reduces the load on the hub and the myenergi servers
7. Click 'DONE' and the app will create the devices
