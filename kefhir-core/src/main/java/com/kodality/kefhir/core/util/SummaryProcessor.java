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
package com.kodality.kefhir.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kodality.kefhir.core.service.conformance.ConformanceHolder;
import com.kodality.kefhir.core.service.conformance.SummaryPathIndex;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies the FHIR `_summary` parameter modes (true, text, data, false) to a serialized resource.
 *
 * Drives the keep/drop decision off {@link SummaryPathIndex} sets stored in
 * {@link ConformanceHolder}, which in turn are derived from `ElementDefinition.isSummary` and
 * `ElementDefinition.min` on the resource's StructureDefinition. `_summary=count` is handled
 * upstream at the framework layer (count-only Bundle) and never reaches this class.
 *
 * Always-keep fields (regardless of mode): `id`, `meta`, `extension`, `modifierExtension`,
 * `resourceType`. For TRUE and TEXT, a `SUBSETTED` security tag is added to `meta.tag`
 * per the FHIR spec.
 */
@Slf4j
public final class SummaryProcessor {
  public enum Mode { TRUE, TEXT, DATA, FALSE }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final Set<String> ALWAYS_KEEP_FIELDS = Set.of(
      "id", "meta", "extension", "modifierExtension", "resourceType"
  );

  private static final String SUBSETTED_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ObservationValue";
  private static final String SUBSETTED_CODE = "SUBSETTED";

  private SummaryProcessor() {}

  public static Mode parse(String value) {
    if (value == null) {
      return Mode.FALSE;
    }
    return switch (value.toLowerCase()) {
      case "true" -> Mode.TRUE;
      case "text" -> Mode.TEXT;
      case "data" -> Mode.DATA;
      default -> Mode.FALSE;
    };
  }

  /**
   * Returns the resource JSON shaped for {@code mode}. For {@link Mode#FALSE} returns
   * {@code resourceJson} unchanged. On parse failure returns the input unchanged and logs a
   * warning — rather return a slightly oversized resource than 500 the caller.
   */
  public static String apply(String resourceJson, String resourceType, Mode mode) {
    return apply(resourceJson, resourceType, mode, ConformanceHolder.getSummaryIndex(resourceType));
  }

  /**
   * Test-friendly overload that takes the index explicitly, bypassing {@link ConformanceHolder}.
   */
  public static String apply(String resourceJson, String resourceType, Mode mode, SummaryPathIndex index) {
    if (mode == null || mode == Mode.FALSE || resourceJson == null) {
      return resourceJson;
    }
    if (index == null && (mode == Mode.TRUE || mode == Mode.TEXT)) {
      // No metadata for this resource type — refuse to strip blindly.
      return resourceJson;
    }
    try {
      JsonNode root = MAPPER.readTree(resourceJson);
      if (!(root instanceof ObjectNode obj)) {
        return resourceJson;
      }
      walk(obj, resourceType, index, mode);
      if (mode == Mode.TRUE || mode == Mode.TEXT) {
        addSubsettedTag(obj);
      }
      return MAPPER.writeValueAsString(obj);
    } catch (Exception e) {
      log.warn("SummaryProcessor: failed to apply {} to {}: {}", mode, resourceType, e.getMessage());
      return resourceJson;
    }
  }

  private static void walk(ObjectNode node, String path, SummaryPathIndex index, Mode mode) {
    List<String> toRemove = new ArrayList<>();
    Iterator<String> it = node.fieldNames();
    while (it.hasNext()) {
      String field = it.next();
      if (ALWAYS_KEEP_FIELDS.contains(field)) {
        continue;
      }
      String childPath = path + "." + field;
      String choicePath = choiceTypePath(path, field);
      if (!shouldKeep(childPath, choicePath, index, mode)) {
        toRemove.add(field);
      }
    }
    for (String f : toRemove) {
      node.remove(f);
    }

    Iterator<String> remaining = node.fieldNames();
    while (remaining.hasNext()) {
      String field = remaining.next();
      if (ALWAYS_KEEP_FIELDS.contains(field)) {
        continue;
      }
      JsonNode child = node.get(field);
      String childPath = path + "." + field;
      String choicePath = choiceTypePath(path, field);
      String walkPath = choicePath != null ? choicePath : childPath;
      recurse(child, walkPath, index, mode);
    }
  }

  private static void recurse(JsonNode node, String path, SummaryPathIndex index, Mode mode) {
    if (node instanceof ObjectNode obj) {
      walk(obj, path, index, mode);
    } else if (node instanceof ArrayNode arr) {
      for (JsonNode element : arr) {
        recurse(element, path, index, mode);
      }
    }
  }

  private static boolean shouldKeep(String path, String choicePath, SummaryPathIndex index, Mode mode) {
    return switch (mode) {
      case FALSE -> true;
      case DATA -> {
        // strip only the root narrative
        yield !endsWith(path, ".text");
      }
      case TRUE -> {
        if (index == null) yield true;
        yield index.summaryPaths.contains(path)
            || index.mandatoryPaths.contains(path)
            || (choicePath != null && (index.summaryPaths.contains(choicePath) || index.mandatoryPaths.contains(choicePath)));
      }
      case TEXT -> {
        if (index == null) yield true;
        yield index.textPaths.contains(path)
            || (choicePath != null && index.textPaths.contains(choicePath));
      }
    };
  }

  private static boolean endsWith(String path, String suffix) {
    int dot = path.indexOf('.');
    if (dot < 0) {
      return false;
    }
    String afterRoot = path.substring(dot);
    return afterRoot.equals(suffix);
  }

  /**
   * For a JSON field that is the concrete typing of a FHIR choice element (e.g. {@code valueString}
   * for {@code value[x]}), return the {@code [x]} path; otherwise null.
   *
   * Heuristic: a choice typing has a lowercase prefix immediately followed by an uppercase letter.
   * The prefix is the property name; the rest is the FHIR type. We don't validate the type — the
   * StructureDefinition itself constrains which types are allowed for the choice.
   */
  static String choiceTypePath(String parentPath, String field) {
    if (field.length() < 2) {
      return null;
    }
    int split = -1;
    for (int i = 1; i < field.length(); i++) {
      char c = field.charAt(i);
      if (Character.isUpperCase(c)) {
        split = i;
        break;
      }
    }
    if (split <= 0) {
      return null;
    }
    String prefix = field.substring(0, split);
    return parentPath + "." + prefix + "[x]";
  }

  private static void addSubsettedTag(ObjectNode root) {
    ObjectNode meta = (ObjectNode) root.get("meta");
    if (meta == null) {
      meta = root.putObject("meta");
    }
    ArrayNode tag = (ArrayNode) meta.get("tag");
    if (tag == null) {
      tag = meta.putArray("tag");
    } else {
      for (JsonNode t : tag) {
        if (t.has("code") && SUBSETTED_CODE.equals(t.get("code").asText())) {
          return;
        }
      }
    }
    ObjectNode subsetted = tag.addObject();
    subsetted.put("system", SUBSETTED_SYSTEM);
    subsetted.put("code", SUBSETTED_CODE);
  }
}
