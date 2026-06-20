package com.kodality.kefhir.core.api.resource;

import com.kodality.kefhir.structure.api.ResourceContent;

/**
 * Interface should be used to provide actual functionality of a <b>base</b> level operation.
 * @see OperationDefinition
 */
public interface BaseOperationDefinition extends OperationDefinition {
  /**
   * @return Parameters or Resource
   */
  ResourceContent run(ResourceContent parameters);
}
