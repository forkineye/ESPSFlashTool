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
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.showMessageDialog;
import javax.swing.filechooser.FileNameExtensionFilter;

public class ESPSFlashToolUI extends javax.swing.JFrame
{

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
    }

    private boolean serializeConfig()
    {
        ESPSFlashTool.deviceConfig.setSSID(txtSSID.getText());
        ESPSFlashTool.deviceConfig.setPassphrase(txtPassphrase.getText());
        ESPSFlashTool.deviceConfig.setHostname(txtHostname.getText());
        ESPSFlashTool.deviceConfig.setId(txtDevID.getText());
        ESPSFlashTool.deviceConfig.setAp_fallback(CheckBoxApFallback.isSelected());
        ESPSFlashTool.deviceConfig.setReboot(CheckBoxReboot.isSelected());

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

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of
     * this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

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
        jSplitPane1 = new javax.swing.JSplitPane();
        pnlSystem = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtSystemOutput = new javax.swing.JTextArea();
        jLabel7 = new javax.swing.JLabel();
        btnFlash = new javax.swing.JButton();
        btnExport = new javax.swing.JButton();
        btnDownload = new javax.swing.JButton();
        pnlSerial = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        txtSerialOutput = new javax.swing.JTextArea();
        jLabel6 = new javax.swing.JLabel();
        txtHostname = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        lblRelease = new javax.swing.JLabel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0));
        CheckBoxApFallback = new javax.swing.JCheckBox();
        CheckBoxReboot = new javax.swing.JCheckBox();

        dlgSave.setDialogType(javax.swing.JFileChooser.SAVE_DIALOG);
        dlgSave.setDialogTitle("Save Firmware Update");

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("ESPixelStick Flash Tool");

        txtPassphrase.setToolTipText("Enter your AP Passphrase");

        cboxFirmware.setModel(modelBoard);
        cboxFirmware.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                cboxFirmwareActionPerformed(evt);
            }
        });

        txtDevID.setToolTipText("Plain text name to help you identify this device");

        jLabel5.setText("Device ID");

        jLabel4.setText("Serial Port");

        cboxPort.setModel(modelPort);
        cboxPort.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                cboxPortActionPerformed(evt);
            }
        });

        jLabel3.setText("Hardware");

        txtSSID.setToolTipText("Enter your AP SSID");

        jLabel2.setText("Passphrase");

        jLabel1.setText("SSID");

        jSplitPane1.setBorder(null);
        jSplitPane1.setDividerLocation(200);
        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        txtSystemOutput.setEditable(false);
        txtSystemOutput.setColumns(20);
        txtSystemOutput.setRows(5);
        jScrollPane1.setViewportView(txtSystemOutput);

        jLabel7.setText("Status");

        btnFlash.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        btnFlash.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/forkineye/espsflashtool/upload.png"))); // NOI18N
        btnFlash.setText("Upload");
        btnFlash.setToolTipText("Program your ESPixelStick with current settings");
        btnFlash.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnFlashActionPerformed(evt);
            }
        });

        btnExport.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        btnExport.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/forkineye/espsflashtool/export.png"))); // NOI18N
        btnExport.setText("Build EFU");
        btnExport.setToolTipText("Builds an OTA update file");
        btnExport.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnExportActionPerformed(evt);
            }
        });

        btnDownload.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        btnDownload.setText("Download");
        btnDownload.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnDownloadActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnlSystemLayout = new javax.swing.GroupLayout(pnlSystem);
        pnlSystem.setLayout(pnlSystemLayout);
        pnlSystemLayout.setHorizontalGroup(
            pnlSystemLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 491, Short.MAX_VALUE)
            .addGroup(pnlSystemLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlSystemLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnlSystemLayout.createSequentialGroup()
                        .addComponent(btnDownload)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnFlash, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnExport))
                    .addGroup(pnlSystemLayout.createSequentialGroup()
                        .addComponent(jLabel7)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        pnlSystemLayout.setVerticalGroup(
            pnlSystemLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnlSystemLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlSystemLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnFlash)
                    .addComponent(btnExport)
                    .addComponent(btnDownload))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 132, Short.MAX_VALUE))
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
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 491, Short.MAX_VALUE)
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
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 119, Short.MAX_VALUE))
        );

        jSplitPane1.setBottomComponent(pnlSerial);

        txtHostname.setToolTipText("Auto-generated if left blank");
        txtHostname.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                txtHostnameKeyReleased(evt);
            }
        });

        jLabel8.setText("Hostname");

        lblRelease.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        lblRelease.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblRelease.setText("* Release Information Not Found *");

        CheckBoxApFallback.setSelected(true);
        CheckBoxApFallback.setText("AP Fallback");
        CheckBoxApFallback.setToolTipText("Select to fallback to AP mode when no network AP can be found.");
        CheckBoxApFallback.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        CheckBoxApFallback.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                CheckBoxApFallbackActionPerformed(evt);
            }
        });

        CheckBoxReboot.setText("Reboot");
        CheckBoxReboot.setActionCommand("CheckBoxReboot");
        CheckBoxReboot.setFocusTraversalPolicyProvider(true);
        CheckBoxReboot.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSplitPane1))
            .addComponent(lblRelease, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                    .addGap(16, 16, 16)
                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addComponent(jLabel8, javax.swing.GroupLayout.Alignment.TRAILING)))
                                .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.TRAILING))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(21, 21, 21)
                                .addComponent(jLabel5)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtDevID, javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(txtHostname, javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(txtPassphrase, javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(txtSSID, javax.swing.GroupLayout.Alignment.CENTER)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(77, 77, 77)
                                .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE))))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(CheckBoxApFallback, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(CheckBoxReboot)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, 62, Short.MAX_VALUE)
                            .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(cboxFirmware, 0, 425, Short.MAX_VALUE)
                            .addComponent(cboxPort, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
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
                    .addComponent(CheckBoxApFallback, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(CheckBoxReboot))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboxFirmware, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboxPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jSplitPane1)
                .addContainerGap())
        );

        CheckBoxApFallback.getAccessibleContext().setAccessibleName(" AP Fallback");

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

    private void CheckBoxApFallbackActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_CheckBoxApFallbackActionPerformed
    {//GEN-HEADEREND:event_CheckBoxApFallbackActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_CheckBoxApFallbackActionPerformed

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
            disableInterface();
            ImageTask ftask = new ImageTask(ImageTask.ImageTaskActionToPerform.CREATE_AND_UPLOAD_ALL); // SwingWorker task to build and flash
            ftask.execute();
        }
    }//GEN-LAST:event_btnFlashActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox CheckBoxApFallback;
    private javax.swing.JCheckBox CheckBoxReboot;
    private javax.swing.JButton btnDownload;
    private javax.swing.JButton btnExport;
    private javax.swing.JButton btnFlash;
    private javax.swing.JComboBox<Board> cboxFirmware;
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
