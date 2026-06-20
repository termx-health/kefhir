package com.kodality.kefhir.rest.operation;

import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.service.conformance.ConformanceHolder;
import com.kodality.kefhir.rest.model.KefhirRequest;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.kefhir.structure.service.ResourceFormatService;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceOperationComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.RestfulCapabilityMode;
import org.hl7.fhir.r5.model.Enumerations.FHIRTypes;
import org.hl7.fhir.r5.model.Enumerations.OperationParameterUse;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.OperationDefinition.OperationDefinitionParameterComponent;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.ResourceType;

@Singleton
@RequiredArgsConstructor
public class OperationParametersReader {
  private final ResourceFormatService resourceFormatService;

  public ResourceContent readOperationParameters(String operation, KefhirRequest req) {
    OperationDefinition opDef = req.getType() == null ? findBaseOperationDefinition(operation) : findOperationDefinition(operation, req.getType());

    if (req.getMethod().equals("GET")) {
      if (opDef.getAffectsState()) {
        throw new FhirException(400, IssueType.INVALID, "Performing an state affecting operation using GET not allowed");
      }
      Parameters parameters = new Parameters();
      req.getParameters().forEach((k, v) -> parameters.addParameter(k, String.join(",", v)));
      return resourceFormatService.compose(parameters, "json");
    }

    Resource body = req.getBody() == null ? null : resourceFormatService.parse(req.getBody());
    if (body != null && body.getResourceType() == ResourceType.Parameters) {
      return new ResourceContent(req.getBody(), req.getContentTypeName());
    }

    List<OperationDefinitionParameterComponent> resourceParams =
        opDef.getParameter().stream().filter(p -> p.getUse() == OperationParameterUse.IN && p.getType() == FHIRTypes.RESOURCE).toList();
    if (body == null && !resourceParams.isEmpty()) {
      throw new FhirException(400, IssueType.INVALID, "Operation body required");
    }
    if (body != null && resourceParams.size() != 1) {
      throw new FhirException(400, IssueType.INVALID,
          "Operation MAY accept Resource in body only if operation definition has exactly one input parameter whose type is a FHIR Resource");
    }
    String resourceParameterName = resourceParams.get(0).getName();

    Parameters parameters = new Parameters();
    req.getParameters().forEach((k, v) -> parameters.addParameter(k, String.join(",", v)));
    parameters.addParameter().setName(resourceParameterName).setResource(body);
    return resourceFormatService.compose(parameters, req.getContentTypeName());
  }

  private static OperationDefinition findBaseOperationDefinition(String operation) {
    CapabilityStatementRestResourceOperationComponent capabilityOp =
        ConformanceHolder.getCapabilityStatement().getRest().stream().filter(r -> r.getMode() == RestfulCapabilityMode.SERVER)
            .findFirst()
            .orElseThrow()
            .getOperation().stream().filter(op -> ("$" + op.getName()).equals(operation)).findFirst()
            .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "Operation " + operation + " not defined in capability statement"));
    OperationDefinition opDef = ConformanceHolder.getOperationDefinition(capabilityOp.getDefinition());
    if (opDef == null) {
      throw new FhirException(400, IssueType.INVALID, "Operation " + operation + " not defined");
    }
    return opDef;
  }

  private static OperationDefinition findOperationDefinition(String operation, String type) {
    CapabilityStatementRestResourceOperationComponent capabilityOp = ConformanceHolder
        .getCapabilityResource(type).getOperation().stream().filter(op -> ("$" + op.getName()).equals(operation)).findFirst()
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "Operation " + operation + " not defined in capability statement"));
    OperationDefinition opDef = ConformanceHolder.getOperationDefinition(capabilityOp.getDefinition());
    if (opDef == null) {
      throw new FhirException(400, IssueType.INVALID, "Operation " + operation + " not defined");
    }
    return opDef;
  }
}
