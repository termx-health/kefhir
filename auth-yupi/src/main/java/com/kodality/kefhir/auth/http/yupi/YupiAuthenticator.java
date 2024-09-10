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
package com.kodality.kefhir.auth.http.yupi;

import com.kodality.kefhir.auth.User;
import com.kodality.kefhir.auth.http.AuthenticationProvider;
import com.kodality.kefhir.auth.http.HttpAuthorization;
import com.kodality.kefhir.rest.model.KefhirRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;

@Singleton
public class YupiAuthenticator implements AuthenticationProvider {
  private static final Map<String, String> yupiOrgs = Map.of(
      "yupi", "yupland",
      "ipuy", "dnalpuy"
  );

  @Override
  public User autheticate(KefhirRequest request) {
    List<HttpAuthorization> auths = HttpAuthorization.parse(request.getHeaders().get("Authorization"));
    return auths.stream()
        .filter(a -> a.isType("Bearer"))
        .filter(bearer -> yupiOrgs.containsKey(bearer.getCredential()))
        .map(bearer -> makeYupi(bearer.getCredential(), yupiOrgs.get(bearer.getCredential())))
        .map(user -> decorateClaims(user, getClaimHeaders(request))).findFirst().orElse(null);
  }

  private Map<String, String> getClaimHeaders(KefhirRequest request) {
    Map<String, String> claims = new HashMap<>();
    request.getHeaders().keySet().stream().filter(k -> k.startsWith("x-claim-")).forEach(k -> {
      String claim = k.replace("x-claim-", "");
      claims.put(claim, request.getHeader(k));
    });
    return claims;
  }

  private User decorateClaims(User user, Map<String, String> claimHeaders) {
    claimHeaders.forEach((k, v) -> user.getClaims().put(k, v));
    return user;
  }

  private static User makeYupi(String sub, String org) {
    User user = new User();
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", sub);
    claims.put("org", org);
    user.setClaims(claims);
    user.setScopes(Collections.singleton("user/*.*"));
    return user;
  }

}
