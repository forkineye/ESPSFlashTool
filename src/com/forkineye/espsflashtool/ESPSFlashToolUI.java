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
import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.showMessageDialog;
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
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
     * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
         */
        try
        {
            /*
        for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                javax.swing.UIManager.setLookAndFeel(info.getClassName());
                break;
            }
        }*/

            // Set system look and feel
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());

        }
        catch (ClassNotFoundException ex)
        {
            java.util.logging.Logger.getLogger(ESPSFlashToolUI.class
                    .getName()).log(java.util.logging.Level.SEVERE, null, ex);

        }
        catch (InstantiationException ex)
        {
            java.util.logging.Logger.getLogger(ESPSFlashToolUI.class
                    .getName()).log(java.util.logging.Level.SEVERE, null, ex);

        }
        catch (IllegalAccessException ex)
        {
            java.util.logging.Logger.getLogger(ESPSFlashToolUI.class
                    .getName()).log(java.util.logging.Level.SEVERE, null, ex);

        }
        catch (javax.swing.UnsupportedLookAndFeelException ex)
        {
            java.util.logging.Logger.getLogger(ESPSFlashToolUI.class
                    .getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
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
                    txtSerialOutput.append(new String(data, StandardCharsets.US_ASCII));
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
    private void initComponents()
    {

        dlgSave = new javax.swing.JFileChooser();
        jFormattedTextField1 = new javax.swing.JFormattedTextField();
        jFrame1 = new javax.swing.JFrame();
        jFrame2 = new javax.swing.JFrame();
        lblRelease = new javax.swing.JLabel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0));
        jPanel1 = new javax.swing.JPanel();
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
        btnDownload = new javax.swing.JButton();
        btnFlash = new javax.swing.JButton();
        btnExport = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtSystemOutput = new javax.swing.JTextArea();
        jScrollPane2 = new javax.swing.JScrollPane();
        txtSerialOutput = new javax.swing.JTextArea();
        jLabelSystemOutput = new javax.swing.JLabel();
        jLabelSerialOutput = new javax.swing.JLabel();
        jButtonClearSystemOutput = new javax.swing.JButton();
        jButtonClearSerialOutput = new javax.swing.JButton();

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

        lblRelease.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        lblRelease.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblRelease.setText("* Release Information Not Found *");

        jLabel4.setText("Serial Port");

        jLabel3.setText("Hardware");

        cboxPort.setModel(modelPort);
        cboxPort.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                cboxPortActionPerformed(evt);
            }
        });

        cboxFirmware.setModel(modelBoard);
        cboxFirmware.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                cboxFirmwareActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(cboxFirmware, 0, 405, Short.MAX_VALUE)
                    .addComponent(cboxPort, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(cboxFirmware, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(cboxPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        CheckBoxApFallback.setSelected(true);
        CheckBoxApFallback.setText("AP Fallback");
        CheckBoxApFallback.setToolTipText("Select to fallback to AP mode when no network AP can be found.");
        CheckBoxApFallback.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        CheckBoxReboot.setText("Reboot");
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

        jLabel5.setText("Device ID");

        txtDevID.setToolTipText("Plain text name to help you identify this device");

        jLabel8.setText("Hostname");

        txtHostname.setToolTipText("Auto-generated if left blank");
        txtHostname.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                txtHostnameKeyReleased(evt);
            }
        });

        jLabel2.setText("Passphrase");

        jLabelISSID.setText("SSID");

        txtPassphrase.setToolTipText("Enter your AP Passphrase");

        txtSSID.setToolTipText("Enter your AP SSID");

        jLabelIpAddress.setText("IP Address");

        jLabelIpMask.setText("IP Mask");

        jLabelGatewayIpAddress.setText("Gateway IP Address");

        jTextFieldIpAddress.setText("000.000.000.000");
        jTextFieldIpAddress.setToolTipText("");
        jTextFieldIpAddress.addFocusListener(new java.awt.event.FocusAdapter()
        {
            public void focusLost(java.awt.event.FocusEvent evt)
            {
                IpAddressFocusLost(evt);
            }
        });
        jTextFieldIpAddress.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                IpAddressKeyReleased(evt);
            }
        });

        jTextFieldIpMask.setText("000.000.000.000");
        jTextFieldIpMask.setToolTipText("");
        jTextFieldIpMask.addFocusListener(new java.awt.event.FocusAdapter()
        {
            public void focusLost(java.awt.event.FocusEvent evt)
            {
                IpAddressFocusLost(evt);
            }
        });
        jTextFieldIpMask.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                IpAddressKeyReleased(evt);
            }
        });

        jTextFieldGatewayIpAddress.setText("000.000.000.000");
        jTextFieldGatewayIpAddress.setToolTipText("");
        jTextFieldGatewayIpAddress.addFocusListener(new java.awt.event.FocusAdapter()
        {
            public void focusLost(java.awt.event.FocusEvent evt)
            {
                IpAddressFocusLost(evt);
            }
        });
        jTextFieldGatewayIpAddress.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                IpAddressKeyReleased(evt);
            }
        });

        javax.swing.GroupLayout jPanelDeviceConfigLayout = new javax.swing.GroupLayout(jPanelDeviceConfig);
        jPanelDeviceConfig.setLayout(jPanelDeviceConfigLayout);
        jPanelDeviceConfigLayout.setHorizontalGroup(
            jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelDeviceConfigLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelDeviceConfigLayout.createSequentialGroup()
                        .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel8)
                            .addComponent(jLabel5)
                            .addComponent(jLabel2)
                            .addComponent(jLabelISSID))
                        .addGap(18, 18, 18)
                        .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtDevID)
                            .addComponent(txtHostname)
                            .addComponent(txtPassphrase)
                            .addComponent(txtSSID)))
                    .addGroup(jPanelDeviceConfigLayout.createSequentialGroup()
                        .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanelDeviceConfigLayout.createSequentialGroup()
                                .addComponent(CheckBoxApFallback, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(CheckBoxReboot))
                            .addComponent(jLabelIpAddress, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabelIpMask, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabelGatewayIpAddress, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(CheckBoxUseDhcp)
                            .addComponent(jTextFieldIpAddress, javax.swing.GroupLayout.DEFAULT_SIZE, 101, Short.MAX_VALUE)
                            .addComponent(jTextFieldIpMask)
                            .addComponent(jTextFieldGatewayIpAddress))
                        .addContainerGap(232, Short.MAX_VALUE))))
        );
        jPanelDeviceConfigLayout.setVerticalGroup(
            jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelDeviceConfigLayout.createSequentialGroup()
                .addGap(23, 23, 23)
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtSSID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelISSID))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(txtPassphrase, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(txtHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(txtDevID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(CheckBoxApFallback, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(CheckBoxReboot)
                    .addComponent(CheckBoxUseDhcp))
                .addGap(11, 11, 11)
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelIpAddress)
                    .addComponent(jTextFieldIpAddress, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelIpMask)
                    .addComponent(jTextFieldIpMask, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelDeviceConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelGatewayIpAddress)
                    .addComponent(jTextFieldGatewayIpAddress, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        CheckBoxApFallback.getAccessibleContext().setAccessibleName(" AP Fallback");

        btnDownload.setText("Download Config");
        btnDownload.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnDownloadActionPerformed(evt);
            }
        });

        btnFlash.setText("Upload Images");
        btnFlash.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnFlashActionPerformed(evt);
            }
        });

        btnExport.setText("Build EFU");
        btnExport.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnExportActionPerformed(evt);
            }
        });

        txtSystemOutput.setColumns(20);
        txtSystemOutput.setRows(5);
        jScrollPane1.setViewportView(txtSystemOutput);

        txtSerialOutput.setColumns(20);
        txtSerialOutput.setRows(5);
        jScrollPane2.setViewportView(txtSerialOutput);

        jLabelSystemOutput.setText("System Output");
        jLabelSystemOutput.setToolTipText("");

        jLabelSerialOutput.setText("Serial Output");

        jButtonClearSystemOutput.setText("Clear");
        jButtonClearSystemOutput.setActionCommand("jButtonClearSystemOutput");
        jButtonClearSystemOutput.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                jButtonClearSystemOutputActionPerformed(evt);
            }
        });

        jButtonClearSerialOutput.setText("Clear");
        jButtonClearSerialOutput.setActionCommand("jButtonClearSerialOutput");
        jButtonClearSerialOutput.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                jButtonClearSerialOutputActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGap(20, 20, 20)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jPanelDeviceConfig, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addComponent(lblRelease, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGap(144, 144, 144)
                        .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(btnDownload, javax.swing.GroupLayout.PREFERRED_SIZE, 164, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnFlash, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(btnExport, javax.swing.GroupLayout.PREFERRED_SIZE, 158, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(10, 10, 10))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(jScrollPane1)
                        .addContainerGap())
                    .addComponent(jScrollPane2)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabelSystemOutput, javax.swing.GroupLayout.PREFERRED_SIZE, 76, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jButtonClearSystemOutput))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabelSerialOutput, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jButtonClearSerialOutput)))
                        .addContainerGap())))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblRelease)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelDeviceConfig, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnDownload)
                    .addComponent(btnFlash)
                    .addComponent(btnExport))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSystemOutput)
                    .addComponent(jButtonClearSystemOutput))
                .addGap(8, 8, 8)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 181, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSerialOutput)
                    .addComponent(jButtonClearSerialOutput))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 175, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18))
        );

        btnFlash.getAccessibleContext().setAccessibleName("Upload Image Set");
        jLabelSystemOutput.getAccessibleContext().setAccessibleName("System Output");
        jLabelSystemOutput.getAccessibleContext().setAccessibleDescription("");
        jButtonClearSystemOutput.getAccessibleContext().setAccessibleName("jButtonClearSystemOutput");

        pack();
    }// </editor-fold>//GEN-END:initComponents

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

    private void cboxFirmwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cboxFirmwareActionPerformed
        ESPSFlashTool.board = cboxFirmware.getItemAt(cboxFirmware.getSelectedIndex());
    }//GEN-LAST:event_cboxFirmwareActionPerformed

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
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanelDeviceConfig;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
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
