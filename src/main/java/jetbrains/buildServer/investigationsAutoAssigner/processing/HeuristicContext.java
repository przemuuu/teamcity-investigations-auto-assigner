

package jetbrains.buildServer.investigationsAutoAssigner.processing;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.vcs.SelectPrevBuildPolicy;
import org.jetbrains.annotations.NotNull;

public final class HeuristicContext {
  private final SProject mySProject;
  private final List<BuildProblem> myBuildProblems;
  private final List<STestRun> mySTestRuns;
  private final SBuild mySBuild;
  private final Set<String> myUsersToIgnore;
  private Set<Long> myCommittersIds = null;

  public HeuristicContext(SBuild sBuild,
                          SProject sProject,
                          List<BuildProblem> buildProblems,
                          List<STestRun> sTestRuns,
                          @NotNull Set<String> usernameBlackList) {
    this.mySBuild = sBuild;
    this.mySProject = sProject;
    this.myBuildProblems = buildProblems;
    this.mySTestRuns = sTestRuns;
    this.myUsersToIgnore = usernameBlackList;
  }

  @NotNull
  public SBuild getBuild() {
    return mySBuild;
  }

  @NotNull
  public SProject getProject() {
    return mySProject;
  }

  public List<BuildProblem> getBuildProblems() {
    return myBuildProblems;
  }

  public List<STestRun> getTestRuns() {
    return mySTestRuns;
  }

  @NotNull
  public Set<String> getUsersToIgnore() {
    return myUsersToIgnore;
  }

  @NotNull
  public Set<Long> getCommittersIds() {
    if (myCommittersIds == null) myCommittersIds = calculateCommitersIds(mySBuild);
    return myCommittersIds;
  }

  private static Set<Long> calculateCommitersIds(SBuild sBuild) {
    return sBuild.getCommitters(SelectPrevBuildPolicy.SINCE_LAST_BUILD)
                 .getUsers()
                 .stream()
                 .map(User::getId)
                 .collect(Collectors.toSet());
  }
}