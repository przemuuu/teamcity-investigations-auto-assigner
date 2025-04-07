

package jetbrains.buildServer.investigationsAutoAssigner.processing;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.investigationsAutoAssigner.common.DefaultUserResponsibility;
import jetbrains.buildServer.investigationsAutoAssigner.common.HeuristicResult;
import jetbrains.buildServer.investigationsAutoAssigner.common.Responsibility;
import jetbrains.buildServer.investigationsAutoAssigner.persistent.StatisticsReporter;
import jetbrains.buildServer.investigationsAutoAssigner.utils.TargetProjectFinder;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.responsibility.TestNameResponsibilityFacade;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.tests.TestName;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserSet;
import jetbrains.buildServer.users.impl.UserImpl;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@Test
public class FailedTestAssignerTest extends BaseTestCase {

  private TestNameResponsibilityFacade myTestNameResponsibilityFacade;
  private STestRun mySTestRun1;
  private HeuristicResult myHeuristicResult;
  private User myUser1;
  private SProject mySProject;
  private STestRun mySTestRun2;
  private User myUser2;
  private FailedTestAssigner myTestedFailedTestAssigner;
  private SBuild mySBuild;
  private TargetProjectFinder myTargetProjectFinderMock;

  @BeforeMethod
  @Override
  protected void setUp() {
    myTestNameResponsibilityFacade = Mockito.mock(TestNameResponsibilityFacade.class);
    WebLinks webLinks = Mockito.mock(WebLinks.class);
    mySTestRun1 = Mockito.mock(STestRun.class);
    STest sTest1 = Mockito.mock(STest.class);
    TestName testName1 = Mockito.mock(TestName.class);
    when(mySTestRun1.getTest()).thenReturn(sTest1);
    when(mySTestRun1.getTestRunId()).thenReturn(0);
    when(sTest1.getName()).thenReturn(testName1);

    mySTestRun2 = Mockito.mock(STestRun.class);
    STest sTest2 = Mockito.mock(STest.class);
    TestName testName2 = Mockito.mock(TestName.class);
    when(mySTestRun2.getTest()).thenReturn(sTest2);
    when(mySTestRun1.getTestRunId()).thenReturn(1);
    when(sTest2.getName()).thenReturn(testName2);

    mySProject = Mockito.mock(SProject.class);
    mySBuild = Mockito.mock(SBuild.class);
    myUser1 = Mockito.mock(UserImpl.class);
    when(myUser1.getUsername()).thenReturn("user1");
    when(myUser1.getId()).thenReturn(1L);
    myUser2 = Mockito.mock(UserImpl.class);
    when(myUser2.getUsername()).thenReturn("user2");
    when(myUser2.getId()).thenReturn(2L);
    myHeuristicResult = new HeuristicResult();

    UserSet userSetMock = Mockito.mock(UserSet.class);
    when(userSetMock.getUsers()).thenReturn(new HashSet<>(Arrays.asList(myUser1, myUser2)));
    when(mySBuild.getCommitters(any())).thenReturn(userSetMock);
    when(mySBuild.getParametersProvider()).thenReturn(Mockito.mock(ParametersProvider.class));

    myTargetProjectFinderMock = Mockito.mock(TargetProjectFinder.class);
    myTestedFailedTestAssigner = new FailedTestAssigner(myTestNameResponsibilityFacade,
                                                        webLinks,
                                                        Mockito.mock(StatisticsReporter.class),
                                                        myTargetProjectFinderMock);
  }

  public void Test_NoTestRuns() {
    myTestedFailedTestAssigner.assign(myHeuristicResult, mySProject, mySBuild, Collections.emptyList());

    Mockito.verify(myTestNameResponsibilityFacade, Mockito.never()).setTestNameResponsibility(anyList(), any(), any());
  }

  public void Test_NoResponsibilitiesFound() {
    myTestedFailedTestAssigner.assign(myHeuristicResult, mySProject, mySBuild, Collections.singletonList(mySTestRun1));

    Mockito.verify(myTestNameResponsibilityFacade, Mockito.never()).setTestNameResponsibility(anyList(), any(), any());
  }

  public void Test_OneResponsibilityFound() {
    Responsibility putResponsibility = new Responsibility(myUser1, "any description");
    myHeuristicResult.addResponsibility(mySTestRun1, putResponsibility);

    myTestedFailedTestAssigner.assign(myHeuristicResult, mySProject, mySBuild, Collections.singletonList(mySTestRun1));

    Mockito.verify(myTestNameResponsibilityFacade, only()).setTestNameResponsibility(anyList(), any(), any());
  }

  public void Test_SetResponsibilityDifferentProject() {
    Responsibility putResponsibility = new Responsibility(myUser1, "any description");
    myHeuristicResult.addResponsibility(mySTestRun1, putResponsibility);

    final SProject project2 = Mockito.mock(SProject.class);
    when(project2.getProjectId()).thenReturn("project2");
    when(myTargetProjectFinderMock.getPreferredInvestigationProject(mySProject, null)).thenReturn(project2);
    
    myTestedFailedTestAssigner.assign(myHeuristicResult, mySProject, mySBuild, Collections.singletonList(mySTestRun1));

    Mockito.verify(myTestNameResponsibilityFacade, only()).setTestNameResponsibility(anyList(), Mockito.eq("project2"), any());
  }

  public void Test_TwoSameResponsibilitiesFound() {
    Responsibility putResponsibility = new Responsibility(myUser1, "any description");
    Responsibility putResponsibility2 = new Responsibility(myUser1, "any description");
    myHeuristicResult.addResponsibility(mySTestRun1, putResponsibility);
    myHeuristicResult.addResponsibility(mySTestRun2, putResponsibility2);

    myTestedFailedTestAssigner.assign(myHeuristicResult, mySProject, mySBuild, Arrays.asList(mySTestRun1, mySTestRun2));

    Mockito.verify(myTestNameResponsibilityFacade, only()).setTestNameResponsibility(anyList(), any(), any());
  }

  public void Test_TwoDifferentResponsibilitiesFound() {
    myHeuristicResult.addResponsibility(mySTestRun1, new Responsibility(myUser1, "any description"));
    myHeuristicResult.addResponsibility(mySTestRun2, new Responsibility(myUser2, "any description"));
    myTestedFailedTestAssigner.assign(myHeuristicResult, mySProject, mySBuild, Arrays.asList(mySTestRun1, mySTestRun2));
    Mockito.verify(myTestNameResponsibilityFacade, times(2)).setTestNameResponsibility(anyList(), any(), any());

    myHeuristicResult = new HeuristicResult();
    clearInvocations(myTestNameResponsibilityFacade);
    myHeuristicResult.addResponsibility(mySTestRun1, new Responsibility(myUser1, "any description"));
    myHeuristicResult.addResponsibility(mySTestRun2, new Responsibility(myUser1, "any description 2"));
    myTestedFailedTestAssigner.assign(myHeuristicResult, mySProject, mySBuild, Arrays.asList(mySTestRun1, mySTestRun2));
    Mockito.verify(myTestNameResponsibilityFacade, times(2)).setTestNameResponsibility(anyList(), any(), any());

    myHeuristicResult = new HeuristicResult();
    clearInvocations(myTestNameResponsibilityFacade);
    myHeuristicResult.addResponsibility(mySTestRun1, new Responsibility(myUser1, "any description"));
    myHeuristicResult.addResponsibility(mySTestRun2, new Responsibility(myUser2, "any description 2"));
    myTestedFailedTestAssigner.assign(myHeuristicResult, mySProject, mySBuild, Arrays.asList(mySTestRun1, mySTestRun2));
    Mockito.verify(myTestNameResponsibilityFacade, times(2)).setTestNameResponsibility(anyList(), any(), any());
  }

  public void Test_FoundNoCommittersOneDefaultResponsibility() {
    UserSet userSetMock = Mockito.mock(UserSet.class);
    when(userSetMock.getUsers()).thenReturn(new HashSet<>(Collections.singletonList(myUser2)));
    when(mySBuild.getCommitters(any())).thenReturn(userSetMock);
    Responsibility putResponsibility = new DefaultUserResponsibility(myUser1);
    myHeuristicResult.addResponsibility(mySTestRun1, putResponsibility);

    myTestedFailedTestAssigner.assign(myHeuristicResult, mySProject, mySBuild, Collections.singletonList(mySTestRun1));

    Mockito.verify(myTestNameResponsibilityFacade, only()).setTestNameResponsibility(anyList(), any(), any());
  }
}