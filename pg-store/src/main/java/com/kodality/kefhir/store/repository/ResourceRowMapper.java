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
 package com.kodality.kefhir.store.repository;

import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.util.JsonUtil;
import com.kodality.kefhir.structure.api.ResourceContent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import org.springframework.jdbc.core.RowMapper;

public class ResourceRowMapper implements RowMapper<ResourceVersion> {

  @Override
  public ResourceVersion mapRow(ResultSet rs, int rowNum) throws SQLException {
    ResourceVersion resource = new ResourceVersion();
    resource.setId(mapVersion(rs));
    resource.setContent(mapContent(rs));
    resource.setDeleted(rs.getString("sys_status").equals("C"));
    resource.setAuthor(JsonUtil.fromJson(rs.getString("author")));
    resource.setModified(new Date(rs.getTimestamp("updated").getTime()));
    return resource;
  }

  private ResourceContent mapContent(ResultSet rs) throws SQLException {
    return new ResourceContent(rs.getString("content"), "json");
  }

  private VersionId mapVersion(ResultSet rs) throws SQLException {
    return new VersionId(rs.getString("type"), rs.getString("id"), rs.getInt("version"));
  }

}
