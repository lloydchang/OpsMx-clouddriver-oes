/*
 * Copyright 2021 Salesforce.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.security;

import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration;
import com.netflix.spinnaker.kork.configserver.CloudConfigResourceService;
import com.netflix.spinnaker.kork.secrets.SecretManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty({"aws.enabled", "aws.custom-property-binding-enabled"})
public class AmazonCustomBinderConfiguration {
  @Bean
  CustomAccountsConfigurationProvider customAccountsConfigurationProvider(
      ConfigurableApplicationContext context,
      CloudConfigResourceService configResourceService,
      SecretManager secretManager) {
    return new CustomAccountsConfigurationProvider(context, configResourceService, secretManager);
  }

  @Bean
  AccountsConfiguration accountsConfiguration(
      CustomAccountsConfigurationProvider bootstrapCredentialsConfigurationProvider) {
    return bootstrapCredentialsConfigurationProvider.getConfigurationProperties();
  }
}
