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

import com.kodality.kefhir.core.api.resource.ResourceAfterDeleteInterceptor;
import com.kodality.kefhir.core.api.resource.ResourceAfterSaveInterceptor;
import com.kodality.kefhir.core.api.resource.ResourceBeforeSaveInterceptor;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.HistorySearchCriterion;
import com.kodality.kefhir.core.util.ResourceUtil;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.kefhir.tx.TransactionService;
import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

import static com.kodality.kefhir.core.api.resource.ResourceAfterSaveInterceptor.FINALIZATION;
import static com.kodality.kefhir.core.api.resource.ResourceAfterSaveInterceptor.TRANSACTION;
import static com.kodality.kefhir.core.api.resource.ResourceBeforeSaveInterceptor.BUSINESS_VALIDATION;
import static com.kodality.kefhir.core.api.resource.ResourceBeforeSaveInterceptor.INPUT_VALIDATION;
import static com.kodality.kefhir.core.api.resource.ResourceBeforeSaveInterceptor.NORMALIZATION;

@Singleton
@RequiredArgsConstructor
public class ResourceService {
  private final ResourceStorageService storageService;
  private final List<ResourceBeforeSaveInterceptor> beforeSaveInterceptors;
  private final List<ResourceAfterSaveInterceptor> afterSaveInterceptors;
  private final List<ResourceAfterDeleteInterceptor> afterDeleteInterceptor;
  private final TransactionService tx;

  public ResourceVersion save(ResourceId id, ResourceContent content, String interaction) {
    interceptBeforeSave(INPUT_VALIDATION, id, content, interaction);
    interceptBeforeSave(NORMALIZATION, id, content, interaction);
    interceptBeforeSave(BUSINESS_VALIDATION, id, content, interaction);

    id.setResourceId(id.getResourceId() == null ? generateNewId(id.getResourceType()) : id.getResourceId());
    ResourceVersion version = tx.transaction(() -> {
      interceptBeforeSave(ResourceBeforeSaveInterceptor.TRANSACTION, id, content, interaction);
      ResourceVersion ver = storageService.store(id, content);
      interceptAfterSave(TRANSACTION, ver);
      return ver;
    });
    interceptAfterSave(FINALIZATION, version);
    return version;
  }

  /**
   * @param reference ResourceType/id
   */
  public ResourceVersion load(String reference) {
    return load(ResourceUtil.parseReference(reference));
  }

  public ResourceVersion load(ResourceId id) {
    return load(new VersionId(id));
  }

  public ResourceVersion load(VersionId id) {
    return storageService.load(id);
  }

  public List<ResourceVersion> load(List<VersionId> ids) {
    return storageService.load(ids);
  }

  public List<ResourceVersion> loadHistory(HistorySearchCriterion criteria) {
    return storageService.loadHistory(criteria);
  }

  public void delete(ResourceId id) {
    storageService.delete(id);
    afterDeleteInterceptor.forEach(i -> i.delete(id));
  }

  public String generateNewId(String resourceType) {
    return storageService.generateNewId(resourceType);
  }

  private void interceptBeforeSave(String phase, ResourceId id, ResourceContent content, String interaction) {
    beforeSaveInterceptors.stream().filter(i -> i.getPhase().equals(phase)).forEach(i -> i.handle(id, content, interaction));
  }

  private void interceptAfterSave(String phase, ResourceVersion version) {
    afterSaveInterceptors.stream().filter(i -> i.getPhase().equals(phase)).forEach(i -> i.handle(version));
  }

}
