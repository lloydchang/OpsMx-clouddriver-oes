/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.data.task.InMemoryTaskRepository;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DefaultDeployHandlerRegistry;
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler;
import com.netflix.spinnaker.clouddriver.deploy.DeployHandlerRegistry;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionAuthorizer;
import com.netflix.spinnaker.clouddriver.deploy.NullOpDeployHandler;
import com.netflix.spinnaker.clouddriver.orchestration.AnnotationsBasedAtomicOperationsRegistry;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationDescriptionPreProcessor;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationsRegistry;
import com.netflix.spinnaker.clouddriver.orchestration.DefaultOrchestrationProcessor;
import com.netflix.spinnaker.clouddriver.orchestration.ExceptionClassifier;
import com.netflix.spinnaker.clouddriver.orchestration.OperationsService;
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor;
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEventHandler;
import com.netflix.spinnaker.clouddriver.saga.persistence.SagaRepository;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.AllowedAccountsValidator;
import com.netflix.spinnaker.kork.web.context.RequestContextProvider;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionSummaryService;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.netflix.spinnaker.clouddriver.orchestration.sagas")
class DeployConfiguration {
  @Bean
  @ConditionalOnMissingBean(TaskRepository.class)
  TaskRepository taskRepository() {
    return new InMemoryTaskRepository();
  }

  @Bean
  @ConditionalOnMissingBean(DeployHandlerRegistry.class)
  DeployHandlerRegistry deployHandlerRegistry(List<DeployHandler> deployHandlers) {
    return new DefaultDeployHandlerRegistry(deployHandlers);
  }

  @Bean
  @ConditionalOnMissingBean(OrchestrationProcessor.class)
  OrchestrationProcessor orchestrationProcessor(
      TaskRepository taskRepository,
      ApplicationContext applicationContext,
      Registry registry,
      Optional<Collection<OperationEventHandler>> operationEventHandlers,
      ObjectMapper objectMapper,
      ExceptionClassifier exceptionClassifier,
      RequestContextProvider contextProvider,
      ExceptionSummaryService exceptionSummaryService) {
    return new DefaultOrchestrationProcessor(
        taskRepository,
        applicationContext,
        registry,
        operationEventHandlers,
        objectMapper,
        exceptionClassifier,
        contextProvider,
        exceptionSummaryService);
  }

  @Bean
  @ConditionalOnMissingBean(DeployHandler.class)
  DeployHandler<String> nullOpDeployHandler() {
    return new NullOpDeployHandler();
  }

  @Bean
  AtomicOperationsRegistry atomicOperationsRegistry() {
    return new AnnotationsBasedAtomicOperationsRegistry();
  }

  @Bean
  OperationsService operationsService(
      AtomicOperationsRegistry atomicOperationsRegistry,
      List<DescriptionAuthorizer> descriptionAuthorizers,
      Optional<Collection<AllowedAccountsValidator>> allowedAccountsValidators,
      Optional<List<AtomicOperationDescriptionPreProcessor>>
          atomicOperationDescriptionPreProcessors,
      AccountCredentialsRepository accountCredentialsRepository,
      Optional<SagaRepository> sagaRepository,
      Registry registry,
      ObjectMapper objectMapper,
      ExceptionMessageDecorator exceptionMessageDecorator) {
    return new OperationsService(
        atomicOperationsRegistry,
        descriptionAuthorizers,
        allowedAccountsValidators,
        atomicOperationDescriptionPreProcessors,
        accountCredentialsRepository,
        sagaRepository,
        registry,
        objectMapper,
        exceptionMessageDecorator);
  }
}
