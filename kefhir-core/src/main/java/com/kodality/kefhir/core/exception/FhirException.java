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
 package com.kodality.kefhir.core.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StringType;

import static java.util.stream.Collectors.joining;

public class FhirException extends RuntimeException {
  private final int httpCode;

  private final List<OperationOutcomeIssueComponent> issues;
  private List<Extension> extensions;
  private List<Resource> contained;

  public FhirException(int httpCode, IssueType type, String description) {
    this(httpCode, composeIssue(IssueSeverity.ERROR, type, description));
  }

  public FhirException(int statusCode, OperationOutcomeIssueComponent issue) {
    this(statusCode, Collections.singletonList(issue));
  }

  public FhirException(int httpCode, List<OperationOutcomeIssueComponent> issues) {
    this.httpCode = httpCode;
    this.issues = issues;
  }

  public int getStatusCode() {
    return httpCode;
  }

  public List<OperationOutcomeIssueComponent> getIssues() {
    return issues;
  }

  public List<Extension> getExtensions() {
    return extensions;
  }

  public void addExtension(String url, String value) {
    Extension ext = new Extension();
    ext.setUrl("fullUrl");
    ext.setValue(new StringType(value));
    addExtension(ext);
  }

  public void addExtension(Extension extension) {
    if (extensions == null) {
      extensions = new ArrayList<>();
    }
    extensions.add(extension);
  }

  public void addContained(Resource resource) {
    if (contained == null) {
      contained = new ArrayList<>();
    }
    contained.add(resource);
  }

  public List<Resource> getContained() {
    return contained;
  }

  @Override
  public String getMessage() {
    return issues.stream().map(i -> i.getDetails().getText()).collect(joining(", "));
  }

  protected static OperationOutcomeIssueComponent composeIssue(IssueSeverity severity,
                                                               IssueType type,
                                                               String detailsText) {
    OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
    issue.setCode(type);
    issue.setSeverity(severity);

    CodeableConcept details = new CodeableConcept();
    details.setText(detailsText);

    issue.setDetails(details);
    return issue;
  }

}
