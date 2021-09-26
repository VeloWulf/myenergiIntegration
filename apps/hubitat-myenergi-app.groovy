/**
 *  myenergi Device Handler
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
 *  2021-06-20	  Initial version
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

def mainPage() {
    state.remove("director")
    //state.remove("nc")
    
    //dynamicPage(name:"HubLoginDetails",title:"myenergi Login Details",,install:true,uninstall:true) {
    dynamicPage(name:"HubLoginDetails",title:"myenergi Login Details",nextPage:"devicePage",install:false,uninstall:true) {
        section ("Login Details") {
            input "hubUsername", "text", title:"Enter User Name:", required:true
            input "hubPassword", "password", title:"Enter Password", required:true
            input "debugOutput", "bool", title: "Enable debug logging", defaultValue: false
        }
        
        //def devices = getDeviceList()
        //trace("End of code")
        //log.info(devices)
    }
}

def devicePage() {
    def availableDevices = getDeviceList()

    dynamicPage(name:"DeviceSelection",title:"Select devices to be managed",install:true,uninstall:false) {
        section ("Devices") {
            paragraph "Select the devices to be managed from the list below"
            input "devicesToManage","enum",title:"Which devices do you want to manage?",multiple:true,required:true,options:availableDevices,submitOnChange:false
        }
        // TODO add the section for scheduling
        section ("Refresh") {
            input "refreshRate","enum",title:"Refresh rate in minutes (defaults to 60)",multiple:false,required:false,options:[30,60,120,300,720],submitOnChange:false
        }
    }
}

def getDeviceList() {
    trace("Running getDeviceList")

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
        currentURI = state.latestASN
        logDebug("Current URI = ${currentURI}")
    }
    
    devicesToParse = pollASNServer(currentURI,"/cgi-jstatus-*")
    logDebug("Devices to parse = ${devicesToParse}")
    
    def devices = []
    def newASN = ""
    def desiredDevices = ["eddi","zappi","harvi"]  // other key values are returned but we only want the main device types in the list
    // the returned values are in nested maps so we need to parse out the values
    devicesToParse.each {first ->
        first.each{second ->
            if(desiredDevices.contains(second.key)) {
                logDebug(second.key + " has a size of " + second.value.size())
                // a value higher than 1 means that there is a map for this key - therefore the device is active
                if(second.value.size() >0) { devices << second.key }
                logDebug(devices)
            }
            // grabbing the asn value for comparison
            if(second.key == "asn")  {
                trace("Grabbing asn")
                newASN = second.value
            }
        }
    }

    //TODO play around with the idea in this https://community.hubitat.com/t/json-map-conversion-fun/73303 to see if above is easier
    
    logDebug(devices)

    // compare the new ASN value in the returned body to that provided to the function. If different store the new value and wipe the state auth details
    compareASN(newASN,currentURI)
    return devices   
}


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
        httpGet(cmdParams) {resp -> 
            if (resp.success) {
                log.error "Unxpected succesful call to director server - please troubleshoot"
                return null
            }
        }
    }
    catch (Exception e) {
        logDebug("AuthMap Response Status = ${e.getResponse().getStatus()}")
        logDebug("AuthMap Response Headers = ${e.getResponse().getAllHeaders()}")

        if(e.getResponse().getStatus() == 401) {
            // this 401 should happen even though auth has been successful
            latestASN = e.getResponse().headers.'X_myenergi-asn'
            logDebug("LatestASN = ${latestASN}")

            if(latestASN) {
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
                log.error "Unxpected succesful authmap call to server - please troubleshoot"
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

def pollASNServer(asnServer,asnPath = "/cgi-jstatus-*") {
    // TODO should get a 200 (success) but if a 401 is received then check to see if stale record (exists and) is true
    // TODO might get a 401 (in which case check header record and run authmap and poll against new server) 

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

def parseData(dataToParse) {
    trace{"Running parseData"}
    result = dataToParse.substring(2,dataToParse.length()-2)
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
    
}

def updated() {
}

def uninstalled() {
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