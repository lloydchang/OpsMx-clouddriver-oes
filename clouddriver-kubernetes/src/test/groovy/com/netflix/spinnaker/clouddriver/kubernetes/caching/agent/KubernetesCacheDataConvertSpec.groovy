/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiGroup
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesDeploymentHandler
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesReplicaSetHandler
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesServiceHandler
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.moniker.Moniker
import org.apache.commons.lang3.tuple.Pair
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesCacheDataConvertSpec extends Specification {
  def mapper = new ObjectMapper()
  def yaml = new Yaml(new SafeConstructor(new LoaderOptions()))

  KubernetesManifest stringToManifest(String input) {
    return mapper.convertValue(yaml.load(input), KubernetesManifest.class)
  }

  @Unroll
  def "given a correctly annotated manifest, build attributes & infer relationships"() {
    setup:
    def rawManifest = """
apiVersion: $apiVersion
kind: $kind
metadata:
  name: $name
  namespace: $namespace
"""
    def moniker = Moniker.builder()
        .app(application)
        .cluster(cluster)
        .build()

    if (account != null) {
      NamerRegistry.lookup()
        .withProvider(KubernetesCloudProvider.ID)
        .withAccount(account)
        .setNamer(KubernetesManifest, new KubernetesManifestNamer())
    }

    def manifest = stringToManifest(rawManifest)
    KubernetesManifestAnnotater.annotateManifest(manifest, moniker)

    when:
    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData()
    KubernetesCacheDataConverter.convertAsResource(
      kubernetesCacheData,
      account,
      new KubernetesSpinnakerKindMap(ImmutableList.of(new KubernetesDeploymentHandler(), new KubernetesReplicaSetHandler(), new KubernetesServiceHandler())),
      new KubernetesManifestNamer(),
      manifest,
      [],
      false)
    def optional = kubernetesCacheData.toCacheData().stream().filter({
      cd -> cd.id == Keys.InfrastructureCacheKey.createKey(kind, account, namespace, name)
    }).findFirst()

    then:
    if (application == null) {
      true
    } else {
      optional.isPresent()
      def cacheData = optional.get()
      cacheData.relationships.get(Keys.LogicalKind.APPLICATIONS.toString()) == [Keys.ApplicationCacheKey.createKey(application)]
      if (cluster) {
        cacheData.relationships.get(Keys.LogicalKind.CLUSTERS.toString()) == [Keys.ClusterCacheKey.createKey(account, application, cluster)]
      } else {
        cacheData.relationships.get(Keys.LogicalKind.CLUSTERS.toString()) == null
      }
      cacheData.attributes.get("name") == name
      cacheData.attributes.get("namespace") == namespace
      cacheData.attributes.get("kind") == kind
      cacheData.id == Keys.InfrastructureCacheKey.createKey(kind, account, namespace, name)
    }

    where:
    kind                       | apiVersion                              | account           | application | cluster       | namespace        | name
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "one-app"   | "the-cluster" | "some-namespace" | "a-name-v000"
    KubernetesKind.DEPLOYMENT  | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "one-app"   | "the-cluster" | "some-namespace" | "a-name"
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1                 | "another-account" | "your-app"  | null          | "some-namespace" | "what-name"
  }

  @Unroll
  void  "given an unclassified resource, application relationships are only cached if `cacheAllRelationships` is set: #cacheAllRelationships"() {
    setup:
    def apiGroup = "any.resource.com"
    def kind = "MyCRD"
    def qualifiedKind = KubernetesKind.from("MyCRD", KubernetesApiGroup.fromString(apiGroup))
    def name = "my-crd"
    def namespace = "my-namespace"
    def application = "one-app"
    def cluster = "the-cluster"
    def account = "my-account"
    def rawManifest = """
apiVersion: ${apiGroup}/v1
kind: $kind
metadata:
  name: $name
  namespace: $namespace
"""

    def moniker = Moniker.builder()
      .app(application)
      .cluster(cluster)
      .build()

    NamerRegistry.lookup()
      .withProvider(KubernetesCloudProvider.ID)
      .withAccount(account)
      .setNamer(KubernetesManifest, new KubernetesManifestNamer())

    def manifest = stringToManifest(rawManifest)
    KubernetesManifestAnnotater.annotateManifest(manifest, moniker)

    when:
    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData()
    KubernetesCacheDataConverter.convertAsResource(
      kubernetesCacheData,
      account,
      new KubernetesSpinnakerKindMap(ImmutableList.of(new KubernetesDeploymentHandler(), new KubernetesReplicaSetHandler(), new KubernetesServiceHandler())),
      new KubernetesManifestNamer(),
      manifest,
      [],
      cacheAllRelationships
    )
    def optional = kubernetesCacheData.toCacheData().stream().filter({
      cd -> cd.id == Keys.InfrastructureCacheKey.createKey(qualifiedKind, account, namespace, name)
    }).findFirst()

    then:
    optional.isPresent()
    def relationships =  optional.get().relationships
    def applicationRelationships = relationships.get(Keys.LogicalKind.APPLICATIONS.toString())
    applicationRelationships.equals(cacheAllRelationships ? [Keys.ApplicationCacheKey.createKey(application)].toSet() : null);

    where:
    cacheAllRelationships << [false, true]
  }

  @Unroll
  def "given a single owner reference, correctly build relationships"() {
    setup:
    def ownerRefs = [new KubernetesManifest.OwnerReference(kind: kind, apiVersion: apiVersion, name: name)]

    when:
    def result = KubernetesCacheDataConverter.ownerReferenceRelationships(account, namespace, ownerRefs)

    then:
    result.contains(new Keys.InfrastructureCacheKey(kind, account, namespace, name))

    where:
    kind                       | apiVersion                              | account           | cluster       | namespace        | name
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "another-clu" | "some-namespace" | "a-name-v000"
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "the-cluster" | "some-namespace" | "a-name-v000"
    KubernetesKind.DEPLOYMENT  | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "the-cluster" | "some-namespace" | "a-name"
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1                 | "another-account" | "cluster"     | "some-namespace" | "what-name"
  }

  def containerMetric(String containerName) {
    return new KubernetesPodMetric.ContainerMetric(containerName, [
        "CPU(cores)": "10m",
        "MEMORY(bytes)": "2Mi"
    ])
  }

  def filterRelationships(Collection<String> keys, List<Pair<KubernetesKind, String>> existingResources) {
    return keys.findAll { sk ->
      def key = (Keys.InfrastructureCacheKey) Keys.parseKey(sk).get()
      return existingResources.find { Pair<KubernetesKind, String> lb ->
        return lb.getLeft() == key.getKubernetesKind() && lb.getRight() == key.getName()
      } != null
    }
  }
}
