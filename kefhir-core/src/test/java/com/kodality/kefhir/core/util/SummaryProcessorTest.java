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
import com.kodality.kefhir.core.service.conformance.SummaryPathIndex;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SummaryProcessorTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  // Hand-rolled index for ValueSet — only fields we care about in tests.
  // Mirrors the production SummaryPathIndex.from() output: literal paths only, no ancestor closure.
  private static SummaryPathIndex valueSetIndex() {
    Set<String> summary = new HashSet<>(Set.of(
        "ValueSet.url",
        "ValueSet.name",
        "ValueSet.status"
    ));
    Set<String> mandatory = new HashSet<>(Set.of("ValueSet.status"));
    Set<String> text = new HashSet<>(Set.of(
        "ValueSet.id",
        "ValueSet.meta",
        "ValueSet.text",
        "ValueSet.status"
    ));
    return new SummaryPathIndex("ValueSet", summary, mandatory, text);
  }

  private static String resource() {
    return """
        {
          "resourceType": "ValueSet",
          "id": "vs-1",
          "url": "https://example.org/ValueSet/vs-1",
          "name": "VS One",
          "status": "active",
          "text": {"status": "generated", "div": "<div>narrative</div>"},
          "compose": {
            "include": [{"system": "http://example.org/cs"}]
          },
          "expansion": {"total": 5}
        }
        """;
  }

  @Test
  public void parseModes() {
    assertEquals(SummaryProcessor.Mode.TRUE, SummaryProcessor.parse("true"));
    assertEquals(SummaryProcessor.Mode.TRUE, SummaryProcessor.parse("TRUE"));
    assertEquals(SummaryProcessor.Mode.TEXT, SummaryProcessor.parse("text"));
    assertEquals(SummaryProcessor.Mode.DATA, SummaryProcessor.parse("data"));
    assertEquals(SummaryProcessor.Mode.FALSE, SummaryProcessor.parse("false"));
    assertEquals(SummaryProcessor.Mode.FALSE, SummaryProcessor.parse(null));
    assertEquals(SummaryProcessor.Mode.FALSE, SummaryProcessor.parse("garbage"));
  }

  @Test
  public void falseModeReturnsInputUnchanged() {
    String input = resource();
    assertSame(input, SummaryProcessor.apply(input, "ValueSet", SummaryProcessor.Mode.FALSE, valueSetIndex()));
  }

  @Test
  public void dataModeStripsRootText() throws Exception {
    String result = SummaryProcessor.apply(resource(), "ValueSet", SummaryProcessor.Mode.DATA, valueSetIndex());
    JsonNode tree = MAPPER.readTree(result);
    assertFalse(tree.has("text"), "text should be stripped");
    assertTrue(tree.has("compose"), "compose should remain");
    assertTrue(tree.has("expansion"), "expansion should remain");
    assertFalse(metaHasSubsettedTag(tree), "DATA mode does not add SUBSETTED tag");
  }

  @Test
  public void trueModeKeepsSummaryAndDropsHeavyData() throws Exception {
    String result = SummaryProcessor.apply(resource(), "ValueSet", SummaryProcessor.Mode.TRUE, valueSetIndex());
    JsonNode tree = MAPPER.readTree(result);
    assertEquals("vs-1", tree.path("id").asText(), "id is always kept");
    assertEquals("https://example.org/ValueSet/vs-1", tree.path("url").asText(), "url is summary");
    assertEquals("VS One", tree.path("name").asText(), "name is summary");
    assertEquals("active", tree.path("status").asText(), "status is summary/mandatory");
    assertFalse(tree.has("compose"), "compose is not summary -> dropped");
    assertFalse(tree.has("expansion"), "expansion is not summary -> dropped");
    assertTrue(metaHasSubsettedTag(tree), "TRUE mode adds SUBSETTED tag");
  }

  @Test
  public void textModeKeepsOnlyNarrativeAndMandatory() throws Exception {
    String result = SummaryProcessor.apply(resource(), "ValueSet", SummaryProcessor.Mode.TEXT, valueSetIndex());
    JsonNode tree = MAPPER.readTree(result);
    assertEquals("vs-1", tree.path("id").asText());
    assertTrue(tree.has("text"), "text retained for TEXT mode");
    assertTrue(tree.has("status"), "mandatory element retained");
    assertFalse(tree.has("url"), "url is summary but not text-set -> dropped");
    assertFalse(tree.has("name"), "name is not in text-set -> dropped");
    assertFalse(tree.has("compose"));
    assertFalse(tree.has("expansion"));
    assertTrue(metaHasSubsettedTag(tree));
  }

  @Test
  public void unknownResourceTypeReturnsInputForTrueMode() {
    String input = resource();
    String result = SummaryProcessor.apply(input, "UnknownType", SummaryProcessor.Mode.TRUE, null);
    assertSame(input, result, "Without an index we refuse to strip blindly");
  }

  @Test
  public void choiceTypePathRecognised() {
    // valueString -> Extension.value[x]
    assertEquals("Extension.value[x]", SummaryProcessor.choiceTypePath("Extension", "valueString"));
    // valueDateTime -> Extension.value[x]
    assertEquals("Extension.value[x]", SummaryProcessor.choiceTypePath("Extension", "valueDateTime"));
    // 'value' alone has no uppercase suffix → not a choice typing
    assertEquals(null, SummaryProcessor.choiceTypePath("Extension", "value"));
    // Capital first letter is not a choice typing
    assertEquals(null, SummaryProcessor.choiceTypePath("Extension", "Value"));
  }

  @Test
  public void existingSubsettedTagNotDuplicated() throws Exception {
    String input = """
        {
          "resourceType": "ValueSet",
          "id": "vs-1",
          "url": "https://example.org/ValueSet/vs-1",
          "name": "VS One",
          "status": "active",
          "meta": {
            "tag": [
              {"system": "http://terminology.hl7.org/CodeSystem/v3-ObservationValue", "code": "SUBSETTED"}
            ]
          }
        }
        """;
    String result = SummaryProcessor.apply(input, "ValueSet", SummaryProcessor.Mode.TRUE, valueSetIndex());
    JsonNode tree = MAPPER.readTree(result);
    JsonNode tags = tree.path("meta").path("tag");
    assertNotNull(tags);
    int count = 0;
    for (JsonNode t : tags) {
      if ("SUBSETTED".equals(t.path("code").asText())) {
        count++;
      }
    }
    assertEquals(1, count, "SUBSETTED tag must not be duplicated");
  }

  private static boolean metaHasSubsettedTag(JsonNode tree) {
    JsonNode tags = tree.path("meta").path("tag");
    if (tags == null || !tags.isArray()) {
      return false;
    }
    for (JsonNode t : tags) {
      if ("SUBSETTED".equals(t.path("code").asText())) {
        return true;
      }
    }
    return false;
  }
}
