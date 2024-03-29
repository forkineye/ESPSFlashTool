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
public class UpdateBuilder
{

    private static final byte[] SIGNATURE = new byte[]
    {
        'E', 'F', 'U', 0x00
    };
    private static final int VERSION = 1;

    private enum RecordType
    {
        NULL_RECORD(0x00),
        SKETCH_IMAGE(0x01),
        SPIFFS_IMAGE(0x02),
        EEPROM_IMAGE(0x03);

        private final int value;

        private RecordType(int value)
        {
            this.value = value;
        }

        public int getValue()
        {
            return value;
        }
    }

    public static void build(String sketch, String spiffs, String target) throws IOException
    {
        System.out.println("sketch:" + sketch);
        System.out.println("spiffs:" + spiffs);
        System.out.println("target:" + target);

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
            {x bytes of data}
         */
        ESPSFlashTool.flashToolUI.appendTxtSystemOutput("Write EFU header\n");
        dsTarget.write(SIGNATURE, 0, SIGNATURE.length);
        dsTarget.writeShort(VERSION);

        ESPSFlashTool.flashToolUI.appendTxtSystemOutput("Write Sketch Image\n");
        int szSketch = (int) new File(sketch).length();
        dsTarget.writeShort(RecordType.SKETCH_IMAGE.getValue());
        dsTarget.writeInt(szSketch);
        for (int i = 0; i < szSketch; i++)
        {
            dsTarget.write(dsSketch.read());
        }

        ESPSFlashTool.flashToolUI.appendTxtSystemOutput("Write SPIFFS/LittelFs Image\n");
        int szSpiffs = (int) new File(spiffs).length();
        dsTarget.writeShort(RecordType.SPIFFS_IMAGE.getValue());
        dsTarget.writeInt(szSpiffs);
        for (int i = 0; i < szSpiffs; i++)
        {
            dsTarget.write(dsSpiffs.read());
        }
        ESPSFlashTool.flashToolUI.appendTxtSystemOutput("Build EFU Image Done\n");
        dsSketch.close();
        dsSpiffs.close();
        dsTarget.close();
    }
}
