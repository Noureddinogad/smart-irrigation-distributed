package core.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Db {

    private static final String URL =
            "jdbc:sqlserver://DESKTOP-MB7273C\\SQLEXPRESS;"
                    + "databaseName=IrrigationDB;"
                    + "encrypt=true;"
                    + "trustServerCertificate=true;";

    private static final String USER = "talend_user";
    private static final String PASS = "Talend@2025";

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
