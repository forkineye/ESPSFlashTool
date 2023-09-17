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

import com.fazecast.jSerialComm.*;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.awt.Image;
import java.awt.Toolkit;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.showMessageDialog;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

public class ESPSFlashToolUI extends javax.swing.JFrame
{

    private static final String IPV4_REGEX
            = "^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    private static final Pattern IPv4_PATTERN = Pattern.compile(IPV4_REGEX);

    private final DefaultComboBoxModel<Board> modelBoard = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<ESPSSerialPort> modelPort = new DefaultComboBoxModel<>();

    /* Validation Patterns */
    private static final String HOSTNAME_PATTERN = "^([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])$";

    private SerialPort lastPort;

    /**
     * Creates new form ESPSFlashToolUI
     */
    public ESPSFlashToolUI()
    {
    }

    public void init()
    {       
        // Set Flat Look and Feel
        try {
            UIManager.setLookAndFeel( new FlatLightLaf() );
        } catch( Exception ex ) {
            System.err.println( "Failed to initialize LaF" );
        }
        
        // Netbeans init routine
        initComponents();
        setLocationRelativeTo(null);

        
        // jFormattedTextFieldIpAddress
        // Set release label
        lblRelease.setText(ESPSFlashTool.ftconfig.getRelease());

        // Setup export dialog
        dlgSave.setFileFilter(
                new FileNameExtensionFilter("ESPS Firmware Update", "efu"));
        dlgSave.setSelectedFile(
                new File("espixelstick.efu"));

        if (ESPSFlashTool.ftconfig.getBoards().isEmpty())
        {
            showMessageDialog(null, "No boards found in configuration file",
                    "Bad configuration", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        // Verify and Populate boards
        for (Board x : ESPSFlashTool.ftconfig.getBoards())
        {
            String FwPath = ESPSFlashTool.paths.getFwPath();
            if (x.verify(FwPath))
            {
                modelBoard.addElement(x);
            }
            else
            {
                showMessageDialog(null, "Firmware file(s) missing for " + x.toString(),
                        "Bad Firmware Configuration", JOptionPane.ERROR_MESSAGE);
            }
        }

        // Populate serial ports
        for (SerialPort serial : SerialPort.getCommPorts())
        {
            modelPort.addElement(new ESPSSerialPort(serial));
        }
        populateConfigValues();

        // Trigger state changes
        CheckBoxUseDhcpItemStateChanged(null);
        
        // Start serial monitor
        monitor();
    }

    public void populateConfigValues()
    {
        // Populate config
        txtSSID.setText(ESPSFlashTool.deviceConfig.getSSID());
        txtPassphrase.setText(ESPSFlashTool.deviceConfig.getPassphrase());
        txtHostname.setText(ESPSFlashTool.deviceConfig.getHostname());
        txtDevID.setText(ESPSFlashTool.deviceConfig.getId());
        CheckBoxApFallback.setSelected(ESPSFlashTool.deviceConfig.getAp_fallback());
        CheckBoxReboot.setSelected(ESPSFlashTool.deviceConfig.getReboot());
        CheckBoxUseDhcp.setSelected(ESPSFlashTool.deviceConfig.getDHCP());
        jTextFieldIpAddress.setText(ESPSFlashTool.deviceConfig.getIP());
        jTextFieldIpMask.setText(ESPSFlashTool.deviceConfig.getMask());
        jTextFieldGatewayIpAddress.setText(ESPSFlashTool.deviceConfig.getGatewayIp());
    }

    private boolean serializeConfig()
    {
        ESPSFlashTool.deviceConfig.setSSID(txtSSID.getText());
        ESPSFlashTool.deviceConfig.setPassphrase(txtPassphrase.getText());
        ESPSFlashTool.deviceConfig.setHostname(txtHostname.getText());
        ESPSFlashTool.deviceConfig.setId(txtDevID.getText());
        ESPSFlashTool.deviceConfig.setAp_fallback(CheckBoxApFallback.isSelected());
        ESPSFlashTool.deviceConfig.setReboot(CheckBoxReboot.isSelected());
        ESPSFlashTool.deviceConfig.setDHCP(CheckBoxUseDhcp.isSelected());
        ESPSFlashTool.deviceConfig.setIP(jTextFieldIpAddress.getText());
        ESPSFlashTool.deviceConfig.setMask(jTextFieldIpMask.getText());
        ESPSFlashTool.deviceConfig.setGatewayIp(jTextFieldGatewayIpAddress.getText());

        return ESPSFlashTool.deviceConfig.serializeConfig();
    }

    // execPath + EspPlatformPath +
    public void monitor()
    {
        System.out.println("monitor - Start");
        do // once
        {
            if (ESPSFlashTool.port == null)
            {
                txtSerialOutput.append("No Port Defined");
                break;
            }

            SerialPort serial = ESPSFlashTool.port.getPort();
            if (serial == null)
            {
                txtSerialOutput.append("Desired Serial Port Not Found");
                break;
            }

            serial.setComPortParameters(Integer.parseInt(ESPSFlashTool.ftconfig.getBaudrate()),
                    8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY
            );
            if (!serial.openPort())
            {
                txtSerialOutput.append("Failed to open serial port " + serial.getSystemPortName());
                break;
            }

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
                    LocalDateTime LocalTimeStamp = LocalDateTime.now();
                    DateTimeFormatter TimeStampFormater = DateTimeFormatter.ofPattern("HH:mm:ss");
                    String formattedtime = LocalTimeStamp.format(TimeStampFormater);
                    String Line = new String(data, StandardCharsets.US_ASCII).replace("\n", "\n" + formattedtime + ": ");
                    txtSerialOutput.append(Line);
                    txtSerialOutput.setCaretPosition(txtSerialOutput.getDocument().getLength());
                }
            });
        } while (false);
        System.out.println("monitor - End");
    }

    private void disableInterface()
    {
        cboxPort.setEnabled(false);
        cboxFirmware.setEnabled(false);
        CheckBoxApFallback.setEnabled(false);
        disableButtons();
    }

    public void enableInterface()
    {
        cboxPort.setEnabled(true);
        cboxFirmware.setEnabled(true);
        CheckBoxApFallback.setEnabled(true);
        enableButtons();
    }

    private void disableButtons()
    {
        btnFlash.setEnabled(false);
        btnExport.setEnabled(false);
        btnDownload.setEnabled(false);
    }

    private void enableButtons()
    {
        btnFlash.setEnabled(true);
        btnExport.setEnabled(true);
        btnDownload.setEnabled(true);
    }

    public void setTxtSystemOutput(String message)
    {
        txtSystemOutput.setText(message);
    }

    public void appendTxtSystemOutput(String message)
    {
        txtSystemOutput.append(message);
        txtSystemOutput.setCaretPosition(txtSystemOutput.getDocument().getLength());
    }

    public String getEfuTarget()
    {
        return dlgSave.getSelectedFile().getAbsolutePath();
    }

    private int ValidateIpAddress(String value)
    {
        int response = 0;
        do // once
        {
            if (value.isEmpty())
            {
                // empty address is valid
                response = 1;
                break;
            }

            if (value.equals("(IP unset)"))
            {
                // empty address is valid
                response = 1;
                break;
            }

            Matcher validator = IPv4_PATTERN.matcher(value);
            response = (validator.matches()) ? 1 : 0;
        } while (false);
        return response;
    }

    private void ValidateIpAddresses()
    {
        int ValidIps = 0;

        ValidIps += ValidateIpAddress(jTextFieldIpAddress.getText());
        ValidIps += ValidateIpAddress(jTextFieldIpMask.getText());
        ValidIps += ValidateIpAddress(jTextFieldGatewayIpAddress.getText());

        if (3 == ValidIps)
        {
            enableButtons();
        }
        else
        {
            disableButtons();
        }
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of
     * this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        dlgSave = new javax.swing.JFileChooser();
        jFormattedTextField1 = new javax.swing.JFormattedTextField();
        jFrame1 = new javax.swing.JFrame();
        jFrame2 = new javax.swing.JFrame();
        lblRelease = new javax.swing.JLabel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0));
        jPanelHardware = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        cboxPort = new javax.swing.JComboBox<>();
        cboxFirmware = new javax.swing.JComboBox<>();
        jPanelDeviceConfig = new javax.swing.JPanel();
        CheckBoxApFallback = new javax.swing.JCheckBox();
        CheckBoxReboot = new javax.swing.JCheckBox();
        CheckBoxUseDhcp = new javax.swing.JCheckBox();
        jLabel5 = new javax.swing.JLabel();
        txtDevID = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        txtHostname = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jLabelISSID = new javax.swing.JLabel();
        txtPassphrase = new javax.swing.JTextField();
        txtSSID = new javax.swing.JTextField();
        jLabelIpAddress = new javax.swing.JLabel();
        jLabelIpMask = new javax.swing.JLabel();
        jLabelGatewayIpAddress = new javax.swing.JLabel();
        jTextFieldIpAddress = new javax.swing.JTextField();
        jTextFieldIpMask = new javax.swing.JTextField();
        jTextFieldGatewayIpAddress = new javax.swing.JTextField();
        jPanelButtons = new javax.swing.JPanel();
        btnExport = new javax.swing.JButton();
        btnFlash = new javax.swing.JButton();
        btnDownload = new javax.swing.JButton();
        jSplitPane1 = new javax.swing.JSplitPane();
        jPanelSystemOutput = new javax.swing.JPanel();
        jLabelSystemOutput = new javax.swing.JLabel();
        jButtonClearSystemOutput = new javax.swing.JButton();
        jScrollPaneSystemOutput = new javax.swing.JScrollPane();
        txtSystemOutput = new javax.swing.JTextArea();
        jPanelSerialOutput = new javax.swing.JPanel();
        jLabelSerialOutput = new javax.swing.JLabel();
        jButtonClearSerialOutput = new javax.swing.JButton();
        jScrollPaneSerialOutput = new javax.swing.JScrollPane();
        txtSerialOutput = new javax.swing.JTextArea();
        jButtonSaveLogs = new javax.swing.JButton();

        dlgSave.setDialogType(javax.swing.JFileChooser.SAVE_DIALOG);
        dlgSave.setDialogTitle("Save Firmware Update");

        jFormattedTextField1.setText("jFormattedTextField1");

        javax.swing.GroupLayout jFrame1Layout = new javax.swing.GroupLayout(jFrame1.getContentPane());
        jFrame1.getContentPane().setLayout(jFrame1Layout);
        jFrame1Layout.setHorizontalGroup(
            jFrame1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        jFrame1Layout.setVerticalGroup(
            jFrame1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout jFrame2Layout = new javax.swing.GroupLayout(jFrame2.getContentPane());
        jFrame2.getContentPane().setLayout(jFrame2Layout);
        jFrame2Layout.setHorizontalGroup(
            jFrame2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        jFrame2Layout.setVerticalGroup(
            jFrame2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("ESPixelStick Flash Tool");
        setIconImage(Toolkit.getDefaultToolkit().getImage(ESPSFlashToolUI.class.getResource("Forkineye-icon32.png")));

        lblRelease.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        lblRelease.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblRelease.setText("* Release Information Not Found *");

        jLabel4.setText("Serial Port");

        jLabel3.setText("Hardware");

        cboxPort.setModel(modelPort);
        cboxPort.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cboxPortActionPerformed(evt);
            }
        });

        cboxFirmware.setModel(modelBoard);
        cboxFirmware.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cboxFirmwareActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelHardwareLayout = new javax.swing.GroupLayout(jPanelHardware);
        jPanelHardware.setLayout(jPanelHardwareLayout);
        jPanelHardwareLayout.setHorizontalGroup(
            jPanelHardwareLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelHardwareLayout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addGroup(jPanelHardwareLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel3)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelHardwareLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cboxPort, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cboxFirmware, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanelHardwareLayout.setVerticalGroup(
            jPanelHardwareLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelHardwareLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanelHardwareLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(cboxFirmware, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelHardwareLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboxPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addContainerGap())
        );

        CheckBoxApFallback.setText("AP Fallback");
        CheckBoxApFallback.setToolTipText("Select to fallback to AP mode when no network AP can be found.");
        CheckBoxApFallback.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        CheckBoxReboot.setSelected(true);
        CheckBoxReboot.setText("Reboot");
        CheckBoxReboot.setToolTipText("Reboot when WiFi fails to connect.");
        CheckBoxReboot.setActionCommand("CheckBoxReboot");
        CheckBoxReboot.setFocusTraversalPolicyProvider(true);
        CheckBoxReboot.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);

        CheckBoxUseDhcp.setSelected(true);
        CheckBoxUseDhcp.setText("Use DHCP");
        CheckBoxUseDhcp.setActionCommand("UseDhcp");
        CheckBoxUseDhcp.setFocusable(false);
        CheckBoxUseDhcp.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        CheckBoxUseDhcp.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        CheckBoxUseDhcp.setVerifyInputWhenFocusTarget(false);
        CheckBoxUseDhcp.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                CheckBoxUseDhcpItemStateChanged(evt);
            }
        });

        jLabel5.setText("Device ID");

        txtDevID.setToolTipText("Plain text name to help you identify this device");

        jLabel8.setText("Hostname");

        txtHostname.setToolTipText("Auto-generated if left blank");
        txtHostname.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                txtHostnameKeyReleased(evt);
            }
        });

        jLabel2.setText("Passphrase");

        jLabelISSID.setText("SSID");

        txtPassphrase.setToolTipText("Enter your AP Passphrase");

        txtSSID.setToolTipText("Enter your AP SSID");

        jLabelIpAddress.setText("IP Address");

        jLabelIpMask.setText("Subnet Mask");

        jLabelGatewayIpAddress.setText("Gateway");

        jTextFieldIpAddress.setText("000.000.000.000");
        jTextFieldIpAddress.setToolTipText("");
        jTextFieldIpAddress.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                IpAddressFocusLost(evt);
            }
        });
        jTextFieldIpAddress.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                IpAddressKeyReleased(evt);
            }
        });

        jTextFieldIpMask.setText("000.000.000.000");
        jTextFieldIpMask.setToolTipText("");
        jTextFieldIpMask.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                IpAddressFocusLost(evt);
            }
        });
        jTextFieldIpMask.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                IpAddressKeyReleased(evt);
            }
        });

        jTextFieldGatewayIpAddress.setText("000.000.000.000");
        jTextFieldGatewayIpAddress.setToolTipText("");
        jTextFieldGatewayIpAddress.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                IpAddressFocusLost(evt);
            }
        });
        jTextFieldGatewayIpAddress.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                IpAddressKeyReleased(evt);
            }
        });

        javax.swing.GroupLayout jPanelDeviceConfigLayout = new javax.swing.GroupLayout(jPanelDeviceConfig);
        jPanelDeviceConfig.setLayout(jPanelDeviceConfigLayout);
        jPanelDeviceConfigLayout.setHorizontalGroup(
            jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelDeviceConfigLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel8)
                    .addComponent(jLabel5)
                    .addComponent(jLabel2)
                    .addComponent(jLabelISSID))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelDeviceConfigLayout.createSequentialGroup()
                        .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(txtPassphrase, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtDevID, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtSSID)
                            .addComponent(txtHostname, javax.swing.GroupLayout.Alignment.LEADING))
                        .addGap(18, 18, 18)
                        .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabelIpMask, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabelGatewayIpAddress, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabelIpAddress, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jTextFieldIpAddress, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jTextFieldIpMask, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jTextFieldGatewayIpAddress, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(CheckBoxUseDhcp)))
                    .addGroup(jPanelDeviceConfigLayout.createSequentialGroup()
                        .addComponent(CheckBoxApFallback)
                        .addGap(13, 13, 13)
                        .addComponent(CheckBoxReboot)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanelDeviceConfigLayout.setVerticalGroup(
            jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelDeviceConfigLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtSSID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelISSID)
                    .addComponent(CheckBoxUseDhcp))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(txtPassphrase, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelIpAddress)
                    .addComponent(jTextFieldIpAddress, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(txtHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelIpMask)
                    .addComponent(jTextFieldIpMask, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(txtDevID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelGatewayIpAddress)
                    .addComponent(jTextFieldGatewayIpAddress, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(CheckBoxApFallback, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(CheckBoxReboot)))
        );

        CheckBoxApFallback.getAccessibleContext().setAccessibleName(" AP Fallback");

        btnExport.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        btnExport.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/forkineye/espsflashtool/save_FILL0_wght400_GRAD0_opsz48.png"))); // NOI18N
        btnExport.setText("Build EFU");
        btnExport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExportActionPerformed(evt);
            }
        });

        btnFlash.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        btnFlash.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/forkineye/espsflashtool/upload_FILL0_wght400_GRAD0_opsz48.png"))); // NOI18N
        btnFlash.setText("Flash Device");
        btnFlash.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnFlashActionPerformed(evt);
            }
        });

        btnDownload.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        btnDownload.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/forkineye/espsflashtool/download_FILL0_wght400_GRAD0_opsz48.png"))); // NOI18N
        btnDownload.setText("Download Config");
        btnDownload.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDownloadActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelButtonsLayout = new javax.swing.GroupLayout(jPanelButtons);
        jPanelButtons.setLayout(jPanelButtonsLayout);
        jPanelButtonsLayout.setHorizontalGroup(
            jPanelButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelButtonsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(btnFlash, javax.swing.GroupLayout.PREFERRED_SIZE, 179, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnDownload)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnExport, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanelButtonsLayout.setVerticalGroup(
            jPanelButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelButtonsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelButtonsLayout.createSequentialGroup()
                        .addGap(1, 1, 1)
                        .addComponent(btnFlash, javax.swing.GroupLayout.PREFERRED_SIZE, 46, Short.MAX_VALUE))
                    .addGroup(jPanelButtonsLayout.createSequentialGroup()
                        .addGroup(jPanelButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(btnDownload, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(btnExport, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );

        btnFlash.getAccessibleContext().setAccessibleName("Upload Image Set");

        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        jLabelSystemOutput.setText("System Output");
        jLabelSystemOutput.setToolTipText("");

        jButtonClearSystemOutput.setText("Clear");
        jButtonClearSystemOutput.setActionCommand("jButtonClearSystemOutput");
        jButtonClearSystemOutput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonClearSystemOutputActionPerformed(evt);
            }
        });

        jScrollPaneSystemOutput.setMinimumSize(new java.awt.Dimension(100, 100));

        txtSystemOutput.setColumns(20);
        txtSystemOutput.setRows(5);
        jScrollPaneSystemOutput.setViewportView(txtSystemOutput);

        javax.swing.GroupLayout jPanelSystemOutputLayout = new javax.swing.GroupLayout(jPanelSystemOutput);
        jPanelSystemOutput.setLayout(jPanelSystemOutputLayout);
        jPanelSystemOutputLayout.setHorizontalGroup(
            jPanelSystemOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSystemOutputLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSystemOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPaneSystemOutput, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanelSystemOutputLayout.createSequentialGroup()
                        .addComponent(jLabelSystemOutput)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jButtonClearSystemOutput)))
                .addContainerGap())
        );
        jPanelSystemOutputLayout.setVerticalGroup(
            jPanelSystemOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSystemOutputLayout.createSequentialGroup()
                .addGap(4, 4, 4)
                .addGroup(jPanelSystemOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSystemOutput)
                    .addComponent(jButtonClearSystemOutput))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPaneSystemOutput, javax.swing.GroupLayout.DEFAULT_SIZE, 100, Short.MAX_VALUE)
                .addContainerGap())
        );

        jButtonClearSystemOutput.getAccessibleContext().setAccessibleName("jButtonClearSystemOutput");

        jSplitPane1.setLeftComponent(jPanelSystemOutput);

        jLabelSerialOutput.setText("Serial Output");

        jButtonClearSerialOutput.setText("Clear");
        jButtonClearSerialOutput.setActionCommand("jButtonClearSerialOutput");
        jButtonClearSerialOutput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonClearSerialOutputActionPerformed(evt);
            }
        });

        jScrollPaneSerialOutput.setMinimumSize(new java.awt.Dimension(100, 100));

        txtSerialOutput.setColumns(20);
        txtSerialOutput.setRows(5);
        jScrollPaneSerialOutput.setViewportView(txtSerialOutput);

        javax.swing.GroupLayout jPanelSerialOutputLayout = new javax.swing.GroupLayout(jPanelSerialOutput);
        jPanelSerialOutput.setLayout(jPanelSerialOutputLayout);
        jPanelSerialOutputLayout.setHorizontalGroup(
            jPanelSerialOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSerialOutputLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSerialOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPaneSerialOutput, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelSerialOutputLayout.createSequentialGroup()
                        .addComponent(jLabelSerialOutput)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jButtonClearSerialOutput)))
                .addContainerGap())
        );
        jPanelSerialOutputLayout.setVerticalGroup(
            jPanelSerialOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSerialOutputLayout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addGroup(jPanelSerialOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSerialOutput)
                    .addComponent(jButtonClearSerialOutput))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPaneSerialOutput, javax.swing.GroupLayout.DEFAULT_SIZE, 115, Short.MAX_VALUE))
        );

        jSplitPane1.setBottomComponent(jPanelSerialOutput);

        jButtonSaveLogs.setText("Save Logs");
        jButtonSaveLogs.setActionCommand("jButtonSaveLogs");
        jButtonSaveLogs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSaveLogsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(lblRelease, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanelDeviceConfig, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanelHardware, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanelButtons, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addGap(144, 144, 144)
                .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
            .addComponent(jSplitPane1)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jButtonSaveLogs)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblRelease)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelDeviceConfig, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelHardware, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelButtons, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 291, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButtonSaveLogs)
                .addContainerGap())
        );

        jButtonSaveLogs.getAccessibleContext().setAccessibleName("jButtonSaveLogs");

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void txtHostnameKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtHostnameKeyReleased
        if (txtHostname.getText().length() == 0 | txtHostname.getText().matches(HOSTNAME_PATTERN))
        {
            txtHostname.setForeground(Color.black);
            enableButtons();
        }
        else
        {
            disableButtons();
            txtHostname.setForeground(Color.red);
        }
    }//GEN-LAST:event_txtHostnameKeyReleased

    private void btnDownloadActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnDownloadActionPerformed
    {//GEN-HEADEREND:event_btnDownloadActionPerformed
        txtSystemOutput.setText("Downloading Configuration Files from the ESP\n");
        ESPSFlashTool.deviceConfig.ProcessOnDeviceConfigFiles();
    }//GEN-LAST:event_btnDownloadActionPerformed

    private void btnExportActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnExportActionPerformed
    {//GEN-HEADEREND:event_btnExportActionPerformed
        dlgSave.setSelectedFile(
                new File(ESPSFlashTool.board.name + ".efu"));
        try
        {
            File StartingPath = new File("./" + ESPSFlashTool.paths.getFwPath() + "/../");
            System.out.println("StartingPath: " + StartingPath.toString());
            dlgSave.setCurrentDirectory(StartingPath);
        }
        catch (Exception e)
        {
            System.out.println("use current default dir: " + e.toString());
        }

        if (dlgSave.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            if (serializeConfig())
            {
                disableInterface();

                ImageTask ftask = new ImageTask(ImageTask.ImageTaskActionToPerform.MAKEEFU); // SwingWorker task to build and flash
                ftask.execute();
            }
        }
    }//GEN-LAST:event_btnExportActionPerformed

    private void btnFlashActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnFlashActionPerformed
    {//GEN-HEADEREND:event_btnFlashActionPerformed
        if (serializeConfig())
        {
            // disableInterface();
            ImageTask ftask = new ImageTask(ImageTask.ImageTaskActionToPerform.CREATE_AND_UPLOAD_ALL); // SwingWorker task to build and flash
            ftask.execute();
        }
    }//GEN-LAST:event_btnFlashActionPerformed

    private void IpAddressFocusLost(java.awt.event.FocusEvent evt)//GEN-FIRST:event_IpAddressFocusLost
    {//GEN-HEADEREND:event_IpAddressFocusLost
        ValidateIpAddresses();
    }//GEN-LAST:event_IpAddressFocusLost

    private void IpAddressKeyReleased(java.awt.event.KeyEvent evt)//GEN-FIRST:event_IpAddressKeyReleased
    {//GEN-HEADEREND:event_IpAddressKeyReleased
        ValidateIpAddresses();
    }//GEN-LAST:event_IpAddressKeyReleased

    private void jButtonClearSystemOutputActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_jButtonClearSystemOutputActionPerformed
    {//GEN-HEADEREND:event_jButtonClearSystemOutputActionPerformed
        txtSystemOutput.setText("");
    }//GEN-LAST:event_jButtonClearSystemOutputActionPerformed

    private void jButtonClearSerialOutputActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_jButtonClearSerialOutputActionPerformed
    {//GEN-HEADEREND:event_jButtonClearSerialOutputActionPerformed
        txtSerialOutput.setText("");
    }//GEN-LAST:event_jButtonClearSerialOutputActionPerformed

    private void jButtonSaveLogsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_jButtonSaveLogsActionPerformed
    {//GEN-HEADEREND:event_jButtonSaveLogsActionPerformed
        final JFileChooser LogFileChooser = new JFileChooser();
        LogFileChooser.setCurrentDirectory(new File(ESPSFlashTool.paths.getFsPath() + "/.."));
        int returnVal = LogFileChooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION)
        {
            File LogFilePath = LogFileChooser.getSelectedFile();
            try ( FileWriter LogFileWriter = new FileWriter(LogFilePath, false))
            {
                LogFileWriter.write("System Output: \n\n");
                LogFileWriter.write(txtSystemOutput.getText());
                LogFileWriter.write("\n\nSerial Output: \n\n");
                LogFileWriter.write(txtSerialOutput.getText());
            }
            catch (IOException e)
            {
                txtSystemOutput.append("ERROR writing to log file: " + e.toString());
            }
        }
    }//GEN-LAST:event_jButtonSaveLogsActionPerformed

    private void cboxFirmwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cboxFirmwareActionPerformed
        ESPSFlashTool.board = cboxFirmware.getItemAt(cboxFirmware.getSelectedIndex());
    }//GEN-LAST:event_cboxFirmwareActionPerformed

    private void cboxPortActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cboxPortActionPerformed

        do // once
        {
            ESPSFlashTool.port = cboxPort.getItemAt(cboxPort.getSelectedIndex());
            if (ESPSFlashTool.port == null)
            {
                break;
            }
            if (lastPort != null)
            {
                lastPort.closePort();
            }
            lastPort = ESPSFlashTool.port.getPort();

            monitor();
        } while (false);
    }//GEN-LAST:event_cboxPortActionPerformed

    private void CheckBoxUseDhcpItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_CheckBoxUseDhcpItemStateChanged
       if (CheckBoxUseDhcp.isSelected()) {
            jTextFieldIpAddress.setEnabled(false);
            jTextFieldIpMask.setEnabled(false);
            jTextFieldGatewayIpAddress.setEnabled(false);
        } else {
            jTextFieldIpAddress.setEnabled(true);
            jTextFieldIpMask.setEnabled(true);
            jTextFieldGatewayIpAddress.setEnabled(true);
        } 
    }//GEN-LAST:event_CheckBoxUseDhcpItemStateChanged

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox CheckBoxApFallback;
    private javax.swing.JCheckBox CheckBoxReboot;
    private javax.swing.JCheckBox CheckBoxUseDhcp;
    private javax.swing.JButton btnDownload;
    private javax.swing.JButton btnExport;
    private javax.swing.JButton btnFlash;
    private javax.swing.JComboBox<Board> cboxFirmware;
    private javax.swing.JComboBox<ESPSSerialPort> cboxPort;
    private javax.swing.JFileChooser dlgSave;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JButton jButtonClearSerialOutput;
    private javax.swing.JButton jButtonClearSystemOutput;
    private javax.swing.JButton jButtonSaveLogs;
    private javax.swing.JFormattedTextField jFormattedTextField1;
    private javax.swing.JFrame jFrame1;
    private javax.swing.JFrame jFrame2;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabelGatewayIpAddress;
    private javax.swing.JLabel jLabelISSID;
    private javax.swing.JLabel jLabelIpAddress;
    private javax.swing.JLabel jLabelIpMask;
    private javax.swing.JLabel jLabelSerialOutput;
    private javax.swing.JLabel jLabelSystemOutput;
    private javax.swing.JPanel jPanelButtons;
    private javax.swing.JPanel jPanelDeviceConfig;
    private javax.swing.JPanel jPanelHardware;
    private javax.swing.JPanel jPanelSerialOutput;
    private javax.swing.JPanel jPanelSystemOutput;
    private javax.swing.JScrollPane jScrollPaneSerialOutput;
    private javax.swing.JScrollPane jScrollPaneSystemOutput;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JTextField jTextFieldGatewayIpAddress;
    private javax.swing.JTextField jTextFieldIpAddress;
    private javax.swing.JTextField jTextFieldIpMask;
    private javax.swing.JLabel lblRelease;
    private javax.swing.JTextField txtDevID;
    private javax.swing.JTextField txtHostname;
    private javax.swing.JTextField txtPassphrase;
    private javax.swing.JTextField txtSSID;
    private javax.swing.JTextArea txtSerialOutput;
    private javax.swing.JTextArea txtSystemOutput;
    // End of variables declaration//GEN-END:variables
}
