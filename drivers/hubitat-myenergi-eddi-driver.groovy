/**
 *  myenergi eddi Device Handler
 *
 *  Copyright 2023 Paul Hutton
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Date          Comments
 *  2021-10-06	  Initial version
 *  2022-06-22    Update to manual boost to reflect that API is currently restricted to a 60 minute boost only
 *  2023-06-04    Added priority attribute and made some bug fixes
 *  2023-06-09    Added setPriority command
 *
 */

 metadata {
     definition(name:"myenergi eddi device", namespace:"velowulf", 
        description:"Driver for Hubitat Elevation to control the Hubitat eddi solar diverter",
        author:"Paul Hutton",
        importUrl:"") {
            capability "EnergyMeter"
            // capability "Initialize" //initialize()
            // capability "Notification" //deviceNotification(text)
            capability "PowerMeter"
            capability "Polling" //poll() 
            capability "Refresh" //refresh()
            capability "RelaySwitch" //on(), off()
            capability "Sensor"
            capability "Switch"
            capability "TemperatureMeasurement"
            capability "VoltageMeasurement"

            /* standard attributes included with capabilities
            attribute "energy", "number"
            attribute "power", "number"
            attribute "switch", "enum", ["on","off"]
            attribute "temperature", "number"
            attribute "voltage", "number"
            attribute "frequency", "number"*/
            
            attribute "boostMode", "number"
            // attribute "sessionTotal", "number"
            attribute "diversionAmount", "number"
            attribute "generatedWatts", "number"
            // attribute "gridWatts", "number"
            attribute "remainingBoost", "number"
            attribute "status", "number"
            attribute "priority", "number"
    
            // command "getLatestData"
            command "manualBoost", [
                [name:"heater", 
                    description:"Select the heater to boost",
                    type:"ENUM", constraints:["1","2"]],
                [name:"duration", 
                    description:"Determines the boost duration (0 cancels the boost)",
                    type:"ENUM", constraints:["0","20","40","60"]]
            ]
            command "scheduledBoost", [
                [name:"heater", description:"Select the heater to schedule",
                    type:"ENUM", constraints:["Heater 1","Heater 2","Relay 1","Relay 2"]],
                [name:"slot", description:"Select the schedule slot",
                    type:"ENUM", constraints:[1,2,3,4]],
                [name:"startTime", description:"Start time (hhmm in multiples of 15 mins), example: 11:15pm = 2315", type:"STRING"],
                [name:"duration", description:"Duration (hmm in multiples of 15 mins) - max boost is 8 hours", type:"STRING"],
                [name:"monday", type:"ENUM", constraints:["Off","On"]],
                [name:"tuesday", type:"ENUM", constraints:["Off","On"]],
                [name:"wednesday", type:"ENUM", constraints:["Off","On"]],
                [name:"thursday", type:"ENUM", constraints:["Off","On"]],
                [name:"friday", type:"ENUM", constraints:["Off","On"]],
                [name:"saturday", type:"ENUM", constraints:["Off","On"]],
                [name:"sunday", type:"ENUM", constraints:["Off","On"]],
                //[name:"days", description:"Enter days to run (example MTWTFSS = 1111111)",type:"STRING"]
            ]
            command "removeScheduledBoost", [
                [name:"heater", description:"Select the heater to cancel",
                    type:"ENUM", constraints:["Heater 1","Heater 2","Relay 1","Relay 2"]],
                [name:"slot", description:"Select the schedule slot to cancel",
                    type:"ENUM", constraints:[1,2,3,4]]
            ]
            command "setPriority", [
                [name:"toPriority", description:"1-3", type:"NUMBER"]
            ]
        }
 
    preferences {
        /*input (
                type: "enum",
                name: "defaultBoost",
                title: "Default Boost",
                description: "Selects the default boost time (defaults to 60 minutes)",
                options: [ "20","40","60","90","120","240" ],
                required: false,
                defaultValue: "60"
            )*/
        input (name: "infoLogging", type: "bool", title: "Enable info message logging", defaultValue: true)
        input (name: "debugLogging", type: "bool", title: "Enable debug message logging", defaultValue: false)
        input (name: "traceLogging", type: "bool", title: "Enable trace message logging", defaultValue: false)
    }
 }

private def info (msg) {
    if (infoLogging) {
        log.info "${device.displayName}: ${msg}"\
    }
}
private def debug (msg) {
    if (debugLogging) {
        log.debug "${device.displayName}: ${msg}"
    }
}
private def trace (msg) {
    if (traceLogging) {
        log.trace "${device.displayName}: ${msg}"
    }
}
private def warn (msg) {
    log.warn "${device.displayName}: ${msg}"
}
private def error (msg) {
    log.error "${device.displayName}: ${msg}"
}

 def installed() {
     poll()
     state.boost = parent.pollASNServer("/cgi-boost-time-E${device.deviceNetworkId}")
 }

def uninstalled() {

}

def updated() {
    poll(true)
    state.boost = parent.pollASNServer("/cgi-boost-time-E${device.deviceNetworkId}")
}

def poll(updateData = false, eddiMap = null, zappiMap = null, harviMap = null, libbiMap = null) {
    trace("Polling device data")
    
    debug("updateData=${updateData}")    
    // as an exception this method may need to update the data stored in the parent app
    if (updateData) {
        //currentURI = parent.getCurrentURI()
        trace("Updating device data")
        parent.pollASNServer("/cgi-jstatus-*")
    }
    
    // get the latest eddi data from the parent app
    if (!eddiMap) { eddiMap = parent.state.eddi }
    debug("eddiMap = ${eddiMap}")
    parseEddiData(eddiMap)

    // update the state boost variable with the current boost settings
    state.boost = parent.pollASNServer("/cgi-boost-time-E${device.deviceNetworkId}")

}

def manualBoost(heater,duration) {
    trace("Running manualBoost")
    def serial = device.deviceNetworkId
    // use the supplied parameters to set a manual boost on the device (a duration of 0 will cancel the boost)
    if (duration != "0") {
        info("Boosting ${device.displayName} for ${duration} minutes")
        debug("Command being issued = /cgi-eddi-boost-E${serial}-10-${heater}-${duration}")
        response = parent.pollASNServer("/cgi-eddi-boost-E${serial}-10-${heater}-${duration}")
    } else {
        if (device.currentValue("remainingBoost") > 0)
        info("Cancelling active boost on ${device.displayName}")
        response = parent.pollASNServer("/cgi-eddi-boost-E${serial}-1-${heater}-0")
    }
}

def on() {
    trace("On is running")
    // check the status of device and if it is off then switch it on
    def currentStatus = device.currentValue("switch")
    def serial = device.deviceNetworkId
    if (currentStatus == 'off') {
        info("Switching ${device.displayName} ON")
        response = parent.pollASNServer("/cgi-eddi-mode-E${serial}-1")
            
        // switching on takes a few moments so wait for 5 seconds before re-polling the device
        pauseExecution(5000)
        poll(true) // update the values to reflect the device is now on
    }
}

def off() {
    trace("Off is running")
    // check the status of device and if it is on then switch it off
    def currentStatus = device.currentValue("switch")
    def serial = device.deviceNetworkId
    if (currentStatus == 'on') {
        info("Switching ${device.displayName} OFF")
        response = parent.pollASNServer("/cgi-eddi-mode-E${serial}-0")

        // switching off takes a few moments so wait for 5 seconds before re-polling the device
        pauseExecution(5000)
        poll(true) // update the values to reflect the device is now off
    }
}

def parseEddiData(eddiMap) {
    trace("Parsing eddi data")
    int dni = device.deviceNetworkId as Integer
    
    def data = eddiMap.find {it.sno == dni}
    debug("data=${data}")
    debug("data size: ${data.size()}")
    assert data.size() > 0 : "Not able to find data for device ${device.displayName}"

    data.each {attribute, datavalue ->
        switch (attribute) {
            case ("bsm"):
                def desc = ""
                switch (datavalue) {
                    case 0:
                        desc = "Boost is OFF"
                        break
                    case 1:
                        desc = "Boost mode is ON"
                        break
                }

                sendEvent(name:"boostMode", 
                    value:datavalue, 
                    descriptionText:desc)
                break
            case ("div"):
                sendEvent(name:"diversionAmount",
                    value:datavalue,
                    unit:"W")
                break
            case ("frq"):
                sendEvent(name:"frequency",
                    value:datavalue,
                    unit:"Hz")
                break
            case ("gen"):
                sendEvent(name:"generatedWatts",
                    value:datavalue,
                    unit:"W")
                break
            case ("grd"):
                sendEvent(name:"power",
                    value:datavalue,
                    unit:"W")
                break
            case ("rbt"):
                if (datavalue) {
                    sendEvent(name:"remainingBoost",
                        value:datavalue,
                        unit:"seconds")
                } else {
                    sendEvent(name:"remainingBoost",
                        value:"0",
                        unit:"seconds")
                }
                break           
            case ("sta"):
                def desc = ""
                switch (datavalue) {
                    case 1:
                        desc = "${device.displayName} is PAUSED"
                        break
                    case 3:
                        desc = "${device.displayName} is diverting energy"
                        break
                    case 4:
                        desc = "${device.displayName} is BOOSTING"
                        break
                    case 5:
                        desc = "${device.displayName} has reached MAX temperature"
                        break
                    case 6:
                        desc = "${device.displayName} is STOPPED"
                        break
                }

                sendEvent(name:"status",
                    value:datavalue,
                    descriptionText:desc)

                // calculate the switch setting from the status - 6 is OFF , everything else is ON
                def onList = [1,3,4,5]
                def devSwitchState = device.currentValue("switch")
                if (onList.contains (datavalue)) {
                    if (devSwitchState == "off" | devSwitchState == null) {
                            sendEvent(name:"switch",
                                value:"on",
                                descriptionText:"${device.displayName} is ON")
                    }
                } else {
                    if (devSwitchState == "on" | devSwitchState == null) {
                            sendEvent(name:"switch",
                                value:"off",
                                descriptionText:"${device.displayName} is OFF")
                    }
                }
                break
            case ("vol"):
                sendEvent(name:"voltage",
                    value:datavalue / 10,
                    unit:"V")
                break
            case ("che"):
                sendEvent(name:"energy",
                    value:datavalue,
                    unit:"kWh")
                break
            case ("pri"):
                sendEvent(name:"priority",
                    value:datavalue,
                    descriptionText:"${device.displayName} has a priority of ${datavalue}")
                break
            case ("tp1"):
                sendEvent(name:"temperature",
                    value:datavalue,
                    unit:"C")
                break
        }
    }
}

def refresh() {
    trace("Refresh is running")
    poll(true)
}

def scheduledBoost(heater,slot,startTime,duration,monday,tuesday,wednesday,thursday,friday,saturday,sunday) {
    trace("Running scheduledBoost")
    // setting a scheduled boost takes the parameters, checks the format of the time strings and combines
    // day flags into a string for submission to the eddi
    // NOTE: this command does not change the e sense or temperature flags - these must still be set manually in the required slot

    int startTimeInt = startTime as Integer
    int startTimeMins = startTime.substring(3) as Integer
    int durationInt = duration as Integer
    int durationMins = duration.substring(2) as Integer

    debug("${startTimeMins} ---- divided by 15 = ${startTimeMins / 15} ---- modulus = ${startTimeMins % 15}")

    assert startTime ==~ /[0-9]{4}/ : "scheduledBoost: Start time entered in the incorrect format"
    assert startTimeInt <= 2345 : "scheduledBoost: Duration must be less than 2345"
    assert startTimeMins % 15 == 0 : "scheduledBoost: Start time must end in 15 minute intervals"
    assert duration ==~ /[0-9]{3}/ : "scheduledBoost: Duration entered in the incorrect format"
    assert durationInt <= 800 : "scheduledBoost: Duration must be 8 hours or less"
    assert durationMins % 15 == 0 : "scheduledBoost: Duration must end in 15 minute intervals"

    // change state words to flags and create string
    dayString = convertWordToFlag(monday)+convertWordToFlag(tuesday)+convertWordToFlag(wednesday)+
        convertWordToFlag(thursday)+convertWordToFlag(friday)+convertWordToFlag(saturday)+convertWordToFlag(sunday)
    debug("dayString=${dayString}")

    // create the heater code (heater 1 / 2 = 1 / 2 or relay 1 / 2 = 5 /6)
    debug("heater=${heater}")
    def heaterchar = ""
    
    switch (heater) {
        case "Heater 1": heaterchar = 1
            break
        case "Heater 2": heaterchar = 2
            break
        case "Relay 1": heaterchar = 5
            break
        case "Relay 2": heaterchar = 6
            break
    }

    def slotcode = "${heaterchar}${slot}"

    // combine the parameters into the asnPath string
    def asnPath = "/cgi-boost-time-E${device.deviceNetworkId}-${slotcode}-${startTime}-${duration}-0${dayString}"
    debug("asnPath=${asnPath}")
    // and run the command to update the boost slot
    boostTimes = parent.pollASNServer(asnPath)
}

private String convertWordToFlag(word) {
    switch (word) {
        case "On": return 1
            break
        case "Off": return 0
            break
    }
}

def removeScheduledBoost(heater,slot) {
    trace("Running removeScheduledBoost")
    // create the heater code (heater 1 / 2 = 1 / 2 or relay 1 / 2 = 5 /6)
    debug("heater=${heater}")
    def heaterchar = ""
    
    switch (heater) {
        case "Heater 1": heaterchar = 1
            break
        case "Heater 2": heaterchar = 2
            break
        case "Relay 1": heaterchar = 5
            break
        case "Relay 2": heaterchar = 6
            break
    }

    def slotcode = "${heaterchar}${slot}"

    // combine the parameters into the asnPath string
    def asnPath = "/cgi-boost-time-E${device.deviceNetworkId}-${slotcode}-0000-000-00000000"
    debug("asnPath=${asnPath}")
    boostTimes = parent.pollASNServer(asnPath)
}

/*def getLatestData() {         // replaced by refresh command
    trace("Running getLatestData")
    poll(true)
}*/

def setPriority(toPriority) {
    trace("Running setPriority")
    
    def serial = device.deviceNetworkId
    
    debug("Command being issued = /cgi-set-priority-E${serial}-${toPriority}")
    info("Setting ${device.displayName} priority to ${toPriority}")
    response = parent.pollASNServer("/cgi-set-priority-E${serial}-${toPriority}")
}