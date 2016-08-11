/*
 * Copyright 2016 sporadic.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.forkineye.espsflashtool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 *
 * @author sporadic
 */

// Handles building ESPixelStick Firmware Updates - .efu files
public class UpdateBuilder {
    private static final byte[] SIGNATURE = new byte[] {'E', 'F', 'U', 0x00};
    private static final int VERSION = 1;
    
    private enum RecordType {
        SKETCH_IMAGE(0x00),
        SPIFFS_IMAGE(0x01),
        EEPROM_IMAGE(0x02),
        SPIFFS_FILE(0x03);
        
        private final int value;
        
        private RecordType(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    public static void build (String sketch, String spiffs, String target) throws IOException {
        DataInputStream dsSketch = new DataInputStream(new FileInputStream(sketch));
        DataInputStream dsSpiffs = new DataInputStream(new FileInputStream(spiffs));
        DataOutputStream dsTarget = new DataOutputStream(new FileOutputStream(target));
        
        /*
        Sketch + SPIFFS combined OTA format
            32bit signature
            16bit version

            {n # of records}
            16bit record type
            32bit size
            16bit filename size
            xxbit filename (variable)
            {x bytes of data}
        */
                
        // Write header
        dsTarget.write(SIGNATURE, 0, SIGNATURE.length);
        dsTarget.writeShort(VERSION);
        
        // Write Sketch Image
        int szSketch = (int)new File(sketch).length();
        dsTarget.writeShort(RecordType.SKETCH_IMAGE.getValue());
        dsTarget.write(szSketch);
        dsTarget.write(0);
        for (int i = 0; i < szSketch; i++)
            dsTarget.write(dsSketch.read());
        
        // Write SPIFFS Image
        int szSpiffs = (int)new File(spiffs).length();
        dsTarget.writeShort(RecordType.SPIFFS_IMAGE.getValue());
        dsTarget.write(szSpiffs);
        dsTarget.write(0);
        for (int i = 0; i < szSpiffs; i++)
            dsTarget.write(dsSpiffs.read());

        dsSketch.close();
        dsSpiffs.close();
        dsTarget.close();
    }
}
