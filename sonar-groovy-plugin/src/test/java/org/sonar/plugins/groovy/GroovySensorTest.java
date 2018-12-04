/*
 * Sonar Groovy Plugin
 * Copyright (C) 2010-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.groovy;

import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.sonar.api.batch.fs.internal.*;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.Settings;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.plugins.groovy.foundation.Groovy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GroovySensorTest {

  private Settings settings = new MapSettings();

  private FileLinesContextFactory fileLinesContextFactory = mock(FileLinesContextFactory.class);

  private DefaultFileSystem fileSystem = new DefaultFileSystem(new File("."));

  private GroovySensor sensor = new GroovySensor(settings, fileLinesContextFactory, fileSystem);

  @Test
  public void do_nothing_when_no_groovy_file() {
    SensorContextTester context = SensorContextTester.create(new File("."));
    context = Mockito.spy(context);

    sensor = new GroovySensor(settings, fileLinesContextFactory, context.fileSystem());
    sensor.execute(context);

    Mockito.verify(context, Mockito.never()).newHighlighting();
  }

  @Test
  public void compute_metrics() throws IOException {
    testMetrics(false, 5);
  }

  @Test
  public void compute_metrics_ignoring_header_comment() throws IOException {
    testMetrics(true, 3);
  }

  private void testMetrics(boolean headerComment, int expectedCommentMetric) throws IOException {
    settings.appendProperty(GroovyPlugin.IGNORE_HEADER_COMMENTS, "" + headerComment);

    SensorContextTester context = SensorContextTester.create(new File("src/test/resources"));

    File sourceDir = new File("src/test/resources/org/sonar/plugins/groovy/gmetrics");
    File sourceFile = new File(sourceDir, "Greeting.groovy");

    fileSystem = context.fileSystem();
    fileSystem.add(new DefaultInputDir("", sourceDir.getPath()));

    Metadata metadata = new FileMetadata().readMetadata(
            Files.newBufferedReader(sourceFile.toPath(), StandardCharsets.UTF_8)
    );
    DefaultInputFile groovyFile = new DefaultInputFile(getIndexedFile(sourceFile.getPath()), f -> f.setMetadata(metadata));
    fileSystem.add(groovyFile);

    FileLinesContext fileLinesContext = mock(FileLinesContext.class);
    when(fileLinesContextFactory.createFor(any(DefaultInputFile.class))).thenReturn(fileLinesContext);

    sensor = new GroovySensor(settings, fileLinesContextFactory, fileSystem);
    sensor.execute(context);

    String key = groovyFile.key();
    assertThat(context.measure(key, CoreMetrics.LINES).value()).isEqualTo(33);
    assertThat(context.measure(key, CoreMetrics.NCLOC).value()).isEqualTo(17);
    assertThat(context.measure(key, CoreMetrics.COMMENT_LINES).value()).isEqualTo(expectedCommentMetric);

    // 11 times for comment because we register comment even when ignoring header comment
    Mockito.verify(fileLinesContext, Mockito.times(11)).setIntValue(Mockito.eq(CoreMetrics.COMMENT_LINES_DATA_KEY), Matchers.anyInt(), Mockito.eq(1));
    Mockito.verify(fileLinesContext, Mockito.times(17)).setIntValue(Mockito.eq(CoreMetrics.NCLOC_DATA_KEY), Matchers.anyInt(), Mockito.eq(1));
    Mockito.verify(fileLinesContext).setIntValue(CoreMetrics.COMMENT_LINES_DATA_KEY, 18, 1);
    Mockito.verify(fileLinesContext).setIntValue(CoreMetrics.NCLOC_DATA_KEY, 18, 1);
    // Only "Greeting.groovy" is part of the file system.
    Mockito.verify(fileLinesContext, Mockito.times(1)).save();
  }

  @Test
  public void test_toString() {
    assertThat(sensor.toString()).isEqualTo("GroovySensor");
  }

  @Test
  public void test_description() {
    DefaultSensorDescriptor defaultSensorDescriptor = new DefaultSensorDescriptor();
    sensor.describe(defaultSensorDescriptor);
    assertThat(defaultSensorDescriptor.languages()).containsOnly(Groovy.KEY);
  }

  private static DefaultIndexedFile getIndexedFile(String path) {
    return new DefaultIndexedFile("", Paths.get("."), path, Groovy.KEY);
  }

}
