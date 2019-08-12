/**
 *  Verisue Alarm 0.1
 *
 *  Author: 
 *    Martin Blomgren
 *
 * Thanks to Anders Sveen & Martin Carlsson for the embryo with alarmstate and temperature
 * sensors made for SmartThings (https://github.com/anderssv/smartthings-verisure)
 * And of course Per Sandström for the massive work with the Python Verisure module
 * (https://github.com/persandstrom/python-verisure)
 *
 *    0.1 (June 6 2019)
 *      - Initial Release
 *      - Support for ONE installation
 *      - Child drivers for 
 *          Alarm Status, 
 *          Door/Window Sensor, 
 *          Yale Doorman Lock, 
 *          Smartplug, 
 *          Temperature Sensor
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
 */
import java.net.URLEncoder

def version() {"v0.1"}

metadata {
    definition (name: "Verisure Alarm", namespace: "zenblom", author: "Martin Blomgren") {
		capability "Initialize"
		capability "SecurityKeypad"
		
		attribute "device.status", "string"
		attribute "armaway", "string"
		attribute "armhome", "string"
		attribute "disarm", "string"
		attribute "alarm", "enum"
		attribute "lastaction", "string"
		
        attribute "status", "string"
        attribute "loggedBy", "string"
        attribute "loggedWhen", "Date"
		
		//command "clearInstallationCache"
		
    }
	
    preferences {
        section("Disable updating (polling) here") {
            input "enabled", "bool", defaultValue: "true", title: "Enabled?"
        }

        section("Authentication") {
            input "username", "text", title: "Username"
            input "password", "password", title: "Password"
            input "code", "password", title: "Code"
        }

        /*
        section("Action when disarmed") {
            input "disarmedAction", "enum", title: "Action for unarmed", options: actions, required: false
            input "armedAction", "enum", title: "Action for armed", options: actions, required: false
            input "armedHomeAction", "enum", title: "Action for armed home", options: actions, required: false
        }
        */

        section("Logging") {
            input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
            input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        }
    }
}

// --- Hubitat lifecycle
def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def installed() {
    initialize()
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)

    unschedule()
    initialize()
}

def uninstalled() {
    log.info "[Verisure] uninstalled: Uninstalling Verisure and removing child devices"
    removeChildDevices(getChildDevices())
}

def initialize() {
    try {
        if (logEnable) log.debug "[Verisure] initialize: Scheduling polling"
        schedule("0 * * ? * *", pollStatus)
    } catch (e) {
        log.warn "[Verisure] initialize: Could not initialize app. Not scheduling updates. $e"
    }
}

// --- Getters

def getBaseUrl() {
	if (state.serverUrl == null) {
    	switchBaseUrl()
    }
    return state.serverUrl
}

def switchBaseUrl() {
	if (state.serverUrl == "https://e-api01.verisure.com/xbn/2") {
    	state.serverUrl = "https://e-api02.verisure.com/xbn/2"
    } else {
    	state.serverUrl = "https://e-api01.verisure.com/xbn/2"
    }
    if (logEnable) log.debug "[Verisure] switchBaseUrl: Base url switched to ${state.serverUrl}"
}

private hasChildDevice(id) {
    return getChildDevice(id) != null
}

def getAlarmState() {
    if (logEnable) log.debug "[Verisure] getAlarmState: Retrieving cached alarm state"
    return state.previousAlarmState
}

// -- Fetching data from Verisure
def clearInstallationCache() {
	state.sessionCookie = null
	state.installationId = null
}

def pollStatus() {
    if (logEnable) log.debug "[Verisure] transaction:  ===== START_UPDATE"

    // Backwards compatibility because of rename. Can be deleted after a little while.
    state.disarmedAction = disarmedAction ? disarmedAction : unarmedAction

    // Handling some parameter setup, copying from settings to enable programmatically changing them
    state.app_version = "0.1"

    if (logEnable) log.debug "[Verisure] checkPeriodically: Periodic check from timer"
    if (enabled != null && !enabled) {
        if (logEnable) log.debug "[Verisure] pollStatus: Skip updating as polling is disabled in settings."
        return
    } else if (state.throttleCounter && state.throttleCounter > 0) {
        if (logEnable) log.warn "[Verisure] pollStatus: Too many requests, postponing poll another " + state.throttleCounter + " minutes."
        state.throttleCounter = state.throttleCounter - 1
        return
    }
    try {
		if (state.installationId) {
			// get overview & then update devices
            if (logEnable) log.debug "[Verisure] pollStatus: Skipping cookie & installationId"
			getOverview()
		} else if (state.sessionCookie) {
			// get installationId, overview & then update devices
            if (logEnable) log.debug "[Verisure] pollStatus: Skipping installationId"
			getInstallations()
		} else {
			// get cookie, installationId, overview & then update devices
			if (logEnable) log.debug "[Verisure] pollStatus: Fetch cookie & InstallationId"
			createCookie()
			
		}
		
    } catch (Exception e) {
        log.warn "pollStatus: Error updating alarm state $e"
    }
}

def createCookie() {
		def params = [
			uri        : getBaseUrl() + "/cookie",
			headers    : [
				requestContentType: "application/json",
				Authorization: "Basic " + ("CPE/" + username + ":" + password).bytes.encodeBase64(),
			],
			contentType: "application/json"
		]
		asynchttpPost('handleCreateCookieResponse', params)	
}

def handleCreateCookieResponse(response, data) {
	if (!checkResponse("handlePollResponse", response)) return
    def responseJson = response.getJson()
	state.sessionCookie = responseJson["cookie"]
	if (logEnable) log.debug "[Verisure] pollStatus: Session cookie received."
	
	getInstallations()
}

def getInstallations() {
	// Get the Installtion Id
    if (logEnable) log.debug "[Verisure] getInstallations: Finding installation"
    def params = [
		uri : getBaseUrl() + "/installation/search?email=" + URLEncoder.encode(username),
        contentType: "application/json",
        headers : [
			requestContentType: "application/json",
            Cookie: "vid=" + state.sessionCookie
		]
    ]

    httpGet(params) { installationResponse ->
		state.installationId = installationResponse.data[0]["giid"]
        if (logEnable) log.debug "[Verisure] getInstallationId: Found installation id $installationId"
		
		getOverview()
    }

}

def getOverview() {
		// Get the Overview Status for all devices
		if (logEnable) log.debug "[Verisure] handlePollResponse: Fetching overview"
		def paramsOverview = [
			uri        : getBaseUrl() + "/installation/" + state.installationId + "/overview",
			contentType: "application/json",
			headers    : [
				requestContentType: "application/json",
				Cookie: "vid=" + state.sessionCookie
			]
		]

		asynchttpGet('updateDevices', paramsOverview)	
}

// --- Response handlers for async http
def checkResponse(context, response) {
    if (response.hasError() || response.getStatus() != 200) {
		state.latestError = new Date().toLocaleString()
        log.warn "[Verisure] $context : Did not get correct response. Got response ${response.getErrorData()} "
        if (response.hasError()) {
            if (response.getErrorData().contains("Request limit has been reached")) {
                state.throttleCounter = 2
            } else if (response.getErrorData().contains("XBN Database is not activated")) {
            	switchBaseUrl()
            }
            if (logEnable) log.debug "[Verisure] Response has error: " + response.getErrorData()
        } else {
            if (logEnable) log.debug "[Verisure] Did not get 200. Response code was: " + Integer.toString(response.getStatus())
        }
        return false
    }
    return true
}


def updateDevices(response, data) {
    if (logEnable) log.debug "[Verisure] updateDevices: Overview response received."

    if (!checkResponse("handleOverviewResponse", response)) return

    def responseJson = response.getJson()
    if (logEnable) log.debug "[Verisure] updateDevices: $responseJson"
    if (responseJson["armState"]) parseAlarmState(responseJson["armState"])
    if (responseJson["climateValues"]) parseSensorResponse(responseJson["climateValues"])
    if (responseJson["smartPlugs"]) parseSmartPlugResponse(response.json["smartPlugs"])
    if (responseJson["doorWindow"]) parseDoorWindowResponse(response.json["doorWindow"])
    if (responseJson["doorLockStatusList"]) parseLockResponse(response.json["doorLockStatusList"])

    if (logEnable) log.debug "[Verisure] updateDevices: Overview response handled"
    if (logEnable) log.debug "[Verisure] transaction: ===== END_UPDATE"
}

// --- Parse responses to Hubitat child devices

def parseAlarmState(alarmState) {
    if (state.previousAlarmState == null) {
        state.previousAlarmState = alarmState.statusType
    }

    if (logEnable) log.debug "[Verisure] parseAlarmState: Updating alarm device"
    if (device.currentValue('status') != alarmState.statusType) sendEvent(name: "status", value: alarmState.statusType)
	if (device.currentValue('loggedBy') != alarmState.name) sendEvent(name: "loggedBy", value: alarmState.name)
	if (device.currentValue('loggedWhen') != alarmState.date) sendEvent(name: "loggedWhen", value: alarmState.date)
	state.lastUpdate = new Date().toLocaleString()
	if (txtEnable && device.currentValue('status') != alarmState.statusType) log.info "[Verisure] alarmDevice.updated: | Status: " + alarmState.statusType + " | LoggedBy: " + alarmState.name + " | LoggedWhen: " + alarmState.date

    if (alarmState.statusType != state.previousAlarmState) {
        if (logEnable) log.debug "[Verisure] updateAlarmState: State changed, execution actions"
        state.previousAlarmState = alarmState.statusType
        triggerActions(alarmState.statusType)
    } else {
        if (logEnable) log.debug "[Verisure] updateAlarmState: State not change. Not triggering routines."
    }
}

def parseSensorResponse(climateState) {
    if (logEnable) log.debug "[Verisure] parseSensorResponse: Parsing climate sensors"
    //Add or Update Sensors
    climateState.each { device ->
        Double tempNumber = device.temperature
        Double humidityNumber = device.humidity

        if (!hasChildDevice(device.deviceLabel)) {
			//addChildDevice(String typeName, String deviceNetworkId, Map properties = [:])
            addChildDevice("Verisure Temperature Sensor", device.deviceLabel, [
				isComponent: false, // true or false, if true, device will still show up in device list but will not be able to be deleted or edited in the UI. If false, device can be modified/deleted on the UI.
				name : device.deviceType, // name of child device, if not specified, driver name is used. (driver name is used when pairing i.e. Zigbee devices)
				label      : device.deviceArea // label of child device, if not specified it is left blank. (Actual name like Livingroom motion) 
            ])
            if (txtEnable) log.info "[Verisure] climateDevice.create: " + device.toString()
        }
		
		def existingDevice = getChildDevice(device.deviceLabel) // deviceNetworkId

		if (txtEnable && (
            existingDevice.currentValue('humidity') != humidityNumber ||
            existingDevice.currentValue('timestamp') != device.times ||
            existingDevice.currentValue('type') != device.deviceType ||
            existingDevice.currentValue('temperature') != tempNumber
        )) log.info "[Verisure] climateDevice.updated: " + device.deviceArea + " | Humidity: " + humidityNumber + " | Temperature: " + tempNumber
		if (existingDevice.currentValue('humidity') != humidityNumber) existingDevice.sendEvent(name: "humidity", value: humidityNumber)
		if (existingDevice.currentValue('timestamp') != device.times) existingDevice.sendEvent(name: "timestamp", value: device.times)
		if (existingDevice.currentValue('type') != device.deviceType) existingDevice.sendEvent(name: "type", value: device.deviceType)
		if (existingDevice.currentValue('temperature') != tempNumber) existingDevice.sendEvent(name: "temperature", value: tempNumber)
    }
}

def parseSmartPlugResponse(smartPlugState) {
    if (logEnable) log.debug "[Verisure] parseSmartPlugResponse: Parsing smartplugs"
    //Add or Update Sensors
    smartPlugState.each { device ->
        String switchString = (device.currentState == "ON") ? "on" : "off"
        if (!hasChildDevice(device.deviceLabel)) {
            addChildDevice("Verisure Smartplug", device.deviceLabel, [
				isComponent: false, // true or false, if true, device will still show up in device list but will not be able to be deleted or edited in the UI. If false, device can be modified/deleted on the UI.
				name : device.deviceType, // name of child device, if not specified, driver name is used. (driver name is used when pairing i.e. Zigbee devices)
				label      : device.area // label of child device, if not specified it is left blank. (Actual name like Livingroom motion) 
            ])

            if (txtEnable) log.info "[Verisure] switchDevice.created: " + device.toString()
        }
         
		def childDevice = getChildDevice(device.deviceLabel)

        if (txtEnable && childDevice.currentValue('switch') != switchString) log.info "[Verisure] switchDevice.updated: " + device.area + " | State: " + device.currentState
		if (childDevice.getLabel() != device.area) childDevice.setLabel(device.area)
        if (childDevice.currentValue('switch') != switchString) childDevice.sendEvent(name: "switch", value: switchString)		
    }
}

def parseDoorWindowResponse(doorWindowState) {
    if (logEnable) log.debug "[Verisure] parseDoorWindowResponse: Parsing Door/Window Sensors"
    //Add or Update Contacts
    doorWindowState.doorWindowDevice.each { device ->
        String contactString = (device.state == "CLOSE") ? "closed" : "open"

        if (!hasChildDevice(device.deviceLabel)) {
            addChildDevice("Verisure Door/Window Sensor", device.deviceLabel, [
				isComponent: false, // true or false, if true, device will still show up in device list but will not be able to be deleted or edited in the UI. If false, device can be modified/deleted on the UI.
				name : device.deviceType, // name of child device, if not specified, driver name is used. (driver name is used when pairing i.e. Zigbee devices)
				label      : device.area // label of child device, if not specified it is left blank. (Actual name like Livingroom motion) 
            ])

            if (txtEnable) log.info "[Verisure] contactDevice.created: " + device.toString()
        } else {
            def childDevice = getChildDevice(device.deviceLabel)
	
            if (txtEnable && 
                childDevice.currentValue('contact') != contactString &&
                childDevice.currentValue('timestamp') != device.reportTime) log.info "[Verisure] contactDevice.updated: " + device.area + " | State: " + device.state
			if (childDevice.getLabel() != device.area) childDevice.setLabel(device.area)
            if (childDevice.currentValue('contact') != contactString) childDevice.sendEvent(name: "contact", value: contactString)
            if (childDevice.currentValue('timestamp') != device.reportTime) childDevice.sendEvent(name: "timestamp", value: device.reportTime)
        }
    }
}

def parseLockResponse(lockState) {
    if (logEnable) log.debug "[Verisure] parseLockResponse: Parsing locks"
    //Add or Update Sensors
    lockState.each { device ->
        //String String = (device.currentState == "ON") ? "on" : "off"
        if (!hasChildDevice(device.deviceLabel)) {
            addChildDevice("Verisure Lock", device.deviceLabel, [
				isComponent: false, // true or false, if true, device will still show up in device list but will not be able to be deleted or edited in the UI. If false, device can be modified/deleted on the UI.
				name: device.deviceType, // name of child device, if not specified, driver name is used. (driver name is used when pairing i.e. Zigbee devices)
				label: device.area // label of child device, if not specified it is left blank. (Actual name like Livingroom motion) 
            ])

            if (txtEnable) log.info "[Verisure] lockDevice.created: " + device.toString()
        }
         
		def childDevice = getChildDevice(device.deviceLabel)

        if (txtEnable && childDevice.currentValue('lock') != device.lockedState.toLowerCase()) log.info "[Verisure] lockDevice.updated: " + device.area + " | State: " + device.currentLockState
		childDevice.setLabel(device.area)
		//lock - ENUM ["locked", "unlocked with timeout", "unlocked", "unknown"]
		switch (device.lockedState) {
			case "UNLOCKED":
				if (childDevice.currentValue('lock') != "unlocked") childDevice.sendEvent(name: "lock", value: "unlocked")
				break
			case "LOCKED":
				if (childDevice.currentValue('lock') != "locked") childDevice.sendEvent(name: "lock", value: "locked")
				break
			default:
				if (childDevice.currentValue('lock') != "unknown") childDevice.sendEvent(name: "lock", value: "unknown")
				break
		}
    }
}


// -- Setters for Child Devices
def smartPlugHandler(String deviceId) {
    def currentDevice = getChildDevice(deviceId)

    String switchState = (currentDevice.currentValue("switch")  == "on") ? "False" : "True"
    if (logEnable) log.debug "switchDevice.event: smartPlugHandler: Event from " + deviceId + " with value " + switchState + " , session cookie: " + state.sessionCookie
    if (logEnable) log.debug "smartplug: Change state on ${deviceId} to ${switchState} on installtionId ${state.installationId}"

    def params = [
    	uri: getBaseUrl() + "/installation/" + state.installationId + "/smartplug/state",
        contentType: "application/json",
        headers: [
			requestContentType: "application/json",
        	Cookie: "vid=" + state.sessionCookie
        ],
        body: '[{"deviceLabel":"' + deviceId + '","state":"' + switchState + '"}]'
    ]

    asynchttpPost(handleSmartPlugStateResponse, params, ["device": currentDevice, "state": switchState])	
}

def handleSmartPlugStateResponse(response, data) {
	def status = response.getStatus()
	if (logEnable) log.debug "smartplugHandlerResponse: $status"
	stateValue = (data["state"] == "True") ? "on" : "off"
	if (status == 200) data["device"].sendEvent(name: "switch", value: stateValue)		
}

def childLock(String deviceId) {
    def currentDevice = getChildDevice(deviceId)
	if (txtEnable) log.info "Lock ${currentDevice}"
    if (logEnable) log.debug "[Verisure] lockDevice.event: childLock: Event from " + deviceId + " with value " + currentDevice.currentValue("lock") + " , session cookie: " + state.sessionCookie
	
	def encodedLabel = URLEncoder.encode(deviceId, "UTF-8")
    def params = [
    	uri: getBaseUrl() + "/installation/" + state.installationId + "/device/" + encodedLabel + "/lock",
        contentType: "application/json",
        headers: [
			requestContentType: "application/json",
        	Cookie: "vid=" + state.sessionCookie
        ],
		body: '{"code": ' + code + '}'
    ]
    asynchttpPut(handleLockStateResponse, params, ["device": currentDevice, "state": "locked"])	
}

def childUnlock(String deviceId) {
    def currentDevice = getChildDevice(deviceId)
	if (txtEnable) log.info "Unlock ${currentDevice}"
    if (logEnable) log.debug "[Verisure] unlockDevice.event: childUnlock: Event from " + deviceId + " with value " + currentDevice.currentValue("lock") + " , session cookie: " + state.sessionCookie

	def encodedLabel = URLEncoder.encode(deviceId, "UTF-8")
    def params = [
    	uri: getBaseUrl() + "/installation/" + state.installationId + "/device/" + encodedLabel + "/unlock",
        contentType: "application/json",
        headers: [
			requestContentType: "application/json",
        	Cookie: "vid=" + state.sessionCookie
        ],
		body: '{"code": ' + code + '}'
    ]
    asynchttpPut(handleLockStateResponse, params, ["device": currentDevice, "state": "unlocked"])	
}

def handleLockStateResponse(response, data) {
	def status = response.getStatus()
	if (logEnable) log.debug "handleLockStateResponse: $status"
	
	if (status == 200) {
		def responseData = response.getJson()
		if (logEnable) log.debug "handleLockStateResponse: $responseData"
		// [doorLockStateChangeTransactionId:150668366]
		data["device"].sendEvent(name: "lock", value: data.state)
		
	} else if (status == 400) {
		// <errorMessage>The requested doorlock state is not possible to apply due to state already set</errorMessage>
	}

}

// -- Helper methods

def triggerActions(alarmState) {
    if (alarmState == "ARMED_AWAY" && armedAction) {
        executeAction(armedAction)
    } else if (alarmState == "DISARMED" && state.disarmedAction) {
        executeAction(state.disarmedAction)
    } else if (alarmState == "ARMED_HOME" && armedHomeAction) {
        executeAction(armedHomeAction)
    } else {
        if (logEnable) log.debug "[Verisure] triggerActions: No actions found for state: " + alarmState
    }
}

def executeAction(action) {
    if (logEnable) log.debug "[Verisure] executeAction: Executing action ${action}"
    //location.helloHome?.execute(action)
}

private removeChildDevices(delete) {
    delete.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}
