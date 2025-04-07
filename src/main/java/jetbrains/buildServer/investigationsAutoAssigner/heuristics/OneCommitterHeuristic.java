

package jetbrains.buildServer.investigationsAutoAssigner.heuristics;

import jetbrains.buildServer.investigationsAutoAssigner.exceptions.HeuristicNotApplicableException;
import jetbrains.buildServer.investigationsAutoAssigner.common.HeuristicResult;
import jetbrains.buildServer.investigationsAutoAssigner.common.Responsibility;
import jetbrains.buildServer.investigationsAutoAssigner.processing.BuildProblemsFilter;
import jetbrains.buildServer.investigationsAutoAssigner.processing.HeuristicContext;
import jetbrains.buildServer.investigationsAutoAssigner.processing.ModificationAnalyzerFactory;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.serverSide.BuildStatisticsOptions;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.SelectPrevBuildPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OneCommitterHeuristic implements Heuristic {
  private final ModificationAnalyzerFactory myModificationAnalyzerFactory;

  public OneCommitterHeuristic(@NotNull ModificationAnalyzerFactory modificationAnalyzerFactory) {
    myModificationAnalyzerFactory = modificationAnalyzerFactory;
  }

  @Override
  @NotNull
  public String getId() {
    return "OneCommitter";
  }

  @NotNull
  @Override
  public HeuristicResult findResponsibleUser(@NotNull HeuristicContext heuristicContext) {
    HeuristicResult result = new HeuristicResult();
    SBuild build = heuristicContext.getBuild();
    User responsible = null;
    final SelectPrevBuildPolicy selectPrevBuildPolicy = SelectPrevBuildPolicy.SINCE_LAST_BUILD;
    for (SVcsModification vcsChange : build.getChanges(selectPrevBuildPolicy, true)) {
      try {
        ModificationAnalyzerFactory.ModificationAnalyzer vcsChangeWrapped = myModificationAnalyzerFactory.getInstance(vcsChange);
        User probableResponsible = vcsChangeWrapped.getOnlyCommitter(heuristicContext.getUsersToIgnore());
        if (probableResponsible == null) continue;
        ensureSameUsers(responsible, probableResponsible);
        responsible = probableResponsible;
      } catch (HeuristicNotApplicableException ex) {
        LOGGER.debug("Heuristic \"OneCommitter\" is ignored as " + ex.getMessage() + ". Build: " +
                     LogUtil.describe(build));
        return result;
      }
    }

    if (responsible != null) {
      if (isCompilationErrorFixed(build)) {
        LOGGER.debug("Heuristic \"OneCommitter\" found " + responsible.getDescriptiveName() + "as responsible, but " +
                     "results are ignored as previous build contained compilation errors." +
                     "  Build: " + LogUtil.describe(build));
        return result;
      }

      Responsibility responsibility = new Responsibility(responsible, "was the only committer to the build");
      heuristicContext.getTestRuns().forEach(sTestRun -> result.addResponsibility(sTestRun, responsibility));

      heuristicContext.getBuildProblems()
                      .stream()
                      .filter(problem -> BuildProblemsFilter.SUPPORTED_EVERYWHERE_TYPES.contains(problem.getBuildProblemData().getType()))
                      .forEach(buildProblem -> result.addResponsibility(buildProblem, responsibility));
    }
    return result;
  }

  private boolean isCompilationErrorFixed(final SBuild build) {
    SBuild previousFinished = build.getPreviousFinished();
    return !containsCompilationErrors(build) && previousFinished != null && containsCompilationErrors(previousFinished);
  }

  private boolean containsCompilationErrors(@NotNull SBuild build) {
      BuildStatisticsOptions opts = new BuildStatisticsOptions(BuildStatisticsOptions.COMPILATION_ERRORS, 0);
      return build.getBuildStatistics(opts).getCompilationErrorsCount() > 0;
  }
  private void ensureSameUsers(@Nullable User first,
                               @Nullable User second) {
    if (first != null && second != null && !first.equals(second)) {
      throw new HeuristicNotApplicableException("There are more than one TeamCity users");
    }
  }
}