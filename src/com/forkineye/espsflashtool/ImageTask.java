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
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.showMessageDialog;
import javax.swing.SwingWorker;

class ImageTask extends SwingWorker<Integer, String>
{

    private int state;
    private int status;
    private boolean flash;
    private final String fsBin = "filesystem.bin";      // Filesystem Image

    public ImageTask(boolean _flash)
    {
        flash = _flash;
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

    public int exec(List<String> command)
    {
        int response = 0;
        if (!command.isEmpty())
        {
            String outCommand = "";
            for (String opt : command)
            {
                outCommand = (outCommand + " " + opt);
            }
            publish(outCommand);
            System.out.println(outCommand);

            try
            {
                EnsureSerialPortIsOff();

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

                publish("Done");

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
        }
        return response;
    }

    public Integer UploadImages()
    {
        String command = "";

        // Build Filesystem
        publish("-= Building Filesystem Image =-");
        status = exec(cmdMkfilesystem());
        if (status != 0)
        {
            showMessageDialog(null, "Failed to make Filesytem Image",
                    "Failed mkfilesystem", JOptionPane.ERROR_MESSAGE);
        }
        else
        {
            // Flash the images
            if (flash)
            {
                publish("\n-= Programming ESP =-");
                status = exec(cmdEsptoolErase());

                status = exec(cmdEsptool());
                if (status != 0)
                {
                    showMessageDialog(null, "Failed to program the ESP.\n"
                            + "Verify your device is properly connected and in programming mode.",
                            "Failed esptool", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        return state;
    }

    public List<String> cmdParseFileFystem(String DirName)
    {
        List<String> list = new ArrayList<>();
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

    public List<String> cmdGetfilesystem(String TargetFileName)
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
            list.add(TargetFileName);
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

    public void process(java.util.List<String> messages)
    {
        for (String message : messages)
        {
            ESPSFlashTool.flashToolUI.appendTxtSystemOutput(message + "\n");
        }
    }

    @Override
    protected Integer doInBackground()
    {
        ESPSFlashTool.flashToolUI.setTxtSystemOutput(null);
        ImageTask task = new ImageTask(true);
        return task.UploadImages();
    }

    private void monitor()
    {
        if (ESPSFlashTool.port == null)
        {
            ESPSFlashTool.flashToolUI.appendTxtSystemOutput("No Port Defined");
            return;
        }

        SerialPort serial = ESPSFlashTool.port.getPort();
        if (serial == null)
        {
            ESPSFlashTool.flashToolUI.appendTxtSystemOutput("Desired Serial Port Not Found");
            return;
        }

        serial.setComPortParameters(Integer.parseInt(ESPSFlashTool.ftconfig.getBaudrate()),
                8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY
        );

        ESPSFlashTool.flashToolUI.setTxtSystemOutput("");
        if (!serial.openPort())
        {
            ESPSFlashTool.flashToolUI.appendTxtSystemOutput("Failed to open serial port " + serial.getSystemPortName());
        }
        else
        {
            serial.addDataListener(new SerialPortDataListener()
            {
                @Override
                public int getListeningEvents()
                {
                    return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
                }

                @Override
                public void serialEvent(SerialPortEvent event)
                {
                    if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
                    {
                        return;
                    }
                    byte[] data = new byte[serial.bytesAvailable()];
                    serial.readBytes(data, data.length);
                    ESPSFlashTool.flashToolUI.appendTxtSystemOutput(new String(data, StandardCharsets.US_ASCII));
                }
            });
        }
    }

    public void done()
    {
        monitor();
        if (status == 0)
        {
            if (flash)
            {
                ESPSFlashTool.flashToolUI.appendTxtSystemOutput("\n-= Programming Complete =-");
            }
            else
            {
                ESPSFlashTool.flashToolUI.appendTxtSystemOutput("\n-= Image Creation Complete =-");
            }
        }
        else if (flash)
        {
            ESPSFlashTool.flashToolUI.appendTxtSystemOutput("\n*** PROGRAMMING FAILED ***");
        }
        else
        {
            ESPSFlashTool.flashToolUI.appendTxtSystemOutput("\n*** IMAGE CREATION FAILED ***");
        }
        ESPSFlashTool.flashToolUI.enableInterface();
    }
}
