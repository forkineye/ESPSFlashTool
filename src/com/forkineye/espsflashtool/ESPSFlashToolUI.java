/*
 * Copyright 2016 Shelby Merrick
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
import com.fazecast.jSerialComm.*;
import com.google.gson.*;
import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import static javax.swing.JOptionPane.showMessageDialog;
import javax.swing.filechooser.FileNameExtensionFilter;
import static javax.swing.JOptionPane.showMessageDialog;
import javax.swing.UIManager;


/**
 *
 * @author sporadic
 */

// Board JSON Config
class ESPSBoard {
    String name;
    String description;
    String chip;
    String appbin;
    Esptool esptool;
    ArrayList<Binfile> binfiles;
    Filesystem filesystem;
   
    class Esptool {
        String baudrate;
        String options;
        String flashcmd;
    }
    
    class Binfile {
        String name;
        String offset;
    }
    class Filesystem {
        String page;
        String block;
        String size;
        String offset;
    }
    
    // check if bin files exist
    public boolean verify(String path) {
        boolean valid = true;
        for (Binfile _binfile : binfiles) {
            if (!new File(path + _binfile.name).isFile()) {
                showMessageDialog(null, "Firmware file " + _binfile.name + " missing", 
                        "Bad Firmware Configuration", JOptionPane.ERROR_MESSAGE);
                valid = false;
            }
        }
        return valid;
    }

    // 
    public String getAppbin() {
        return appbin;
    }
    
    @Override
    public String toString() {
        return name;
    }
}

// ESPSFlashTool JSON Config
class FTConfig {
    String release;
    String version;
    String baudrate;
    ArrayList<ESPSBoard> boards;
}

// Serial Ports
class ESPSSerialPort {
    private final SerialPort port;      // Hardwire ID or path to port;

    public ESPSSerialPort(SerialPort port) {
        this.port = port;
    }
    
    public SerialPort getPort() {
        return port;
    }

    @Override
    public String toString() {
        return (port.getSystemPortName() + " - " + port.getDescriptivePortName());
    }    
}

// ESPixelStick JSON Config
class ESPSConfig {
    int cfgver;
    Device device;
    Network network;
                
    class Network {
        String  ssid;
        String  passphrase;
        String  hostname;
        String  ip;
        String  netmask;
        String  gateway;
        boolean dhcp;           // Use DHCP
        int     sta_timeout;    // Timeout when connecting as a client (station)
        boolean ap_fallback;    // Fallback to AP if fail to associate
        int     ap_timeout;     // How long to wait in AP mode with no connection before rebooting
        boolean ap_reboot;      // Reboot on fail to connect
    }

    class Device {
        String  id;             // Device ID
    }
}

public class ESPSFlashToolUI extends javax.swing.JFrame {
    private final DefaultComboBoxModel<ESPSBoard> modelBoard   = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<ESPSSerialPort> modelPort   = new DefaultComboBoxModel<>();
    private final String fwPath = "firmware/";          // Path for firmware binaries
    private final String execPath = "bin/";             // Path for executables
    private final String fsPath = "fs/";                // Path for filesystem
    private final String fsBin = "filesystem.bin";      // Filesystem Image
    private final String configJson = "config.json";    // ESPixelStick config.json
    private ImageTask ftask;                            // SwingWorker task to build and flash

    /* Validation Patterns */
    private static final String HOSTNAME_PATTERN = "^([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])$";
    
    private String OsName;
    private String EspPlatformName;
    private String esptool;      // esptool binary to use with path
    private String mkfilesystem; // filesystem binary to use with path
    private String python;       // python binary to use
    
    private ESPSConfig config;
    private FTConfig ftconfig;
    private ESPSBoard board;
    private ESPSSerialPort port;
    private SerialPort lastPort;
    private boolean isWindows;
    
    /**
     * Creates new form ESPSFlashToolUI
     */
    public ESPSFlashToolUI() {
        // Detect OS and set binary paths
        isWindows = false;
        try {
            if (!detectOS())
                showMessageDialog(null, "Failed to detect OS",            
                        "OS Detection Failure", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            Logger.getLogger(ESPSFlashToolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // Read FT Config and set default device
        Gson gson = new Gson();
        try {
            ftconfig = gson.fromJson(
                    new FileReader(fwPath + "firmware.json"), FTConfig.class);
        } catch (FileNotFoundException ex) {
            showMessageDialog(null, "Unable to find firmware configuration file", 
                    "Failed deserialize", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
        
        if (ftconfig.boards != null) {
            board = ftconfig.boards.get(0);
        } else {
            showMessageDialog(null, "No boards found in configuration file", 
                    "Bad configuration", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
        
        // Netbeans init routine
        initComponents();
        setLocationRelativeTo(null);

        // Set release label
        lblRelease.setText(ftconfig.release);
        
        // Setup export dialog
        dlgSave.setFileFilter(new FileNameExtensionFilter("ESPS Firmware Update", "efu"));
        dlgSave.setSelectedFile(new File ("espixelstick.efu"));
        
        // Verify and Populate boards
        for (ESPSBoard x : ftconfig.boards) {
            if (x.verify(fwPath)) {
                modelBoard.addElement(x);
            } else {
                showMessageDialog(null, "Firmware file(s) missing for " + x.toString(), 
                        "Bad Firmware Configuration", JOptionPane.ERROR_MESSAGE);
            }
        }
               
        // Populate serial ports
        for (SerialPort serial : SerialPort.getCommPorts())
            modelPort.addElement(new ESPSSerialPort(serial));
        
        // Deserialize config.json
        try {
            config = gson.fromJson(new FileReader(fsPath + configJson), ESPSConfig.class);
        } catch (FileNotFoundException ex) {
            showMessageDialog(null, "Unable to find ESPixelStick Configuration file", 
                    "Failed deserialize", JOptionPane.ERROR_MESSAGE);
        }

        // Populate config
        txtSSID.setText(config.network.ssid);
        txtPassphrase.setText(config.network.passphrase);
        txtHostname.setText(config.network.hostname);
        txtDevID.setText(config.device.id);
        
        // Start serial monitor
        monitor();
    }

    private boolean serializeConfig() {
        boolean retval = true;
        config.network.ssid = txtSSID.getText();
        config.network.passphrase = txtPassphrase.getText();
        config.network.hostname = txtHostname.getText();
        config.device.id = txtDevID.getText();

        try (Writer fw = new FileWriter(fsPath + configJson)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            gson.toJson(config, fw);
        } catch (IOException ex) {
            showMessageDialog(null, "Failed to save " + configJson, 
                    "Failed serialize", JOptionPane.ERROR_MESSAGE);
            retval = false;
        }
        
        return retval;
    }
    
    private boolean detectOS() throws IOException {
        boolean retval = true;
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        System.out.println("OS: " + os + " / " + arch);
        
        if (os.contains("win")) {
            System.out.println("Detected Windows");
            OsName = "win32/";
            isWindows = true;
        } else if (os.contains("mac")) {
            System.out.println("Detected Mac");
            OsName = "macos/";
        } else if (os.contains("linux") && arch.contains("64")) {
            OsName = "linux64/";
        } else {
            retval = false;
        }
        
        return retval;
    }
    
    private void SetToolPaths()
    {
        EspPlatformName = board.chip + "/";
        mkfilesystem = execPath + OsName + "mklittlefs";
        esptool  = execPath + "upload.py";
        
        if(isWindows) {
            python = execPath + OsName + "python3/python";
        } else {
            python = "python";
            try
            {
                java.lang.Runtime.getRuntime().exec("chmod 550 " + esptool);
                java.lang.Runtime.getRuntime().exec("chmod 550 " + mkfilesystem);
            }
            catch( IOException ex) {}
        }
    }

    // execPath + EspPlatformPath +
    private void monitor() {
        if (port == null)
        {
            txtSerialOutput.append("No Port Defined");
            return;
        }

        SerialPort serial = port.getPort();
        if (serial == null)
        {
            txtSerialOutput.append("Desired Serial Port Not Found");
            return;
        }

        serial.setComPortParameters(Integer.parseInt(ftconfig.baudrate),
                8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);

        txtSerialOutput.setText(""); 
        if (!serial.openPort()) {
            txtSerialOutput.append("Failed to open serial port " + serial.getSystemPortName());
        } else {
            serial.addDataListener(new SerialPortDataListener() {
                @Override
                public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }

                @Override
                public void serialEvent(SerialPortEvent event) {
                    if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
                        return;
                    byte[] data = new byte[serial.bytesAvailable()];
                    serial.readBytes(data, data.length);
                    txtSerialOutput.append(new String(data, StandardCharsets.US_ASCII));
                }
            });
        }
    }

    private void disableInterface() {
        cboxPort.setEnabled(false);
        cboxFirmware.setEnabled(false);
        disableButtons();
    }

    private void enableInterface() {
        cboxPort.setEnabled(true);
        cboxFirmware.setEnabled(true);
        enableButtons();
    }
    
    private void disableButtons() {
        btnFlash.setEnabled(false);
        btnExport.setEnabled(false);
    }

    private void enableButtons() {
        btnFlash.setEnabled(true);
        btnExport.setEnabled(true);
    }

    private List<String> cmdEsptoolErase()
    {
        List<String> list = new ArrayList<>();
        
        list.add(python);
        list.add(esptool);
        list.add("--chip");
        list.add(board.chip);
        list.add("--baud");
        list.add(board.esptool.baudrate);
        list.add("--port");
        if (isWindows)
            list.add(port.getPort().getSystemPortName());
        else
            list.add("/dev/" + port.getPort().getSystemPortName());

        list.add("erase_flash");
        // list.add(board.filesystem.offset);
        // list.add(board.filesystem.size);

        return list;
    }

    private List<String> cmdEsptool() {
        List<String> list = new ArrayList<>();
        
        list.add(python);
        list.add(esptool);
        list.add("--chip");
        list.add(board.chip);
        list.add("--baud");
        list.add(board.esptool.baudrate);
        list.add("--port");
        if (isWindows)
            list.add(port.getPort().getSystemPortName());
        else
            list.add("/dev/" + port.getPort().getSystemPortName());

        // Reset stuff is  cated in the esptool options
        list.addAll(Arrays.asList(board.esptool.options.split(" ")));

        // Flash command can carry options as well
        list.addAll(Arrays.asList(board.esptool.flashcmd.split(" ")));

        // Add all the bin files
        for (ESPSBoard.Binfile binfile : board.binfiles) {
            list.add(binfile.offset);
            list.add(fwPath + binfile.name);
        }

        // And finally the filesystem
        list.add(board.filesystem.offset);
        list.add(fwPath + fsBin);

        return list;
    }
    
    private List<String> cmdMkfilesystem() {
        List<String> list = new ArrayList<>();
        
        list.add(mkfilesystem);
        list.add("-c");
        list.add(fsPath);
        list.add("-p");
        list.add(board.filesystem.page);
        list.add("-b");
        list.add(board.filesystem.block);
        list.add("-s");
        list.add(board.filesystem.size);
        list.add(fwPath + fsBin);
        
        return list;        
    }
    
    private class ImageTask extends SwingWorker<Integer, String> {
        private int state;
        private int status;
        private boolean flash;

        public ImageTask(boolean flash) {
            if (port != null) {
                SerialPort serial = port.getPort();
                if (serial != null)
                    port.getPort().closePort();
            }
            this.flash = flash;
        }
        
        private int exec(List<String> command) {
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String s;
                BufferedReader stdout = new BufferedReader(
                        new InputStreamReader(p.getInputStream()));
                
                while ((s = stdout.readLine()) != null && !isCancelled())
                    publish(s);

                if (!isCancelled())
                    state = p.waitFor();

                publish("Done");

                p.getInputStream().close();
                p.getOutputStream().close();
                p.getErrorStream().close();
                p.destroy();
                return p.exitValue();
            } catch (IOException | InterruptedException ex) {
                ex.printStackTrace(System.err);
                return -1;
            }
        }
        
        @Override
        protected Integer doInBackground() {
            String command = "";
            
            SetToolPaths();

            // Build Filesystem
            txtSystemOutput.setText(null);
            publish("-= Building Filesystem Image =-");
            for (String opt : cmdMkfilesystem())
                command = (command + " " + opt);
            publish(command);
            status = exec(cmdMkfilesystem());
            if (status != 0)
            {
                showMessageDialog(null, "Failed to make Filesytem Image",
                        "Failed mkfilesystem", JOptionPane.ERROR_MESSAGE);
            } 
            else
            {
                // Flash the images
                if (flash) {
                    publish("\n-= Programming ESP =-");

                    command = "";
                    for (String opt : cmdEsptoolErase())
                        command = (command + " " + opt);
                    publish(command);
                    status = exec(cmdEsptoolErase());

                    command = "";
                    for (String opt : cmdEsptool())
                        command = (command + " " + opt);
                    publish(command);
                    status = exec(cmdEsptool());
                    if (status != 0) {
                        showMessageDialog(null, "Failed to program the ESP.\n" +
                                                "Verify your device is properly connected and in programming mode.",
                                                "Failed esptool", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
                    
            return state;
        }
        
        @Override
        protected void process(java.util.List<String> messages) {
            for (String message : messages)
                txtSystemOutput.append(message + "\n");
        }

        @Override
        protected void done() {
            monitor();
            if (status == 0)
                if (flash)
                    txtSystemOutput.append("\n-= Programming Complete =-");
                else
                    txtSystemOutput.append("\n-= Image Creation Complete =-");
            else
                if (flash)
                    txtSystemOutput.append("\n*** PROGRAMMING FAILED ***");
                else
                    txtSystemOutput.append("\n*** IMAGE CREATION FAILED ***");
            enableInterface();
        }
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        dlgSave = new javax.swing.JFileChooser();
        txtPassphrase = new javax.swing.JTextField();
        cboxFirmware = new javax.swing.JComboBox<>();
        txtDevID = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        cboxPort = new javax.swing.JComboBox<>();
        jLabel3 = new javax.swing.JLabel();
        txtSSID = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        btnFlash = new javax.swing.JButton();
        btnExport = new javax.swing.JButton();
        jSplitPane1 = new javax.swing.JSplitPane();
        pnlSystem = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtSystemOutput = new javax.swing.JTextArea();
        jLabel7 = new javax.swing.JLabel();
        pnlSerial = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        txtSerialOutput = new javax.swing.JTextArea();
        jLabel6 = new javax.swing.JLabel();
        txtHostname = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        lblRelease = new javax.swing.JLabel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0));

        dlgSave.setDialogType(javax.swing.JFileChooser.SAVE_DIALOG);
        dlgSave.setDialogTitle("Save Firmware Update");

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("ESPixelStick Flash Tool");

        txtPassphrase.setToolTipText("Enter your AP Passphrase");

        cboxFirmware.setModel(modelBoard);
        cboxFirmware.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cboxFirmwareActionPerformed(evt);
            }
        });

        txtDevID.setToolTipText("Plain text name to help you identify this device");

        jLabel5.setText("Device ID");

        jLabel4.setText("Serial Port");

        cboxPort.setModel(modelPort);
        cboxPort.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cboxPortActionPerformed(evt);
            }
        });

        jLabel3.setText("Hardware");

        txtSSID.setToolTipText("Enter your AP SSID");

        jLabel2.setText("Passphrase");

        jLabel1.setText("SSID");

        btnFlash.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        btnFlash.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/forkineye/espsflashtool/upload.png"))); // NOI18N
        btnFlash.setText("Upload");
        btnFlash.setToolTipText("Program your ESPixelStick with current settings");
        btnFlash.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnFlashActionPerformed(evt);
            }
        });

        btnExport.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        btnExport.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/forkineye/espsflashtool/export.png"))); // NOI18N
        btnExport.setText("Build EFU");
        btnExport.setToolTipText("Builds an OTA update file");
        btnExport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExportActionPerformed(evt);
            }
        });

        jSplitPane1.setBorder(null);
        jSplitPane1.setDividerLocation(200);
        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        txtSystemOutput.setEditable(false);
        txtSystemOutput.setColumns(20);
        txtSystemOutput.setRows(5);
        jScrollPane1.setViewportView(txtSystemOutput);

        jLabel7.setText("Status");

        javax.swing.GroupLayout pnlSystemLayout = new javax.swing.GroupLayout(pnlSystem);
        pnlSystem.setLayout(pnlSystemLayout);
        pnlSystemLayout.setHorizontalGroup(
            pnlSystemLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 501, Short.MAX_VALUE)
            .addGroup(pnlSystemLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel7)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        pnlSystemLayout.setVerticalGroup(
            pnlSystemLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnlSystemLayout.createSequentialGroup()
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 180, Short.MAX_VALUE))
        );

        jSplitPane1.setTopComponent(pnlSystem);

        pnlSerial.setAlignmentX(0.0F);
        pnlSerial.setAlignmentY(0.0F);

        txtSerialOutput.setEditable(false);
        txtSerialOutput.setColumns(20);
        txtSerialOutput.setRows(5);
        jScrollPane2.setViewportView(txtSerialOutput);

        jLabel6.setText("Serial Output");

        javax.swing.GroupLayout pnlSerialLayout = new javax.swing.GroupLayout(pnlSerial);
        pnlSerial.setLayout(pnlSerialLayout);
        pnlSerialLayout.setHorizontalGroup(
            pnlSerialLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 501, Short.MAX_VALUE)
            .addGroup(pnlSerialLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel6)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        pnlSerialLayout.setVerticalGroup(
            pnlSerialLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlSerialLayout.createSequentialGroup()
                .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 139, Short.MAX_VALUE))
        );

        jSplitPane1.setBottomComponent(pnlSerial);

        txtHostname.setToolTipText("Auto-generated if left blank");
        txtHostname.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                txtHostnameKeyReleased(evt);
            }
        });

        jLabel8.setText("Hostname");

        lblRelease.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        lblRelease.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblRelease.setText("* Release Information Not Found *");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(btnFlash, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnExport)
                .addGap(10, 10, 10))
            .addComponent(jSplitPane1, javax.swing.GroupLayout.Alignment.TRAILING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                            .addGap(7, 7, 7)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(jLabel8, javax.swing.GroupLayout.Alignment.TRAILING)))
                        .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.TRAILING))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel3)
                            .addComponent(jLabel4)
                            .addComponent(jLabel5))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cboxPort, javax.swing.GroupLayout.Alignment.CENTER, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cboxFirmware, javax.swing.GroupLayout.Alignment.CENTER, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(txtDevID, javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(txtHostname, javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(txtPassphrase, javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(txtSSID, javax.swing.GroupLayout.Alignment.CENTER)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(77, 77, 77)
                        .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
            .addComponent(lblRelease, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblRelease)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(txtSSID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(txtPassphrase, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtDevID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboxFirmware, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboxPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnExport)
                    .addComponent(btnFlash))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSplitPane1)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cboxPortActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cboxPortActionPerformed
        port = cboxPort.getItemAt(cboxPort.getSelectedIndex());
        if (port == null)
            return;
        if (lastPort != null)
            lastPort.closePort();
        lastPort = port.getPort();
        monitor();
    }//GEN-LAST:event_cboxPortActionPerformed

    private void cboxFirmwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cboxFirmwareActionPerformed
        board = cboxFirmware.getItemAt(cboxFirmware.getSelectedIndex());
    }//GEN-LAST:event_cboxFirmwareActionPerformed

    private void btnFlashActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnFlashActionPerformed
        if (serializeConfig()) {
            disableInterface();
            ftask = new ImageTask(true);
            ftask.execute();
        }
    }//GEN-LAST:event_btnFlashActionPerformed

    private void btnExportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExportActionPerformed
        if (dlgSave.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                if (serializeConfig()) {
                    disableInterface();
                    ftask = new ImageTask(false);
                    ftask.execute();
                    
                    // Block until filesystem image is built
                    try {
                        ftask.get();
                    } catch (InterruptedException | ExecutionException ex) {
                        Logger.getLogger(ESPSFlashToolUI.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    
                    UpdateBuilder.build(fwPath + board.getAppbin(), fwPath + fsBin,
                            dlgSave.getSelectedFile().getAbsolutePath());
                }
            } catch (IOException ex) {
                showMessageDialog(null, "Failed to build firmware update\n" +
                        ex.getMessage(), "Failed EFU Build", JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_btnExportActionPerformed

    private void txtHostnameKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtHostnameKeyReleased
        if (txtHostname.getText().length() == 0 | txtHostname.getText().matches(HOSTNAME_PATTERN)) {
            txtHostname.setForeground(Color.black);
            enableButtons();
        } else {
            disableButtons();
            txtHostname.setForeground(Color.red);
        }
    }//GEN-LAST:event_txtHostnameKeyReleased

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            /*
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }*/
            
            // Set system look and feel
            javax.swing.UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(ESPSFlashToolUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(ESPSFlashToolUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(ESPSFlashToolUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(ESPSFlashToolUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new ESPSFlashToolUI().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnExport;
    private javax.swing.JButton btnFlash;
    private javax.swing.JComboBox<ESPSBoard> cboxFirmware;
    private javax.swing.JComboBox<ESPSSerialPort> cboxPort;
    private javax.swing.JFileChooser dlgSave;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JLabel lblRelease;
    private javax.swing.JPanel pnlSerial;
    private javax.swing.JPanel pnlSystem;
    private javax.swing.JTextField txtDevID;
    private javax.swing.JTextField txtHostname;
    private javax.swing.JTextField txtPassphrase;
    private javax.swing.JTextField txtSSID;
    private javax.swing.JTextArea txtSerialOutput;
    private javax.swing.JTextArea txtSystemOutput;
    // End of variables declaration//GEN-END:variables
}
