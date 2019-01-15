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

import jetbrains.buildServer.investigationsAutoAssigner.common.Constants;

class Statistics {

  private final String version;
  private int shownButtonsCount;
  private int clickedButtonsCount;
  private int assignedInvestigationsCount;
  private int wrongInvestigationsCount;

  public String getVersion() {
    return version;
  }

  int getShownButtonsCount() {
    return shownButtonsCount;
  }

  int getClickedButtonsCount() {
    return clickedButtonsCount;
  }

  int getAssignedInvestigationsCount() {
    return assignedInvestigationsCount;
  }

  int getWrongInvestigationsCount() {
    return wrongInvestigationsCount;
  }

  Statistics() {
    version = Constants.STATISTICS_FILE_VERSION;
  }

  Statistics(final String version,
             final int shownButtonsCount,
             final int clickedButtonsCount,
             final int assignedInvestigationsCount,
             final int wrongInvestigationsCount) {
    this.version = version;
    this.shownButtonsCount = shownButtonsCount;
    this.clickedButtonsCount = clickedButtonsCount;
    this.assignedInvestigationsCount = assignedInvestigationsCount;
    this.wrongInvestigationsCount = wrongInvestigationsCount;
  }

  void increaseShownButtonsCounter() {
    shownButtonsCount++;
  }

  void increaseClickedButtonsCounter() {
    clickedButtonsCount++;
  }

  void increaseAssignedInvestigationsCounter(final int count) {
    assignedInvestigationsCount += count;
  }

  void increaseWrongInvestigationsCounter(final int count) {
    wrongInvestigationsCount += count;
  }
}
