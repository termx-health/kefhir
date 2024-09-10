/*
 * MIT License
 *
 * Copyright (c) 2024 Kodality
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.kodality.kefhir.core.service.resource;

import com.kodality.kefhir.core.api.resource.ResourceStorage;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.HistorySearchCriterion;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.kefhir.tx.TransactionService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Singleton
public class ResourceStorageService {
  private final Map<String, ResourceStorage> storages;
  private final TransactionService tx;

  public ResourceStorageService(List<ResourceStorage> storages, TransactionService tx) {
    this.storages = storages.stream().collect(Collectors.toMap(s -> s.getResourceType(), s -> s));
    this.tx = tx;
  }

  private ResourceStorage getStorage(String resourceType) {
    return storages.getOrDefault(resourceType, storages.get(ResourceStorage.DEFAULT));
  }

  public ResourceVersion load(VersionId id) {
    ResourceVersion version = getStorage(id.getResourceType()).load(id);
    if (version == null) {
      throw new FhirException(404, IssueType.NOTFOUND, id.getReference() + " not found");
    }
    return version;
  }

  public List<ResourceVersion> load(List<VersionId> ids) {
    Map<String, List<VersionId>> typeIds = ids.stream().collect(Collectors.groupingBy(i -> i.getResourceType()));
    return typeIds.keySet().stream().flatMap(type -> getStorage(type).load(typeIds.get(type)).stream()).collect(Collectors.toList());
  }

  public List<ResourceVersion> loadHistory(HistorySearchCriterion criteria) {
    return getStorage(criteria.getResourceType()).loadHistory(criteria);
  }

  public void delete(ResourceId id) {
    getStorage(id.getResourceType()).delete(id);
  }

  public ResourceVersion store(ResourceId id, ResourceContent content) {
    return getStorage(id.getResourceType()).save(id, content);
  }

  /**
   * use with caution. only business logic
   * outside of transaction
   */
  public ResourceVersion storeForce(ResourceId id, ResourceContent content) {
    return tx.newTransaction(() -> getStorage(id.getResourceType()).save(id, content));
  }

  public String generateNewId(String resourceType) {
    return getStorage(resourceType).generateNewId();
  }

}
