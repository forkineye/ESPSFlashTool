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

import com.google.gson.*;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.swing.JOptionPane;

// JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
class DeviceConfig
{

    private final String DeviceConfigFileName = "config.json";    // ESPixelStick config.json
    // private JsonElement LocalJsonConfig;
    // private JsonElement DeviceJsonConfig;
    private Map<String, Object> LocalConfigMap;
    private Map<String, Object> DeviceConfigMap;
    private JsonParser parser = new JsonParser();

    public void init()
    {
        ProcessLocalDeviceConfigFile();
    } // init

    private void ProcessLocalDeviceConfigFile()
    {
        // read the file that is kept locally
        try
        {
            // LocalJsonConfig = parser.parse(new FileReader(ESPSFlashTool.paths.getFsPath() + DeviceConfigFileName));
            Gson gson = new Gson();
            LocalConfigMap = (Map<String, Object>) gson.fromJson(new FileReader(ESPSFlashTool.paths.getFsPath() + DeviceConfigFileName), Map.class);
            // System.out.println("LocalConfigMap: " + LocalConfigMap.toString());
            /*
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String prettyJsonString = gson.toJson(LocalJsonConfig);
            System.out.println(prettyJsonString);
             */
        }
        catch (FileNotFoundException ex)
        {
            JOptionPane.showMessageDialog(null,
                    "Unable to find ESPixelStick Default Configuration file",
                    "Failed deserialize", JOptionPane.ERROR_MESSAGE);
        }
    } // ProcessLocalDeviceConfigFile

    public void ProcessOnDeviceConfigFiles()
    {
        do // once
        {
            String TargetFsName = ESPSFlashTool.board.name.replace(' ', '_') + "_"
                    + ESPSFlashTool.board.filesystem.offset
                    + "_"
                    + ESPSFlashTool.board.filesystem.size;
            Path fsPath = Paths.get(TargetFsName + ".bin");

            // download the device file system
            GetFsFromDevice(fsPath.toString());

            if (!Files.exists(fsPath))
            {
                ESPSFlashTool.flashToolUI.appendTxtSystemOutput("ERROR: File System Image file '" + fsPath.toString() + "' was not created");
                break;
            }

            try
            {
                // get the config file
                ESPSFlashTool.ftask.exec(ESPSFlashTool.ftask.cmdParseFileFystem(TargetFsName));

                // parse it
                // DeviceJsonConfig = parser.parse(new FileReader(TargetFsName + "/" + DeviceConfigFileName));
                Gson gson = new Gson();
                String ConfigFilePath = TargetFsName + "/" + DeviceConfigFileName;
                // System.out.println(" ConfigFilePath: " + ConfigFilePath);
                DeviceConfigMap = gson.fromJson(new FileReader(ConfigFilePath), Map.class);
                // System.out.println("DeviceConfigMap: " + DeviceConfigMap.toString());
                /*
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String prettyJsonString = gson.toJson(DeviceJsonConfig);
                System.out.println(prettyJsonString);
                 */
            }
            catch (FileNotFoundException ex)
            {
                JOptionPane.showMessageDialog(null,
                        "Unable to process the ESPixelStick Device Configuration file",
                        "Failed deserialize", JOptionPane.ERROR_MESSAGE);
            }

        } while (false);

    } // GetDeviceConfigFiles

    private void GetFsFromDevice(String TargetFsFileName)
    {
        ESPSFlashTool.flashToolUI.setTxtSystemOutput("-= Retrieving Filesystem Image =-\n");
        // System.out.println("-= Retrieving Filesystem Image =-");
        ESPSFlashTool.ftask.exec(ESPSFlashTool.ftask.cmdGetfilesystem(TargetFsFileName));
    } // GetFsFromDevice

    public boolean serializeConfig()
    {
        boolean retval = true;

        try (Writer fw = new FileWriter(ESPSFlashTool.paths.getFsPath() + DeviceConfigFileName))
        {
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            if (null != DeviceConfigMap)
            {
                gson.toJson(DeviceConfigMap, fw);
            }
            else
            {
                gson.toJson(LocalConfigMap, fw);
            }
        }
        catch (IOException ex)
        {
            JOptionPane.showMessageDialog(null, "Failed to save " + DeviceConfigFileName,
                    "Failed serialize", JOptionPane.ERROR_MESSAGE);
            retval = false;
        }

        return retval;
    }

    private String GetJsonValueByKey(String key)
    {
        String Response = "";
        // System.out.println("           Find: " + key);
        if (null != DeviceConfigMap)
        {
            // System.out.println("DeviceConfigMap: " + DeviceConfigMap.toString());
            Response = GetJsonValueByKey(key, DeviceConfigMap);
        }
        if (Response.isEmpty())
        {
            // System.out.println(" LocalConfigMap: " + LocalConfigMap.toString());
            Response = GetJsonValueByKey(key, LocalConfigMap);
        }
        // System.out.println("Response: " + Response);

        return Response;
    } // GetJsonValueByKey

    private String GetJsonValueByKey(String key, Map<String, Object> jsonData)
    {
        String Response = "";

        String KeyPath = GetJsonKeyPath(key, jsonData);
        // System.out.println("KeyPath: " + KeyPath);
        if (!KeyPath.isEmpty())
        {
            // System.out.println("Processing: " + KeyPath);
            Map<String, Object> CurrentNode = jsonData;
            String[] KeyArray = KeyPath.split("[.]");
            // System.out.println("KeyArray.length: " + KeyArray.length);

            for (String CurrentKey : KeyArray)
            {
                // System.out.println("CurrentKey:" + CurrentKey);
                if (CurrentKey.equals(key))
                {
                    Response = CurrentNode.get(key).toString();
                    // System.out.println("Value:" + Response);

                }
                else
                {
                    CurrentNode = (Map<String, Object>) CurrentNode.get(CurrentKey);
                    // System.out.println("New CurrentNode.keySet:" + CurrentNode.keySet());
                }
            }
        }
        return Response;
    } // GetJsonValueByKey

    private String GetJsonKeyPath(String key, Map<String, Object> jsonData)
    {
        String Response = "";
        do // once
        {
            if (null == jsonData)
            {
                break;
            }

            for (Map.Entry<String, Object> entry : jsonData.entrySet())
            {
                // System.out.println("     entry: '" + entry.getKey() + "'");
                // System.out.println("Entry Type: " + entry.getValue().getClass().getSimpleName());
                if (entry.getKey().equals(key))
                {
                    // System.out.println("Found it");
                    Response = key;
                    break;
                }
                else if (((String) (entry.getValue().getClass().getSimpleName())).contentEquals("LinkedTreeMap")) // LinkedTreeMap
                {
                    // System.out.println("Look lower");

                    Map<String, Object> temp = (Map<String, Object>) entry.getValue();
                    String KeyPath = GetJsonKeyPath(key, temp);
                    if (!KeyPath.isEmpty())
                    {
                        Response = entry.getKey() + "." + KeyPath;
                        break;
                    }
                }
            }
        } while (false);

        return Response;
    } // GetJsonKeyPath

    private void SetJsonValueByKey(String key, String value)
    {
        // System.out.println(" LocalConfigMap: " + LocalConfigMap.toString());
        // System.out.println("DeviceConfigMap: " + LocalConfigMap.toString());
        // System.out.println("           Find: " + key);

        SetJsonValueByKey(key, value, LocalConfigMap);
        SetJsonValueByKey(key, value, DeviceConfigMap);

        // System.out.println("  Desired value: " + value);
        // System.out.println("Validated value: " + GetJsonValueByKey(key));
    } // SetJsonValueByKey

    private void SetJsonValueByKey(String key, String value, Map<String, Object> jsonData)
    {
        String KeyPath = GetJsonKeyPath(key, jsonData);
        // System.out.println("KeyPath: " + KeyPath);
        if (!KeyPath.isEmpty())
        {
            // System.out.println("Processing: " + KeyPath);
            Map<String, Object> CurrentNode = jsonData;
            String[] KeyArray = KeyPath.split("[.]");
            // System.out.println("KeyArray.length: " + KeyArray.length);

            for (String CurrentKey : KeyArray)
            {
                // System.out.println("CurrentKey:" + CurrentKey);
                if (CurrentKey.equals(key))
                {
                    CurrentNode.put(key, value);
                }
                else
                {
                    CurrentNode = (Map<String, Object>) CurrentNode.get(CurrentKey);
                    // System.out.println("New CurrentNode.keySet:" + CurrentNode.keySet());
                }
            }
        }

    } // SetJsonValueByKey

    public void setSSID(String value)
    {
        SetJsonValueByKey("ssid", value);
    }

    public String getSSID()
    {
        return GetJsonValueByKey("ssid");
    }

    public void setPassphrase(String value)
    {
        SetJsonValueByKey("passphrase", value);
    }

    public String getPassphrase()
    {
        return GetJsonValueByKey("passphrase");
    }

    public void setHostname(String value)
    {
        SetJsonValueByKey("hostname", value);
    }

    public String getHostname()
    {
        return GetJsonValueByKey("hostname");
    }

    public void setId(String value)
    {
        SetJsonValueByKey("id", value);
    }

    public String getId()
    {
        return GetJsonValueByKey("id");
    }

} // class DeviceConfig
