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

import com.kodality.kefhir.core.exception.FhirException;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

public final class SearchUtil {
  /**
   * @return [resourceType, searchParam, (targetResourceType)]
   */
  public static String[] parseInclude(String include) {
    if (include == null) {
      return null;//fdsfds
    }
    String[] tokens = StringUtils.split(include, ":");
    if (tokens.length < 2 || tokens.length > 3) {
      String details = "_include parameter invalid. ResourceType:SearchParameter[:targetResourceType]";
      throw new FhirException(400, IssueType.PROCESSING, details);
    }
    return new String[] { tokens[0], tokens[1], tokens.length > 2 ? tokens[2] : null };
  }

}
