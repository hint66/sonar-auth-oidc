
package org.vaulttec.sonarqube.db;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.sonar.api.server.authentication.UserIdentity;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.core.util.UuidGeneratorImpl;

/**
 * Database service to access and make queries
 */

public class SonarDbService {

	private static final Logger LOGGER = Loggers.get(SonarDbService.class);
	private static final SonarDbService INSTANCE = new SonarDbService();

	Properties sonarProperties;
	private String dbUser;
	private String dbPassword;
	private String dbUrl;

	public SonarDbService() {

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("SonarDbService: constructor");
		}

		try (InputStream input = new FileInputStream("./conf/sonar.properties")) {

			sonarProperties = new Properties();
			// load a properties file
			sonarProperties.load(input);

			dbUser = sonarProperties.getProperty("sonar.jdbc.username");
			dbPassword = sonarProperties.getProperty("sonar.jdbc.password");
			dbUrl = sonarProperties.getProperty("sonar.jdbc.url");
			if (LOGGER.isDebugEnabled()) {
				LOGGER.info("SonarDbService: JDBC URL : {}", dbUrl);
			}

		} catch (IOException e) {
			LOGGER.error("Error while fetching sonar properties - CANNOT USE DATABASE !", e);
		}

		// Force loading Postgresql class.
		// If we don't we have this issue :
		// java.sql.SQLException: No suitable driver found for
		// jdbc:postgresql://dbpostgresql-1-prd/sonar
		try {
			Class.forName("org.postgresql.Driver");
		} catch (ClassNotFoundException e) {
			LOGGER.error("Impossible to load PostgreSQL Driver ! CANNOT USE DATABASE !", e);
		}

	}

	public static SonarDbService getInstance() {
		return INSTANCE;
	}

	/**
	 * Updates a user in database
	 *
	 * @param login            the user's login
	 * @param name             the user's full name
	 * @param email            the user's e-mail
	 * @param externalLogin    the user's external Login
	 * @param identityProvider the identity provider (ex : OIDC)
	 * @throws Exception if the update failed
	 */
	public void updateUser(String login, String name, String email, String externalLogin, String identityProvider)
			throws Exception {
		// Database connection properties
		// SQL query to update the record

		String updateQuery = "UPDATE users SET name = ?, email = ?, login = ? WHERE external_identity_provider = ? and external_login = ?";
		// Values for the update
		try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
				PreparedStatement preparedStatement = connection.prepareStatement(updateQuery)) {
			// Set the values for the update query
			preparedStatement.setString(1, name);
			preparedStatement.setString(2, email);
			preparedStatement.setString(3, login);
			preparedStatement.setString(4, identityProvider);
			preparedStatement.setString(5, externalLogin);

			// Execute the update
			int rowsUpdated = preparedStatement.executeUpdate();

			// Check if the update was successful
			if (rowsUpdated > 0) {
				LOGGER.info("updateUser: Record updated successfully for {}", login);
			} else {
				throw new Exception("updateUser: Record not found or update failed for " + login);
			}
		} catch (SQLException e) {
			throw new Exception("Error while updating user " + login, e);
		}
	}

	/**
	 * Find a user by its external Id
	 *
	 * @param externalLogin    the user's external Login
	 * @param identityProvider the identity provider (ex : OIDC)
	 * @return
	 * @throws Exception if there was a problem
	 */

	public UserIdentity findUserByExternalLogin(String externalLogin, String identityProvider)
			throws Exception {
		// Database connection properties
		// SQL query to update the record

		String query = "SELECT * FROM users WHERE external_identity_provider = ? and external_login = ?";
		// Values for the update
		try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
				PreparedStatement preparedStatement = connection.prepareStatement(query)) {
			// Set the values for the update query
			preparedStatement.setString(1, identityProvider);
			preparedStatement.setString(2, externalLogin);
			ResultSet rs = preparedStatement.executeQuery();
			while (rs.next()) {
				UserIdentity userIdentity = UserIdentity.builder().setProviderLogin(externalLogin)
						.setEmail(rs.getString("email"))
						.setName(rs.getString("name"))
						.setProviderId(rs.getString("external_id")).build();

				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("User found : {}", userIdentity);
				}
				return userIdentity;
			}

			// Not found
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("User \"{}\" not found", externalLogin);
			}
			return null;
		} catch (SQLException e) {
			throw new Exception("Error while finding user " + externalLogin, e);
		}
	}

}
