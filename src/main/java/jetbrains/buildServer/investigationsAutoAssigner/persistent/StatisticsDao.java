/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

package jetbrains.buildServer.investigationsAutoAssigner.persistent;

import com.google.gson.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jetbrains.buildServer.investigationsAutoAssigner.common.Constants;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StatisticsDao {

  private final Path myStatisticsPath;
  private final Path myPluginDataDirectory;
  private Gson myGson;

  public StatisticsDao(@NotNull final ServerPaths serverPaths) {
    GsonBuilder builder = new GsonBuilder();
    builder.registerTypeAdapter(Statistics.class, new StatisticsConverter());
    myGson = builder.create();
    myPluginDataDirectory = Paths.get(serverPaths.getPluginDataDirectory().getPath(),
                                      Constants.PLUGIN_DATA_DIR);
    myStatisticsPath = Paths.get(serverPaths.getPluginDataDirectory().getPath(),
                                 Constants.PLUGIN_DATA_DIR,
                                 Constants.STATISTICS_FILE_NAME);
  }

  @NotNull
  Statistics read() {
    if (!Files.exists(myStatisticsPath)) {
      return new Statistics();
    }

    try (BufferedReader reader = Files.newBufferedReader(myStatisticsPath)) {
      Statistics statistics = myGson.fromJson(reader, Statistics.class);
      if (!isValidStatisticsFile(statistics)) {
        statistics = new Statistics();
      }
      return statistics;
    } catch (IOException ex) {
      throw new RuntimeException("An error during reading statistics occurs", ex);
    }
  }

  private boolean isValidStatisticsFile(@Nullable Statistics statistics) {
    return statistics != null && Constants.STATISTICS_FILE_VERSION.equals(statistics.getVersion());
  }

  void write(@NotNull Statistics statistics) {
    try {
      if (!Files.exists(myPluginDataDirectory)) {
        Files.createDirectory(myPluginDataDirectory);
      }

      try (BufferedWriter writer = Files.newBufferedWriter(myStatisticsPath)) {
        myGson.toJson(statistics, writer);
      }
    } catch (IOException ex) {
      throw new RuntimeException("An error during writing statistics occurs", ex);
    }
  }

  private class StatisticsConverter implements JsonSerializer<Statistics>, JsonDeserializer<Statistics> {
    @Override
    public Statistics deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
      throws JsonParseException {
      JsonObject object = json.getAsJsonObject();
      return new Statistics(object.get("version").getAsString(),
                            Long.parseLong(object.get("assignedInvestigationsCount").getAsString()),
                            Long.parseLong(object.get("wrongInvestigationsCount").getAsString()),
                            Long.parseLong(object.get("shownButtonCount").getAsString()),
                            Long.parseLong(object.get("clickedButtonCount").getAsString()));
    }

    @Override
    public JsonElement serialize(final Statistics src, final Type typeOfSrc, final JsonSerializationContext context) {
      JsonObject object = new JsonObject();
      object.addProperty("version", src.getVersion());
      object.addProperty("assignedInvestigationsCount", src.getAssignedInvestigationsCount());
      object.addProperty("wrongInvestigationsCount", src.getWrongInvestigationsCount());
      object.addProperty("shownButtonCount", src.getShownButtonsCount());
      object.addProperty("clickedButtonCount", src.getClickedButtonsCount());
      return object;
    }
  }
}
