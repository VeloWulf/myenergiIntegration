/**
 *  myenergi Integration for Hubitat
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
 *  2021-06-20	  Initial version
 *  2023-06-04    Bug fix to correct director call
 *
 */

definition (
    name:"myenergi Device Integration",
    namespace:"velowulf",
    author:"Paul Hutton",
    description:"Integrates myenergi devices controlled by a Hub into Hubitat",
    category:"Green Living",
    iconUrl: "", // empty string - not implemented in Hubitat
    iconX2Url: "", // empty string - not implemented in Hubitat
    iconX3Url: "", // empty string - not implemented in Hubitat
    documentationLink: "",
	singleInstance: true
)

preferences {
    page(name:"mainPage")
    page(name:"devicePage")
}

// static variables
def getChildNamespace () {return "velowulf"}
def getChildTypeName (group) {
    switch (group) {
        case "eddi":
            return "myenergi eddi device"
            break
        case "zappi":
            return "myenergi zappi device"
            break
        case "harvi":
            return "myenergi harvi device"
            break
        case "libbi":
            return "myenergi libbi device"
            break
    }
}


def mainPage() {
    //state.remove("director")
    //state.remove("nc")
    
    //dynamicPage(name:"HubLoginDetails",title:"myenergi Login Details",,install:true,uninstall:true) {
    dynamicPage(name:"HubLoginDetails",title:"myenergi Login Details",nextPage:"devicePage",install:false,uninstall:true) {
        // section ("Login Details") {
        section {
            input "hubUsername", "text", title:"Enter Hub Serial Number:", required:true
            input "hubPassword", "password", title:"Enter API Key <a style='font-size:11px;' href='https://support.myenergi.com/hc/en-gb/articles/5069627351185-How-do-I-get-an-API-key-' target='_blank'>(How do I get an API key?)</a>", required:true
            input "debugOutput", "bool", title: "Enable debug logging", defaultValue: false
        }
        
        //def devices = getDeviceList()
        //trace("End of code")
    }

    //TODO improve the pages and add header footer with paypal links
    //TODO add priority settings for the selected devices (sets the pri flag per device - need to obtain command via proxy)
}

def devicePage() {
    def availableDevices = getDeviceList()

    logDebug("availableDevices = ${availableDevices}")
    availableDevices.each {dni ->
        def devGroup = ""
        def devID = ""
        
        // for whatever reason I cannot stop these variables from being created as lists
        // so the code uses a temp variable as a stop gap and uses the first element of the extracted value to set to a string variable
        // - any help here on how to extract them as strings without the intermediate step would be appreciated
        temp = dni.findAll(/^([^\s]+)/)
        devGroup = temp[0]
        temp = dni.findAll(/[0-9]+/)
        devID = temp[0]

        logDebug("devGroup = ${devGroup} ----- devID = ${devID}")
        logDebug(state[devGroup])
    }

    dynamicPage(name:"DeviceSelection",title:"Select devices to be managed",install:true,uninstall:false) {
        section ("Devices") {
            paragraph "Select the devices to be managed from the list below"
            input "devicesToManage","enum",title:"Which devices do you want to manage?",multiple:true,required:true,options:availableDevices,submitOnChange:false
        }
        // TODO add the section for scheduling
        section ("Refresh") {
            input (
                name:"refreshRate",
                type:"enum",
                title:"Refresh rate in minutes (defaults to 15)",
                multiple:false,
                required:false,
                options:[1,5,10,15,30,60,180],
                submitOnChange:false
            )
        }
    }
}

def getDeviceList() {
    trace("Running getDeviceList")

/*    def currentURI = ""

    currentURI = getCurrentURI()*/

    pollASNServer("/cgi-jstatus-*")
        
    //def devicesList = []
    def devicesList = []
    def newASN = ""
    //def desiredDevices = ["eddi","zappi","harvi","libbi"]  // other key values are returned but we only want the main device types in the list

    // the returned values are in nested maps so we need to parse out the values
    eddiMap = state.eddi
    if (eddiMap) {
        logDebug("eddiMap = ${eddiMap}")
        logDebug("eddimap size = ${eddiMap.size}")
        if (eddiMap.size > 0) {
            eddiMap.each {valuesMap ->
                logDebug("ValuesMap = $valuesMap}")
                devicesList << "eddi (serial number:${valuesMap.sno})"
            }
        }
    }
    zappiMap = state.zappi
    if (zappiMap) {
        logDebug("zappiMap = ${zappiMap}")
        logDebug("zappimap size = ${zappiMap.size}")
        if (zappiMap.size > 0) {
            zappiMap.each {valuesMap ->
                logDebug("ValuesMap = $valuesMap}")
                devicesList << "zappi (serial number:${valuesMap.sno})"
            }
        }
    }
    /* code to add both harvi and libbi to the list of devices once the drivers have been written
    harviMap = state.harvi
    logDebug("harvimap size = ${harviMap.size}")
    if (harviMap.size > 0) {
        harviMap.each {valuesMap ->
            logDebug("ValuesMap = $valuesMap}")
            devicesList << "harvi (serial number:${valuesMap.sno})"
        }
    }
    libbiMap = state.libbi
    logDebug("libbimap size = ${libbiMap.size}")
    if (libbiMap.size > 0) {
        libbiMap.each {valuesMap ->
            logDebug("ValuesMap = $valuesMap}")
            devicesList << "libbi (serial number:${valuesMap.sno})"
        }
    }
*/

    logDebug(devicesList)
    return devicesList   
}

def parseAllDevices (devicesToParse) {      // sort order updated Feb '24' to be alphabetical 
//TO DO: figure out how to seach for the index to prevent future reordering breaking the code
    trace("Parsing all devices")
    state.eddi = devicesToParse[0].eddi
    state.zappi = devicesToParse[3].zappi
    state.harvi = devicesToParse[1].harvi
    state.libbi = devicesToParse[2].libbi
}

/*def parseAllDevices (devicesToParse) {
    trace("Parsing all devices")
    state.eddi = devicesToParse[0].eddi
    state.zappi = devicesToParse[1].zappi
    state.harvi = devicesToParse[2].harvi
    state.libbi = devicesToParse[3].libbi
}*/

// This function will interrogate the Director server and obtain the ASN for commands
def getASN() {
    trace("Running getASN")
    // logDebug("CurrentURI: ${currentURI}")

    def authstring = ""
    def authmap = [:]
    def asn = ""

    // Create base parameters
    def cmdParams = [
        uri: "https://director.myenergi.net",
        path: "/cgi-jstatus-*",
        //requestContentType: "application/json",
        // contentType:"application/json",
        timeout:300
    ]

    // make an initial call to the director server to obtain the nonce
    authmap = getAuthMap(cmdParams)
    
    // create the digest token based on the nonce returned from the director
    trace("Adding auth header")
    
    digeststring = calcDigestAuth(authmap,cmdParams.path)
    authheader = ['Authorization':digeststring]
    cmdParams.put('headers',authheader)
    logDebug("Auth Command parameters = ${cmdParams}")

    // finish by passing the digest token to the director server and obtaining the ASN server from the header
    try {
        httpGet(cmdParams) {resp->
            if(resp.success) {
                logDebug("Response = ${resp.data}")
                logDebug("Headers1 = ${resp?.getAllHeaders()}")
                                
                latestASN = resp.headers.'X_myenergi-asn'
                logDebug("LatestASN = ${latestASN}")

                if(latestASN) {
                    state.latestASN = latestASN
                    return latestASN   
                }    
            }
        }
    }

    catch (Exception e) {
        logDebug("AuthMap Response Status = ${e.getResponse().getStatus()}")
        logDebug("AuthMap Response Headers = ${e.getResponse().getAllHeaders()}")

        if(e.getResponse().getStatus() == 401) {
            // this 401 may happen even though auth has been successful
            latestASN = e.getResponse().headers.'X_myenergi-asn'
            logDebug("LatestASN = ${latestASN}")

            if(latestASN) {
                state.latestASN = latestASN
                return latestASN
            } else {
                log.error "Authentication failed on the director server"
            }

            
        }
    }

    
    // asyncRequest(cmdParams,"ASN")    
}

def getAuthMap(cmdParams) {
    try {
        httpGet(cmdParams) {resp -> 
            if (resp.success) {
                // this should never happen - we are expecting a 401
                log.error "Unexpected succesful authmap call to server - please troubleshoot"
                return null
            }
        }
    }
    catch (Exception e) {
        logDebug("AuthMap Response Status = ${e.getResponse().getStatus()}")
        logDebug("AuthMap Response Headers = ${e.getResponse().getAllHeaders()}")

        if(e.getResponse().getStatus() == 401) {
            // the 401 return code indicates that the server requires the digest token
            authstring = e.getResponse().headers.'www-authenticate'
            logDebug("Authorization = ${authstring}")

            authmap = authstring.replaceAll("Digest ", "").replaceAll(", ", ",").replaceAll("\"", "").findAll(/([^,=]+)=([^,]+)/) { full, name, value -> [name, value] }.collectEntries( { it })
            logDebug("Auth Map = ${authmap}")

            // write the returned authmap to the director state variable (only done for 401 - new nonce is provided)
            //state.director = authmap - may not need this
            return authmap
        }
    }
}

// def pollASNServer(asnServer,asnPath = "/cgi-jstatus-*") {
def pollASNServer(asnPath = "/cgi-jstatus-*") {
    def asnServer = ""

    asnServer = getCurrentURI()

    def cmdParams = [
        uri: "https://" + asnServer,
        path: asnPath,
        contentType:"application/json",
        //textParser:true,
        timeout:300
    ]

    if(!state.asnauth) {
        state.asnauth = getAuthMap(cmdParams)
    }
    
    digeststring = calcDigestAuth(state.asnauth,cmdParams.path)
    authheader = ['Authorization':digeststring]
    cmdParams.put('headers',authheader)
    logDebug("Auth Command parameters = ${cmdParams}")
    
    try {
        httpGet(cmdParams) {resp->
            if(resp.success) {
                logDebug("Response = ${resp.data}")
                logDebug("Headers = ${resp?.getAllHeaders()}")
                
                def result = [:]

                result = resp.data
                if(asnPath == "/cgi-jstatus-*") {parseAllDevices(result)}
                return result   
            } 
        }
    }
    catch (Exception e) {
        //TODO look for a 401 and then obtain the x_myenergi-asn header, store in state var and rerun the try else do the below code
        logDebug("Exception in pollASNServer call: ${e.message}")
        return false
    }
}

def compareASN(newASN,oldASN) {
    trace("Running compareASN")
    logDebug("Old ASN = ${oldASN} and new ASN = ${newASN}")
    
    // compare the new ASN value in the returned body to that provided to the function. If different store the new value and wipe the state auth details
    if(newASN != oldASN) {
        trace("different ASN")
        state.latestASN = newASN
        state.remove("asnauth")
    }
}

def getCurrentURI() {
    trace("Obtaining current asn server")
    def currentURI = ""

    if (!state.latestASN || state.latestASN == "") {
        currentURI = getASN()

        def cmdParams = [
        uri: "https://" + currentURI,
        path: "/cgi-jstatus-*",
        //requestContentType: "application/json",
        //contentType:"application/json",
        timeout:300
        ]

        state.asnauth = getAuthMap(cmdParams)
        
    } else {
        // TODO add a time check
        currentURI = state.latestASN
        logDebug("Current URI = ${currentURI}")
    }

    return currentURI
}

def asyncRequest(cmdParams,requesttype) {
    trace("Running asyncRequest")
    try {
        asynchttpGet(handler, cmdParams,[returntype:"${requesttype}"])
    }
    catch(Exception e) {
        logDebug("Exception with request: ${e.message}")
        return false
    }    
}

def handler(response,data) {
    trace("Running handler")
    // TO DO need to check if the nonce is stale (need separate function)
    // if it is stale then redo request with new nonce 
        
    logDebug("Return Type = ${data.returntype}")
    logDebug("Response Status = ${response?.getStatus()}")

    def authstring = ""
    def authmap = [:]
    def asn = ""
    def latestASN = ""
    
    if(data.returntype == "ASN") {
        if(response?.getStatus() == 401) {
            logDebug("Response header = ${response?.getHeaders()}")
            
            latestASN = response?.getHeaders()?.getAt('x_myenergi-asn')
            authstring = response?.getHeaders()?.getAt('www-authenticate')
            logDebug("Authorization = ${authstring}")

            authmap = authstring.replaceAll("Digest ", "").replaceAll("\"", "").findAll(/([^,=]+)=([^,]+)/) { full, name, value -> [name, value] }.collectEntries( { it })
            logDebug("Auth Map = ${authmap}")

            if(!latestASN || latestASN == "") {
                state.director = authmap
                getASN()
            } else {
                logDebug("Latest ASN = ${latestASN}")
                currentURI = latestASN 
            }
        }
    }

    if(authmap.Stale == "true") {
    // TO DO rerun 
    }
}


// calculate digest token, more details: http://en.wikipedia.org/wiki/Digest_access_authentication#Overview
private String calcDigestAuth(digestmap,digestPath) {
    trace("Running calcDigest")
    
    //def digestmap = [:]
    
	// increase nc every request by one
	if(!state.nc) {
        trace("resetting nc")
        state.nc = 1
    } else {
        state.nc = state.nc + 1
    }
    
    /*// select correct digestmap
    switch(statetype) {
        case "director":
            digestmap = state.director
            logDebug("Digest Map = ${digestmap}")
            logDebug("test value = ${digestmap.nonce}")
            break
    }*/
    
    // create the response MD5 hash
    def HA1 = hashMD5("${hubUsername}:" + digestmap.realm + ":${hubPassword}")
	def HA2 = hashMD5("GET:${digestPath}")
	def cnonce = java.util.UUID.randomUUID().toString().replaceAll('-', '').substring(0, 8)
	def response = "${HA1}:" + digestmap.nonce + ":" + state.nc + ":" + cnonce + ":" + digestmap.qop + ":${HA2}"
	def response_enc = hashMD5(response)

	logDebug("HA1: " + HA1 + " ===== orig:" + "${hubUsername}:" + digestmap.realm.trim() + ":${hubPassword}")
	logDebug("HA2: " + HA2 + " ===== orig:" + "GET:${digestPath}")
	logDebug("Response: " + response_enc + " =====   orig:" + response)
	
	def eol = " "

    // send back the digest token    
    return 'Digest username="' + hubUsername.trim() + '",' + eol +
           'realm="' + digestmap.realm.trim() + '",' + eol +
           'qop="' + digestmap.qop.trim() + '",' + eol +
           'algorithm="MD5",' + eol +
           'uri="'+ digestPath.trim() + '",' +  eol +
           'nonce="' + digestmap.nonce.trim() + '",' + eol +
           'cnonce="' + cnonce.trim() + '",'.trim() + eol +
           'opaque="' + digestmap.opaque.trim() + '",' + eol +
           'nc=' + state.nc + ',' + eol +
           'response="' + response_enc.trim() + '"'

}

private hashMD5(String somethingToHash) {
	java.security.MessageDigest.getInstance("MD5").digest(somethingToHash.getBytes("UTF-8")).encodeHex().toString()
}

private trace(msg) {
  logDebug(msg,true)
}

private logDebug(msg, trace = false) {
	if (settings?.debugOutput != false) {
		if (trace) {
            log.trace msg
        } else {
           log.debug msg 
        }
    }
}

def installed() {
    trace("Installation started")
    logDebug("Settings = ${settings}")
    
    createDevices()
    setScheduler()
}

def updated() {
    trace("Update started")
    
    createDevices()
    pollAllDevices()
    setScheduler()
}

def uninstalled() {

    unschedule()
    removeAllDevices()
}

def createDevices() {
    devicesToManage.each {dni ->
        def devGroup = ""
        def devID = ""

        // for whatever reason I cannot stop these variables from being created as lists
        // so I must use the first element of the extracted value and set to a string variable
        // - any help here on how to extract them as strings would be appreciated
        temp = dni.findAll(/^([^\s]+)/)
        devGroup = temp[0]
        temp = dni.findAll(/[0-9]+/)
        devID = temp[0]
        
        logDebug("devGroup = ${devGroup} ----- devID = ${devID}")
        
        d = getChildDevice(devID) // find devices with the current network ID
        if (!d) { //the return is empty so no device exists so add it
            newChild = addChildDevice(getChildNamespace(), getChildTypeName(devGroup), devID, [name:dni,isComponent:false])
            log.info "Created a new ${newChild.displayName} with id ${devID}"
        } else { //inform user that the device already exists
            log.warn "device with id ${devID} already exists - device has not been (re)created"
        }
    }

    //TODO remove unselected devices that have been created previously (when this is run from updated)
}

def displayHeader() {
	section (getFormat("title", "Myenergi Integration")) {
		paragraph getFormat("line")
	}
}

def displayFooter(){
	section() {
		paragraph getFormat("line")
		paragraph "<div style='color:#1A77C9;text-align:center'>Myenergi Integration<br><a href='https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=7LBRPJRLJSDDN&source=url' target='_blank'><img src='https://www.paypalobjects.com/webstatic/mktg/logo/pp_cc_mark_37x23.jpg' border='0' alt='PayPal Logo'></a><br><br>Please consider donating. This app took a lot of work to make.<br>If you find it valuable, I'd certainly appreciate it!</div>"
	}
}

def pollAllDevices () {
    trace("Running pollAllDevices")

    // request updated data from the server
    updateMap = pollASNServer("/cgi-jstatus-*")

    logDebug("updateMap=${updateMap}")

    def allDevices = getChildDevices()
    
    pauseExecution(2000)
    logDebug("Devices returned= ${allDevices}")
    allDevices.each {
        // tell the child device to update from the stored state values
        it.poll(false,updateMap[0].eddi,updateMap[1].zappi,updateMap[2].harvi,updateMap[3].libbi)
    }

}

def setScheduler () {
    trace("Running setScheduler")
    // uses the refreshRate nominated by the user to set up a regular refresh of devices
    
    // cancel schedules first
    unschedule()
    
    // set up new schedule to poll all devices
    switch (refreshRate) {
        case "1":
            runEvery1Minute("pollAllDevices")
            break
        case "5":
            runEvery5Minutes("pollAllDevices")
            break
        case "10":
            runEvery10Minutes("pollAllDevices")
            break
        case "15":
            runEvery15Minutes("pollAllDevices")
            break
        case "30":
            runEvery30Minutes("pollAllDevices")
            break
        case "60":
            runEvery1Hour("pollAllDevices")
            break
        case "180":
            runEvery3Hours("pollAllDevices")
            break
        default:
            runEvery15Minutes("pollAllDevices")
            break
    }
}

def removeAllDevices () {
    trace("Running removeAllDevices")
    getAllChildDevices().each {
        log.info "Deleting myenergi device: ${it.displayName}"
        try {
            deleteChildDevice(it.deviceNetworkId)
        }
        catch (e) {
            logDebug "${e} deleting the myenergi device: ${it.deviceNetworkId}"
        }
    }
}