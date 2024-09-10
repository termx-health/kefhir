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
package com.kodality.kefhir.auth.http.oidc;

import com.kodality.kefhir.auth.User;
import com.kodality.kefhir.auth.http.AuthenticationProvider;
import com.kodality.kefhir.auth.http.HttpAuthorization;
import com.kodality.kefhir.rest.model.KefhirRequest;
import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

//TODO: .well-known
@Singleton
@RequiredArgsConstructor
public class OidcAuthenticator implements AuthenticationProvider {
  private OidcUserProvider oidcUserProvider;

  @Override
  public User autheticate(KefhirRequest request) {
    List<HttpAuthorization> auths = HttpAuthorization.parse(request.getHeaders().get("Authorization"));
    String bearerToken = auths.stream().filter(a -> a.isType("Bearer")).findFirst().map(HttpAuthorization::getCredential).orElse(null);
    return oidcUserProvider.getUser(bearerToken);
  }

}
