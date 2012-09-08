/*
 * Sonar Groovy Plugin
 * Copyright (C) 2010 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */

package org.sonar.plugins.groovy.cobertura;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.CoverageExtension;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.plugins.cobertura.api.AbstractCoberturaParser;
import org.sonar.plugins.cobertura.api.CoberturaUtils;
import org.sonar.plugins.groovy.foundation.Groovy;

import java.io.File;

public class CoberturaSensor implements Sensor, CoverageExtension {

  private static final Logger LOG = LoggerFactory.getLogger(CoberturaSensor.class);

  public boolean shouldExecuteOnProject(Project project) {
    return project.getAnalysisType().isDynamic(true) && Groovy.KEY.equals(project.getLanguageKey());
  }

  public void analyse(Project project, SensorContext context) {
      String path = (String) project.getProperty(CoreProperties.COBERTURA_REPORT_PATH_PROPERTY);
      if (path == null) {
        // wasn't configured - skip
        return;
      }
      File report = project.getFileSystem().resolvePath(path);
      if (!report.exists() || !report.isFile()) {
        LOG.warn("Cobertura report not found at {}", report);
        return;
      }
      parseReport(report, context);
  }

  protected void parseReport(File xmlFile, final SensorContext context) {
    LOG.info("parsing {}", xmlFile);
    new GroovyCoberturaParser().parseReport(xmlFile, context);
  }

  private static final class GroovyCoberturaParser extends AbstractCoberturaParser {
    @Override
    protected Resource<?> getResource(String fileName) {
      fileName = fileName.replace(".", "/") + ".groovy";
      return new org.sonar.api.resources.File(fileName);
    }
  };

  @Override
  public String toString() {
    return "Groovy CoberturaSensor";
  }

}
