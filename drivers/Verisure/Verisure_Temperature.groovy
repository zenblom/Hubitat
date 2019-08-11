/**
 *  Verisue Temperature 0.1
 *
 *  Author: 
 *    Martin Blomgren
 *
 *
 *    0.1 (June 6 2019)
 *      - Initial Release
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
    definition(name: "Verisure Temperature Sensor", namespace: "zenblom", author: "Martin Blomgren") {
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Sensor"
		
        attribute "timestamp", "string"
        attribute "type", "string"
        attribute "humidity", "number"
        attribute "temperature", "number"
    }
}

def parse(String description) {
    // log.debug("[deviceParse] " + device)
    // log.debug('[device.currentValue("timestamp")] ' + device.currentValue("timestamp"))
    // log.debug('[device.currentValue("humidity")] ' + device.currentValue("humidity"))
    // log.debug('[device.currentValue("type")] ' + device.currentValue("type"))
    // log.debug('[device.currentValue("temperature")] ' + device.currentValue("temperature"))

    def evnt01 = createEvent(name: "timestamp", value: device.currentValue("timestamp"))
    def evnt02 = createEvent(name: "humidity", value: device.currentValue("humidity"))
    def evnt03 = createEvent(name: "type", value: device.currentValue("type"))
    def evnt04 = createEvent(name: "temperature", value: device.currentValue("temperature"))

    return [evnt01, evnt02, evnt03, evnt04]
}

