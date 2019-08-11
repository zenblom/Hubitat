/**
 *  Innr SP 120 Smart Plug 0.1
 *
 *  Author: 
 *    Martin Blomgren
 *
 *
 *    0.1 (May 27 2019)
 *      - Initial Release
 *      - Reports Active Power (state power in Watts)
 *      - Reports RMS Voltage (state voltage in Volts)
 *		- Reports Current Summation Delivered (state energi in kWh, unsure about resolution...)
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
metadata {
    definition (name: "Innr SP 120", namespace: "zenblom", author: "Martin Blomgren") {
        capability "Actuator"
		capability "Sensor"
        capability "Configuration"
		capability "Energy Meter"
        capability "Refresh"
        capability "Switch"
        capability "Power Meter"
		capability "Voltage Measurement"

        fingerprint profileId: "C05E", inClusters: "0000,0004,0003,0006,0008,0005,0B04,0702,000A", outClusters: "0003,0019,000A", manufacturer: "innr", model: "SP 120", deviceJoinName: "Innr SP 120"
    }
	
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def updated(){
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)
    
    configure()
}

// Parse incoming device messages to generate events
def parse(String description) {
    if (logEnable) log.debug "parse description: ${description}"
    //if (description.startsWith("catchall")) return
			
    def event = zigbee.getEvent(description)
	
	if (!event) {
		event = getDescription(zigbee.parseDescriptionAsMap(description))	
	}
	
    if (event) {
		if (txtEnable) log.info "$device eventMap name: ${event.name} value: ${event.value}"	
        sendEvent(event)
    } else {
        if (logEnable) log.warn "DID NOT PARSE MESSAGE for description : $description"
        if (logEnable) log.debug zigbee.parseDescriptionAsMap(description)
    }
}

def getDescription(descMap) {

	switch (descMap.cluster) {
		case "0702":
			if (descMap.attrId == "0000") {
				if(descMap.value != "ffff") {
					return [name: "energy", value: zigbee.convertHexToInt(descMap.value)]
				}
			}
			break
				
		case "0B04":
			if (descMap.attrId == "050B" && descMap.additionalAttrs) {
				def powerValue = ((double)zigbee.convertHexToInt(descMap.additionalAttrs[0].value)) / 10
				return	[name: "power", value: powerValue]

			} else if (descMap.attrId == "0505") {
				if(descMap.value != "ffff") {
					return	[name: "voltage", value: zigbee.convertHexToInt(descMap.value)]
				}
			}
			break

		default:
			return null
	}
	
	return null
}

def off() {
    zigbee.off()
}

def on() {
    zigbee.on()
}

def refresh() {
	if (logEnable) log.debug "refresh"
	
	def refreshCmds = []
    refreshCmds += zigbee.onOffRefresh()
    refreshCmds += zigbee.onOffConfig()
	
    refreshCmds += zigbee.readAttribute(0x0702, 0x0000) // Current Summation Delivered (kWh?)
    refreshCmds += zigbee.readAttribute(0x0B04, 0x050B) // Active Power (W)
    refreshCmds += zigbee.readAttribute(0x0B04, 0x0505) // RMS Voltage (V)
	
    return refreshCmds	
}

def configure() {
    log.debug "Configuring Reporting and Bindings."

    def configCmds = [] 
    configCmds += zigbee.onOffRefresh() 
    configCmds += zigbee.onOffConfig()
    
    // ClusterId, AttributeId, DataType, Min Reporting, Max Reporting, 
    //List configureReporting(Integer clusterId, Integer attributeId, Integer dataType, Integer minReportTime, Integer maxReportTime, Integer reportableChange = null, Map additionalParams=[:], int delay = STANDARD_DELAY_INT)
	configCmds += zigbee.configureReporting(0x0702, 0x0000, 0x25, 1, 300, 0x01) // 0x0702=METERING_CLUSTER_ID, 0x0400=Curent Summation Delivered (kWh), type Zcl48BitUint
	configCmds += zigbee.configureReporting(0x0B04, 0x050B, 0x29, 0, 300, null) // 0x0B04=ELECTRICAL_MEASUREMENT_CLUSTER_ID, 0x050B=Active Power (W), type Zcl16BitInt
	configCmds += zigbee.configureReporting(0x0B04, 0x0505, 0x29, 1, 300, 0x01) // 0x0B04=ELECTRICAL_MEASUREMENT_CLUSTER_ID, 0x0505=RMS Voltage (V), type Zcl16BitInt

    return refresh() + configCmds	
}
