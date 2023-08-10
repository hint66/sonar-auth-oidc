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
package org.vaulttec.sonarqube.api;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.sonar.api.server.ServerSide;
import org.sonar.api.server.authentication.UserIdentity;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.vaulttec.sonarqube.auth.oidc.OidcConfiguration;
import org.vaulttec.sonarqube.db.SonarDbService;

import com.google.gson.Gson;

@ServerSide
public class UserApiWebservice implements WebService {

	private static final Logger LOGGER = Loggers.get(UserApiWebservice.class);

	private final OidcConfiguration config;
	private Gson gson = null;

	public UserApiWebservice(OidcConfiguration config) {

		LOGGER.info("UserApiWebservice: constructor");
		this.config = config;

		gson = new Gson();

	}

	@Override
	public void define(Context context) {
		LOGGER.info("UserApiWebservice: define");
		NewController controller = context.createController("api/user");
		controller.setDescription("Additional User API");

		// UPDATE USER API
		NewAction action = controller.createAction("update")
				.setDescription("Updates a OIDC user when classic SonarQube API cannot do")
				.setPost(true)
				.setHandler(this::callUpdateUserAPI)
				.setSince("2.1.2");

		action.createParam("name")
				.setDescription("User's full name")
				.setExampleValue("John Doe")
				.setRequired(true);

		action.createParam("email")
				.setDescription("User's e-mail")
				.setExampleValue("john.doe@example.com")
				.setRequired(true);

		action.createParam("login")
				.setDescription("User's login")
				.setExampleValue("johndoe")
				.setRequired(true);

		// FIND USER API
		NewController controllerFind = context.createController("api");
		NewAction actionFind = controllerFind.createAction("user")
				.setDescription("Find a user by external login")
				.setHandler(this::callFindUserAPI)
				.setSince("2.1.2");

		actionFind.createParam("login")
				.setDescription("User's external login")
				.setExampleValue("johndoe")
				.setRequired(true);

		actionFind.createParam("provider")
				.setDescription("Identity provider (default : oidc)")
				.setExampleValue("oidc")
				.setDefaultValue("oidc")
				.setRequired(false);

		controllerFind.done();

		// ADD USER API
		NewAction actionAdd = controller.createAction("create")
				.setDescription("Create a user")
				.setHandler(this::callCreateUserAPI)
				.setPost(true)
				.setSince("2.1.2");

		actionAdd.createParam("login")
				.setDescription("User's login")
				.setExampleValue("johndoe")
				.setRequired(true);

		actionAdd.createParam("name")
				.setDescription("User's full name")
				.setExampleValue("John Doe")
				.setRequired(true);

		actionAdd.createParam("email")
				.setDescription("User's e-mail")
				.setExampleValue("john.doe@example.com")
				.setRequired(true);

		actionAdd.createParam("provider")
				.setDescription("Identity provider (default : oidc)")
				.setExampleValue("oidc")
				.setDefaultValue("oidc")
				.setRequired(false);

		// ACTIVATE USER
		NewAction actionActivate = controller.createAction("activate")
				.setDescription("Activates a user")
				.setHandler(this::callActivateUserAPI)
				.setPost(true)
				.setSince("2.1.2");

		actionActivate.createParam("login")
				.setDescription("User's login")
				.setExampleValue("johndoe")
				.setRequired(true);

		actionActivate.createParam("provider")
				.setDescription("Identity provider (default : oidc)")
				.setExampleValue("oidc")
				.setDefaultValue("oidc")
				.setRequired(false);

		// DEACTIVATE USER
		actionActivate = controller.createAction("deactivate")
				.setDescription("Deactivates a user")
				.setHandler(this::callDeactivateUserAPI)
				.setPost(true)
				.setSince("2.1.2");

		actionActivate.createParam("login")
				.setDescription("User's login")
				.setExampleValue("johndoe")
				.setRequired(true);

		actionActivate.createParam("provider")
				.setDescription("Identity provider (default : oidc)")
				.setExampleValue("oidc")
				.setDefaultValue("oidc")
				.setRequired(false);

		controller.done();

	}

	/**
	 * API GET /api/user
	 * Find a user. If multiple records are found, returns the first.
	 *
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	private void callFindUserAPI(Request request, Response response) throws IOException {

		LOGGER.info("UserApiWebservice: start callFindUserAPI");
		String login = request.mandatoryParam("login");
		String provider = request.param("provider");
		LOGGER.info("callFindUserAPI : login={}, provider={}", login, provider);

		try {
			UserIdentity user = SonarDbService.getInstance().findUserByExternalLogin(login, provider);

			if (user == null) {
				// Not found
				response.stream().setStatus(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
			sendAsJson(response, user, HttpServletResponse.SC_OK);

		} catch (Exception e) {
			LOGGER.error("Error while finding user " + login, e);
			response.stream().setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}

	}

	private void callUpdateUserAPI(Request request, Response response) throws IOException {

		LOGGER.info("UserApiWebservice: start callUpdateUserAPI");
		String name = request.mandatoryParam("name");
		String email = request.mandatoryParam("email");
		String login = request.mandatoryParam("login");
		LOGGER.info("POST user : name={}, email={}, login={}", name, email, login);

		if (!config.isEnabled() || config.updateOidPropsName()) {

			// If the property updateOidProps is enabled (auto update from OIDc), return NOT
			// ALLOWED
			LOGGER.info("Update user not allowed (update from OIDC is enabled)");
			response.stream().setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
			return;

		}
		LOGGER.info("Update user (update from OIDC is disabled)");

		Map<String, String> output = new HashMap<String, String>();
		try {
			SonarDbService.getInstance().updateUser(login, name, email, login, "oidc");

			output.put("status", "ok");
			sendAsJson(response, output, HttpServletResponse.SC_OK);

		} catch (Exception e) {
			LOGGER.error("Error while updating user " + login, e);
			response.stream().setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}

	}

	/**
	 * API POST /api/user
	 * Create a user.
	 *
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	private void callCreateUserAPI(Request request, Response response) throws IOException {

		LOGGER.info("UserApiWebservice: start callCreateUserAPI");
		String login = request.mandatoryParam("login");
		String name = request.mandatoryParam("name");
		String email = request.mandatoryParam("email");
		String provider = request.param("provider");
		LOGGER.info("callCreateUserAPI : login={}, provider={}, name={}, email={}", login, provider, name, email);

		try {
			SonarDbService.getInstance().createUser(login, name, email, login, provider);
		} catch (Exception e) {
			LOGGER.error("Error while creating user " + login, e);
			response.stream().setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}

	}

	/**
	 * Activates a user
	 *
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	private void callActivateUserAPI(Request request, Response response) throws IOException {

		LOGGER.info("UserApiWebservice: start callActivateUserAPI");
		changeUserStatus(request, response, true);
	}

	/**
	 * Deactivates a user
	 *
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	private void callDeactivateUserAPI(Request request, Response response) throws IOException {

		LOGGER.info("UserApiWebservice: start callDeactivateUserAPI");
		changeUserStatus(request, response, false);

	}

	/**
	 *
	 * Change the user status in database for activation/deactivation
	 */
	private void changeUserStatus(Request request, Response response, boolean isActive) {
		String login = request.mandatoryParam("login");
		String provider = request.param("provider");

		Map<String, String> output = new HashMap<String, String>();
		try {
			SonarDbService.getInstance().changeActiveStatus(login, provider, isActive);

			output.put("status", "ok");
			sendAsJson(response, output, HttpServletResponse.SC_OK);

		} catch (Exception e) {
			LOGGER.error("Error while updating user " + login, e);
			response.stream().setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}

	/**
	 * Serialize an object into JSON and set it as request response.
	 *
	 * @param response
	 * @param obj
	 * @param status
	 * @throws IOException
	 */
	private void sendAsJson(
			Response response,
			Object obj, int status) throws IOException {

		LOGGER.info("UserApiWebservice:sendAsJson");

		response.stream().setMediaType("application/json").setStatus(status);

		OutputStream out = response.stream().output();

		String res = gson.toJson(obj);
		System.out.println("UserApiWebservice: send JSON : " + res);
		out.write(res.getBytes());
		out.flush();
		out.close();
	}

}
