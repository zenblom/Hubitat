/**
 *  Verisue Smartplug 0.1
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
    definition(name: "Verisure Smartplug", namespace: "zenblom", author: "Martin Blomgren") {
        capability "Switch"
    }
}

def parse(String description) {
    //log.debug("[deviceParse] " + device)
    //log.debug('[device.currentValue("switch")] ' + device.currentValue("switch"))

    def evnt01 = createEvent(name: "switch", value: device.currentValue("switch"))

    return evnt01
}

void on() {
	parent.smartPlugHandler(device.deviceNetworkId)
}

void off() {
	parent.smartPlugHandler(device.deviceNetworkId)
}

