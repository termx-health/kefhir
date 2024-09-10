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
package com.kodality.kefhir.rest.feature;

import com.kodality.kefhir.core.api.resource.OperationInterceptor;
import com.kodality.kefhir.core.api.resource.ResourceBeforeSaveInterceptor;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchCriterionBuilder;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.core.service.resource.ResourceSearchService;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.kefhir.structure.service.ResourceFormatService;
import com.kodality.kefhir.structure.util.ResourcePropertyUtil;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.model.Resource;

/**
 * https://www.hl7.org/fhir/http.html#transaction
 * Conditional Reference implementation.
 * originally should only apply to transaction, but we thought might be global..
 */
@Singleton
public class ResourceConditionalReferenceFeature extends ResourceBeforeSaveInterceptor implements OperationInterceptor {
  @Inject
  private ResourceFormatService representationService;
  @Inject
  private Provider<ResourceSearchService> resourceSearchService;

  public ResourceConditionalReferenceFeature() {
    super(ResourceBeforeSaveInterceptor.NORMALIZATION);
  }

  @Override
  public void handle(String level, String operation, ResourceContent parameters) {
    replaceRefs(parameters);
  }

  @Override
  public void handle(ResourceId id, ResourceContent content, String interaction) {
    replaceRefs(content);
  }

  private void replaceRefs(ResourceContent content) {
    if (StringUtils.isEmpty(content.getValue())) {
      return;
    }
    Resource resource = representationService.parse(content.getValue());
    ResourcePropertyUtil.findProperties(resource, Reference.class).forEach(reference -> replaceReference(reference));
    content.setValue(representationService.compose(resource, content.getContentType()).getValue());
  }

  private void replaceReference(Reference reference) {
    String uri = reference.getReference();
    if (uri == null || !uri.contains("?")) {
      return;
    }
    String resourceType = StringUtils.substringBefore(uri, "?");
    String query = StringUtils.substringAfter(uri, "?");

    SearchCriterion criteria = SearchCriterionBuilder.parse(query, resourceType);
    criteria.getResultParams().clear();
    SearchResult result = resourceSearchService.get().search(criteria);
    if (result.getTotal() != 1) {
      throw new FhirException(400, IssueType.PROCESSING, "found " + result.getTotal() + " resources by " + uri);
    }
    reference.setReference(result.getEntries().get(0).getId().getResourceReference());
  }

}
