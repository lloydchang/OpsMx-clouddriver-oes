/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.ec2.model.KeyPairInfo
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent

import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.KEY_PAIRS

import groovy.util.logging.Slf4j

@Slf4j
class AmazonKeyPairCachingAgent implements CachingAgent, AccountAware, CustomScheduledAgent {
  private static final long DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(5)
  private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5)

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(KEY_PAIRS.ns)
  ] as Set)

  AmazonKeyPairCachingAgent(AmazonClientProvider amazonClientProvider,
                            NetflixAmazonCredentials account,
                            String region) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
  }

  @Override
  String getProviderName() {
    AwsInfrastructureProvider.name
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${AmazonKeyPairCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    account.name
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")
    def ec2 = amazonClientProvider.getAmazonEC2(account, region)
    def keyPairs = ec2.describeKeyPairs().keyPairs

    List<CacheData> data = keyPairs.collect { KeyPairInfo keyPair ->
      new DefaultCacheData(Keys.getKeyPairKey(keyPair.keyName, region, account.name), [
        keyName       : keyPair.keyName,
        keyFingerprint: keyPair.keyFingerprint
      ], [:])
    }
    log.info("Caching ${data.size()} items in ${agentType}")
    new DefaultCacheResult([(KEY_PAIRS.ns): data])
  }

  @Override
  long getPollIntervalMillis() {
    return DEFAULT_POLL_INTERVAL_MILLIS
  }

  @Override
  long getTimeoutMillis() {
    return DEFAULT_TIMEOUT_MILLIS
  }
}
