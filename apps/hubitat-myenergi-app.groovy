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
}

def mainPage() {
    dynamicPage(name:"HubLoginDetails",title:"myenergi Login Details",install:true,uninstall:true) {
        section ("Login Details") {
            input "hubUsername", "text", title:"Enter User Name:", required:true
            input "hubPassword", "password", title:"Enter Password", required:true
            input "debugOutput", "bool", title: "Enable debug logging", defaultValue: false
        }
        state.remove("director")
        //state.remove("nc")
        def devices = getDeviceList()
        trace("End of code")
        //log.info(devices)
    }
}

def getDeviceList() {
    // state.auth = "empty"
    def currentURI = getASN()
    logDebug("Current URI = ${currentURI}")
}


// This function will interrogate the Director server and obtain the BaseURI for commands. Initially set to return known URI - TO DO
def getASN() {
    trace("Running getASN")
    // logDebug("CurrentURI: ${currentURI}")

    // Create base parameters
    def cmdParams = [
        uri: "https://director.myenergi.net",
        path: "/cgi-jstatus-*",
        //requestContentType: "application/json",
        // contentType:"application/json",
        timeout:300
    ]

    if(state.director) {
        trace("Adding auth header")
        // if the nonce value exists and it isn't stale then add digest response
        authstring = calcDigestAuth("director",cmdParams.path)
        authheader = ['Authorization':authstring]
        cmdParams.put('headers',authheader)
        
    }

    logDebug("Command parameters = ${cmdParams}")
    asyncRequest(cmdParams,"ASN")    
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
    // TO DO need to check if the nonce is stale
    // if it is stale then redo request with new nonce (need separate function)
    // next check to see if ASN exists
    
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
private String calcDigestAuth(statetype,digestPath) {
    trace("calcDigest")
    
    def digestmap = [:]
    
	// increase nc every request by one
	if(!state.nc) {
        trace("resetting nc")
        state.nc = 1
    } else {
        state.nc = state.nc + 1
    }
    
    // select correct digestmap
    switch(statetype) {
        case "director":
            digestmap = state.director
            break
    }
    
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
}

def updated() {
}

def uninstalled() {
}