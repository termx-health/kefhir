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
package com.kodality.kefhir.auth.smart;

import java.util.List;
import java.util.Map;

import static com.kodality.kefhir.core.model.InteractionType.CREATE;
import static com.kodality.kefhir.core.model.InteractionType.DELETE;
import static com.kodality.kefhir.core.model.InteractionType.HISTORYINSTANCE;
import static com.kodality.kefhir.core.model.InteractionType.HISTORYSYSTEM;
import static com.kodality.kefhir.core.model.InteractionType.HISTORYTYPE;
import static com.kodality.kefhir.core.model.InteractionType.READ;
import static com.kodality.kefhir.core.model.InteractionType.SEARCHSYSTEM;
import static com.kodality.kefhir.core.model.InteractionType.SEARCHTYPE;
import static com.kodality.kefhir.core.model.InteractionType.UPDATE;
import static com.kodality.kefhir.core.model.InteractionType.VREAD;

public final class SmartPermission {
  public static final String create = "c";
  public static final String read = "r";
  public static final String update = "u";
  public static final String delete = "d";
  public static final String search = "s";

  //VALIDATE, OPERATION
  public static final Map<String, List<String>> interactions = Map.of(
      create, List.of(CREATE),
      read, List.of(READ, VREAD, HISTORYINSTANCE),
      update, List.of(UPDATE),
      delete, List.of(DELETE),
      search, List.of(SEARCHTYPE, SEARCHSYSTEM, HISTORYTYPE, HISTORYSYSTEM)
  );
}
