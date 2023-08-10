
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

		LOGGER.info("SonarDbService: constructor");

		try (InputStream input = new FileInputStream("./conf/sonar.properties")) {

			sonarProperties = new Properties();
			// load a properties file
			sonarProperties.load(input);

			dbUser = sonarProperties.getProperty("sonar.jdbc.username");
			dbPassword = sonarProperties.getProperty("sonar.jdbc.password");
			dbUrl = sonarProperties.getProperty("sonar.jdbc.url");
			LOGGER.info("SonarDbService: JDBC URL : {}", dbUrl);

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

				LOGGER.info("User found : {}", userIdentity);
				return userIdentity;
			}

			LOGGER.info("User \"{}\" not found", externalLogin);
			// Not found
			return null;
		} catch (SQLException e) {
			throw new Exception("Error while finding user " + externalLogin, e);
		}
	}


	/**
	 * Creates a user in database
	 *
	 * @param login            the user's login
	 * @param name             the user's full name
	 * @param email            the user's e-mail
	 * @param externalLogin    the user's external Login
	 * @param identityProvider the identity provider (ex : OIDC)
	 * @throws Exception if the update failed
	 */
	public void createUser(String login, String name, String email, String externalLogin, String identityProvider)
			throws Exception {

		// Check if the user exists first



		// Create user
		String query = "INSERT INTO users (uuid, login, name, email, external_login, external_identity_provider, external_id, is_root, user_local, onboarded, reset_password) "
		+ " VALUES (?, ?, ?, ?, ?, ?, ?, false, false, false, false)";
		// Values for the update
		try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
				PreparedStatement preparedStatement = connection.prepareStatement(query)) {
			// Set the values for the update query
			// Generates uuid
			String uuid =  new UuidGeneratorImpl().generate().toString();

			preparedStatement.setString(1, uuid);
			preparedStatement.setString(2, login);
			preparedStatement.setString(3, name);
			preparedStatement.setString(4, email);
			preparedStatement.setString(5, login);
			preparedStatement.setString(6, identityProvider);
			preparedStatement.setString(7, login);

			// Execute the update
			int rowsUpdated = preparedStatement.executeUpdate();

			// Check if the update was successful
			if (rowsUpdated > 0) {
				LOGGER.info("createUser:Used added successfully for {}", login);

				// Set groups for user
				List<String> defaultGroupsList = Arrays.asList("sonar-users");
				Map<String, String> groups = listGroups(connection);
				defaultGroupsList.forEach(groupName -> {
					// Find and add group
					String groupId = groups.get(groupName);
					if (groupId != null) {
						// Group found -> Add to user
						try {
							addGroupWithUserUuid(uuid, groupId, connection);
						} catch (Exception e) {
							LOGGER.error("Error while adding group for user", e);
						}
					}
				});

			} else {
				throw new Exception("Insert failed for " + login);
			}
		} catch (Exception e) {
			throw new Exception("Error while creating user " + login + " : " + e.getMessage(), e);
		}
	}

	/**
	 * Add a group for the user UUID.
	 *
	 * @param userUuid   the user uuid
	 * @param groupUuid  the group uuid
	 * @param connection the database connection
	 * @throws Exception
	 */
	private void addGroupWithUserUuid(String userUuid, String groupUuid, Connection connection) throws Exception {

		// Create user
		String query = "INSERT INTO groups_users (group_uuid, user_uuid) VALUES (?, ?)";
		// Values for the update
		try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {

			preparedStatement.setString(1, groupUuid);
			preparedStatement.setString(2, userUuid);

			// Execute the update
			int rowsUpdated = preparedStatement.executeUpdate();

			// Check if the update was successful
			if (rowsUpdated > 0) {
				LOGGER.info("addGroupWithUserUuid: Group {} added successfully for user {}", groupUuid, userUuid);

			} else {
				throw new Exception("Insert failed for group " + groupUuid + " and user "  + userUuid);
			}
		} catch (SQLException e) {
			throw new Exception("Error while adding group  " + groupUuid + " for user " + userUuid, e);
		}
	}

	/**
	 * List all sonar groups from database and return them into a map.
	 * @param connection the current db connection
	 * @return a list of sonar groups
	 * @throws Exception if there was a problem
	 */
	private Map<String, String> listGroups(Connection connection) throws Exception {
		Map<String, String> groups = new HashMap<>();


		String query = "SELECT * FROM groups ORDER BY name ASC";

		// Values for the update
		try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
			// Set the values for the update query
			ResultSet rs = preparedStatement.executeQuery();
			while (rs.next()) {
				groups.put(rs.getString("name"), rs.getString("uuid"));

			}

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Sonar groups found : {}", groups);

			}
			return groups;
		} catch (SQLException e) {
			throw new Exception("Error while finding sonar groups", e);
		}

	}

	/**
	 * Change status for user (enabled/disabled).
	 * @param externalLogin
	 * @param identityProvider
	 * @param isActive
	 * @throws Exception
	 */
	public void changeActiveStatus(String externalLogin, String identityProvider, boolean isActive)
			throws Exception {

		// Check first if the user is at the same status ?

		String updateQuery = "UPDATE users SET active = ? WHERE external_identity_provider = ? and external_login = ?";
		// Values for the update
		try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
				PreparedStatement preparedStatement = connection.prepareStatement(updateQuery)) {
			// Set the values for the update query
			preparedStatement.setBoolean(1, isActive);
			preparedStatement.setString(2, identityProvider);
			preparedStatement.setString(3, externalLogin);

			// Execute the update
			preparedStatement.executeUpdate();

			// Check if the update was successful

		} catch (SQLException e) {
			throw new Exception("Error while updating user " + externalLogin, e);
		}
	}


}
