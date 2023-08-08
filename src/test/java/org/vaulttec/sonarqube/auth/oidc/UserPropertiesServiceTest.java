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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonar.api.CoreProperties;
import org.sonar.api.config.Configuration;
import org.sonar.api.web.ServletFilter;

import static org.assertj.core.api.Assertions.assertThat;

public class UserPropertiesServiceTest extends AbstractOidcTest {

  private static final String SONAR_URL = "http://acme.com/sonar";

	private StringWriter writer;



	@Before
    public void setup() {

		writer = new StringWriter();
	}
  @Test
  public void testUserGet() throws Exception {
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getContextPath()).thenReturn("/sonar");

    Configuration configurationMock = mock(Configuration.class);
    when(configurationMock.getBoolean("sonar.auth." + OidcIdentityProvider.KEY + ".enabled"))
        .thenReturn(Optional.of(true));
    when(configurationMock.get(CoreProperties.SERVER_BASE_URL)).thenReturn(Optional.of(SONAR_URL));

    UserPropertiesService service = new UserPropertiesService(new OidcConfiguration(configurationMock));

    HttpServletRequest request = mock(HttpServletRequest.class, RETURNS_DEEP_STUBS);
    when(request.getRequestURL()).thenReturn(new StringBuffer(SONAR_URL + "/userapi/"));
    when(request.getServerName()).thenReturn("acme.com");
	when(request.getParameter("uid")).thenReturn("USER1");



    HttpServletResponse response = mock(HttpServletResponse.class);
	when(response.getWriter()).thenReturn(new PrintWriter(writer));

	service.doGet(request, response);

	verify(response).setContentType("application/json");
	verify(response).setStatus(HttpServletResponse.SC_OK);
	verify(response).setStatus(HttpServletResponse.SC_OK);
	assertThat(writer.toString()).isEqualTo("{\"uid\":\"USER1\"}");

  }

  	@Test
	public void testUserPutNotAllowed() throws Exception {
		System.out.println("testUserPutNotAllowed");
		ServletContext servletContext = mock(ServletContext.class);
		when(servletContext.getContextPath()).thenReturn("/sonar");

		Configuration configurationMock = mock(Configuration.class);
		when(configurationMock.getBoolean("sonar.auth." + OidcIdentityProvider.KEY + ".enabled"))
				.thenReturn(Optional.of(true));
		when(configurationMock.get(CoreProperties.SERVER_BASE_URL)).thenReturn(Optional.of(SONAR_URL));

		UserPropertiesService service = new UserPropertiesService(new OidcConfiguration(configurationMock));

		HttpServletRequest request = mock(HttpServletRequest.class, RETURNS_DEEP_STUBS);
		when(request.getRequestURL()).thenReturn(new StringBuffer(SONAR_URL + "/userapi/"));
		when(request.getServerName()).thenReturn("acme.com");
		when(request.getParameter("uid")).thenReturn("USER1");
		when(request.getParameter("name")).thenReturn("John Doe");
		when(request.getParameter("email")).thenReturn("john.doe@example.com");

		HttpServletResponse response = mock(HttpServletResponse.class);
		when(response.getWriter()).thenReturn(new PrintWriter(writer));

		service.doPut(request, response);

		verify(response).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);

	}

	@Test
	public void testUserPut() throws Exception {
		System.out.println("testUserPut");
		ServletContext servletContext = mock(ServletContext.class);
		when(servletContext.getContextPath()).thenReturn("/sonar");

		Configuration configurationMock = mock(Configuration.class);
		when(configurationMock.getBoolean( OidcConfiguration.ENABLED))
				.thenReturn(Optional.of(true));
		when(configurationMock.getBoolean(OidcConfiguration.UPDATE_OID_PROPS_STRATEGY))
				.thenReturn(Optional.of(false));

		when(configurationMock.get(OidcConfiguration.ISSUER_URI))
				.thenReturn(Optional.of("http://idp.com"));
		when(configurationMock.get(OidcConfiguration.CLIENT_ID))
				.thenReturn(Optional.of("id"));

		when(configurationMock.get(CoreProperties.SERVER_BASE_URL)).thenReturn(Optional.of(SONAR_URL));

		UserPropertiesService service = new UserPropertiesService(new OidcConfiguration(configurationMock));

		HttpServletRequest request = mock(HttpServletRequest.class, RETURNS_DEEP_STUBS);
		when(request.getRequestURL()).thenReturn(new StringBuffer(SONAR_URL + "/userapi/"));
		when(request.getServerName()).thenReturn("acme.com");
		when(request.getParameter("uid")).thenReturn("USER1");
		when(request.getParameter("name")).thenReturn("John Doe");
		when(request.getParameter("email")).thenReturn("john.doe@example.com");

		HttpServletResponse response = mock(HttpServletResponse.class);
		when(response.getWriter()).thenReturn(new PrintWriter(writer));

		service.doPut(request, response);

		verify(response).setStatus(HttpServletResponse.SC_NO_CONTENT);

	}
}
