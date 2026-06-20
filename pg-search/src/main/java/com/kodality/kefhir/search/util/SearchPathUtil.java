package com.kodality.kefhir.search.util;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

public final class SearchPathUtil {

  public static Set<String> parsePaths(String expression) {
    return Stream.of(expression.split("\\|"))
        .map(s -> StringUtils.trim(s))
        .map(s -> replaceFhirpathWhereResolve(s))
        .collect(Collectors.toSet());
  }

  /**
   * who are we to implement fhirpath?
   * "Encounter.subject.where(resolve() is Patient)" basically means we should search by "Encounter.subject", but reference query value should have "Patient" in it.
   */
  private static String replaceFhirpathWhereResolve(String s) {
    if (!s.contains(".where(resolve() is ")) {
      return s;
    }
    return RegExUtils.removeAll(s, "\\.where\\(resolve\\(\\) is [a-zA-Z]*\\)");
  }

  public static String getFhirpathWhereResolveType(String s) {
    if (!s.contains(".where(resolve() is ")) {
      return null;
    }
    Matcher m = Pattern.compile(".*\\.where\\(resolve\\(\\) is ([a-zA-Z]*)\\)").matcher(s);
    m.matches();
    return m.group(1);
  }
}
