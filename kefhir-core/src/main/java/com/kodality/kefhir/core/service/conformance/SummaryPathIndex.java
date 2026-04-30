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
package com.kodality.kefhir.core.service.conformance;

import java.util.HashSet;
import java.util.Set;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.StructureDefinition;

/**
 * Per-resource-type index of element paths classified by their FHIR `_summary` membership.
 *
 * Paths use the canonical dot-separated form from {@link ElementDefinition#getPath()}, e.g.
 * {@code ValueSet.compose.include}. Choice-type paths retain the {@code [x]} suffix
 * (e.g. {@code Extension.value[x]}); the JSON walker matches concrete typed fields like
 * {@code valueString} by stripping the type suffix and re-checking against the {@code [x]} form.
 *
 * The keep semantics for the four FHIR _summary modes are:
 *  - TRUE  -> path is in {@code summaryPaths} (its element is marked isSummary=true), or mandatory
 *  - TEXT  -> path is in {@code textPaths} (root id/meta/text), or mandatory
 *  - DATA  -> any path EXCEPT {@code <ResourceType>.text}
 *  - FALSE -> all paths
 *
 * Ancestry: {@code summaryPaths} and {@code textPaths} are closed under ancestry (if
 * {@code A.b.c} is in the set, so are {@code A.b} and {@code A}). This lets the walker decide
 * "drop everything below this object" in one set lookup.
 */
public class SummaryPathIndex {
  public final String resourceType;
  public final Set<String> summaryPaths;
  public final Set<String> mandatoryPaths;
  public final Set<String> textPaths;

  public SummaryPathIndex(String resourceType,
                          Set<String> summaryPaths,
                          Set<String> mandatoryPaths,
                          Set<String> textPaths) {
    this.resourceType = resourceType;
    this.summaryPaths = summaryPaths;
    this.mandatoryPaths = mandatoryPaths;
    this.textPaths = textPaths;
  }

  public static SummaryPathIndex from(StructureDefinition sd) {
    String resourceType = sd.getType();
    Set<String> summary = new HashSet<>();
    Set<String> mandatory = new HashSet<>();
    if (sd.hasSnapshot()) {
      for (ElementDefinition ed : sd.getSnapshot().getElement()) {
        String path = ed.getPath();
        if (path == null) {
          continue;
        }
        if (ed.getIsSummary()) {
          addWithAncestors(summary, path);
        }
        if (ed.getMin() > 0) {
          addWithAncestors(mandatory, path);
        }
      }
    }
    Set<String> text = new HashSet<>();
    text.add(resourceType);
    text.add(resourceType + ".id");
    text.add(resourceType + ".meta");
    text.add(resourceType + ".text");
    text.addAll(mandatory);
    return new SummaryPathIndex(resourceType, summary, mandatory, text);
  }

  private static void addWithAncestors(Set<String> set, String path) {
    int idx = path.length();
    while (idx > 0) {
      set.add(path.substring(0, idx));
      int dot = path.lastIndexOf('.', idx - 1);
      if (dot < 0) {
        break;
      }
      idx = dot;
    }
  }
}
