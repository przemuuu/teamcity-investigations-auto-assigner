

package jetbrains.buildServer.investigationsAutoAssigner.heuristics;

import com.intellij.openapi.util.Pair;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import jetbrains.buildServer.investigationsAutoAssigner.exceptions.HeuristicNotApplicableException;
import jetbrains.buildServer.investigationsAutoAssigner.common.HeuristicResult;
import jetbrains.buildServer.investigationsAutoAssigner.common.Responsibility;
import jetbrains.buildServer.investigationsAutoAssigner.processing.BuildProblemsFilter;
import jetbrains.buildServer.investigationsAutoAssigner.processing.HeuristicContext;
import jetbrains.buildServer.investigationsAutoAssigner.processing.ModificationAnalyzerFactory;
import jetbrains.buildServer.investigationsAutoAssigner.utils.ProblemTextExtractor;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.ChangeDescriptor;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.SelectPrevBuildPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BrokenFileHeuristic implements Heuristic {
  private final ProblemTextExtractor myProblemTextExtractor;
  private final ModificationAnalyzerFactory myModificationAnalyzerFactory;

  public BrokenFileHeuristic(@NotNull ProblemTextExtractor problemTextExtractor,
                             @NotNull ModificationAnalyzerFactory modificationAnalyzerFactory) {
    this.myProblemTextExtractor = problemTextExtractor;
    this.myModificationAnalyzerFactory = modificationAnalyzerFactory;
  }

  @Override
  @NotNull
  public String getId() {
    return "BrokenFile";
  }

  @NotNull
  public HeuristicResult findResponsibleUser(@NotNull HeuristicContext heuristicContext) {
    final HeuristicResult emptyResult = new HeuristicResult();
    SBuild sBuild = heuristicContext.getBuild();

    final BuildPromotion buildPromotion = sBuild.getBuildPromotion();
    if (!(buildPromotion instanceof BuildPromotionEx)) return emptyResult;

    SelectPrevBuildPolicy prevBuildPolicy = SelectPrevBuildPolicy.SINCE_LAST_BUILD;
    List<SVcsModification> vcsChanges = ((BuildPromotionEx)buildPromotion).getDetectedChanges(prevBuildPolicy, false)
                                                                          .stream()
                                                                          .map(ChangeDescriptor::getRelatedVcsChange)
                                                                          .filter(Objects::nonNull)
                                                                          .collect(Collectors.toList());
    try {
      return processTestsAndBuildProblems(heuristicContext, vcsChanges);

    } catch (HeuristicNotApplicableException ex) {
      LOGGER.debug("Heuristic \"BrokenFile\" is ignored as " + ex.getMessage() + ". Build: " +
                   LogUtil.describe(heuristicContext.getBuild()));
      return emptyResult;
    }
  }

  private HeuristicResult processTestsAndBuildProblems(@NotNull final HeuristicContext heuristicContext,
                                                       final List<SVcsModification> vcsChanges) {
    HeuristicResult result = new HeuristicResult();
    SBuild sBuild = heuristicContext.getBuild();

    for (STestRun sTestRun : heuristicContext.getTestRuns()) {
      String problemText = myProblemTextExtractor.getBuildProblemText(sTestRun);
      Responsibility responsibility = findResponsibleUser(vcsChanges, problemText, heuristicContext);
      if (responsibility != null) {
        result.addResponsibility(sTestRun, responsibility);
      }
    }

    for (BuildProblem buildProblem : heuristicContext.getBuildProblems()) {
      String buildProblemType = buildProblem.getBuildProblemData().getType();
      if (!BuildProblemsFilter.SUPPORTED_EVERYWHERE_TYPES.contains(buildProblemType)) {
        continue;
      }

      String problemText = myProblemTextExtractor.getBuildProblemText(buildProblem, sBuild);
      Responsibility responsibility = findResponsibleUser(vcsChanges, problemText, heuristicContext);
      if (responsibility != null) {
        result.addResponsibility(buildProblem, responsibility);
      }
    }

    return result;
  }

  @Nullable
  private Responsibility findResponsibleUser(List<SVcsModification> vcsChanges,
                                             String problemText,
                                             HeuristicContext heuristicContext) {
    Pair<User, String> foundBrokenFile = null;
    for (SVcsModification vcsChange : vcsChanges) {
      ModificationAnalyzerFactory.ModificationAnalyzer vcsChangeWrapped =
        myModificationAnalyzerFactory.getInstance(vcsChange);
      Pair<User, String> brokenFile =
        vcsChangeWrapped.findProblematicFile(problemText, heuristicContext.getUsersToIgnore());
      if (brokenFile == null) continue;

      ensureSameUsers(foundBrokenFile, brokenFile);
      foundBrokenFile = brokenFile;
    }

    if (foundBrokenFile == null) return null;

    String description =
      String.format("changed the suspicious file \"%s\" which probably broke the build", foundBrokenFile.second);
    return new Responsibility(foundBrokenFile.first, description);
  }

  private void ensureSameUsers(@Nullable Pair<User, String> foundBrokenFile,
                               @Nullable final Pair<User, String> broken) {
    if (foundBrokenFile != null &&
        broken != null &&
        !foundBrokenFile.first.equals(broken.first)) {
      throw new HeuristicNotApplicableException("There are more than one TeamCity users");
    }
  }
}