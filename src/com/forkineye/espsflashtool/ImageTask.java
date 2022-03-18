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

import com.fazecast.jSerialComm.SerialPort;
import com.forkineye.espsflashtool.ImageTask.ImageTaskActionToPerform;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.showMessageDialog;
import javax.swing.SwingWorker;

class ImageTask extends SwingWorker<ImageTaskActionToPerform, String>
{

    public enum ImageTaskActionToPerform
    {
        NOTHING,
        ERASE_FLASH,
        DOWNLOAD_FILESYSTEM,
        UNPACK_FILESYSTEM,
        CREATE_FILESYSTEM,
        MAKEEFU,
        UPLOAD_FIRMWARE,
        CREATE_AND_UPLOAD_ALL
    }

    private int state = 0;
    private int status = 0;
    private ImageTaskActionToPerform flashAction = ImageTaskActionToPerform.NOTHING;
    private final String fsBin = "filesystem.bin";      // Filesystem Image

    public ImageTask(ImageTaskActionToPerform action)
    {
        System.out.println("ImageTask Created");
        flashAction = action;
        EnsureSerialPortIsOff();
    }

    private void EnsureSerialPortIsOff()
    {
        if (ESPSFlashTool.port != null)
        {
            SerialPort serial = ESPSFlashTool.port.getPort();
            if (serial != null)
            {
                ESPSFlashTool.port.getPort().closePort();
            }
        }

    } // EnsureSerialPortIsOff

    @Override
    public void process(java.util.List<String> messages)
    {
        for (String message : messages)
        {
            ESPSFlashTool.flashToolUI.appendTxtSystemOutput(message + "\n");
            System.out.println(message);
        }
    }

    @Override
    protected ImageTaskActionToPerform doInBackground()
    {
        System.out.println("doInBackground - Start");
        status = 0;
        ImageTaskActionToPerform Response = ImageTaskActionToPerform.NOTHING;
        ESPSFlashTool.paths.setToolPaths();

        switch (flashAction)
        {
            case ERASE_FLASH:
            {
                System.out.println("doInBackground - ERASE_FLASH");
                status = EraseDeviceFlash();
                break;
            }
            case DOWNLOAD_FILESYSTEM:
            {
                System.out.println("doInBackground - DOWNLOAD_FILESYSTEM");
                status = DownloadDeviceFileSystem();
            }
            case UNPACK_FILESYSTEM:
            {
                System.out.println("doInBackground - UNPACK_FILESYSTEM");
                status = UnpackDeviceFileSystem();
                ESPSFlashTool.deviceConfig.processDownloadedDeviceConfigFiles();
                break;
            }
            case CREATE_FILESYSTEM:
            {
                System.out.println("doInBackground - CREATE_FILESYSTEM");
                status = CreateFileSystemImage();
                break;
            }
            case MAKEEFU:
            {
                status = CreateFileSystemImage();
                try
                {
                    UpdateBuilder.build(
                            ESPSFlashTool.paths.getFwPath() + ESPSFlashTool.board.getAppbin(),
                            ESPSFlashTool.paths.getFwPath() + ESPSFlashTool.paths.getFsBin(),
                            ESPSFlashTool.flashToolUI.getEfuTarget());
                }
                catch (IOException ex)
                {
                    showMessageDialog(null, "Failed to build firmware update\n"
                            + ex.getMessage(), "Failed EFU Build", JOptionPane.ERROR_MESSAGE);
                }

                break;
            }

            case UPLOAD_FIRMWARE:
            {
                System.out.println("doInBackground - UPLOAD_FIRMWARE");
                status = UploadFwImages();
                break;
            }
            case CREATE_AND_UPLOAD_ALL:
            {
                System.out.println("doInBackground - CREATE_AND_UPLOAD_ALL");
                status = CreateFileSystemImage();
                status |= EraseDeviceFlash();
                status |= UploadFwImages();
                break;
            }
            case NOTHING:
            default:
            {
                // Nothing to do
                System.out.println("doInBackground - NOTHING");
            }
        }
        System.out.println("doInBackground - End");
        return Response;
    }

    @Override
    public void done()
    {
        ESPSFlashTool.flashToolUI.monitor();
        if (status == 0)
        {
            ESPSFlashTool.flashToolUI.appendTxtSystemOutput("\n-= ESP Action Complete =-");
        }
        else
        {
            ESPSFlashTool.flashToolUI.appendTxtSystemOutput("\n*** ESP Action FAILED ***");
        }
        ESPSFlashTool.flashToolUI.enableInterface();
    }

    private int exec(List<String> command)
    {
        System.out.println("exec - Start");
        int response = 0;

        do // once
        {
            if (command.isEmpty())
            {
                // nothing to do
                break;
            }

            String outCommand = "";
            for (String opt : command)
            {
                outCommand = (outCommand + " " + opt);
            }
            publish("Command: " + outCommand);

            try
            {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String s;
                BufferedReader stdout = new BufferedReader(
                        new InputStreamReader(p.getInputStream()));

                while ((s = stdout.readLine()) != null && !isCancelled())
                {
                    publish(s);
                }

                if (!isCancelled())
                {
                    state = p.waitFor();
                }

                publish("Command: " + outCommand + " - Done");

                p.getInputStream().close();
                p.getOutputStream().close();
                p.getErrorStream().close();
                p.destroy();
                response = p.exitValue();
            }
            catch (IOException | InterruptedException ex)
            {
                ex.printStackTrace(System.err);
                response = -1;
            }
        } while (false);

        System.out.println("exec - End");
        return response;
    }

    private Integer DownloadDeviceFileSystem()
    {
        System.out.println("DownloadDeviceFileSystem - Start");
        Integer Response = 0;
        publish("-= Retreiving Filesystem Image =-");

        Response = exec(cmdGetfilesystem());
        if (Response != 0)
        {
            showMessageDialog(null, "Failed to Download the Filesytem Image from the device\n"
                    + "Verify your device is properly connected and in programming mode.",
                    "Failed cmdGetfilesystem", JOptionPane.ERROR_MESSAGE);
        }
        publish("DownloadDeviceFileSystem - End");
        return Response;
    }

    private Integer UnpackDeviceFileSystem()
    {
        System.out.println("UnpackDeviceFileSystem - Start");
        Integer Response = 0;
        publish("-= Unpacking Filesystem Image =-");

        Response = exec(cmdUnpackfilesystem());
        if (Response != 0)
        {
            showMessageDialog(null, "Failed to Download the Filesytem Image from the device\n"
                    + "Verify your device is properly connected and in programming mode.",
                    "Failed cmdGetfilesystem", JOptionPane.ERROR_MESSAGE);
        }
        publish("UnpackDeviceFileSystem - End");
        return Response;
    }

    private Integer CreateFileSystemImage()
    {
        Integer Response = 0;

        // Build Filesystem
        publish("-= Building Filesystem Image =-");

        Response = exec(cmdMkfilesystem());
        if (Response != 0)
        {
            showMessageDialog(null, "Failed to make Filesytem Image",
                    "Failed mkfilesystem", JOptionPane.ERROR_MESSAGE);
        }
        publish("-= Building Filesystem Image - Done =-");
        return Response;
    }

    private Integer EraseDeviceFlash()
    {
        Integer Response = 0;

        publish("\n-= Erasing ESP Flash =-");

        Response = exec(cmdEsptoolErase());
        if (Response != 0)
        {
            showMessageDialog(null, "Failed to Erase ESP Device Flash\n"
                    + "Verify your device is properly connected and in programming mode.",
                    "Failed mkfilesystem", JOptionPane.ERROR_MESSAGE);
        }
        publish("\n-= Erasing ESP Flash - Done =-");

        return Response;
    }

    private Integer UploadFwImages()
    {
        Integer Response = 0;

        publish("\n-= Uploading Firmware =-");

        Response = exec(cmdEsptool());
        if (Response != 0)
        {
            showMessageDialog(null, "Failed to program the ESP.\n"
                    + "Verify your device is properly connected and in programming mode.",
                    "Failed esptool", JOptionPane.ERROR_MESSAGE);
        }
        publish("\n-= Uploading Firmware - Done =-");
        return Response;
    }

    private List<String> cmdUnpackfilesystem()
    {
        String DirName = ESPSFlashTool.deviceConfig.GetDownloadedFsPath();
        List< String> list = new ArrayList<>();
        list.add(ESPSFlashTool.paths.getMkfilesystem());
        list.add("-b");
        list.add(ESPSFlashTool.board.filesystem.block);
        list.add("-p");
        list.add(ESPSFlashTool.board.filesystem.page);
        list.add("-s");
        list.add(ESPSFlashTool.board.filesystem.size);
        list.add("--unpack");
        list.add("\"" + DirName + "\" \"" + DirName + ".bin\"");
        return list;
    }

    private List<String> cmdEsptoolErase()
    {
        List<String> list = new ArrayList<>();

        list.add(ESPSFlashTool.paths.getPython());
        list.add(ESPSFlashTool.paths.getEsptool());
        list.add("--chip");
        list.add(ESPSFlashTool.board.chip);
        list.add("--baud");
        list.add(ESPSFlashTool.board.esptool.baudrate);
        list.add("--port");
        if (ESPSFlashTool.paths.IsWindows())
        {
            list.add(ESPSFlashTool.port.getPort().getSystemPortName());
        }
        else
        {
            list.add("/dev/" + ESPSFlashTool.port.getPort().getSystemPortName());
        }

        list.add("erase_flash");
        // list.add(board.filesystem.offset);
        // list.add(board.filesystem.size);

        return list;
    }

    private List<String> cmdEsptool()
    {
        List<String> list = new ArrayList<>();

        list.add(ESPSFlashTool.paths.getPython());
        list.add(ESPSFlashTool.paths.getEsptool());
        list.add("--chip");
        list.add(ESPSFlashTool.board.chip);
        list.add("--baud");
        list.add(ESPSFlashTool.board.esptool.baudrate);
        list.add("--port");
        if (ESPSFlashTool.paths.IsWindows())
        {
            list.add(ESPSFlashTool.port.getPort().getSystemPortName());
        }
        else
        {
            list.add("/dev/" + ESPSFlashTool.port.getPort().getSystemPortName());
        }

        // Reset stuff is located in the esptool options
        list.addAll(Arrays.asList(ESPSFlashTool.board.esptool.options.split(" ")));

        // Flash command can carry options as well
        list.addAll(Arrays.asList(ESPSFlashTool.board.esptool.flashcmd.split(" ")));

        // Add all the bin files
        for (Board.Binfile binfile : ESPSFlashTool.board.binfiles)
        {
            list.add(binfile.offset);
            list.add(ESPSFlashTool.paths.getFwPath() + binfile.name);
        }

        // And finally the filesystem
        list.add(ESPSFlashTool.board.filesystem.offset);
        list.add(ESPSFlashTool.paths.getFwPath() + fsBin);

        return list;
    }

    private List<String> cmdGetfilesystem()
    {
        List<String> list = new ArrayList<>();

        if (null != ESPSFlashTool.port.getPort())
        {
            list.add(ESPSFlashTool.paths.getPython());
            list.add(ESPSFlashTool.paths.getEsptool());
            list.add("--chip");
            list.add(ESPSFlashTool.board.chip);
            list.add("--baud");
            list.add(ESPSFlashTool.board.esptool.baudrate);
            list.add("--port");
            if (ESPSFlashTool.paths.IsWindows())
            {
                list.add(ESPSFlashTool.port.getPort().getSystemPortName());
            }
            else
            {
                list.add("/dev/" + ESPSFlashTool.port.getPort().getSystemPortName());
            }

            list.add("--before");
            list.add("default_reset");
            list.add("--after");
            list.add("hard_reset");
            list.add("read_flash");
            list.add(ESPSFlashTool.board.filesystem.offset);
            list.add(ESPSFlashTool.board.filesystem.size);
            list.add(ESPSFlashTool.deviceConfig.GetDownloadedFsPath() + ".bin");
        }

        return list;
    }

    private List<String> cmdMkfilesystem()
    {
        List<String> list = new ArrayList<>();

        list.add(ESPSFlashTool.paths.getMkfilesystem());
        list.add("-c");
        list.add(ESPSFlashTool.paths.getFsPath());
        list.add("-p");
        list.add(ESPSFlashTool.board.filesystem.page);
        list.add("-b");
        list.add(ESPSFlashTool.board.filesystem.block);
        list.add("-s");
        list.add(ESPSFlashTool.board.filesystem.size);
        list.add(ESPSFlashTool.paths.getFwPath() + fsBin);

        return list;
    }
}
