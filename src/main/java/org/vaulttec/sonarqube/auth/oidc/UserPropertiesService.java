/*
 * OpenID Connect Authentication for SonarQube
 * Copyright (c) 2021 Torsten Juergeleit
 * mailto:torsten AT vaulttec DOT org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vaulttec.sonarqube.auth.oidc;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;

import org.sonar.api.server.ServerSide;
import org.sonar.api.server.authentication.UserIdentity;
import org.sonar.api.user.UserQuery;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.google.gson.Gson;

import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;

@ServerSide
public class UserPropertiesService implements WebService {

  private static final Logger LOGGER = Loggers.get(UserPropertiesService.class);


  private final OidcConfiguration config;
  private Gson gson = null;
  Properties sonarProperties;
  private String dbUser;
  private String dbPassword;
  private String dbUrl;

  public UserPropertiesService(OidcConfiguration config) {

	LOGGER.info("UserPropertiesService: constructor");
    this.config = config;

    gson = new Gson();

	try (InputStream input = new FileInputStream("./conf/sonar.properties")) {

		sonarProperties= new Properties();
		// load a properties file
		sonarProperties.load(input);


		dbUser = sonarProperties.getProperty("sonar.jdbc.username");
		dbPassword = sonarProperties.getProperty("sonar.jdbc.password");
		dbUrl = sonarProperties.getProperty("sonar.jdbc.url");
		LOGGER.info("UserPropertiesService:sonar JDBC URL : {}", dbUrl);


	} catch (IOException ex) {
	ex.printStackTrace();
	}


  }

	@Override
	public void define(Context context) {
		LOGGER.info("UserPropertiesService: define");
		NewController controller = context.createController("api/user");
		controller.setDescription("Additional User API");

		NewAction action = controller.createAction("update")
				.setDescription("Updates a OIDC user when classic SonarQube API cannot do")
				.setPost(true)
				.setHandler(this::handleMyEndpoint)
				.setSince("2.1.2");

		action.createParam("name")
			.setDescription("User's full name")
			.setExampleValue("John Doe")
			.setRequired(true);

		action.createParam("email")
				.setDescription("User's e-mail")
				.setExampleValue("john.doe@example.com")
				.setRequired(true);

		action.createParam("uid")
				.setDescription("User's sonar UID")
				.setExampleValue("johndoe")
				.setRequired(true);

		controller.done();
	}

	private void handleMyEndpoint(Request request, Response response) throws IOException {

		System.out.println("UserPropertiesService: handleMyEndpoint");
		LOGGER.info("UserPropertiesService: start handleMyEndpoint");
		String name = request.mandatoryParam("name");
		String email = request.mandatoryParam("email");
		String uid = request.mandatoryParam("uid");
		LOGGER.info("POST user : name={}, email={}, uid={}", name, email, uid);


		if (!config.isEnabled() || config.updateOidPropsName()) {

			// If the property updateOidProps is enabled (auto update from OIDc), return NOT
			// ALLOWED
			LOGGER.info("Update user not allowed (update from OIDC is enabled)");
			response.stream().setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
			response.noContent();
			return;

		}
		LOGGER.info("Update user (update from OIDC is disabled)");

		Map<String, String> output = new HashMap<String, String>();
		try {
			updateUser(uid, name, email, uid, "oidc");

			output.put("status", "ok");
			sendAsJson(response, output, 200);

		} catch (Exception e) {
			e.printStackTrace();
			output.put("status", "error");
			sendAsJson(response, output, 404);
		}


	}



  private void sendAsJson(
    Response response,
    Object obj, int status) throws IOException {

		LOGGER.info("UserPropertiesService:sendAsJson");
		System.out.println("UserPropertiesService:sendAsJson");

    response.stream().setMediaType("application/json").setStatus(status);

	OutputStream out = response.stream().output();

    String res = gson.toJson(obj);
	System.out.println("UserPropertiesService: send JSON : " + res);
	out.write(res.getBytes());
    out.flush();
	out.close();
  }


  private void updateUser(String login, String name, String email, String externalId, String identityProvider) throws Exception {
		// Database connection properties
		// SQL query to update the record

		String updateQuery = "UPDATE users SET name = ?, email = ?, login = ? WHERE external_identity_provider = ? and external_login = ?";
		Class.forName("org.postgresql.Driver");
		System.out.println("Driver version: " + org.postgresql.Driver.getVersion());
		// Values for the update
		try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
				PreparedStatement preparedStatement = connection.prepareStatement(updateQuery)) {
			// Set the values for the update query
			preparedStatement.setString(1, name);
			preparedStatement.setString(2, email);
			preparedStatement.setString(3, login);
			preparedStatement.setString(4, identityProvider);
			preparedStatement.setString(5, externalId);

			// Execute the update
			int rowsUpdated = preparedStatement.executeUpdate();

			// Check if the update was successful
			if (rowsUpdated > 0) {
				LOGGER.info("updateUser: Record updated successfully for {}", login);
			} else {
				throw new Exception("updateUser: Record not found or update failed for " + login);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new Exception("Error while updating user " + login, e);
		}
  }
}
