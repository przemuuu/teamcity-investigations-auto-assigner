

package jetbrains.buildServer.investigationsAutoAssigner.processing;

import com.intellij.openapi.diagnostic.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jetbrains.buildServer.BuildProblemTypes;
import jetbrains.buildServer.investigationsAutoAssigner.common.Constants;
import jetbrains.buildServer.investigationsAutoAssigner.common.FailedBuildInfo;
import jetbrains.buildServer.investigationsAutoAssigner.common.HeuristicResult;
import jetbrains.buildServer.investigationsAutoAssigner.persistent.AssignerArtifactDao;
import jetbrains.buildServer.investigationsAutoAssigner.utils.CustomParameters;
import jetbrains.buildServer.serverSide.BuildEx;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.investigationsAutoAssigner.common.Constants.SHOULD_PERSIST_FILTERED_TESTS_DESCRIPTION;

public class FailedTestAndBuildProblemsProcessor extends BaseProcessor {

  private static final Logger LOGGER = Constants.LOGGER;
  private final FailedTestFilter myFailedTestFilter;
  private final BuildProblemsFilter myBuildProblemsFilter;
  private final FailedTestAssigner myFailedTestAssigner;
  private final BuildProblemsAssigner myBuildProblemsAssigner;
  @NotNull private final AssignerArtifactDao myAssignerArtifactDao;
  @NotNull private final ResponsibleUserFinder myResponsibleUserFinder;


  public FailedTestAndBuildProblemsProcessor(@NotNull final ResponsibleUserFinder responsibleUserFinder,
                                             @NotNull final FailedTestFilter failedTestFilter,
                                             @NotNull final FailedTestAssigner failedTestAssigner,
                                             @NotNull final BuildProblemsFilter buildProblemsFilter,
                                             @NotNull final BuildProblemsAssigner buildProblemsAssigner,
                                             @NotNull final AssignerArtifactDao assignerArtifactDao) {
    this.myResponsibleUserFinder = responsibleUserFinder;
    this.myFailedTestFilter = failedTestFilter;
    this.myFailedTestAssigner = failedTestAssigner;
    this.myBuildProblemsFilter = buildProblemsFilter;
    this.myBuildProblemsAssigner = buildProblemsAssigner;
    this.myAssignerArtifactDao = assignerArtifactDao;
  }

  public void processBuild(final FailedBuildInfo failedBuildInfo) {
    SBuild sBuild = failedBuildInfo.getBuild();
    SProject sProject = getProject(sBuild);
    if (sProject == null) return;

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Start processing build #" + sBuild.getBuildId() + ". " +
                   "Delay assignment: " + failedBuildInfo.shouldDelayAssignments());
    }
    if (failedBuildInfo.isOverProcessedProblemsThreshold()) {
      LOGGER.debug("Stop processing build #" + sBuild.getBuildId() + " as the threshold was exceeded.");
      return;
    }

    Map<Long, String> notApplicableTestsDescription = new HashMap<>();
    List<BuildProblem> allBuildProblems = ((BuildEx)sBuild).getBuildProblems();
    List<STestRun> allFailedTests = requestBrokenTestsWithStats(sBuild);
    List<BuildProblem> applicableProblems = myBuildProblemsFilter.apply(failedBuildInfo, sProject, allBuildProblems);
    List<STestRun> applicableFailedTests = myFailedTestFilter.apply(failedBuildInfo, sProject, allFailedTests, notApplicableTestsDescription);
    logProblemsNumber(sBuild, applicableFailedTests, applicableProblems);

    HeuristicResult heuristicsResult =
      myResponsibleUserFinder.findResponsibleUser(sBuild, sProject, applicableProblems, applicableFailedTests);

    List<STestRun> testsForAssign = myFailedTestFilter.getStillApplicable(failedBuildInfo, sProject, applicableFailedTests, notApplicableTestsDescription);
    List<BuildProblem> problemsForAssign =
      myBuildProblemsFilter.getStillApplicable(failedBuildInfo, sProject, applicableProblems);
    logChangedProblemsNumber(sBuild, applicableFailedTests, testsForAssign, applicableProblems, problemsForAssign);

    myAssignerArtifactDao.appendHeuristicsResult(sBuild, testsForAssign, heuristicsResult);
    if (TeamCityProperties.getBoolean(SHOULD_PERSIST_FILTERED_TESTS_DESCRIPTION)) {
      myAssignerArtifactDao.appendNotApplicableTestsDescription(sBuild, notApplicableTestsDescription);
    }

    if (heuristicsResult.isEmpty()) {
      return;
    }

    if (CustomParameters.isBuildFeatureEnabled(sBuild) && !failedBuildInfo.shouldDelayAssignments()) {
      myFailedTestAssigner.assign(heuristicsResult, sProject, sBuild, testsForAssign);
      myBuildProblemsAssigner.assign(heuristicsResult, sProject, sBuild, problemsForAssign);
      failedBuildInfo.addHeuristicsResult(heuristicsResult);

      return;
    }

    if (!CustomParameters.isBuildFeatureEnabled(sBuild)) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(String.format("Build id:%s. Found investigations but build feature is not configured.",
                                   sBuild.getBuildId()));
      }
    } else if (failedBuildInfo.shouldDelayAssignments()) {
      List<BuildProblem> forcedAssignInstantlyProblems =
        problemsForAssign.stream()
                         .filter(x -> !BuildProblemTypes.TC_EXIT_CODE_TYPE.equals(x.getBuildProblemData().getType()))
                         .collect(Collectors.toList());
      if (!forcedAssignInstantlyProblems.isEmpty()) {
        myBuildProblemsAssigner.assign(heuristicsResult, sProject, sBuild, forcedAssignInstantlyProblems);
      }

      LOGGER.debug(String.format("Build id:%s. Found investigations but assignments should be delayed.",
                                 sBuild.getBuildId()));
    }

    failedBuildInfo.addHeuristicsResult(heuristicsResult);
  }
}