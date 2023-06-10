/**
 *  myenergi zappi Device Handler
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
 *  2023-06-04	  Initial version
 *  2023-06-09    Added setPriority and minGreenLevel commands
 *  2023-06-10    Introduced lastChargeMode state variable - used to return the zappi back to prior charge state when it is switched on
 *
 */

 metadata {
     definition(name:"myenergi zappi device", namespace:"velowulf", 
        description:"Driver for Hubitat Elevation to control the Hubitat zappi EV charger",
        author:"Paul Hutton",
        importUrl:"") {
            capability "EnergyMeter"
            // capability "Initialize" //initialize()
            // capability "Notification" //deviceNotification(text)
            capability "PowerMeter"
            capability "Polling" //poll() 
            capability "Refresh" //refresh()
            capability "Sensor"
            capability "Switch" //on(), off()
            capability "VoltageMeasurement"

            /* standard attributes included with capabilities
            attribute "energy", "number"
            attribute "power", "number"
            attribute "switch", "enum", ["on","off"]
            attribute "temperature", "number"
            attribute "voltage", "number"
            attribute "frequency", "number"*/
            
            attribute "boostMode", "number"
            attribute "sessionTotal", "number"
            attribute "diversionAmount", "number"
            attribute "generatedWatts", "number"
            // attribute "gridWatts", "number"
            attribute "remainingBoostCharge", "number"
            attribute "status", "number"
            attribute "minimiumGreenLevel", "number"
            attribute "priority", "number"
            attribute "chargeStatus", "string"
            attribute "zappiMode", "number"
    
            command "manualBoost", [
                [name:"additionalCharge", 
                    description:"Determines the additional charge that will be added to the EV (0 cancels the boost)",
                    type:"ENUM", constraints:["0","5","10","20","30","40"]]
            ]
            command "smartBoost", [
                [name:"additionalCharge", 
                    description:"Determines the additional charge that will be added to the EV (0 cancels the boost)",
                    type:"ENUM", constraints:["0","5","10","20","30","40"]],
                [name:"timeComplete", 
                    description:"The time the boost should be complete (hhmm)",
                    type:"STRING"]
            ]
            command "scheduledBoost", [
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
                [name:"slot", description:"Select the schedule slot to cancel",
                    type:"ENUM", constraints:[1,2,3,4]]
            ]
            command "chargeMode", [
                [name:"mode", description:"Choose the charging mode",
                    type:"ENUM", constraints:["FAST","ECO","ECO+","STOP"]]
            ]
            command "minimumGreenLevel", [
                [name:"greenLevel", description:"1-100", type:"NUMBER"]
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
     state.boost = parent.pollASNServer("/cgi-boost-time-Z${device.deviceNetworkId}")
 }

def uninstalled() {

}

def updated() {
    poll(true)
    state.boost = parent.pollASNServer("/cgi-boost-time-Z${device.deviceNetworkId}")
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
    
    // get the latest zappi data from the parent app
    if (!zappiMap) { zappiMap = parent.state.zappi }
    debug("zappiMap = ${zappiMap}")
    parseZappiData(zappiMap)

    // update the state boost variable with the current boost settings
    state.boost = parent.pollASNServer("/cgi-boost-time-Z${device.deviceNetworkId}")
    

}

def manualBoost(additionalCharge) {
    trace("Running manualBoost")
    def serial = device.deviceNetworkId
    // use the supplied parameters to set a manual boost on the device (a duration of 0 will cancel the boost)
    if (additionalCharge != "0") {
        info("Boosting ${device.displayName} to ${additionalCharge} kWh")
        debug("Command being issued = /cgi-zappi-mode-Z${serial}-0-10-${additionalCharge}-0000")
        response = parent.pollASNServer("/cgi-zappi-mode-Z${serial}-0-10-${additionalCharge}-0000")
    } else {
        if (device.currentValue("remainingBoost") > 0)
        info("Cancelling active boost on ${device.displayName}")
        response = parent.pollASNServer("/cgi-zappi-mode-Z${serial}-0-2-0-0000")
    }
}

def smartBoost(additionalCharge, timeComplete) {
    trace("Running smartBoost")
    def serial = device.deviceNetworkId

    int timeCompleteInt = timeComplete as Integer
    int timeCompleteMins = timeComplete.substring(3) as Integer

    debug("${timeCompleteMins} ---- divided by 15 = ${timeCompleteMins / 15} ---- modulus = ${timeCompleteMins % 15}")

    assert timeComplete ==~ /[0-9]{4}/ : "smartBoost: Start time entered in the incorrect format"
    assert timeCompleteInt <= 2345 : "smartBoost: Duration must be less than 2345"
    assert timeCompleteMins % 15 == 0 : "smartBoost: Start time must end in 15 minute intervals"


    // use the supplied parameters to set a manual boost on the device (a duration of 0 will cancel the boost)
    if (additionalCharge != "0") {
        info("Boosting ${device.displayName} to ${additionalCharge} kWh")
        debug("Command being issued = /cgi-zappi-mode-Z${serial}-0-11-${additionalCharge}-${timeComplete}")
        response = parent.pollASNServer("/cgi-zappi-mode-Z${serial}-0-11-${additionalCharge}-${timeComplete}")
    } else {
        if (device.currentValue("remainingBoost") > 0)
        info("Cancelling active boost on ${device.displayName}")
        response = parent.pollASNServer("/cgi-zappi-mode-Z${serial}-0-2-0-0000")
    }
}

def on() {
    trace("On is running")
    def currentStatus = device.currentValue("switch")
    if (currentStatus == 'off') {
        info("Switching ${device.displayName} ON")
        if (state.lastChargeMode) {
            chargeMode(state.lastChargeMode) // will turn on the zappi using the charge mode used prior to being turned off (or set to STOP)
        } else {
            chargeMode("ECO") // switches on the zappi using the default ECO mode if the lastChargeMode doesn't exist
        }
    }
}

def off() {
    trace("Off is running")
    def currentStatus = device.currentValue("switch")
    if (currentStatus == 'on') {
        chargeMode("STOP")
    }
}

def chargeMode (mode) {
    trace("chargeMode is running")
    def serial = device.deviceNetworkId
    // convert string mode into integer
    def modeInt = 0
    switch (mode) {
        case ("FAST"):
            info("Switching ${device.displayName} to FAST mode")
            response = parent.pollASNServer("/cgi-zappi-mode-Z${serial}-1-0-0-0000")
            break
        case ("ECO"):
            info("Switching ${device.displayName} to ECO mode")
            response = parent.pollASNServer("/cgi-zappi-mode-Z${serial}-2-0-0-0000")
            break
        case ("ECO+"):
            info("Switching ${device.displayName} to ECO+ mode")
            response = parent.pollASNServer("/cgi-zappi-mode-Z${serial}-3-0-0-0000")
            break
        case ("STOP"):
            info("Switching ${device.displayName} to STOP mode")
            response = parent.pollASNServer("/cgi-zappi-mode-Z${serial}-4-0-0-0000")
            break
    }    
    
    // changing mode takes a few moments so wait for 5 seconds before re-polling the device
    pauseExecution(5000)
    poll(true) // update the values to reflect the device operating a different mode

}

def parseZappiData(zappiMap) {
    trace("Parsing zappi data")
    int dni = device.deviceNetworkId as Integer
    
    def data = zappiMap.find {it.sno == dni}
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
            case ("mgl"):
                sendEvent(name:"minimumGreenLevel",
                    value:datavalue,
                    unit:"%")
                break
            case ("tbk"):
                if (datavalue) {
                    sendEvent(name:"remainingBoostCharge",
                        value:datavalue,
                        unit:"seconds")
                } else {
                    sendEvent(name:"remainingBoostCharge",
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
                        desc = "${device.displayName} is charging"
                        break
                    case 5:
                        desc = "${device.displayName} is COMPLETE"
                        break
                }

                sendEvent(name:"status",
                    value:datavalue,
                    descriptionText:desc)
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
            case ("pst"):
                def desc=""
                switch (datavalue) {
                    case "A":
                        desc = "EV Disconnected"
                        break
                    case "B1":
                        desc = "EV Connected"
                        break
                    case "B2":
                        desc = "Waiting for EV"
                        break
                    case "C1":
                        desc = "EV Ready to Charge"
                        break
                    case "C2":
                        desc = "Charging"
                        break
                    case "F":
                        desc = "Fault"
                        break
                }

                sendEvent(name:"chargeStatus",
                    value:datavalue,
                    descriptionText:desc)
                break
            case ("zmo")    :
                def desc = ""
                switch (datavalue) {
                    case 1:
                        desc = "${device.displayName} is in FAST mode"
                        state.lastChargeMode="FAST"
                        break
                    case 2:
                        desc = "${device.displayName} is in ECO mode"
                        state.lastChargeMode="ECO"
                        break
                    case 3:
                        desc = "${device.displayName} is in ECO+ mode"
                        state.lastChargeMode="ECO+"
                        break
                    case 4:
                        desc = "${device.displayName} is STOPPED"
                        break
                }

                sendEvent(name:"status",
                    value:datavalue,
                    descriptionText:desc)

                // calculate the switch setting from the status - 6 is OFF , everything else is ON
                def onList = [1,2,3]
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
        }
    }
}

def refresh() {
    trace("Refresh is running")
    poll(true)
}

// TODO - need to confirm the command via proxy
def scheduledBoost(slot,startTime,duration,monday,tuesday,wednesday,thursday,friday,saturday,sunday) {
    trace("Running scheduledBoost")
    // setting a scheduled boost takes the parameters, checks the format of the time strings and combines
    // day flags into a string for submission to the zappi
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

    def slotcode = "1${slot}"

    // combine the parameters into the asnPath string
    def asnPath = "/cgi-boost-time-Z${device.deviceNetworkId}-${slotcode}-${startTime}-${duration}-0${dayString}"
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

def removeScheduledBoost(slot) {
    trace("Running removeScheduledBoost")
    
    def slotcode = "1${slot}"

    // combine the parameters into the asnPath string
    def asnPath = "/cgi-boost-time-Z${device.deviceNetworkId}-${slotcode}-0000-000-00000000"
    debug("asnPath=${asnPath}")
    boostTimes = parent.pollASNServer(asnPath)
}

def minimumGreenLevel(greenLevel) {
    trace("Running minimumGreenLevel")
    
    def serial = device.deviceNetworkId
    
    debug("Command being issued = /cgi-set-min-green-Z${serial}-${greenLevel}")
    info("Setting ${device.displayName} to a minimum green level of ${greenLevel}")
    response = parent.pollASNServer("/cgi-set-min-green-Z${serial}-${greenLevel}")
}

def setPriority(toPriority) {
    trace("Running setPriority")

    def serial = device.deviceNetworkId

    debug("Command being issued = /cgi-set-priority-Z${serial}-${toPriority}")
    info("Setting ${device.displayName} priority to ${toPriority}")
    response = parent.pollASNServer("/cgi-set-priority-Z${serial}-${toPriority}")
}