

package jetbrains.buildServer.investigationsAutoAssigner.processing;

import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.BuildProblemTypes;
import jetbrains.buildServer.investigationsAutoAssigner.common.Constants;
import jetbrains.buildServer.investigationsAutoAssigner.common.FailedBuildInfo;
import jetbrains.buildServer.investigationsAutoAssigner.common.HeuristicResult;
import jetbrains.buildServer.investigationsAutoAssigner.utils.AggregationLogger;
import jetbrains.buildServer.serverSide.BuildEx;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.problems.BuildProblem;

public class DelayedAssignmentsProcessor extends BaseProcessor {
  private static final Logger LOGGER = Constants.LOGGER;
  private final BuildProblemsFilter myBuildProblemsFilter;
  private final FailedTestFilter myFailedTestFilter;
  private final AggregationLogger myAggregationLogger;
  private final BuildProblemsAssigner myBuildProblemsAssigner;
  private final FailedTestAssigner myFailedTestAssigner;

  public DelayedAssignmentsProcessor(BuildProblemsAssigner buildProblemsAssigner,
                                     FailedTestAssigner failedTestAssigner,
                                     BuildProblemsFilter buildProblemsFilter,
                                     FailedTestFilter failedTestFilter,
                                     AggregationLogger aggregationLogger) {
    this.myBuildProblemsAssigner = buildProblemsAssigner;
    this.myFailedTestAssigner = failedTestAssigner;
    this.myBuildProblemsFilter = buildProblemsFilter;
    this.myFailedTestFilter = failedTestFilter;
    this.myAggregationLogger = aggregationLogger;
  }

  public void processBuild(final FailedBuildInfo failedBuildInfo, SBuild nextBuild) {
    SBuild sBuild = failedBuildInfo.getBuild();
    SProject sProject = getProject(sBuild);
    if (sProject == null) return;

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Start processing delayed assignments for build #" + sBuild.getBuildId() + ".");
    }
    HeuristicResult heuristicsResult = failedBuildInfo.getHeuristicsResult();

    List<STestRun> applicableFailedTests =
      requestBrokenTestsWithStats(sBuild).stream()
                                         .filter(failedTest -> heuristicsResult.getResponsibility(failedTest) != null)
                                         .collect(Collectors.toList());

    List<String> nextBuildProblemIdentities =
      ((BuildEx)nextBuild).getBuildProblems()
                          .stream()
                          .map(buildProblem -> buildProblem.getBuildProblemData().getIdentity())
                          .collect(Collectors.toList());

    List<BuildProblem> applicableProblems =
      ((BuildEx)sBuild).getBuildProblems()
                       .stream()
                       .filter(buildProblem -> heuristicsResult.getResponsibility(buildProblem) != null &&
                                               nextBuildProblemIdentities.contains(buildProblem.getBuildProblemData().getIdentity()) &&
                                               BuildProblemTypes.TC_EXIT_CODE_TYPE.equals(buildProblem.getBuildProblemData().getType()))
                       .collect(Collectors.toList());

    logProblemsNumber(sBuild, applicableFailedTests, applicableProblems);
    List<STestRun> testsForAssign = myFailedTestFilter.getStillApplicable(failedBuildInfo, sProject, applicableFailedTests);
    List<BuildProblem> problemsForAssign =
      myBuildProblemsFilter.getStillApplicable(failedBuildInfo, sProject, applicableProblems);
    logChangedProblemsNumber(sBuild, applicableFailedTests, testsForAssign, applicableProblems, problemsForAssign);

    myFailedTestAssigner.assign(heuristicsResult, sProject, sBuild, testsForAssign);
    myBuildProblemsAssigner.assign(heuristicsResult, sProject, sBuild, problemsForAssign);

    myAggregationLogger.logDelayedResults(sBuild, nextBuild, heuristicsResult, testsForAssign, problemsForAssign);
  }
}