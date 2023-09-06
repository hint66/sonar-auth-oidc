package org.vaulttec.sonarqube.db;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


public class SonarDataSource {

	private static final Logger LOGGER = Loggers.get(SonarDbService.class);
    private static HikariConfig config = new HikariConfig();

	private static Properties sonarProperties = null;
	private static String dbUser = null;
	private static String dbPassword = null;
	private static String dbUrl = null;
    private static HikariDataSource ds;

	static {

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
	}

	public static Connection getConnection() throws SQLException {
		if (sonarProperties == null) {
			initialize();
		}

		return ds.getConnection();
	}

	private static void initialize() {

		try (InputStream input = new FileInputStream("./conf/sonar.properties")) {

			sonarProperties = new Properties();
			// load a properties file
			sonarProperties.load(input);

			dbUser = sonarProperties.getProperty("sonar.jdbc.username");
			dbPassword = sonarProperties.getProperty("sonar.jdbc.password");
			dbUrl = sonarProperties.getProperty("sonar.jdbc.url");

			config.setJdbcUrl(dbUrl);
			config.setUsername(dbUser);
			config.setPassword(dbPassword);
			ds = new HikariDataSource(config);

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("SonarDbService: JDBC URL : {}", dbUrl);
			}

		} catch (IOException e) {
			LOGGER.error("Error while fetching sonar properties - CANNOT USE DATABASE !", e);
		}
	}

	private SonarDataSource() {
	}
}
