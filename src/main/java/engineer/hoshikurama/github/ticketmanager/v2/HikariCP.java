package engineer.hoshikurama.github.ticketmanager.v2;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class HikariCP {
    private static HikariDataSource ds;

    static void LaunchDatabase(final String host, final String port, final String dbname, final String username, final String password) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbname + "?serverTimezone=America/Chicago");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds = new HikariDataSource(config);

        DatabaseHandler.checkTables();
    }

    static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }
}
