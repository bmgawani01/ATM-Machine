package atm.system;

import java.sql.*;

public class Conn {

    Connection c;
    Statement s;

    public Conn() {
        try {
            // Load the MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Corrected: Replaced the semicolon with a comma after the URL string
            c = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/bankmanagementsystem_db",
                "root",
                "#Brightgawani2006"
            );

            s = c.createStatement();

            System.out.println("Database connected successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
