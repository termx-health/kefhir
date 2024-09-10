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
package com.kodality.kefhir;

import com.kodality.kefhir.core.service.conformance.ConformanceInitializationService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import lombok.RequiredArgsConstructor;

//TODO: auth. check resource types?
@Controller("/conformance-tools")
@RequiredArgsConstructor
public class ConformanceToolsController {
  private final ConformanceFileImportService conformanceFileImportService;
  private final ConformanceDownloadService conformanceDownloadService;
  private final ConformanceInitializationService conformanceInitializationService;

  @Post("/import-file")
  public HttpResponse<?> importFile(@QueryValue String file) {
    conformanceFileImportService.importFromFile(file);
    return HttpResponse.ok();
  }

  @Post("/import-url")
  public HttpResponse<?> importUrl(@QueryValue String url) {
    conformanceDownloadService.importFromUrl(url);
    return HttpResponse.ok();
  }

  @Post("/download-fhir")
  public HttpResponse<?> downloadFhir() {
//    conformanceDownloadService.importFromUrl("http://www.hl7.org/fhir/definitions.json.zip");
    conformanceDownloadService.importFromUrl("https://kexus.kodality.com/repository/store-public/kefhir/definitions.json.zip");
    return HttpResponse.ok();
  }

  @Post("/refresh")
  public HttpResponse<?> refreshConformance() {
    conformanceInitializationService.refresh();
    return HttpResponse.ok();
  }
}
