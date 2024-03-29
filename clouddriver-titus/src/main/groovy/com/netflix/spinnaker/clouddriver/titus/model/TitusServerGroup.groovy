/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.titus.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.client.model.DisruptionBudget
import com.netflix.spinnaker.clouddriver.titus.client.model.Efs
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.MigrationPolicy
import com.netflix.spinnaker.clouddriver.titus.client.model.ServiceJobProcesses
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest

/**
 * Equivalent of a Titus {@link com.netflix.spinnaker.clouddriver.titus.client.model.Job}
 *
 */
class TitusServerGroup implements ServerGroup, Serializable {

  String id
  String name
  final String type = TitusCloudProvider.ID
  final String cloudProvider = TitusCloudProvider.ID
  String entryPoint
  String cmd
  String awsAccount
  String accountId
  String iamProfile
  List<String> securityGroups
  List<String> hardConstraints
  List<String> softConstraints
  List<String> targetGroups = []
  Map env
  Long submittedAt
  String application
  Map<String, Object> image = [:]
  List<Map> scalingPolicies = []
  Map labels
  Map containerAttributes
  Set<Instance> instances = [] as Set
  ServerGroup.Capacity capacity
  DisruptionBudget disruptionBudget
  TitusServerGroupResources resources = new TitusServerGroupResources()
  TitusServerGroupPlacement placement = new TitusServerGroupPlacement()
  Boolean disabled
  Efs efs
  String capacityGroup
  int retries
  int runtimeLimitSecs
  Map buildInfo
  MigrationPolicy migrationPolicy
  ServiceJobProcesses serviceJobProcesses
  SubmitJobRequest.Constraints constraints

  @JsonIgnore
  private Map<String, Object> extraAttributes = new LinkedHashMap<String, Object>()

  TitusServerGroup() {}

  TitusServerGroup(Job job, String account, String region) {
    id = job.id
    name = job.name
    disruptionBudget = job.disruptionBudget
    image << [dockerImageName: job.applicationName]
    image << [dockerImageVersion: job.version]
    image << [dockerImageDigest: job.digest]
    entryPoint = job.entryPoint
    cmd = job.cmd
    iamProfile = job.iamProfile
    resources.cpu = job.cpu
    resources.memory = job.memory
    resources.disk = job.disk
    resources.gpu = job.gpu
    resources.networkMbps = job.networkMbps
    resources.ports = job.ports ? job.ports.toList() : []
    resources.allocateIpAddress = job.allocateIpAddress
    capacityGroup = job.capacityGroup
    env = job.environment
    labels = job.labels
    containerAttributes = job.containerAttributes
    submittedAt = job.submittedAt ? job.submittedAt.time : null
    application = Names.parseName(job.name).app
    placement.account = account
    placement.region = region
    instances = job.tasks.findAll { it != null }.collect { new TitusInstance(job, it) } as Set
    capacity = new ServerGroup.Capacity(min: job.instancesMin, max: job.instancesMax, desired: job.instancesDesired)
    disabled = !job.inService
    securityGroups = job.securityGroups
    hardConstraints = job.hardConstraints
    softConstraints = job.softConstraints
    retries = job.retries
    runtimeLimitSecs = job.runtimeLimitSecs
    efs = job.efs
    migrationPolicy = job.migrationPolicy
    buildInfo = [
      images: ["${image.dockerImageName}:${image.dockerImageVersion ?: image.dockerImageDigest}".toString()],
      docker: [
        "image" : "${image.dockerImageName}".toString(),
        "tag"   : "${image.dockerImageVersion}".toString(),
        "digest": "${image.dockerImageDigest}".toString()
      ]
    ]
    serviceJobProcesses = job.serviceJobProcesses
    constraints = job.constraints
  }

  @JsonAnyGetter
  @Override
  Map<String, Object> getExtraAttributes() {
    return extraAttributes
  }

  /**
   * Setter for non explicitly defined values.
   *
   * Used for both Jackson mapping {@code @JsonAnySetter} as well
   * as Groovy's implicit Map constructor (this is the reason the
   * method is named {@code set(String name, Object value)}
   * @param name The property name
   * @param value The property value
   */
  @JsonAnySetter
  void set(String name, Object value) {
    extraAttributes.put(name, value)
  }

  @Override
  String getRegion() {
    placement.region
  }

  @Override
  Boolean isDisabled() {
    disabled
  }

  @Override
  Long getCreatedTime() {
    submittedAt
  }

  @Override
  Set<String> getLoadBalancers() {
    [] as Set
  }

  @Override
  Set<Map> getSecurityGroups() {
    securityGroups as Set
  }

  @Override
  Set<String> getZones() {
    placement.zones as Set
  }

  @Override
  Map<String, Object> getLaunchConfig() {
    [:]
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    Set<Instance> instances = getInstances()
    new ServerGroup.InstanceCounts(
      total: instances.size(),
      up: filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0,
      down: filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0,
      unknown: filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0,
      starting: filterInstancesByHealthState(instances, HealthState.Starting)?.size() ?: 0,
      outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0)
  }

  @Override
  ServerGroup.ImagesSummary getImagesSummary() {
    def i = image
    String imageDetails = "${i.dockerImageName}:${i.dockerImageVersion?: i.dockerImageDigest}"
    return new ServerGroup.ImagesSummary() {
      @Override
      List<ServerGroup.ImageSummary> getSummaries() {
        return [new ServerGroup.ImageSummary() {
          String serverGroupName = name
          String imageName = imageDetails
          String imageId = imageDetails

          @Override
          Map<String, Object> getBuildInfo() {
            return null
          }

          @Override
          Map<String, Object> getImage() {
            return i
          }
        }]
      }
    }
  }

  @Override
  Map<String, Object> getTags() {
    labels
  }

  @Override
  ServerGroup.ImageSummary getImageSummary() {
    imagesSummary?.summaries?.get(0)
  }

  static Set filterInstancesByHealthState(Set instances, HealthState healthState) {
    instances.findAll { Instance it -> it.getHealthState() == healthState }
  }

}
