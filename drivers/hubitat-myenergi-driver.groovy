/**
 *  myenergi eddi Device Handler
 *
 *  Copyright 2021 Paul Hutton
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
    
            command "manualBoost", [
                [name:"heater", 
                    description:"Select the heater to boost",
                    type:"ENUM", constraints:[1,2]],
                [name:"duration", 
                    description:"Determines the boost duration (0 cancels the boost)",
                    type:"ENUM", constraints:[0,20,40,60,90,120,240]]
            ]
            command "scheduledBoost", [
                [name:"heater", description:"Select the heater to schedule",
                    type:"ENUM", constraints:["Heater 1","Heater 2","Relay 1","Relay 2"]],
                [name:"slot", description:"Select the schedule slot",
                    type:"ENUM", constraints:[1,2,3,4]],
                [name:"startTime", description:"Start time (hh:mm)", type:"STRING"],
                [name:"duration", description:"Duration (hh:mm)", type:"STRING"],
                [name:"monday", type:"ENUM", constraints:["Off","On"]],
                [name:"tuesday", type:"ENUM", constraints:["Off","On"]],
                [name:"wednesday", type:"ENUM", constraints:["Off","On"]],
                [name:"thursday", type:"ENUM", constraints:["Off","On"]],
                [name:"friday", type:"ENUM", constraints:["Off","On"]],
                [name:"saturday", type:"ENUM", constraints:["Off","On"]],
                [name:"sunday", type:"ENUM", constraints:["Off","On"]],
                //[name:"days", description:"Enter days to run (example MTWTFSS = 1111111)",type:"STRING"]
            ]
        }
 
    preferences {
        input (
                type: "enum",
                name: "defaultBoost",
                title: "Default Boost",
                description: "Selects the default boost time (defaults to 60 minutes)",
                options: [ "20","40","60","90","120","240" ],
                required: false,
                defaultValue: "60"
            )
        input (name: "infoLogging", type: "bool", title: "Enable info message logging", defaultValue: true)
        input (name: "debugLogging", type: "bool", title: "Enable debug message logging", defaultValue: false)
        input (name: "traceLogging", type: "bool", title: "Enable trace message logging", defaultValue: false)
    }
 }

//TODO Add functions for trace and debug

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
 }

def uninstalled() {

}

def updated() {
    poll(true)
}

def poll(updateData = false) {
    trace("Polling device data")
    //TODO this will call the parent pollASNServer method that will get the data for parsing 

    debug("updateData=${updateData}")    
    // as an exception this method may need to update the data stored in the parent app
    if (updateData) {
        currentURI = parent.getCurrentURI()
        parent.pollASNServer(currentURI,"/cgi-jstatus-*")
    }
    
    // get the latest eddi data from the parent app
    eddiMap = parent.state.eddi
    debug("eddiMap = ${eddiMap}")
    parseEddiData(eddiMap)

}

def manualBoost(heater,duration) {

}

def on() {

}

def off() {

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
                    unit:kWh)
                break
            case ("tp1"):
                sendEvent(name:"temperature",
                    value:datavalue,
                    unit:"C")
        }
    }
    
    /*eddiMap.each {valuesMap ->
        debug("valuesMap=${valuesMap}")
        debug("dni=${dni}")
                
        int eddiSNO = valuesMap.sno as Integer

        if (eddiSNO == dni) {
            //TODO map results to attributes and send Events
            

            }

        

        
    }*/
}

def refresh() {
    trace("Refreshing (gets new data from parent)")
    poll(true)
}