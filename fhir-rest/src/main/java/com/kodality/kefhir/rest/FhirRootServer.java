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
package com.kodality.kefhir.rest;

import com.kodality.kefhir.core.exception.FhirServerException;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.search.HistorySearchCriterion;
import com.kodality.kefhir.core.service.conformance.ConformanceHolder;
import com.kodality.kefhir.core.service.resource.ResourceService;
import com.kodality.kefhir.rest.bundle.BundleSaveHandler;
import com.kodality.kefhir.rest.interaction.FhirInteraction;
import com.kodality.kefhir.rest.model.KefhirRequest;
import com.kodality.kefhir.rest.model.KefhirResponse;
import com.kodality.kefhir.rest.util.BundleUtil;
import com.kodality.kefhir.structure.service.ResourceFormatService;
import jakarta.inject.Provider;
import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleType;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.OperationOutcome;

import static com.kodality.kefhir.core.model.InteractionType.CONFORMANCE;
import static com.kodality.kefhir.core.model.InteractionType.HISTORYSYSTEM;
import static com.kodality.kefhir.core.model.InteractionType.SEARCHSYSTEM;
import static com.kodality.kefhir.core.model.InteractionType.TRANSACTION;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class FhirRootServer {
  private final Provider<KefhirEndpointInitializer> restResourceInitializer;
  private final ResourceFormatService resourceFormatService;
  private final ResourceService resourceService;
  private final Provider<BundleSaveHandler> bundleService;

  @FhirInteraction(interaction = CONFORMANCE, mapping = "GET /metadata")
  public KefhirResponse conformance(KefhirRequest req) {
    String mode = req.getParameter("mode");
    if ("terminology".equals(mode)) {
      return new KefhirResponse(200, ConformanceHolder.getTerminologyCapabilities());
    }
    return new KefhirResponse(200, restResourceInitializer.get().getModifiedCapability());
  }

  @FhirInteraction(interaction = TRANSACTION, mapping = "POST /")
  public KefhirResponse transaction(KefhirRequest req) {
    if (StringUtils.isEmpty(req.getBody())) {
      return new KefhirResponse(204);
    }
    String prefer = req.getHeader("Prefer");
    Bundle responseBundle = bundleService.get().save(resourceFormatService.parse(req.getBody()), prefer);
    return new KefhirResponse(200, responseBundle);
  }

  @FhirInteraction(interaction = HISTORYSYSTEM, mapping = "GET /_history")
  public KefhirResponse history(KefhirRequest req) {
    HistorySearchCriterion criteria = new HistorySearchCriterion();
    criteria.setSince(req.getParameter(HistorySearchCriterion._SINCE));
    criteria.setCount(req.getParameter(HistorySearchCriterion._COUNT));
    List<ResourceVersion> versions = resourceService.loadHistory(criteria);
    return new KefhirResponse(200, BundleUtil.compose(null, versions, BundleType.HISTORY));
  }

  @FhirInteraction(interaction = SEARCHSYSTEM, mapping = "GET /_search")
  public KefhirResponse search(KefhirRequest req) {
    throw new FhirServerException(501, "system search not implemented");
  }

  @FhirInteraction(interaction = "custom", mapping = "GET /")
  public KefhirResponse welcome(KefhirRequest req) {
    OperationOutcome op = new OperationOutcome();
    op.addIssue().setDetails(new CodeableConcept().setText("Welcome to Kefhir"));
    return new KefhirResponse(200, op);
  }

}
