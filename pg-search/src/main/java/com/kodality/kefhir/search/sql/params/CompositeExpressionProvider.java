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
package com.kodality.kefhir.search.sql.params;

import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.search.QueryParam;
import com.kodality.kefhir.core.service.conformance.ConformanceHolder;
import com.kodality.kefhir.search.sql.ExpressionProvider;
import com.kodality.kefhir.search.sql.SearchSqlUtil;
import com.kodality.kefhir.util.sql.SqlBuilder;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.hl7.fhir.r5.model.SearchParameter;

import static java.util.stream.Collectors.toList;

public class CompositeExpressionProvider extends ExpressionProvider {


  @Override
  public SqlBuilder makeExpression(QueryParam param, String alias) {
    SearchParameter sp = ConformanceHolder.getSearchParam(param.getResourceType(), param.getKey());
    List<SqlBuilder> ors = param.getValues().stream().filter(v -> !StringUtils.isEmpty(v)).map(v -> {
      String[] parts = StringUtils.split(v, "$");
      if (parts.length != sp.getComponent().size()) {
        throw new FhirException(400, IssueType.INVALID, "invalid composite parameter");
      }
      List<SqlBuilder> ands = sp.getComponent().stream().map(c -> {
        String p = parts[sp.getComponent().indexOf(c)];
        String subParamId = StringUtils.substringAfterLast(c.getDefinition(), "/");
        SearchParameter subParam = ConformanceHolder.getSearchParams().get(subParamId);
        // XXX: c.getExpression() is ignored.
        // looks like fhir tries to narrow-down search parameter expressions when using composite params.
        // we can't do that, however, because expressions are done on 'SearchParameter' level
        QueryParam qp = new QueryParam(subParam.getCode(), null, subParam.getType(), param.getResourceType());
        qp.setValues(List.of(p));
        return SearchSqlUtil.condition(qp, alias);
      }).collect(toList());
      return new SqlBuilder().append(ands, "AND");
    }).collect(toList());
    return new SqlBuilder().append(ors, "OR");
  }

  @Override
  public SqlBuilder order(String resourceType, String key, String alias, String direction) {
    throw new FhirException(400, IssueType.NOTSUPPORTED, "order by composite param not implemented");
  }
}
