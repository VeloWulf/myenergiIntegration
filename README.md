# myenergiIntegration
App and driver using the myenergi API to control their product line via Hubitat

Changelog:
  2023-06-04  Added zappi driver and resolved bugs in director call in the app

INSTRUCTIONS FOR USE:

1. Install the app driver
2. Install the driver files for the devices that you wish to control (currently EDDI or ZAPPI)
3. Run the app and enter your hub login and password (not the myenergi portal credentials)
4. Select debug setting based on your personal preference and click 'NEXT'
5. The devices connected to your hub should be present in the drop down - select the device(s) that you want to manage
6. Choose a refresh rate for the devices. This defaults to 15 minutes, which reduces the load on the hub and the myenergi servers
7. Click 'DONE' and the app will create the devices
