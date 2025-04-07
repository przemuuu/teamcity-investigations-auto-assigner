

package jetbrains.buildServer.investigationsAutoAssigner.representation;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.investigationsAutoAssigner.common.Constants;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.PluginUIContext;
import jetbrains.buildServer.web.openapi.SimplePageExtension;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

public class SakuraTestDetailsController extends BaseController {
  private final SimplePageExtension myExtension;
  private final PluginDescriptor myPluginDescriptor;

  public SakuraTestDetailsController(@NotNull final PagePlaces pagePlaces,
                                     @NotNull final PluginDescriptor descriptor,
                                     @NotNull final WebControllerManager controllerManager) {
    String url = "/sakuraTestDetailsExtension.html";
    this.myExtension = new SimplePageExtension(pagePlaces,
          new PlaceId("SAKURA_TEST_DETAILS_ACTIONS"),
          Constants.BUILD_FEATURE_TYPE,
          url);
    this.myPluginDescriptor = descriptor;
    controllerManager.registerController(url, this);
  }

  public void register() {
    myExtension.register();
  }

  public void unregister() {
    myExtension.unregister();
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
    final ModelAndView mv = new ModelAndView(myPluginDescriptor.getPluginResourcesPath("testDetailsExtension.jsp"));
    final Map<String, Object> model = mv.getModel();
    PluginUIContext pluginUIContext = PluginUIContext.getFromRequest(request);
    model.put("buildId", pluginUIContext.getBuildId());
    model.put("testId", pluginUIContext.getTestRunId());
    return mv;
  }
}