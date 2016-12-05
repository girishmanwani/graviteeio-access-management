/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.handler.oauth2;

import io.gravitee.am.definition.Domain;
import io.gravitee.am.definition.Identity;
import io.gravitee.am.gateway.handler.oauth2.authentication.OAuth2LoginUrlAuthenticationEntryPoint;
import io.gravitee.am.gateway.handler.oauth2.provider.code.RepositoryAuthorizationCodeServices;
import io.gravitee.am.gateway.handler.oauth2.provider.token.RepositoryTokenStore;
import io.gravitee.am.gateway.idp.core.IdentityProviderManager;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.SecurityConfigurer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.approval.ApprovalStore;
import org.springframework.security.oauth2.provider.approval.TokenApprovalStore;
import org.springframework.security.oauth2.provider.approval.TokenStoreUserApprovalHandler;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeServices;
import org.springframework.security.oauth2.provider.error.OAuth2AccessDeniedHandler;
import org.springframework.security.oauth2.provider.error.OAuth2AuthenticationEntryPoint;
import org.springframework.security.oauth2.provider.request.DefaultOAuth2RequestFactory;
import org.springframework.security.oauth2.provider.token.TokenStore;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@EnableWebSecurity
public class OAuth2SecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final Logger logger = LoggerFactory.getLogger(OAuth2SecurityConfiguration.class);

    @Autowired
    private Domain domain;

    @Autowired
    private ClientDetailsService clientDetailsService;

    @Autowired
    private OAuth2AccessDeniedHandler oAuth2AccessDeniedHandler;

    @Autowired
    private OAuth2AuthenticationEntryPoint oAuth2AuthenticationEntryPoint;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    public void globalUserDetails(AuthenticationManagerBuilder auth) throws Exception {
        logger.info("Loading identity providers for authentication");

        for (Identity idp : domain.getIdentities()) {
            logger.info("Loading identity provider: {}", idp.getType());

            AuthenticationProvider authenticationProvider = identityProviderManager.create(idp.getType(), idp.getConfiguration());

            if (authenticationProvider != null) {
                Object authProviderInstance = authenticationProvider.configure();

                if (authProviderInstance instanceof org.springframework.security.authentication.AuthenticationProvider) {
                    auth.authenticationProvider((org.springframework.security.authentication.AuthenticationProvider) authProviderInstance);
                }
                else if (authProviderInstance instanceof SecurityConfigurer) {
                    auth.apply((SecurityConfigurer) authProviderInstance);
                }
            } else {
                logger.error("Identity provider can not be found for type {}", idp.getType());
            }
        }
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .anyRequest().authenticated()
                .and()
            .formLogin()
                .permitAll()
                .and()
            .exceptionHandling()
                .authenticationEntryPoint(new OAuth2LoginUrlAuthenticationEntryPoint("/login"));
    }

    @Override
    @Bean
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Bean
    public TokenStore tokenStore() {
        return new RepositoryTokenStore();
    }

    @Bean
    public AuthorizationCodeServices authorizationCodeServices() {
        return new RepositoryAuthorizationCodeServices();
    }

    @Bean
    @Autowired
    public TokenStoreUserApprovalHandler userApprovalHandler(TokenStore tokenStore){
        TokenStoreUserApprovalHandler handler = new TokenStoreUserApprovalHandler();
        handler.setTokenStore(tokenStore);
        handler.setRequestFactory(new DefaultOAuth2RequestFactory(clientDetailsService));
        handler.setClientDetailsService(clientDetailsService);
        return handler;
    }

    @Bean
    @Autowired
    public ApprovalStore approvalStore(TokenStore tokenStore) throws Exception {
        TokenApprovalStore store = new TokenApprovalStore();
        store.setTokenStore(tokenStore);
        return store;
    }
}
