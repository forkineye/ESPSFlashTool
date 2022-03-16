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

import java.io.IOException;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.showMessageDialog;

public class ESPSPaths
{

    private String OsName;
    private String EspPlatformName;
    private String esptool;      // esptool binary to use with path
    private String mkfilesystem; // filesystem binary to use with path
    private String python;       // python binary to use
    private final String execPath = "bin/";             // Path for executables
    private final String fsPath = "fs/";         // Path for filesystem
    private final String fsBin = "filesystem.bin";      // Filesystem Image
    private final String fwPath = "firmware/";          // Path for firmware binaries
    private final String downloadPath = "downloaded/"; // path for downloaded FS objects

    private boolean isWindows = false;

    public void Paths()
    {
    } // Paths

    public void init()
    {
        // System.out.println("Paths - Entry");

        // Detect OS and set binary paths
        detectOS();
        setToolPaths();

        // System.out.println("Paths - Exit");
    }

    private void detectOS()
    {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        System.out.println("          OS: " + os + " / " + arch);

        if (os.contains("win"))
        {
            OsName = "win32/";
            isWindows = true;
        }
        else if (os.contains("mac"))
        {
            OsName = "macos/";
        }
        else if (os.contains("linux") && arch.contains("64"))
        {
            OsName = "linux64/";
        }
        else
        {
            showMessageDialog(null, "Unsupported environment OS: " + os,
                    "Unsupported OS", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
        System.out.println(" Detected OS: " + os);
        System.out.println("     OS Name: " + OsName);

    } // detectOS

    public void updatePlatformName()
    {
        EspPlatformName = ESPSFlashTool.board.chip + "/";

    } // updatePlatformName

    public void setToolPaths()
    {
        mkfilesystem = execPath + OsName + "mklittlefs";
        esptool = execPath + "upload.py";

        if (isWindows)
        {
            python = execPath + OsName + "python3/python";
        }
        else
        {
            python = "python";
            try
            {
                java.lang.Runtime.getRuntime().exec("chmod 550 " + esptool);
                java.lang.Runtime.getRuntime().exec("chmod 550 " + mkfilesystem);
            }
            catch (IOException ex)
            {
                // ignore exception
            }
        }
        System.out.println("    execPath: " + execPath);
        System.out.println("mkfilesystem: " + mkfilesystem);
        System.out.println("     esptool: " + esptool);
        System.out.println("      python: " + python);

    }// setToolPaths

    public String getFsBin()
    {
        return fsBin;
    }

    public String getExecPath()
    {
        return execPath;
    }

    public String getFsPath()
    {
        return fsPath;
    }

    public String getFwPath()
    {
        return fwPath;
    }

    public String getDownloadPath()
    {
        return downloadPath;
    }

    public String getOsName()
    {
        return OsName;
    }

    public void setOsName(String OsName)
    {
        this.OsName = OsName;
    }

    public String getEspPlatformName()
    {
        return EspPlatformName;
    }

    public void setEspPlatformName(String EspPlatformName)
    {
        this.EspPlatformName = EspPlatformName;
    }

    public String getEsptool()
    {
        return esptool;
    }

    public void setEsptool(String esptool)
    {
        this.esptool = esptool;
    }

    public String getMkfilesystem()
    {
        return mkfilesystem;
    }

    public void setMkfilesystem(String mkfilesystem)
    {
        this.mkfilesystem = mkfilesystem;
    }

    public String getPython()
    {
        return python;
    }

    public void setPython(String python)
    {
        this.python = python;
    }

    public boolean IsWindows()
    {
        return isWindows;
    }
} // class Paths
