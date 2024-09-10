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
 package com.kodality.kefhir.auth.http;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import static java.util.stream.Collectors.toList;

public class HttpAuthorization {
  private final String type;
  private final String credential;

  public HttpAuthorization() {
    this(null, null);
  }

  public HttpAuthorization(String type, String credential) {
    this.type = type;
    this.credential = credential;
  }

  public static List<HttpAuthorization> parse(Collection<String> headers) {
    if (CollectionUtils.isEmpty(headers)) {
      return Collections.emptyList();
    }
    return headers.stream()
        .filter(h -> !StringUtils.isEmpty(h))
        .flatMap(h -> Stream.of(h.split(",")))
        .map(a -> a.trim())
        .map(auth -> {
          String[] parts = auth.split("\\s");
          if (parts.length < 2) {
            return null;
          }
          return new HttpAuthorization(parts[0], parts[1]);
        })
        .filter(a -> a != null)
        .collect(toList());
  }

  public String getType() {
    return type;
  }

  public String getCredential() {
    return credential;
  }

  public boolean isType(String type) {
    return StringUtils.equals(type, this.type);
  }

  public String getCredentialDecoded() {
    try {
      return new String(Base64.getDecoder().decode(credential), "UTF8");
    } catch (IllegalArgumentException | UnsupportedEncodingException e) {
      return credential;
    }
  }
}
