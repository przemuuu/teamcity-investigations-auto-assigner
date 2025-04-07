

package jetbrains.buildServer.investigationsAutoAssigner.representation;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.investigationsAutoAssigner.utils.TargetProjectFinder;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.responsibility.ResponsibilityEntryEx;
import jetbrains.buildServer.responsibility.TestNameResponsibilityFacade;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.STestManager;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModelEx;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

public class AssignInvestigationController extends BaseController {

  private final SecurityContext mySecurityContext;
  private final TestNameResponsibilityFacade myTestNameResponsibilityFacade;
  private final ProjectManager myProjectManager;
  private final STestManager myTestManager;
  private final UserModelEx myUserModel;
  private final TargetProjectFinder myTargetProjectFinder;

  public AssignInvestigationController(@NotNull final SBuildServer server,
                                       @NotNull final WebControllerManager controllerManager,
                                       @NotNull final TestNameResponsibilityFacade testNameResponsibilityFacade,
                                       @NotNull final UserModelEx userModelEx,
                                       @NotNull final STestManager sTestManager,
                                       @NotNull final SecurityContext securityContext,
                                       @NotNull final ProjectManager projectManager,
                                       @NotNull final TargetProjectFinder targetProjectFinder) {
    super(server);
    this.mySecurityContext = securityContext;
    this.myProjectManager = projectManager;
    this.myTargetProjectFinder = targetProjectFinder;
    controllerManager.registerController("/assignInvestigation.html", this);
    this.myTestNameResponsibilityFacade = testNameResponsibilityFacade;
    this.myUserModel = userModelEx;
    this.myTestManager = sTestManager;
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull final HttpServletRequest request,
                                  @NotNull final HttpServletResponse response) throws IllegalAccessException {
    final long userId;
    final long testNameId;
    final int buildId;
    try {
      userId = Long.parseLong(request.getParameter("userId"));
      testNameId = Long.parseLong(request.getParameter("testNameId"));
      buildId = Integer.parseInt(request.getParameter("buildId"));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Provided parameter is not valid", ex);
    }

    final String description = request.getParameter("description");
    if (description == null) throw new IllegalArgumentException("Description is not specified");


    AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();

    @Nullable
    User reporterUser = authorityHolder.getAssociatedUser();

    if (reporterUser == null) reporterUser = SessionUser.getUser(request);

    @Nullable final SBuild build = myServer.findBuildInstanceById(buildId);
    if (build == null) throw new IllegalStateException("Build was not found by provided buildId");

    @Nullable String projectId = build.getProjectId();
    if (projectId == null) throw new IllegalStateException("ProjectId is not specified on the build");
    final SProject project = myProjectManager.findProjectById(projectId);
    if (project == null) throw new IllegalStateException("Cannot find project by ID " + projectId);

    @Nullable
    User responsibleUser = myUserModel.findUserById(userId);
    if (responsibleUser == null) throw new IllegalStateException("Investigator was not found in the model by his id");

    @Nullable
    STest sTest = myTestManager.findTest(testNameId, projectId);
    if (sTest == null) throw new IllegalStateException("Test was not found by provided testNameId");


    if (!authorityHolder.getPermissionsGrantedForProject(projectId).contains(Permission.ASSIGN_INVESTIGATION)) {
      throw new IllegalAccessException("Current user doesn't have permissions to assign investigations");
    }

    if (reporterUser instanceof SUser) {
      SProject preferredProject =
        myTargetProjectFinder.getPreferredInvestigationProject(project, (SUser)reporterUser);
      if (preferredProject != null) {
        projectId = preferredProject.getProjectId();
      }
    }

    myTestNameResponsibilityFacade.setTestNameResponsibility(
      sTest.getName(), projectId,
      new ResponsibilityEntryEx(
        ResponsibilityEntry.State.TAKEN, responsibleUser, reporterUser, Dates.now(),
        description, ResponsibilityEntry.RemoveMethod.WHEN_FIXED));

    return null;
  }
}