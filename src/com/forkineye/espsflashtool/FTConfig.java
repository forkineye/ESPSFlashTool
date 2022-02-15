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

import com.google.gson.Gson;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.showMessageDialog;

// ESPSFlashTool JSON Config
class FTConfig
{

    public class FTConfigData
    {

        public String release;
        public String version;
        public String baudrate;
        public ArrayList<Board> boards;
    }

    FTConfigData configData;

    /**
     * Read the FlashTool configuration file
     */
    public void init()
    {
        // Read FT Config and set default device
        try
        {
            Gson gson = new Gson();
            String path = ESPSFlashTool.paths.getFwPath() + "firmware.json";
            // System.out.println("     FW Path: " + path);
            configData = gson.fromJson(
                    new FileReader(path), FTConfigData.class);
        }
        catch (FileNotFoundException ex)
        {
            showMessageDialog(null, "Unable to find firmware configuration file",
                    "Failed deserialize", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        // System.out.println("     release: " + configData.release);
        // System.out.println("     version: " + configData.version);
        // System.out.println("    baudrate: " + configData.baudrate);
        if (configData.boards != null)
        {
            configData.boards.add(0, new Board());
            ESPSFlashTool.board = configData.boards.get(0);
            /*
            for (Board currentBoard : configData.boards)
            {
                currentBoard.dump();
            }
             */
        }
        else
        {
            showMessageDialog(null, "No boards found in configuration file",
                    "Bad configuration", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
        ESPSFlashTool.paths.updatePlatformName();

    } // init

    public String getRelease()
    {
        return configData.release;
    }

    public String getBaudrate()
    {
        return configData.baudrate;
    }

    public ArrayList<Board> getBoards()
    {
        return configData.boards;
    }

} // class FTConfig
