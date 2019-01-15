/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import jetbrains.buildServer.investigationsAutoAssigner.common.HeuristicResult;
import jetbrains.buildServer.investigationsAutoAssigner.common.Responsibility;
import jetbrains.buildServer.investigationsAutoAssigner.persistent.StatisticsReporter;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityFacade;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.responsibility.ResponsibilityEntryEx;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemInfo;
import jetbrains.buildServer.util.Dates;
import org.jetbrains.annotations.NotNull;

public class BuildProblemsAssigner extends BaseAssigner {

  private static final Logger LOGGER = Logger.getInstance(BuildProblemsAssigner.class.getName());
  @NotNull private final BuildProblemResponsibilityFacade myBuildProblemResponsibilityFacade;
  private final StatisticsReporter myStatisticsReporter;

  public BuildProblemsAssigner(@NotNull final BuildProblemResponsibilityFacade buildProblemResponsibilityFacade,
                               @NotNull final StatisticsReporter statisticsReporter) {
    myBuildProblemResponsibilityFacade = buildProblemResponsibilityFacade;
    myStatisticsReporter = statisticsReporter;
  }

  void assign(final HeuristicResult heuristicsResult,
              final SProject sProject,
              final SBuild sBuild,
              final List<BuildProblem> buildProblems) {
    if (heuristicsResult.isEmpty()) return;

    HashMap<Responsibility, List<BuildProblemInfo>> responsibilityToBuildProblem = new HashMap<>();
    for (BuildProblem buildProblem : buildProblems) {
      Responsibility responsibility = heuristicsResult.getResponsibility(buildProblem);
      responsibilityToBuildProblem.putIfAbsent(responsibility, new ArrayList<>());
      List<BuildProblemInfo> buildProblemList = responsibilityToBuildProblem.get(responsibility);
      buildProblemList.add(buildProblem);
    }

    Set<Long> committersIds = calculateCommitersIds(sBuild);

    Set<Responsibility> uniqueResponsibilities = responsibilityToBuildProblem.keySet();
    for (Responsibility responsibility : uniqueResponsibilities) {
      if (shouldAssignInvestigation(responsibility, committersIds)) {
        LOGGER.info(String.format("Automatically assigning investigation(s) to %s in %s because of %s",
                                  responsibility.getUser().getUsername(),
                                  sProject.describe(false),
                                  responsibility.getAssignDescription()));
        List<BuildProblemInfo> buildProblemList = responsibilityToBuildProblem.get(responsibility);

        myBuildProblemResponsibilityFacade.setBuildProblemResponsibility(
          buildProblemList,
          sProject.getProjectId(),
          new ResponsibilityEntryEx(
            ResponsibilityEntry.State.TAKEN, responsibility.getUser(), null, Dates.now(),
            responsibility.getAssignDescription(), ResponsibilityEntry.RemoveMethod.WHEN_FIXED)
        );
        myStatisticsReporter.reportAssignedInvestigations();
      }
    }
  }
}
