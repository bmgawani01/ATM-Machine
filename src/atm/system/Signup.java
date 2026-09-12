package atm.system;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.sql.*;
import com.toedter.calendar.JDateChooser;
import java.util.*;

public class Signup extends JFrame implements ActionListener {
    JLabel l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12, l13, l14, l15;
    JTextField t1, t2, t3, t4, t5, t6, t7;
    JRadioButton r1, r2, r3, r4, r5;
    JButton b;
    JDateChooser dateChooser;
    Random ran = new Random();
    long first4 = (ran.nextLong() % 9000L) + 1000L;
    String first = "" + Math.abs(first4);

    Signup() {
        setTitle("NEW ACCOUNT APPLICATION FORM");
        setLayout(null); // Using absolute positioning

        // Logo / Icon (Ensure the path exists or handle missing resource gracefully)
        try {
            ImageIcon i1 = new ImageIcon(ClassLoader.getSystemResource("atm system/icons/logo.jpg"));
            Image i2 = i1.getImage().getScaledInstance(100, 100, Image.SCALE_DEFAULT);
            ImageIcon i3 = new ImageIcon(i2);
            JLabel l11 = new JLabel(i3);
            l11.setBounds(20, 0, 100, 100);
            add(l11);
        } catch (Exception e) {
            System.out.println("Logo icon not found.");
        }

        // Labels
        l1 = new JLabel("APPLICATION FORM NO. " + first);
        l1.setFont(new Font("Raleway", Font.BOLD, 38));
        l1.setBounds(140, 20, 600, 40);
        add(l1);

        l2 = new JLabel("Page 1: Personal Details");
        l2.setFont(new Font("Raleway", Font.BOLD, 22));
        l2.setBounds(290, 80, 400, 30);
        add(l2);

        l3 = new JLabel("Name:");
        l3.setFont(new Font("Raleway", Font.BOLD, 20));
        l3.setBounds(100, 140, 200, 30);
        add(l3);

        t1 = new JTextField();
        t1.setFont(new Font("Raleway", Font.BOLD, 14));
        t1.setBounds(300, 140, 400, 30);
        add(t1);

        l4 = new JLabel("Father's Name:");
        l4.setFont(new Font("Raleway", Font.BOLD, 20));
        l4.setBounds(100, 190, 200, 30);
        add(l4);

        t2 = new JTextField();
        t2.setFont(new Font("Raleway", Font.BOLD, 14));
        t2.setBounds(300, 190, 400, 30);
        add(t2);

        l5 = new JLabel("Date of Birth:");
        l5.setFont(new Font("Raleway", Font.BOLD, 20));
        l5.setBounds(100, 240, 200, 30);
        add(l5);

        dateChooser = new JDateChooser();
        dateChooser.setForeground(new Color(105, 105, 105));
        dateChooser.setBounds(300, 240, 400, 30);
        add(dateChooser);

        l6 = new JLabel("Gender:");
        l6.setFont(new Font("Raleway", Font.BOLD, 20));
        l6.setBounds(100, 290, 200, 30);
        add(l6);

        r1 = new JRadioButton("Male");
        r1.setFont(new Font("Raleway", Font.BOLD, 14));
        r1.setBackground(Color.WHITE);
        r1.setBounds(300, 290, 120, 30);
        add(r1);

        r2 = new JRadioButton("Female");
        r2.setFont(new Font("Raleway", Font.BOLD, 14));
        r2.setBackground(Color.WHITE);
        r2.setBounds(450, 290, 120, 30);
        add(r2);

        ButtonGroup gendergroup = new ButtonGroup();
        gendergroup.add(r1);
        gendergroup.add(r2);

        l7 = new JLabel("Email Address:");
        l7.setFont(new Font("Raleway", Font.BOLD, 20));
        l7.setBounds(100, 340, 200, 30);
        add(l7);

        t3 = new JTextField();
        t3.setFont(new Font("Raleway", Font.BOLD, 14));
        t3.setBounds(300, 340, 400, 30);
        add(t3);

        l8 = new JLabel("Marital Status:");
        l8.setFont(new Font("Raleway", Font.BOLD, 20));
        l8.setBounds(100, 390, 200, 30);
        add(l8);

        r3 = new JRadioButton("Married");
        r3.setFont(new Font("Raleway", Font.BOLD, 14));
        r3.setBackground(Color.WHITE);
        r3.setBounds(300, 390, 100, 30);
        add(r3);

        r4 = new JRadioButton("Unmarried");
        r4.setFont(new Font("Raleway", Font.BOLD, 14));
        r4.setBackground(Color.WHITE);
        r4.setBounds(420, 390, 120, 30);
        add(r4);

        r5 = new JRadioButton("Other");
        r5.setFont(new Font("Raleway", Font.BOLD, 14));
        r5.setBackground(Color.WHITE);
        r5.setBounds(560, 390, 100, 30);
        add(r5);

        ButtonGroup maritalgroup = new ButtonGroup();
        maritalgroup.add(r3);
        maritalgroup.add(r4);
        maritalgroup.add(r5);

        l9 = new JLabel("Address:");
        l9.setFont(new Font("Raleway", Font.BOLD, 20));
        l9.setBounds(100, 440, 200, 30);
        add(l9);

        t4 = new JTextField();
        t4.setFont(new Font("Raleway", Font.BOLD, 14));
        t4.setBounds(300, 440, 400, 30);
        add(t4);

        l10 = new JLabel("City:");
        l10.setFont(new Font("Raleway", Font.BOLD, 20));
        l10.setBounds(100, 490, 200, 30);
        add(l10);

        t5 = new JTextField();
        t5.setFont(new Font("Raleway", Font.BOLD, 14));
        t5.setBounds(300, 490, 400, 30);
        add(t5);

        // Next Button
        b = new JButton("Next");
        b.setBackground(Color.BLACK);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Raleway", Font.BOLD, 14));
        b.setBounds(600, 550, 100, 30);
        b.addActionListener(this);
        add(b);

        getContentPane().setBackground(Color.WHITE);
        setSize(850, 680);
        setLocation(500, 120);
        setVisible(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public void actionPerformed(ActionEvent ae) {
        String formno = first;
        String name = t1.getText();
        String fname = t2.getText();
        String dob = ((JTextField) dateChooser.getDateEditor().getUiComponent()).getText();
        String gender = null;
        if (r1.isSelected()) {
            gender = "Male";
        } else if (r2.isSelected()) {
            gender = "Female";
        }

        String email = t3.getText();
        String marital = null;
        if (r3.isSelected()) {
            marital = "Married";
        } else if (r4.isSelected()) {
            marital = "Unmarried";
        } else if (r5.isSelected()) {
            marital = "Other";
        }

        String address = t4.getText();
        String city = t5.getText();

        try {
            if (name.equals("")) {
                JOptionPane.showMessageDialog(null, "Name is Required");
            } else {
                // Database connectivity code snippet goes here
                // Conn c1 = new Conn();
                // String q1 = "insert into signup values('"+formno+"','"+name+"','"+fname+"','"+dob+"','"+gender+"','"+email+"','"+marital+"','"+address+"','"+city+"')";
                // c1.s.executeUpdate(q1);
                
                setVisible(false);
                // new Signup2(formno).setVisible(true); // Proceed to Page 2
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new Signup();
    }
}
