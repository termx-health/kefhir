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
package com.kodality.kefhir.core.context;

import com.kodality.kefhir.core.util.SummaryProcessor;

/**
 * Per-request holder for the {@code _summary} mode of the current FHIR read / vread call,
 * scoped via a {@link ThreadLocal}. The read entry-points in
 * {@code com.kodality.kefhir.rest.DefaultFhirResourceServer} (in the sibling
 * {@code fhir-rest} module) {@link #set(SummaryProcessor.Mode) set} the mode before
 * invoking storage and {@link #clear() clear} it in a {@code finally} so the value never
 * leaks across threads or requests.
 *
 * <p>Storage implementations that want to short-circuit expensive loads when the response
 * will be summarised anyway (e.g. skip {@code concept[]} entity reads for a CodeSystem
 * when {@code _summary=true}) can read the mode with {@link #get()}. Returns {@code null}
 * when no read is in scope — e.g. internal storage calls outside the REST request flow —
 * which storage code should treat as "default behaviour" (i.e. load full).
 *
 * <p>Search has its own {@code SearchCriterion._SUMMARY} mechanism; this context is
 * exclusively for the read / vread flow where there's no search-criterion object to pass.
 */
public final class RequestSummaryContext {
  private static final ThreadLocal<SummaryProcessor.Mode> CURRENT = new ThreadLocal<>();

  private RequestSummaryContext() {
  }

  /** Returns the mode set for the current read/vread, or {@code null} if none. */
  public static SummaryProcessor.Mode get() {
    return CURRENT.get();
  }

  /** Sets the mode for the current thread. Callers MUST pair with {@link #clear()} in a
   *  {@code finally} block. {@code null} is allowed and clears the slot. */
  public static void set(SummaryProcessor.Mode mode) {
    if (mode == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(mode);
    }
  }

  /** Removes the mode for the current thread. Safe to call when nothing was set. */
  public static void clear() {
    CURRENT.remove();
  }
}
