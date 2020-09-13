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
        capability "Energy Meter"
        capability "Refresh"
    }

    // Preferences
    preferences {
        input "ip", "text", title: "Shelly IP Address", description: "IP Address in form 192.168.1.135", required: true, displayDuringSetup: true
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
    
    refresh()
}

def refresh() {
    if (logEnable) log.debug "[Shelly Plug] Executing 'refresh()'"
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
        
    } else if ( url.contains("/btn/on") ) {
        //if (device.currentValue('switch') != 'on') sendEvent(name: "switch", value: "on")
        
    }

    if (!headerString) {
        log.warn "headerstring was null for some reason :("
    }

}

private void updateDevice(String name, String value) {
    switch (name) {
        case "temp":
            sendEvent([name: "temperature", value: value])
            break
        case "hum": 
            sendEvent([name: "humidity", value: value])
            break    
        default: 
            log.error "No Attribute case for ${name}"
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

def sendLanMessage(message) {
    if (logEnable) log.debug "[Shelly Plug] sendLanMessage: Executing 'sendLanMessage' ${message}"
    if (settings.ip != null) {
        try {
            httpGet("http://${ip}/${message}") { resp ->
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

void on() {
    sendLanMessage("relay/0?turn=on")
    sendEvent([name: "switch", value: "on"])
    log.info "Turn on"
}

void off() {
    sendLanMessage("relay/0?turn=off")
    sendEvent([name: "switch", value: "off"])
    log.info "Turn off"
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex.toUpperCase()
}