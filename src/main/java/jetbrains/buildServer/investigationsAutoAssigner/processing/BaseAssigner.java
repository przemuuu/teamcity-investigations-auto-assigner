/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.investigationsAutoAssigner.processing;

import jetbrains.buildServer.investigationsAutoAssigner.common.Constants;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface BaseAssigner {

  @NotNull
  default ResponsibilityEntry.RemoveMethod getRemoveMethod(@Nullable SBuildType buildType) {
    if (buildType == null) {
      return ResponsibilityEntry.RemoveMethod.WHEN_FIXED;
    }
    return ((BuildTypeEx)buildType).getBooleanInternalParameter(Constants.SHOULD_ASSIGN_RESOLVE_MANUALLY) ?
           ResponsibilityEntry.RemoveMethod.MANUALLY :
           ResponsibilityEntry.RemoveMethod.WHEN_FIXED;
  }
}
