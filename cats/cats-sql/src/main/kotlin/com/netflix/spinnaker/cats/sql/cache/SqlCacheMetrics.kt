/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.cats.sql.cache

interface SqlCacheMetrics {
  fun merge(
    prefix: String,
    type: String,
    itemCount: Int,
    itemsStored: Int,
    relationshipCount: Int,
    relationshipsStored: Int,
    selectOperations: Int,
    writeOperations: Int,
    deleteOperations: Int,
    duplicates: Int
  ) {}

  fun evict(
    prefix: String,
    type: String,
    itemCount: Int,
    itemsDeleted: Int,
    deleteOperations: Int
  ) {}

  fun get(
    prefix: String,
    type: String,
    itemCount: Int,
    requestedSize: Int,
    relationshipsRequested: Int,
    selectOperations: Int,
    async: Boolean = false
  ) {}
}

class NoopCacheMetrics : SqlCacheMetrics
