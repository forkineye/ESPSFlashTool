/*
 * Copyright 2022 Shelby Merrick
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

public class ESPSFlashTool
{

    public static ESPSPaths paths = new ESPSPaths();

    public static FTConfig ftconfig = new FTConfig();
    public static ESPSFlashToolUI flashToolUI = new ESPSFlashToolUI();
    public static Board board = new Board();
    public static ESPSSerialPort port = new ESPSSerialPort();
    public static DeviceConfig deviceConfig = new DeviceConfig();
    // public static ImageTask ftask = new ImageTask(true); // SwingWorker task to build and flash

    public static void main(String[] args)
    {
        paths.init();
        ftconfig.init();
        deviceConfig.init();
        flashToolUI.init();

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                flashToolUI.setVisible(true);
            }
        });

    } // main

}// class ESPSFlashTool
