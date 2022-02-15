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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.swing.JOptionPane;

// JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
class DeviceConfig
{

    private final String DeviceConfigFileName = "config.json";    // ESPixelStick config.json
    private Map<String, Object> LocalConfigMap;
    private Map<String, Object> DeviceConfigMap;

    public void init()
    {
        ProcessLocalDeviceConfigFile();
    } // init

    @SuppressWarnings("unchecked")
    private void ProcessLocalDeviceConfigFile()
    {
        // read the file that is kept locally
        try
        {
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

    @SuppressWarnings("unchecked")
    public void processDownloadedDeviceConfigFiles()
    {
        ESPSFlashTool.flashToolUI.monitor();

        String TargetFileName = GetDownloadFsName();
        Path fsPath = Paths.get(TargetFileName + ".bin");

        // parse it
        Gson gson = new Gson();
        String ConfigFilePath = TargetFileName + "/" + DeviceConfigFileName;
        System.out.println(" ConfigFilePath: " + ConfigFilePath);
        try
        {
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
                    "Unable to find ESPixelStick Downloaded Configuration file",
                    "Failed deserialize", JOptionPane.ERROR_MESSAGE);
        }

        ESPSFlashTool.flashToolUI.populateConfigValues();
    }

    public void ProcessOnDeviceConfigFiles()
    {
        System.out.println("ProcessOnDeviceConfigFiles - Start");
        do // once
        {
            if (ESPSFlashTool.board.name.isEmpty())
            {
                ESPSFlashTool.flashToolUI.appendTxtSystemOutput("Skipping empty board.\nDone\n");
                break;
            }

            // download and parse the device file system
            System.out.println("GetFsFromDevice - Start");
            ImageTask ftask = new ImageTask(ImageTask.ImageTaskActionToPerform.DOWNLOAD_FILESYSTEM); // SwingWorker task to build and flash
            ftask.execute();

        } while (false);

        System.out.println("ProcessOnDeviceConfigFiles - Done");
    } // GetDeviceConfigFiles

    private void GetFsFromDevice(String TargetFsFileName)
    {
        System.out.println("GetFsFromDevice - Start");
        ImageTask ftask = new ImageTask(ImageTask.ImageTaskActionToPerform.DOWNLOAD_FILESYSTEM); // SwingWorker task to build and flash
        ftask.execute();
        // Block until filesystem image is downloaded
/*
        try
        {
            System.out.println("GetFsFromDevice - Task Start");
            ftask.get();
            System.out.println("GetFsFromDevice - Task Done");
        }
        catch (InterruptedException | ExecutionException ex)
        {
            System.out.println("GetFsFromDevice - Task Error");
            Logger.getLogger(ESPSFlashToolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
         */
        System.out.println("GetFsFromDevice - End");
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
            else if (null != LocalConfigMap)
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

    public String GetDownloadFsName()
    {
        String TargetFileName = ESPSFlashTool.board.name + "_" + ESPSFlashTool.board.filesystem.offset + "_" + ESPSFlashTool.board.filesystem.size;
        TargetFileName = TargetFileName.replace(" ", "_");
        return TargetFileName;
    }

    private Object GetJsonValueByKey(String key)
    {
        Object Response = null;
        // System.out.println("           Find: " + key);
        if (null != DeviceConfigMap)
        {
            // System.out.println("DeviceConfigMap: " + DeviceConfigMap.toString());
            Response = GetJsonValueByKey(key, DeviceConfigMap);
        }
        if (null == Response)
        {
            // System.out.println(" LocalConfigMap: " + LocalConfigMap.toString());
            Response = GetJsonValueByKey(key, LocalConfigMap);
        }
        // System.out.println("Response: " + Response);

        return Response;
    } // GetJsonValueByKey

    @SuppressWarnings("unchecked")
    private Object GetJsonValueByKey(String key, Map<String, Object> jsonData)
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

    @SuppressWarnings("unchecked")
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

    private void SetJsonValueByKey(String key, Object value)
    {
        // System.out.println(" LocalConfigMap: " + LocalConfigMap.toString());
        // System.out.println("DeviceConfigMap: " + LocalConfigMap.toString());
        // System.out.println("           Find: " + key);

        SetJsonValueByKey(key, value, LocalConfigMap);
        SetJsonValueByKey(key, value, DeviceConfigMap);

        // System.out.println("  Desired value: " + value);
        // System.out.println("Validated value: " + GetJsonValueByKey(key));
    } // SetJsonValueByKey

    @SuppressWarnings("unchecked")
    private void SetJsonValueByKey(String key, Object value, Map<String, Object> jsonData)
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
        String response = "";
        Object value = GetJsonValueByKey("ssid");
        if (null != value)
        {
            response = value.toString();
        }
        return response;
    }

    public void setPassphrase(String value)
    {
        SetJsonValueByKey("passphrase", value);
    }

    public String getPassphrase()
    {
        String response = "";
        Object value = GetJsonValueByKey("passphrase");
        if (null != value)
        {
            response = value.toString();
        }
        return response;
    }

    public void setHostname(String value)
    {
        SetJsonValueByKey("hostname", value);
    }

    public String getHostname()
    {
        String response = "";
        Object value = GetJsonValueByKey("hostname");
        if (null != value)
        {
            response = value.toString();
        }
        return response;
    }

    public void setId(String value)
    {
        SetJsonValueByKey("id", value);
    }

    public String getId()
    {
        String response = "";
        Object value = GetJsonValueByKey("id");
        if (null != value)
        {
            response = value.toString();
        }
        return response;
    }

    public void setAp_fallback(boolean value)
    {
        SetJsonValueByKey("ap_fallback", value);
    }

    public boolean getAp_fallback()
    {
        boolean response = false;
        Object value = GetJsonValueByKey("ap_fallback");
        if (null != value)
        {
            response = Boolean.valueOf(value.toString());
        }
        return response;
    }

} // class DeviceConfig
