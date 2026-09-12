package atm.system;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.sql.*;

public class Pin extends JFrame implements ActionListener {
    
    JPasswordField t1, t2;
    JButton b1, b2;                               
    JLabel l1, l2, l3;
    String pin;

    Pin(String pin) {
        this.pin = pin;
        
        
        ImageIcon i1 = new ImageIcon("src/atm/system/icons/atm.jpg");
        Image i2 = i1.getImage().getScaledInstance(1000, 1180, Image.SCALE_DEFAULT);
        ImageIcon i3 = new ImageIcon(i2);
        JLabel l4 = new JLabel(i3);
        l4.setBounds(0, 0, 960, 1080);
        add(l4);
        
        l1 = new JLabel("CHANGE YOUR PIN");
        l1.setFont(new Font("System", Font.BOLD, 16));
        l1.setForeground(Color.WHITE);
        
        l2 = new JLabel("New PIN:");
        l2.setFont(new Font("System", Font.BOLD, 16));
        l2.setForeground(Color.WHITE);
        
        l3 = new JLabel("Re-Enter New PIN:");
        l3.setFont(new Font("System", Font.BOLD, 16));
        l3.setForeground(Color.WHITE);
        
        t1 = new JPasswordField();
        t1.setFont(new Font("Raleway", Font.BOLD, 25));
        
        t2 = new JPasswordField();
        t2.setFont(new Font("Raleway", Font.BOLD, 25));
        
        b1 = new JButton("CHANGE");
        b2 = new JButton("BACK");
        
        b1.addActionListener(this);
        b2.addActionListener(this);
        
        setLayout(null);
        
        l1.setBounds(280, 330, 800, 35);
        l4.add(l1);
        
        l2.setBounds(180, 390, 150, 35);
        l4.add(l2);
        
        l3.setBounds(180, 440, 200, 35);
        l4.add(l3);
        
        t1.setBounds(350, 390, 180, 25);
        l4.add(t1);
        
        t2.setBounds(350, 440, 180, 25);
        l4.add(t2);
        
        b1.setBounds(390, 588, 150, 35);
        l4.add(b1);
        
        b2.setBounds(390, 633, 150, 35);
        l4.add(b2);
        
        setSize(960, 1080);
        setLocation(500, 0);
        setUndecorated(true); // Removes window borders for a clean ATM look
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == b1) {
            try {
                String npin = new String(t1.getPassword());
                String rpin = new String(t2.getPassword());
                
                if (!npin.equals(rpin)) {
                    JOptionPane.showMessageDialog(null, "Entered PIN does not match");
                    return;
                }
                
                if (npin.equals("")) {
                    JOptionPane.showMessageDialog(null, "Please enter new PIN");
                    return;
                }
                
                // Establish connection and update database
                // Replace 'Conn' with your actual JDBC connection class name
                Conn c1 = new Conn();
                
                // Update queries across all tables tracking the user's PIN
                String q1 = "update bank set pin = '"+npin+"' where pin = '"+pin+"'";
                String q2 = "update login set pin = '"+npin+"' where pin = '"+pin+"'";
                String q3 = "update signupthree set pin = '"+npin+"' where pin = '"+pin+"'";

                c1.s.executeUpdate(q1);
                c1.s.executeUpdate(q2);
                c1.s.executeUpdate(q3);

                JOptionPane.showMessageDialog(null, "PIN changed successfully");
                
                setVisible(false);
                new Transactions(npin).setVisible(true); // Redirect back to dashboard
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (ae.getSource() == b2) {
            setVisible(false);
            new Transactions(pin).setVisible(true); // Go back without changing anything
        }
    }

    public static void main(String[] args) {
        new Pin("").setVisible(true);
    }
}
