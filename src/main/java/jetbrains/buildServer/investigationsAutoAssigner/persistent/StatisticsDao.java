/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jetbrains.buildServer.investigationsAutoAssigner.common.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class StatisticsDao {

  private final Path myStatisticsPath;
  private final Path myPluginDataDirectory;
  private final Gson myGson;
  private Statistics myStatisticsOnDisc;

  StatisticsDao(@NotNull final Path pluginDataDir) {
    myGson = new Gson();
    myPluginDataDirectory = pluginDataDir.resolve(Constants.PLUGIN_DATA_DIR);
    myStatisticsPath = myPluginDataDirectory.resolve(Constants.STATISTICS_FILE_NAME);
    myStatisticsOnDisc = new Statistics();
  }

  @NotNull
  Statistics read() {
    if (!Files.exists(myStatisticsPath)) {
      myStatisticsOnDisc = new Statistics();
      return myStatisticsOnDisc.clone();
    }

    try (BufferedReader reader = Files.newBufferedReader(myStatisticsPath)) {
      myStatisticsOnDisc = parseStatistics(reader);
      return myStatisticsOnDisc.clone();
    } catch (IOException ex) {
      throw new RuntimeException("An error during reading statistics occurs", ex);
    }
  }

  @NotNull
  private Statistics parseStatistics(final BufferedReader reader) {
    Statistics statistics;

    try {
      statistics = myGson.fromJson(reader, Statistics.class);

      if (!isValidStatisticsFile(statistics)) {
        statistics = new Statistics();
      }
    } catch (JsonParseException err) {
      statistics = new Statistics();
    }

    return statistics;
  }

  private boolean isValidStatisticsFile(@Nullable Statistics statistics) {
    return statistics != null && Constants.STATISTICS_FILE_VERSION.equals(statistics.getVersion());
  }

  void write(@NotNull Statistics statistics) {
    if (myStatisticsOnDisc.equals(statistics)) {
      return;
    }

    try {
      if (!Files.exists(myPluginDataDirectory)) {
        Files.createDirectory(myPluginDataDirectory);
      }

      try (BufferedWriter writer = Files.newBufferedWriter(myStatisticsPath)) {
        myGson.toJson(statistics, writer);
      }

      myStatisticsOnDisc = statistics;
    } catch (IOException ex) {
      throw new RuntimeException("An error during writing statistics occurs", ex);
    }
  }
}
