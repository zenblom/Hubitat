/**
 *  Intesis HVAC 0.1
 *
 *  Author: 
 *    Martin Blomgren
 *
 * Thanks to James Nimmo for the massive work with the Python IntesisHome module
 * (https://github.com/jnimmo/pyIntesisHome)
 *
 *    0.1 (May 27 2019)
 *      - Initial Release
 *
 * MIT License
 *
 * Copyright (c) 2019 
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
def version() {"v0.1"}

import groovy.transform.Field
import hubitat.helper.InterfaceUtils
import groovy.json.JsonSlurper

metadata {
    definition (name: "IntesisHome HVAC", namespace: "zenblom", author: "Martin Blomgren") {
        capability "Configuration"
		capability "Initialize"
		capability "Refresh"
		capability "Telnet"

		capability "Actuator"

		capability "Relative Humidity Measurement"
		capability "Temperature Measurement"
		capability "Sensor"

		capability "Energy Meter"
        capability "Power Meter"

		capability "Thermostat"
		capability "ThermostatMode"
		capability "ThermostatSetpoint"
		
		attribute "Telnet", "String"
		attribute "rssi", "Integer"
		
        attribute "swing", "String"
        attribute "temperatureUnit","String"
        attribute "latestMode","String"
		
		command "pollStatus"
		command "connect"
		command "stop"

    }
	
    preferences {
        section("Disable updating here") {
            input "enabled", "bool", defaultValue: "true", title: "Enabled?"
        }

        section("Authentication") {
            input "username", "text", title: "Username"
            input "password", "password", title: "Password"
        }

        section("Logging") {
            input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
            input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        }
    }
}

// --- "Constants" & Global variables
def getINTESIS_URL() { return "https://user.intesishome.com/api.php/get/control" }
def getINTESIS_CMD_STATUS() { return '{"status":{"hash":"x"},"config":{"hash":"x"}}' }
def getINTESIS_API_VER() { return "2.1" }

def getAPI_DISCONNECTED() { return "Disconnected" }
def getAPI_CONNECTING() { return "Connecting" }
def getAPI_AUTHENTICATED() { return "Connected" }
def getAPI_AUTH_FAILED() { return "Wrong username/password" }

def getINTESIS_MAP() { 
    String map = """
    {
        "1": {"name": "power", "values": {"0": "off", "1": "on"}},
        "2": {"name": "mode", "values": {"0": "auto", "1": "heat", "2": "dry", "3": "fan", "4": "cool"}},
        "4": {"name": "fan_speed", "values": {"0": "auto", "1": "quiet", "2": "low", "3": "medium", "4": "high"}},
        "5": {"name": "vvane", "values": {"0": "auto/stop", "10": "swing", "1": "manual1", "2": "manual2", "3": "manual3", "4": "manual4", "5": "manual5"}},
        "6": {"name": "hvane", "values": {"0": "auto/stop", "10": "swing", "1": "manual1", "2": "manual2", "3": "manual3", "4": "manual4", "5": "manual5"}},
        "9": {"name": "setpoint", "null": 32768},
        "10": {"name": "temperature"},
        "13": {"name": "working_hours"},
        "35": {"name": "setpoint_min"},
        "36": {"name": "setpoint_max"},
        "37": {"name": "outdoor_temperature"},
        "68": {"name": "current_power_consumption"},
        "69": {"name": "total_power_consumption"},
        "70": {"name": "weekly_power_consumption"}
    }
    """
    return new JsonSlurper().parseText(map)
}

def getCOMMAND_MAP() {
    String cmd = """
     {
        "power": {"uid": 1, "values": {"off": 0, "on": 1}},
        "mode": {"uid": 2, "values": {"auto": 0, "heat": 1, "dry": 2, "fan": 3, "cool": 4}},
        "fan_speed": {"uid": 4, "values": {"auto": 0, "quiet": 1, "low": 2, "medium": 3, "high": 4}},
        "vvane": {"uid": 5, "values": {"auto/stop": 0, "swing": 10, "manual1": 1, "manual2": 2, "manual3": 3, "manual4": 4, "manual5": 5}},
        "hvane": {"uid": 6, "values": {"auto/stop": 0, "swing": 10, "manual1": 1, "manual2": 2, "manual3": 3, "manual4": 4, "manual5": 5}},
        "setpoint": {"uid": 9}
    }
    """
    return new JsonSlurper().parseText(cmd)
}

def initialize() {
	log.debug "[IntesisHome] initialize"

	setModes();
    connect()
}

def installed() {
    initialize()
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)

    initialize()
}

def setModes() {
	def supportedThermostatModes = ["off", "auto", "heat", "dry", "fan", "cool"]
	def supportedFanModes = ["auto", "quiet", "low", "medium", "high"]
	sendEvent(name: "supportedThermostatModes", value: supportedThermostatModes, displayed: false, isStateChange: true)
	sendEvent(name: "supportedThermostatFanModes", value: supportedFanModes, displayed: false, isStateChange: true)	
}

def checkState() {
	log.info "[IntesisHome] current Telnet status is ${device.currentValue("Telnet")}"
}

def connect() {
    if (state.server && state.serverPort && state.token) {
    	log.info "[IntesisHome] connect: session to IntesisHome at ${state.server}:${state.serverPort}"

        //Connect to the IntesisHome
        try {
    		//open telnet connection
    		telnetConnect([termChars: [125,125], terminalType: 'VT100'], state.server, state.serverPort, null, null)		
        } 
        catch(e) {
            if (logEnable) log.debug "[IntesisHome] connect: initialize error ${e.message}"
            log.error "[IntesisHome] connect: Telnet connect failed in connect()"
        }
	
	    connectionMade()
    } else {
        //Get connection details
        pollStatus()
    }
}

def connectionMade() {
	// Authenticate
	def authMsg = '{"command":"connect_req","data":{"token":' + state.token + '}}'
	if (logEnable) log.debug "[IntesisHome] connectionMade: authMsg ${authMsg}"
	sendHubCommand(new hubitat.device.HubAction(authMsg, hubitat.device.Protocol.TELNET))
}

def pollStatus() {
    def params = [
        uri    : INTESIS_URL,
		contentType: "application/x-www-form-urlencoded",
        body   : 'username=' + username + '&password=' + password + '&cmd={"status":{"hash":"x"},"config":{"hash":"x"}}&version=1.8.5'
    ]

    try {
        asynchttpPost('handlePollResponse', params, data)
    } catch (e) {
        error("pollStatus", "Error polling", e)
    }
}

def handlePollResponse(response, data) {
	def responseError = response.hasError()
	debug("hasEerror", "$responseError")

	if (responseError) {
		def responseErrorStatus = response.getStatus()
		debug("errorStatus", "$responseErrorStatus")
		def responseErrorData = response.getErrorData()
		debug("errorData", "$responseErrorData")
		def responseErrorMessage= response.getErrorMessage()
		debug("errorMessage", "$responseErrorMessage")

        return
	}

	def responseJson = response.getJson()
	if (logEnable) debug("responseJson", "$responseJson")
	
	state.server = responseJson['config']['serverIP']
	state.serverPort = responseJson['config']['serverPort']
	state.token = responseJson['config']['token'] 

	responseJson['config']['inst'].each { installation ->
		if (logEnable) debug("Found installation", "${installation.name}")
		
		installation['devices'].each { device ->
			if (logEnable) debug("Found device", "${device.name}")
			state.deviceId = device.id
		}	
	}
	
	// Update state attributes
	responseJson['status']['status'].each { status ->
		updateDeviceState(status['deviceId'], status['uid'], status['value'])
	}
    
    connect()
}

def updateDeviceState(long deviceId, int uid, int value) {
	if (uid == 60002) return
	if (logEnable) log.debug "[IntesisHome] updateDeviceState: deviceId=${deviceId}, uid=${uid}, value=${value}"

	if (INTESIS_MAP.containsKey(uid.toString())) {
		
		if (INTESIS_MAP[uid.toString()].containsKey('values')) { // power, mode, fan_speed, vvane, hvane
			def valuesValue = INTESIS_MAP[uid.toString()].values[value.toString()]
			
			switch (INTESIS_MAP[uid.toString()].name) {
				case "power": // off, on
					if (txtEnable) log.info "[IntesisHome] updateDeviceState power: $valuesValue"
					if (valuesValue == "off") {
						sendEvent(name: "thermostatMode", value: "off")
					} else if (valuesValue == "on") {
						sendEvent(name: "thermostatMode", value: device.currentValue("latestMode"))
					}
					break
				
				case "mode": // auto, heat, dry, fan, cool
					if (txtEnable) log.info "[IntesisHome] updateDeviceState mode: $valuesValue"
					sendEvent(name: "thermostatMode", value: valuesValue)
					sendEvent(name: "latestMode", value: valuesValue)
					break

				case "fan_speed": // auto, quiet, low, medium, high
					if (txtEnable) log.info "[IntesisHome] updateDeviceState fan_speed: $valuesValue"
					sendEvent(name: "thermostatFanMode", value: valuesValue)
					break
				
				case "vvane": // auto/stop, swing, manual1, manual2, manual3, manual4, manual5
					if (txtEnable) log.info "[IntesisHome] updateDeviceState vvane: $valuesValue"
					//sendEvent(name: "ThermostatSetpoint", value: value/10)
					break
				
				case "hvane": // auto/stop, swing, manual1, manual2, manual3, manual4, manual5
					if (txtEnable) log.info "[IntesisHome] updateDeviceState hvane: $valuesValue"
					//sendEvent(name: "ThermostatSetpoint", value: value/10)
					break
				
				default:
					if (logEnable) log.info "[IntesisHome] updateDeviceState values uid NOT FOUND"
					break
			}			
			
			
		} else if (INTESIS_MAP[uid.toString()].containsKey('null') && value == INTESIS_MAP[uid.toString()].null) {
			//setPointTemperature should be set to none...
			
		} else {
			switch (INTESIS_MAP[uid.toString()].name) {
				case "setpoint":
					if (txtEnable) log.info "[IntesisHome] updateDeviceState setpoint: ${value/10}"
					sendEvent(name: "thermostatSetpoint", value: value/10)
					sendEvent(name: "coolingSetpoint", value: value/10)
					sendEvent(name: "heatingSetpoint", value: value/10)
					break
				
				case "temperature":
					if (txtEnable) log.info "[IntesisHome] updateDeviceState temperature: ${value/10}"
					sendEvent(name: "temperature", value: value/10)
					break

				case "working_hours":
					if (txtEnable) log.info "[IntesisHome] updateDeviceState working_hours: $value"
					//sendEvent(name: "ThermostatSetpoint", value: value/)
					break
				
				case "setpoint_min":
					if (txtEnable) log.info "[IntesisHome] updateDeviceState setpoint_min: ${value/10}"
					//sendEvent(name: "ThermostatSetpoint", value: value/10)
					break
				
				case "setpoint_max":
					if (txtEnable) log.info "[IntesisHome] updateDeviceState setpoint_max: ${value/10}"
					//sendEvent(name: "ThermostatSetpoint", value: value/10)
					break
				
				case "outdoor_temperature":
					if (txtEnable) log.info "[IntesisHome] updateDeviceState outdoor_temperature: ${value/10}"
					sendEvent(name: "outdoorTemperature", value: value/10)
					break
				
				case "current_power_consumption":
					if (txtEnable) log.info "[IntesisHome] updateDeviceState current_power_consumption: $value"
					sendEvent(name: "power", value: value)
					
					// thermostatMode - auto, heat, dry, fan, cool
					// thermostatOperatingState - ENUM ["vent economizer", "pending cool", "cooling", "heating", "pending heat", "fan only", "idle"]
					if (value < 20) {
						sendEvent(name: "thermostatOperatingState", value: "idle")
						
					} else if (device.currentValue("thermostatMode") == "auto") {
						//sendEvent(name: "thermostatOperatingState", value: "")
				
					} else if (device.currentValue("thermostatMode") == "heat") {
						sendEvent(name: "thermostatOperatingState", value: "heating")
			
					} else if (device.currentValue("thermostatMode") == "dry") {
						sendEvent(name: "thermostatOperatingState", value: "vent economizer")
		
					} else if (device.currentValue("thermostatMode") == "fan") {
						sendEvent(name: "thermostatOperatingState", value: "fan only")
	
					} else if (device.currentValue("thermostatMode") == "cool") {
						sendEvent(name: "thermostatOperatingState", value: "cooling")

					}
					break
				
				case "total_power_consumption":
					if (txtEnable) log.info "[IntesisHome] updateDeviceState total_power_consumption: $value"
					sendEvent(name: "energy", value: value)
					break
				
				case "weekly_power_consumption":
					if (txtEnable) log.info "[IntesisHome] updateDeviceState weekly_power_consumption: $value"
					//sendEvent(name: "ThermostatSetpoint", value: value/10)
					break
				
				default:
					if (logEnable) log.debug "[IntesisHome] updateDeviceState non-values uid NOT FOUND"
					break
			}
		}
	}
}


def sendMsg(String msg) {
	return new hubitat.device.HubAction(msg, hubitat.device.Protocol.TELNET)
}

def stop() {
    log.info "[IntesisHome] stop..."
	telnetClose()
    log.info "[IntesisHome] stop: Telnet connection dropped..."
	sendEvent(name: "Telnet", value: "Disconnected")
}

// Parse incoming device messages to generate events
def parse(String message) {
	if (message.contains('"uid":60002')) return // rssi
	
	// As we don't have any termination character we nee to put back the curly braces again
	//def msg = message + '}}'
	//log.debug "[IntesisHome] parse message: ${msg}"
	
	def jsonSlurper = new JsonSlurper()
	def messageJson = jsonSlurper.parseText(message + '}}')
	
	if (logEnable) log.debug "[IntesisHome] parse messageJson: ${messageJson}"
	
	switch (messageJson.command) {
		case "connect_rsp":
			if (messageJson.data.status == "ok") {
				sendEvent(name: "Telnet", value: "Connected");
			}
			break
		
		case "status":
			//updateDeviceState(Int deviceId, Int uid, int value) 
			updateDeviceState(messageJson.data.deviceId, messageJson.data.uid, messageJson.data.value)
			break
		
		case "rssi":
			// [command:rssi, data:[deviceId:0123456789, value:196]]
			break
		
		default:
			break
	}
	//[command:connect_rsp, data:[status:ok]]
	

}

def telnetStatus(String status) {
    //if (logEnable) 
	if (logEnable) log.debug "[IntesisHome] telnetStatus: ${status}"

	if (status == "receive error: Stream is closed"){

		log.error "[IntesisHome] telnetStatus: Telnet connection dropped..."
		sendEvent(name: "Telnet", value: "Disconnected")
		pollStatus()
	} else {
	   	if (txtEnable) log.info "[IntesisHome] telnetStatus: OK, ${status}"
		sendEvent(name: "Telnet", value: "Connected")
	}
	
}

def socketStatus(String message) {
   //if (logEnable) 
	if (logEnable) log.debug "[IntesisHome] socketStatus: ${message}"    
}

def setHeatingSetpoint(double value) {
	def intValue = (int)(value * 10)
	if (txtEnable) log.info "[IntesisHome] setHeatingSetpoint to: $intValue"

	//def uid = 9
	def uid = COMMAND_MAP['setpoint']['uid']
	def message = '{"command":"set","data":{"deviceId":' + state.deviceId + ',"uid":' + uid + ',"value":' + intValue + ',"seqNo":0}}'
	
	sendHubCommand(new hubitat.device.HubAction(message, hubitat.device.Protocol.TELNET))
}

def setCoolingSetpoint(double value) {
	def intValue = (int)(value * 10)
	if (txtEnable) log.info "[IntesisHome] setCoolingSetpoint to: $intValue"

	//def uid = 9
	def uid = COMMAND_MAP['setpoint']['uid']
	def message = '{"command":"set","data":{"deviceId":' + state.deviceId + ',"uid":' + uid + ',"value":' + intValue + ',"seqNo":0}}'
	
	sendHubCommand(new hubitat.device.HubAction(message, hubitat.device.Protocol.TELNET))
}

def setThermostatMode(String mode) {
	if (txtEnable) log.info "[IntesisHome] setThermostatMode to: $mode"
	//supportedThermostatModes : [off, auto, heat, dry, fan, cool]
	def uid = COMMAND_MAP['mode']['uid']
	def value = COMMAND_MAP['mode'].values[mode]
	
	def message = '{"command":"set","data":{"deviceId":' + state.deviceId + ',"uid":' + uid + ',"value":' + value + ',"seqNo":0}}'
	if (logEnable) log.debug "[IntesisHome] send message: $message"
	sendHubCommand(new hubitat.device.HubAction(message, hubitat.device.Protocol.TELNET))
}

def setThermostatFanMode(String mode) {
	if (txtEnable) log.info "[IntesisHome] setThermostatFanMode to: $mode"
	//supportedThermostatFanModes : [auto, quiet, low, medium, high]
	def uid = COMMAND_MAP['fan_speed']['uid']
	def value = COMMAND_MAP['fan_speed'].values[mode]
	
	def message = '{"command":"set","data":{"deviceId":' + state.deviceId + ',"uid":' + uid + ',"value":' + value + ',"seqNo":0}}'
	if (logEnable) log.debug "[IntesisHome] send message: $message"
	sendHubCommand(new hubitat.device.HubAction(message, hubitat.device.Protocol.TELNET))
}

def cool() {}

def setValue() {}

def refresh() {
	if (logEnable) log.debug "refresh"
}

def configure() {
    if (txtEnable) log.debug "Configuring Reporting and Bindings."
    initialize()
}

private createLogString(String context, String message) {
    return "[IntesisHome." + context + "] " + message
}

private error(String context, String text, Exception e) {
    error(context, text, e, true)
}

private error(String context, String text, Exception e, Boolean remote) {
    log.error(createLogString(context, text), e)
}

private debug(String context, String text) {
    debug(context, text, true)
}

private debug(String context, String text, Boolean remote) {
    log.debug(createLogString(context, text))
}

