

package jetbrains.buildServer.investigationsAutoAssigner.processing;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jetbrains.buildServer.BuildProblemTypes;
import jetbrains.buildServer.investigationsAutoAssigner.common.Constants;
import jetbrains.buildServer.investigationsAutoAssigner.common.FailedBuildInfo;
import jetbrains.buildServer.investigationsAutoAssigner.utils.BuildProblemUtils;
import jetbrains.buildServer.investigationsAutoAssigner.utils.CustomParameters;
import jetbrains.buildServer.investigationsAutoAssigner.utils.InvestigationsManager;
import jetbrains.buildServer.messages.ErrorData;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class BuildProblemsFilter {

  private static final Logger LOGGER = Constants.LOGGER;
  public static final Set<String> SUPPORTED_EVERYWHERE_TYPES = Collections.unmodifiableSet(
    new HashSet<>(Arrays.asList(BuildProblemTypes.TC_COMPILATION_ERROR_TYPE, BuildProblemTypes.TC_EXIT_CODE_TYPE)));
  public static final Set<String> SNAPSHOT_DEPENDENCY_ERROR_TYPES = Collections.unmodifiableSet(
    new HashSet<>(Arrays.asList(ErrorData.SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE,
                                ErrorData.SNAPSHOT_DEPENDENCY_ERROR_TYPE)));
  private final BuildProblemUtils myBuildProblemUtils;
  private final CustomParameters myCustomParameters;
  private final InvestigationsManager myInvestigationsManager;

  public BuildProblemsFilter(@NotNull final InvestigationsManager investigationsManager,
                             @NotNull final BuildProblemUtils buildProblemUtils,
                             @NotNull final CustomParameters customParameters) {
    this.myInvestigationsManager = investigationsManager;
    this.myBuildProblemUtils = buildProblemUtils;
    this.myCustomParameters = customParameters;
  }

  List<BuildProblem> apply(final FailedBuildInfo failedBuildInfo,
                           final SProject sProject,
                           final List<BuildProblem> buildProblems) {
    SBuild sBuild = failedBuildInfo.getBuild();
    loggerDebugInfo(String.format("Filtering of build problems for build id:%s started", sBuild.getBuildId()));


    List<BuildProblem> filteredBuildProblems = buildProblems.stream()
                                                            .filter(failedBuildInfo::checkNotProcessed)
                                                            .filter(problem -> isApplicable(sProject, sBuild, problem))
                                                            .limit(failedBuildInfo.getLimitToProcess())
                                                            .collect(Collectors.toList());

    failedBuildInfo.addProcessedBuildProblems(buildProblems);
    failedBuildInfo.increaseProcessedNumber(filteredBuildProblems.size());

    return filteredBuildProblems;
  }


  List<BuildProblem> getStillApplicable(final FailedBuildInfo failedBuildInfo,
                                        final SProject sProject,
                                        final List<BuildProblem> allBuildProblems) {
    SBuild sBuild = failedBuildInfo.getBuild();
    loggerDebugInfo(String.format("Filtering before assign of build problems for build id:%s started", sBuild.getBuildId()));

    return allBuildProblems.stream()
                           .filter(buildProblem -> isApplicable(sProject, sBuild, buildProblem))
                           .collect(Collectors.toList());
  }

  private boolean isApplicable(@NotNull final SProject project,
                               @NotNull final SBuild sBuild,
                               @NotNull final BuildProblem problem) {
    String reason = null;
    String buildProblemType = problem.getBuildProblemData().getType();

    if (problem.isMuted()) {
      reason = "is muted";
    } else if (!myBuildProblemUtils.isNew(problem)) {
      reason = "occurs not for the first time";
    } else if (myInvestigationsManager.checkUnderInvestigation(project, sBuild, problem)) {
      reason = "is already under an investigation";
    } else if (BuildProblemTypes.TC_FAILED_TESTS_TYPE.equals(problem.getBuildProblemData().getType())) {
      reason = "has unsupported failed tests build problem type";
    } else if (myCustomParameters.getBuildProblemTypesToIgnore(sBuild).contains(buildProblemType)) {
      reason = "is among build problem types to ignore";
    }

    boolean isApplicable = reason == null;
    loggerDebugInfo(String.format("Build problem id:%s:%s is %s.%s",
                                 sBuild.getBuildId(),
                                 problem.getTypeDescription(),
                                 (isApplicable ? "applicable" : "not applicable"),
                                 (isApplicable ? "" : String.format(" Reason: this build problem %s.", reason))));
    return isApplicable;
  }

  private void loggerDebugInfo(String message) {
    if(LOGGER.isDebugEnabled()) {
        LOGGER.debug(message);
    }
  }
}