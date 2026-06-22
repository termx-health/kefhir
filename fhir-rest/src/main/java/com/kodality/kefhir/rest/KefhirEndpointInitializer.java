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

import com.kodality.kefhir.core.api.conformance.ConformanceUpdateListener;
import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.OperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.model.InteractionType;
import com.kodality.kefhir.core.service.conformance.ConformanceHolder;

import java.util.*;

import jakarta.inject.Singleton;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceOperationComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.ResourceVersionPolicy;
import org.hl7.fhir.r5.model.CapabilityStatement.RestfulCapabilityMode;
import org.hl7.fhir.r5.model.CapabilityStatement.SystemRestfulInteraction;
import org.hl7.fhir.r5.model.StructureDefinition;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class KefhirEndpointInitializer implements ConformanceUpdateListener {
  private final KefhirEndpointService endpointService;
  private final List<FhirResourceServer> resourceServers;
  private final DefaultFhirResourceServer defaultResourceServer;
  private final FhirRootServer rootServer;

  private final List<TypeOperationDefinition> typeOperations;
  private final List<InstanceOperationDefinition> instanceOperations;
  private final List<com.kodality.kefhir.core.api.resource.BaseOperationDefinition> baseOperations;

  private CapabilityStatement capability;

  @Override
  public void updated() {
    capability = prepareCapability(ConformanceHolder.getCapabilityStatement(), ConformanceHolder.getDefinitions());
    restart();
  }

  public CapabilityStatement getModifiedCapability() {
    return capability;
  }

  private void restart() {
    stop();
    if (capability != null) {
      capability.getRest().stream().filter(r -> r.getMode() == RestfulCapabilityMode.SERVER).forEach(this::start);
    }
  }

  private void stop() {
    endpointService.getEnabledOperations().clear();
  }

  private CapabilityStatement prepareCapability(CapabilityStatement capability, List<StructureDefinition> definitions) {
    if (capability == null || CollectionUtils.isEmpty(definitions)) {
      return null;
    }
    List<String> defined = definitions.stream()
        .map(StructureDefinition::getName)
        .toList();

    // multiple capabilities. how should we handle these?
    CapabilityStatement capabilityStatement = capability.copy();
    capabilityStatement.setText(null);
    capabilityStatement.getRest().forEach(rest -> {
      rest.setResource(rest.getResource().stream().filter(rr -> defined.contains(rr.getType())).collect(toList()));
      rest.getResource().forEach(rr -> {
        rr.setVersioning(ResourceVersionPolicy.VERSIONED);// shouldn't this be in fhirs 'full' conformance?
      });
    });
    capabilityStatement.getRest().forEach(rest -> {
      prepareOperations(rest);
      prepareBaseOperations(rest);
      List<String> interactions = asList("transaction", "batch", SystemRestfulInteraction.HISTORYSYSTEM.toCode());
      rest.setInteraction(rest.getInteraction()
          .stream()
          .filter(i -> interactions.contains(i.getCode().toCode()))
          .toList());
      rest.getResource().forEach(rr -> rr.setReferencePolicy(Collections.emptyList()));
    });

    return capabilityStatement;
  }

  /**
   * remove unimplemented operations
   */
  private void prepareOperations(CapabilityStatementRestComponent rest) {
    rest.getResource().forEach(this::prepareOperationsForResource);
  }

  private void prepareOperationsForResource(CapabilityStatementRestResourceComponent r) {
    Map<String, List<OperationDefinition>> opsByName = Stream.concat(
        instanceOperations.stream().filter(o -> o.getResourceType().equals(r.getType())),
        typeOperations.stream().filter(o -> o.getResourceType().equals(r.getType()))
    ).collect(Collectors.groupingBy(OperationDefinition::getOperationName));

    List<CapabilityStatementRestResourceOperationComponent> operations =
        new ArrayList<>(r.getOperation().stream().filter(operationComponent -> {
          org.hl7.fhir.r5.model.OperationDefinition operationDefinition = ConformanceHolder.getOperationDefinition(operationComponent.getDefinition());
          List<OperationDefinition> implementations = opsByName.getOrDefault(operationComponent.getName(), List.of());
          opsByName.remove(operationComponent.getName());
          return validateOperation(r.getType(), operationComponent, operationDefinition, implementations);
        }).toList());

    // Auto-expose operations that have a server-side implementation but no CapabilityStatement entry / loaded
    // OperationDefinition: synthesize a minimal OperationDefinition, register it on the ConformanceHolder, and add
    // the operation to this resource — so an operation only needs an implementation bean to become invokable.
    opsByName.forEach((name, impls) -> {
      org.hl7.fhir.r5.model.OperationDefinition od = synthesizeOperationDefinition(r.getType(), name, impls);
      ConformanceHolder.registerImplementedOperation(r.getType(), od);
      operations.add(new CapabilityStatementRestResourceOperationComponent().setName(name).setDefinition(od.getUrl()));
      log.info("Auto-exposed implemented operation '${}' for resource {} (no CapabilityStatement entry)", name, r.getType());
    });

    r.setOperation(operations);
  }

  /** A minimal synthetic OperationDefinition for an implemented-but-undeclared operation (type/instance from the impls). */
  private static org.hl7.fhir.r5.model.OperationDefinition synthesizeOperationDefinition(
      String resourceType, String name, List<OperationDefinition> impls) {
    org.hl7.fhir.r5.model.OperationDefinition od = new org.hl7.fhir.r5.model.OperationDefinition();
    od.setUrl("urn:kefhir:operation:" + resourceType + "-" + name);
    od.setName(name);
    od.setCode(name);
    od.setStatus(org.hl7.fhir.r5.model.Enumerations.PublicationStatus.ACTIVE);
    od.setKind(org.hl7.fhir.r5.model.OperationDefinition.OperationKind.OPERATION);
    od.setAffectsState(false);
    od.setSystem(false);
    od.setType(impls.stream().anyMatch(TypeOperationDefinition.class::isInstance));
    od.setInstance(impls.stream().anyMatch(InstanceOperationDefinition.class::isInstance));
    return od;
  }

  /**
   * Auto-exposes server-level (BaseOperationDefinition) operations that have an implementation but no
   * CapabilityStatement entry — the system-level counterpart of {@link #prepareOperationsForResource}. Adds them to
   * the REST-level operation list so {@code POST /$op} resolves.
   */
  private void prepareBaseOperations(CapabilityStatementRestComponent rest) {
    Set<String> existing = rest.getOperation().stream().map(CapabilityStatementRestResourceOperationComponent::getName).collect(java.util.stream.Collectors.toSet());
    Map<String, List<OperationDefinition>> baseOpsByName = baseOperations.stream()
        .collect(Collectors.groupingBy(OperationDefinition::getOperationName));
    List<CapabilityStatementRestResourceOperationComponent> operations = new ArrayList<>(rest.getOperation());
    baseOpsByName.forEach((name, impls) -> {
      if (existing.contains(name)) {
        return;
      }
      org.hl7.fhir.r5.model.OperationDefinition od = synthesizeBaseOperationDefinition(name);
      ConformanceHolder.registerImplementedBaseOperation(od);
      operations.add(new CapabilityStatementRestResourceOperationComponent().setName(name).setDefinition(od.getUrl()));
      log.info("Auto-exposed implemented base operation '${}' (no CapabilityStatement entry)", name);
    });
    rest.setOperation(operations);
  }

  private static org.hl7.fhir.r5.model.OperationDefinition synthesizeBaseOperationDefinition(String name) {
    org.hl7.fhir.r5.model.OperationDefinition od = new org.hl7.fhir.r5.model.OperationDefinition();
    od.setUrl("urn:kefhir:operation:base-" + name);
    od.setName(name);
    od.setCode(name);
    od.setStatus(org.hl7.fhir.r5.model.Enumerations.PublicationStatus.ACTIVE);
    od.setKind(org.hl7.fhir.r5.model.OperationDefinition.OperationKind.OPERATION);
    od.setAffectsState(false);
    od.setSystem(true);
    od.setType(false);
    od.setInstance(false);
    return od;
  }

  private boolean validateOperation(
      String resourceType,
      CapabilityStatementRestResourceOperationComponent operationComponent,
      org.hl7.fhir.r5.model.OperationDefinition operationDefinition,
      List<OperationDefinition> impls
  ) {
    if (operationDefinition == null) {
      log.error("Missing OperationDefinition for referenced in CapabilityStatement operation {} for resource {}",
          operationComponent.getDefinition(), resourceType);
      return false;
    }

    boolean validType = true;
    if (operationDefinition.getType()) {
      List<OperationDefinition> typeImpls = impls.stream()
          .filter(TypeOperationDefinition.class::isInstance)
          .toList();
      validType = validateOperation(typeImpls, operationComponent.getDefinition(), resourceType);
    }

    boolean validInstance = true;
    if (operationDefinition.getInstance()) {
      List<OperationDefinition> instanceImpls = impls.stream()
          .filter(InstanceOperationDefinition.class::isInstance)
          .toList();
      validInstance = validateOperation(instanceImpls, operationComponent.getDefinition(), resourceType);
    }

    return validType && validInstance;
  }

  private boolean validateOperation(List<OperationDefinition> implTypes, String opName, String resourceType) {
    if (implTypes.isEmpty()) {
      log.error("Cannot find implementation for declared in capability statement operation '{}'", opName);
      return false;
    }

    if (implTypes.size() > 1) {
      log.debug("There is {} implementations for operation '{}' for resource '{}'", implTypes, opName, resourceType);
    }
    log.trace("Beans {} are bound to operation '{}' for resource '{}'", implTypes, opName, resourceType);
    return true;
  }

  private void start(CapabilityStatementRestComponent rest) {
    endpointService.startRoot(rootServer);
    rest.getResource().forEach(this::start);
    log.info("Started " + (rest.getResource().size() + 1) + " rest services.");
  }

  private void start(CapabilityStatementRestResourceComponent resourceRest) {
    String type = resourceRest.getType();
    List<String> interactions = resourceRest.getInteraction().stream().filter(i -> i.getCode() != null).map(i -> i.getCode().toCode()).collect(toList());
    if (CollectionUtils.isNotEmpty(resourceRest.getOperation())) {
      interactions.add(InteractionType.OPERATION); // XXX for some reason operation is not in default fhir capability statement. need to add it.
    }
    log.debug("Starting: " + type + ": " + String.join(", ", interactions));
    FhirResourceServer service = resourceServers.stream().filter(s -> s.getTargetType().equals(type)).findFirst().orElse(defaultResourceServer);
    interactions.forEach(i -> endpointService.start(type, i, service));
  }
}
