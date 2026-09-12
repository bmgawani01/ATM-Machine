package atm.system;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.sql.*;

public class Transactions extends JFrame implements ActionListener {

    JLabel l1;
    JButton b1, b2, b3, b4, b5, b6, b7;
    String pin;

    public Transactions(String pin) {
        this.pin = pin;
        
        // Background ATM Image Setup
        ImageIcon icon = new ImageIcon(ClassLoader.getSystemResource("icons/atm.jpg"));
        Image i2 = icon.getImage().getScaledInstance(1000, 1180, Image.SCALE_DEFAULT);
        ImageIcon i3 = new ImageIcon(i2);
        JLabel l2 = new JLabel(i3);
        l2.setBounds(0, 0, 960, 1080);
        add(l2);

        // Header Label
        l1 = new JLabel("Please Select Your Transaction");
        l1.setForeground(Color.WHITE);
        l1.setFont(new Font("System", Font.BOLD, 16));
        l1.setBounds(235, 400, 700, 35);
        l2.add(l1);

        // Button Initializations
        b1 = new JButton("DEPOSIT");
        b2 = new JButton("CASH WITHDRAWL");
        b3 = new JButton("FAST CASH");
        b4 = new JButton("MINI STATEMENT");
        b5 = new JButton("PIN CHANGE");
        b6 = new JButton("BALANCE ENQUIRY");
        b7 = new JButton("EXIT");

        // Layout & Position Setup for Buttons
        setLayout(null);

        b1.setBounds(170, 499, 150, 35);
        l2.add(b1);

        b2.setBounds(390, 499, 150, 35);
        l2.add(b2);

        b3.setBounds(170, 543, 150, 35);
        l2.add(b3);

        b4.setBounds(390, 543, 150, 35);
        l2.add(b4);

        b5.setBounds(170, 588, 150, 35);
        l2.add(b5);

        b6.setBounds(390, 588, 150, 35);
        l2.add(b6);

        b7.setBounds(390, 633, 150, 35);
        l2.add(b7);

        // Action Listeners
        b1.addActionListener(this);
        b2.addActionListener(this);
        b3.addActionListener(this);
        b4.addActionListener(this);
        b5.addActionListener(this);
        b6.addActionListener(this);
        b7.addActionListener(this);

        // Window Frame Settings
        setSize(960, 1080);
        setLocation(500, 0);
        setUndecorated(true);
        setVisible(true);
    } 

    // Mandatory method implementation for ActionListener interface
    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == b7) {
            System.exit(0); // Closes the application when EXIT is clicked
        } else if (ae.getSource() == b1) {
            // TODO: Link to Deposit screen (e.g., new Deposit(pin).setVisible(true);)
            setVisible(false);
        } else if (ae.getSource() == b2) {
            // TODO: Link to Withdrawal screen
            setVisible(false);
        } else if (ae.getSource() == b3) {
            // TODO: Link to Fast Cash screen
            setVisible(false);
        } else if (ae.getSource() == b4) {
            // TODO: Link to Mini Statement screen
            setVisible(false);
        } else if (ae.getSource() == b5) {
            // TODO: Link to Pin Change screen
            setVisible(false);
        } else if (ae.getSource() == b6) {
            // TODO: Link to Balance Enquiry screen
            setVisible(false);
        }
    }

    public static void main(String[] args) {
        new Transactions("");
    }
}
