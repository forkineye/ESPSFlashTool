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

import java.io.*;
import java.util.ArrayList;
import javax.swing.*;

public class Board
{

    String name = "";
    String description = "";
    String chip = "";
    String appbin = "";
    Esptool esptool;
    ArrayList<Binfile> binfiles = new ArrayList<Binfile>();
    Filesystem filesystem;

    class Esptool
    {

        String baudrate;
        String options;
        String flashcmd;
    }

    class Binfile
    {

        String name;
        String offset;
    }

    class Filesystem
    {

        String page;
        String block;
        String size;
        String offset;
    }

    // check if bin files exist
    public boolean verify(String path)
    {
        boolean valid = true;
        for (Binfile _binfile : binfiles)
        {
            if (!new File(path + _binfile.name).isFile())
            {
                JOptionPane.showMessageDialog(null, "Firmware file " + _binfile.name + " missing",
                        "Bad Firmware Configuration", JOptionPane.ERROR_MESSAGE);
                valid = false;
            }
        }
        return valid;
    }

    //
    public String getAppbin()
    {
        return appbin;
    }

    @Override
    public String toString()
    {
        return name;
    }

    public void dump()
    {
        System.out.println("              name: " + name);
        System.out.println("       description: " + description);
        System.out.println("              chip: " + chip);
        System.out.println("            appbin: " + appbin);
        System.out.println("  esptool.baudrate: " + esptool.baudrate);
        System.out.println("   esptool.options: " + esptool.options);
        System.out.println("  esptool.flashcmd: " + esptool.flashcmd);
        System.out.println("   filesystem.page: " + filesystem.page);
        System.out.println("  filesystem.block: " + filesystem.block);
        System.out.println("   filesystem.size: " + filesystem.size);
        System.out.println(" filesystem.offset: " + filesystem.offset);

        for (Binfile apbin : binfiles)
        {
            System.out.println("     binfiles.name: " + apbin.name);
            System.out.println("   binfiles.offset: " + apbin.offset);
        }
    }
} // Board
