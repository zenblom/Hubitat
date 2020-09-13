/**
 *  Shelly_Plug.groovy
 *
 *  https://raw.githubusercontent.com/zenblom/Hubitat/master/drivers/Shelly/Shelly_Plug.groovy
 *
 *  Copyright 2020 Martin Blomgren
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
 *  Change History:
 *
 *    Date        Who              What
 *    ----        ---              ----
 *    2020-09-14  Martin Blomgren  Added support for power and authentication
 *    2020-05-09  Martin Blomgren  Original Creation
 *	
 */

metadata {
    definition (name: "Shelly Plug", namespace: "zenblom", author: "Martin Blomgren") {
        capability "Actuator"
        capability "Switch"
        capability "Light"
        capability "Sensor"
        capability "Power Meter"
        capability "Refresh"        
    }

    // Preferences
    preferences {
        input "ip", "text", title: "Shelly IP Address", description: "IP Address in form 192.168.1.135", required: true, displayDuringSetup: true
        input "username", "text", title: "Shelly Username", description: "Leave empty if not set in device", required: false, displayDuringSetup: true
        input "password", "password", title: "Shelly Password", description: "Leave empty if not set in device", required: false, displayDuringSetup: true
        input name: "pollInterval", type: "enum", title: "Power reporting interval", defaultValue: "0", required: true,
            options: [["0" : "Off [DEFAULT]"],
                      ["1" : "1 min"],
                      ["5" : "5 min"],
                      ["10" : "10 min"],
                      ["15" : "15 min"],
                      ["30" : "30 min"],
                      ["60" : "60 min"]
            ]
        input name: "powerThreshold", type: "enum", title: "Power reporting treshold", defaultValue: "15", required: true,
            options: [["0" : "Any"],
                      ["1" : "1%"],
                      ["2" : "2%"],
                      ["3" : "3%"],
                      ["4" : "4%"],
                      ["5" : "5%"],
                      ["10" : "10%"],
                      ["15" : "15% [DEFAULT]"],
                      ["20" : "20%"],
                      ["25" : "25%"],
                      ["30" : "30%"],
                      ["35" : "35%"],
                      ["40" : "40%"],
                      ["45" : "45%"],
                      ["50" : "50%"],
                      ["55" : "55%"],
                      ["60" : "60%"],
                      ["65" : "65%"],
                      ["70" : "70%"],
                      ["75" : "75%"],
                      ["80" : "80%"],
                      ["85" : "85%"],
                      ["90" : "90%"],
                      ["95" : "95%"],
                      ["100" : "100%"]
            ]
        input name: "powerPollWhenOff", type: "enum", title: "Power reporting when switch is off?", defaultValue: "false", required: true,
            options: [["false" : "NO [DEFAULT]"],
                      ["true" : "YES"]
            ]
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    log.info "[Shelly Plug] Executing 'installed()'"
}

def uninstalled() {
    log.info "[Shelly Plug] Executing 'uninstalled()'"
}

def initialize() {
    log.info "[Shelly Plug] Executing 'initialize()'"
    
    refresh()
}

def updated() {
    log.info "[Shelly Plug] Executing 'updated()'"
    log.info "[Shelly Plug] Hub IP Address = ${device.hub.getDataValue("localIP")}, Hub Port = ${device.hub.getDataValue("localSrvPortTCP")}"
    
    def iphex = convertIPtoHex(ip)
    log.info "[Shelly Plug] Setting DNI = ${iphex}"
    device.setDeviceNetworkId("${iphex}")
    
    if (logEnable) {
        log.info "[Shelly Plug] Enabling Debug Logging for 30 minutes" 
        runIn(1800,logsOff)
    } else {
        unschedule(logsoff)
    }

    configureReportUrl()
    configurePolling()
    
    refresh()
}

def refresh() {
    if (logEnable) log.debug "[Shelly Plug] Executing 'refresh()'"
    pollStatus()
}

def logsOff(){
    log.warn "[Shelly Plug] debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

// parse events into attributes
def parse(String description) {
    def msg = parseLanMessage(description)
    if (logEnable) log.debug "[Shelly Plug] parse: msg= '${msg}'"
    
    def headerString = msg.header
    def url = msg.headers.entrySet().iterator().next().getKey().toString()
    if (logEnable) log.debug "[Shelly Plug] parse: url= $url"
    if ( url.contains("/relay/0/on") ) {
        if (device.currentValue('switch') != 'on') sendEvent(name: "switch", value: "on")
        
    } else if (url.contains("/relay/0/off")) {
        if (device.currentValue('switch') != 'off') sendEvent(name: "switch", value: "off")
        if (device.currentValue('power') != 0) sendEvent(name: 'power', value: 0)
        
    } else if ( url.contains("/btn/on") ) {
        //if (device.currentValue('switch') != 'on') sendEvent(name: "switch", value: "on")
        
    }

    if (!headerString) {
        log.warn "headerstring was null for some reason :("
    }

}

def configureReportUrl() {
    def onUrl = "http://" + device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP") + "/relay/0/on"
    sendLanMessage("settings/relay/0?out_on_url=${onUrl}")

    def offUrl = "http://" + device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP") + "/relay/0/off"
    sendLanMessage("settings/relay/0?out_off_url=${offUrl}")

    def btnOnUrl = "http://" + device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP") + "/btn/on"
    sendLanMessage("settings/relay/0?btn_on_url=${btnOnUrl}")
}

def configurePolling() {
    log.info "[Shelly Plug] configurePolling: Polling Status set to every ${pollInterval} minutes"
    def pollIntervalInt = pollInterval.toInteger()
    try {
        if (pollIntervalInt > 0 && pollIntervalInt < 60) {
            log.info "[Shelly Plug] initialize: Polling Status between 1-59 minutes"
            schedule("0 */${pollInterval.toString()} * ? * *", pollStatus) //"Seconds" "Minutes" "Hours" "Day Of Month" "Month" "Day Of Week" "Year"
        } else if (pollIntervalInt == 60) {
            log.info "[Shelly Plug] initialize: Polling Status every 60 minutes"
            schedule("0 0/${pollInterval.toString()} * ? * *", pollStatus) //"Seconds" "Minutes" "Hours" "Day Of Month" "Month" "Day Of Week" "Year"
        } else {
            log.info "[Shelly Plug] initialize: Unscheduling polling"
            
        }
        
    } catch (e) {
        log.warn "[Shelly Plug] initialize: Could not enable polling. Not scheduling updates. $e"
    }
   
}

def sendLanMessage(message) {
    if (logEnable) log.debug "[Shelly Plug] sendLanMessage: Executing 'sendLanMessage' ${message}"
    if (settings.ip != null) {
        try {
            httpGet("${getBaseUrl()}${message}") { resp ->
                if (resp.success) { }
                if (logEnable)
                    if (resp.data) log.debug "[Shelly Plug] sendLanMessage: response= ${resp.data}"
            }
        } catch (Exception e) {
            log.warn "[Shelly Plug] Call to on failed: ${e.message}"
        }
    } else {
        log.warn "[Shelly Plug] Please verify IP address are configured."    
    }
}

def pollStatus() {    
    if ((powerPollWhenOff.toBoolean() || device.currentValue('switch') != 'off') && settings.ip != null) {
        // Get the Status for devices
        if (logEnable) log.debug "[Shelly Plug] pollStatus: Fetching status"
        def paramsOverview = [
            uri        : "${getBaseUrl()}meter/0",
            contentType: "application/json",
            headers    : [
                requestContentType: "application/json"
            ]
        ]

        asynchttpGet('updateDevice', paramsOverview)
    }
}

private void updateDevice(response, data) {
    if (logEnable) log.debug "[Shelly Plug] updateDevice: Status response received."
    
    if (!checkResponse("handleStatusResponse", response)) return
    
    def responseJson = response.getJson()
    if (logEnable) log.debug "[Shelly Plug] updateDevice: $responseJson"

    if (responseJson.power != null) {
        def currentPower = device.currentValue('power')
        def updatedPower = responseJson.power
        
        def powerThresholdInt = powerThreshold.toInteger()
        float powerDiffFloat = 0;
        powerDiffFloat = ((updatedPower - currentPower) * 100)
        powerDiffFloat = (currentPower == BigDecimal.ZERO) ? powerDiffFloat : (powerDiffFloat / currentPower)
        int powerDiffInt = Math.abs((int)powerDiffFloat)
        
        if (logEnable) log.debug "[Shelly Plug] updateDevice power: current (${currentPower}w), updated (${updatedPower}w), difference (${powerDiffInt}%)"
        if (powerDiffInt >= powerThresholdInt) {
            if (logEnable) log.debug "[Shelly Plug] updateDevice: power above threshold UPDATE!"
            if (device.currentValue('power').compareTo(responseJson.power) != 0) sendEvent(name: 'power', value: responseJson.power)
        }
    }
}

void on() {
    sendLanMessage("relay/0?turn=on")
    if (device.currentValue('switch') != 'on') sendEvent(name: "switch", value: "on")
    runIn(5, 'pollStatus')
    log.info "Turn on"
}

void off() {
    sendLanMessage("relay/0?turn=off")
    if (device.currentValue('switch') != 'off') sendEvent(name: "switch", value: "off")
    if (device.currentValue('power') != 0) sendEvent(name: 'power', value: 0)
    log.info "Turn off"
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex.toUpperCase()
}

// --- Getters
def getBaseUrl() {
    def baseUrl = ""
    if (username != null && password != null) {
        baseUrl = "http://${username}:${password}@${ip}/"
    } else {
        baseUrl = "http://${ip}/"
    }
    
    return baseUrl
}
    
// --- Response handlers for async http
def checkResponse(context, response) {
    if (response.hasError() || response.getStatus() != 200) {
        state.latestError = new Date().toLocaleString()
        log.warn "[Shelly Plug] $context : Did not get correct response. Got response ${response.getErrorData()} "
        if (response.hasError()) {
            if (logEnable) log.debug "[Shelly Plug] Response has error: " + response.getErrorData()
        } else {
            if (logEnable) log.debug "[Shelly Plug] Did not get 200. Response code was: " + Integer.toString(response.getStatus())
        }
        return false
    }
    return true
}