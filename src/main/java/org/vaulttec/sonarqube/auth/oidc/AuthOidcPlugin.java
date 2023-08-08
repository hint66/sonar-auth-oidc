/*
 * OpenID Connect Authentication for SonarQube
 * Copyright (c) 2017 Torsten Juergeleit
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

import org.sonar.api.Plugin;
import org.sonar.api.SonarQubeSide;

public class AuthOidcPlugin implements Plugin {

  @Override
  public void define(Context context) {
    if (context.getRuntime().getSonarQubeSide() == SonarQubeSide.SERVER) {
      context.addExtensions(OidcConfiguration.class, OidcClient.class, OidcIdentityProvider.class,
          UserIdentityFactory.class, AutoLoginFilter.class, UserPropertiesService.class);
      context.addExtensions(OidcConfiguration.definitions());
    }
  }

}
