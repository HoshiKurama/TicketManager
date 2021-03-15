package engineer.hoshikurama.github.ticketmanager;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class Hikari {
    private static HikariConfig config;
    private static HikariDataSource ds;

    static void LaunchDatabase(final String host, final String port, final String dbname, final String username, final String password) throws SQLException {
        config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbname + "?serverTimezone=America/Chicago");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds = new HikariDataSource(config);

        DatabaseHandler.checkTables();
    }



    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }
}
