/*
 * Copyright 2015-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.cxx;

import com.facebook.buck.apple.clang.HeaderMap;
import com.facebook.buck.core.build.execution.context.ExecutionContext;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.pathformat.PathFormatter;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

class HeaderMapStep implements Step {

  private static final Logger LOG = Logger.get(HeaderMapStep.class);
  private static final HashFunction HASHER = Hashing.sha1();
  private static final String FILE_HASH_VERIFICATION = "hash.verify";

  private final ProjectFilesystem filesystem;
  private final Path output;
  private final ImmutableMap<Path, Path> entries;
  private BuildTarget target;

  public HeaderMapStep(
      ProjectFilesystem filesystem,
      Path output,
      ImmutableMap<Path, Path> entries,
      BuildTarget target) {
    this.filesystem = filesystem;
    this.output = output;
    this.entries = entries;
    this.target = target;
  }

  @Override
  public String getDescription(ExecutionContext context) {
    return "header map @ " + output;
  }

  @Override
  public String getShortName() {
    return "header_map";
  }

  @Override
  public StepExecutionResult execute(ExecutionContext context) throws IOException {
    LOG.debug("Writing header map with %d entries to %s", entries.size(), output);
    HeaderMap.Builder builder = HeaderMap.builder();
    for (Map.Entry<Path, Path> entry : entries.entrySet()) {
      builder.add(PathFormatter.pathWithUnixSeparators(entry.getKey()), entry.getValue());
    }
    HeaderMap headerMap = builder.build();
    filesystem.writeBytesToPath(headerMap.getBytes(), output);
    if (output.getParent() != null) {
      Path metaOut =
          output
              .getParent()
              .resolve(output.getFileName().toString() + "." + FILE_HASH_VERIFICATION);
      HashCode hashCode = HASHER.hashBytes(headerMap.getBytes());
      filesystem.writeContentsToPath(
          String.format("hash: %s\ntarget: %s\nbuild: %s", hashCode, target, context.getBuildId()),
          metaOut);
    }

    return StepExecutionResults.SUCCESS;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof HeaderMapStep)) {
      return false;
    }
    HeaderMapStep that = (HeaderMapStep) obj;
    return Objects.equal(this.output, that.output) && Objects.equal(this.entries, that.entries);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(output, entries);
  }
}
