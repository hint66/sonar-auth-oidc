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
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.vaulttec.sonarqube.auth.oidc.OidcConfiguration;
import org.vaulttec.sonarqube.db.SonarDbService;

import com.google.gson.Gson;
/**
 * Overrides update user API because we need to change user name and mail for OpenID users
 */
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
		if (LOGGER.isDebugEnabled()) {
			LOGGER.info("UserApiWebservice: define");
		}
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


		controller.done();

	}


	private void callUpdateUserAPI(Request request, Response response) throws IOException {

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("UserApiWebservice: start callUpdateUserAPI");
		}
		String name = request.mandatoryParam("name");
		String email = request.mandatoryParam("email");
		String login = request.mandatoryParam("login");
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("callUpdateUserAPI for user : name={}, email={}, login={}", name, email, login);
		}

		if (!config.isEnabled() || config.updateOidPropsName()) {

			// If the property updateOidProps is enabled (auto update from OIDc), return NOT ALLOWED
			LOGGER.info("Update user not allowed (update from OIDC is enabled)");
			response.stream().setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
			return;

		}
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("callUpdateUserAPI: do Update user (update from OIDC is disabled)");
		}

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

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("UserApiWebservice:sendAsJson");
		}

		response.stream().setMediaType("application/json").setStatus(status);

		OutputStream out = response.stream().output();

		String res = gson.toJson(obj);
		out.write(res.getBytes());
		out.flush();
		out.close();
	}

}
