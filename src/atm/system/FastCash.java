package atm.system;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.sql.*;
import java.util.Date;

public class FastCash extends JFrame implements ActionListener {

    JLabel l1;
    JButton b1, b2, b3, b4, b5, b6, b7;
    String pin;

    FastCash(String pin) {
        this.pin = pin;

        
        ImageIcon icon = new ImageIcon(getClass().getResource("/icons/logo.jpg"));





        Image i2 = icon.getImage().getScaledInstance(960, 1080, Image.SCALE_DEFAULT);
        ImageIcon i3 = new ImageIcon(i2);
        JLabel l3 = new JLabel(i3);
        l3.setBounds(0, 0, 960, 1080);
        add(l3);

        l1 = new JLabel("SELECT WITHDRAWAL AMOUNT");
        l1.setForeground(Color.WHITE);
        l1.setFont(new Font("System", Font.BOLD, 16));

        b1 = new JButton("USD 100");
        b2 = new JButton("USD 500");
        b3 = new JButton("USD 1000");
        b4 = new JButton("USD 2000");
        b5 = new JButton("USD 5000");
        b6 = new JButton("USD 10000");
        b7 = new JButton("BACK");

        setLayout(null);

        l1.setBounds(235, 400, 700, 35);
        l3.add(l1);

        b1.setBounds(170, 499, 150, 35);
        l3.add(b1);

        b2.setBounds(390, 499, 150, 35);
        l3.add(b2);

        b3.setBounds(170, 543, 150, 35);
        l3.add(b3);

        b4.setBounds(390, 543, 150, 35);
        l3.add(b4);

        b5.setBounds(170, 588, 150, 35);
        l3.add(b5);

        b6.setBounds(390, 588, 150, 35);
        l3.add(b6);

        b7.setBounds(390, 633, 150, 35);
        l3.add(b7);

        b1.addActionListener(this);
        b2.addActionListener(this);
        b3.addActionListener(this);
        b4.addActionListener(this);
        b5.addActionListener(this);
        b6.addActionListener(this);
        b7.addActionListener(this);

        setSize(960, 1080);
        setLocation(500, 0);
        setUndecorated(true);
        setVisible(true);
    }

    // FIX 2: Fully implemented the action listener logic to query the DB and track transactions
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == b7) {
            setVisible(false);
            // Replace with your actual main menu class name if different (e.g., Transactions)
            new Transactions(pin).setVisible(true); 
        } else {
            // Extracts numbers from the button string (e.g., "Rs 500" -> "500")
            String amount = ((JButton)ae.getSource()).getText().substring(3); 
            try {
                Conn c = new Conn();
                
                // Step 1: Check balance by computing all previous deposits vs withdrawals
                ResultSet rs = c.s.executeQuery("select * from bank where pin = '" + pin + "'");
                int balance = 0;
                while (rs.next()) {
                    if (rs.getString("type").equalsIgnoreCase("Deposit")) {
                        balance += Integer.parseInt(rs.getString("amount"));
                    } else {
                        balance -= Integer.parseInt(rs.getString("amount"));
                    }
                }

                // Step 2: Handle insufficient balance verification
               if (balance < Double.parseDouble(amount)) {
               JOptionPane.showMessageDialog(null, "Insufficient Balance");
                return;
}

                // Step 3: Insert the current cash withdrawal entry into the database
                Date date = new Date();
                String query = "insert into bank values('" + pin + "', '" + date + "', 'Withdrawal', '" + amount + "')";
                c.s.executeUpdate(query);
                
                JOptionPane.showMessageDialog(null, "USD " + amount + " Debited Successfully");
                
                setVisible(false);
                new Transactions(pin).setVisible(true);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new FastCash("");
    }
}
