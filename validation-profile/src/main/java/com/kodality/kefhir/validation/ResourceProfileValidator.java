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
package com.kodality.kefhir.validation;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import com.kodality.kefhir.core.api.resource.OperationInterceptor;
import com.kodality.kefhir.core.api.resource.ResourceBeforeSaveInterceptor;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.exception.FhirServerException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.service.conformance.HapiContextHolder;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.kefhir.structure.service.ResourceFormatService;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import java.util.List;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.ResourceType;

import static java.util.stream.Collectors.toList;

@Requires(property = "kefhir.validation-profile.enabled", value = io.micronaut.core.util.StringUtils.TRUE, defaultValue = io.micronaut.core.util.StringUtils.TRUE)
@Singleton
public class ResourceProfileValidator extends ResourceBeforeSaveInterceptor implements OperationInterceptor {
  @Inject
  private ResourceFormatService resourceFormatService;
  @Inject
  private HapiContextHolder hapiContextHolder;

  public ResourceProfileValidator() {
    super(ResourceBeforeSaveInterceptor.INPUT_VALIDATION);
  }

  @Override
  public void handle(String level, String operation, ResourceContent parameters) {
    if (StringUtils.isEmpty(parameters.getValue())) {
      return;
    }

    // some operations ($transform) accept non-default-spec resources, thus parse fails
    // runValidation(ResourceType.Parameters.name(), parameters);
    validateProfile(parameters);
  }

  @Override
  public void handle(ResourceId id, ResourceContent content, String interaction) {
    String resourceType = id.getResourceType();
    runValidation(resourceType, content);
  }

  private void runValidation(String resourceType, ResourceContent content) {
    Resource resource = validateParse(content);
    validateType(resourceType, resource.getResourceType().name());
    validateProfile(content);
  }

  private Resource validateParse(ResourceContent content) {
    try {
      return resourceFormatService.parse(content);
    } catch (Exception e) {
      throw new FhirException(400, IssueType.STRUCTURE, "error during resource parse: " + e.getMessage());
    }
  }

  private void validateType(String expectedType, String resourceType) {
    if (!resourceType.equals(expectedType)) {
      String msg = "was expecting " + expectedType + " but found " + resourceType;
      throw new FhirException(400, IssueType.INVALID, msg);
    }
  }

  private void validateProfile(ResourceContent content) {
    if (hapiContextHolder.getHapiContext() == null) {
      throw new FhirServerException(500, "fhir context initialization error");
    }
    try {
      List<SingleValidationMessage> errors = hapiContextHolder.getValidator().validateWithResult(content.getValue()).getMessages();
      errors = errors.stream().filter(m -> isError(m.getSeverity())).collect(toList());
      if (!errors.isEmpty()) {
        throw new FhirException(400, errors.stream().map(msg -> {
          OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
          issue.setCode(IssueType.INVALID);
          issue.setSeverity(IssueSeverity.fromCode(msg.getSeverity().getCode()));
          issue.setDetails(new CodeableConcept().setText(msg.getMessage()));
          issue.addLocation(msg.getLocationString());
          return issue;
        }).collect(toList()));
      }
    } catch (Exception e) {
      if (e instanceof FHIRException) {
        throw new FhirException(500, IssueType.INVALID, e.getMessage());
      }
      throw new RuntimeException("exception during profile validation: " + e.getMessage(), e);
    }
  }

  private boolean isError(ResultSeverityEnum level) {
    return level == ResultSeverityEnum.ERROR || level == ResultSeverityEnum.FATAL;
  }

}
