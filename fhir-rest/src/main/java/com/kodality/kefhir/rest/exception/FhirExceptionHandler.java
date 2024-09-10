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
package com.kodality.kefhir.rest.exception;

import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.kefhir.structure.service.ResourceFormatService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

import static java.util.stream.Collectors.joining;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class FhirExceptionHandler {
  private final ResourceFormatService resourceFormatService;

  public HttpResponse<?> handle(HttpRequest request, Throwable e) {
    Throwable root = ExceptionUtils.getRootCause(e);

    if (e instanceof FhirException) {
      return toResponse(request, (FhirException) e);
    }
    if (root instanceof FhirException) {
      return toResponse(request, (FhirException) root);
    }

    log.error("Fhir error occurred", e);
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue().setCode(IssueType.EXCEPTION).setDetails(new CodeableConcept().setText(e.getMessage())).setSeverity(IssueSeverity.ERROR);
    return toResponse(request, outcome, HttpStatus.INTERNAL_SERVER_ERROR.getCode());
  }


  private HttpResponse toResponse(HttpRequest<?> request, FhirException e) {
    log(e);

    OperationOutcome outcome = new OperationOutcome();
    outcome.setExtension(e.getExtensions());
    outcome.setIssue(e.getIssues());
    outcome.setContained(e.getContained());
    return toResponse(request, outcome, e.getStatusCode());
  }

  protected MutableHttpResponse<?> toResponse(HttpRequest<?> request, OperationOutcome outcome, int statusCode) {
    List<String> ct = request.getHeaders().get("Accept") == null ? List.of() : List.of(request.getHeaders().get("Accept").split(","));
    String accept = resourceFormatService.findSupported(ct).stream().findFirst().orElse("application/json");
    ResourceContent c = resourceFormatService.compose(outcome, accept);

    MutableHttpResponse<?> response = HttpResponse.status(HttpStatus.valueOf(statusCode));
    response.body(c.getValue());
    String contentType = c.getContentType();
    response.contentType(toHttpContentType(contentType));
    return response;
  }

  private static String toHttpContentType(String fhirContentType) {
    return fhirContentType.equals("json") ? "application/json" : fhirContentType;
  }

  private static void log(FhirException e) {
    String issues = e.getIssues().stream().map(i -> i.getDetails().getText()).collect(joining(", "));
    String msg = "Status " + e.getStatusCode() + " = " + issues;
    if (e.getStatusCode() >= 500) {
      log.error(msg);
    } else {
      log.info(msg);
    }
  }

}
