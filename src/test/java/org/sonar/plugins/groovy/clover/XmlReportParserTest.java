/*
 * Sonar Clover Plugin
 * Copyright (C) 2008 SonarSource
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

package org.sonar.plugins.groovy.clover;

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.resources.*;
import org.sonar.api.test.IsMeasure;
import org.sonar.plugins.groovy.clover.XmlReportParser;
import org.sonar.test.TestUtils;

import java.io.File;
import java.net.URISyntaxException;
import java.text.ParseException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class XmlReportParserTest {

    private XmlReportParser reportParser;
    private SensorContext context;
    private File xmlFile;
    private Project project;

    @Before
    public void before() throws URISyntaxException {
        xmlFile = TestUtils.getResource(getClass(), "clover.xml");
        context = mock(SensorContext.class);
        project = mock(Project.class);
        reportParser = new XmlReportParser(project, context);
    }

    @Test
    public void collectProjectMeasures() throws Exception {
        reportParser.collect(xmlFile);
        verify(context).saveMeasure(null, CoreMetrics.COVERAGE, 5.0); // coveredelements / elements

        verify(context).saveMeasure(null, CoreMetrics.LINE_COVERAGE, 6.63); // covered methods + covered statements / methods + statements
        verify(context).saveMeasure(null, CoreMetrics.LINES_TO_COVER, 196.0);
        verify(context).saveMeasure(null, CoreMetrics.UNCOVERED_LINES, 183.0); // covered methods + covered statements

        verify(context).saveMeasure(null, CoreMetrics.BRANCH_COVERAGE, 0.0); // covered conditionals / conditionals
        verify(context).saveMeasure(null, CoreMetrics.CONDITIONS_TO_COVER, 64.0); // covered_conditionals
        verify(context).saveMeasure(null, CoreMetrics.UNCOVERED_CONDITIONS, 64.0);
    }

    @Test
    public void collectPackageMeasures() throws ParseException {
        reportParser.collect(xmlFile);
        final Directory pac = new Directory("org/sonar/samples");
        verify(context).saveMeasure(pac, CoreMetrics.COVERAGE, 28.89);

        // lines
        verify(context).saveMeasure(pac, CoreMetrics.LINE_COVERAGE, 28.89);
        verify(context).saveMeasure(pac, CoreMetrics.LINES_TO_COVER, 45.0);
        verify(context).saveMeasure(pac, CoreMetrics.UNCOVERED_LINES, 32.0);

        // no conditions
        verify(context, never()).saveMeasure(eq(pac), eq(CoreMetrics.BRANCH_COVERAGE), anyDouble());
        verify(context, never()).saveMeasure(eq(pac), eq(CoreMetrics.CONDITIONS_TO_COVER), anyDouble());
        verify(context, never()).saveMeasure(eq(pac), eq(CoreMetrics.UNCOVERED_CONDITIONS), anyDouble());
    }

    @Test
    public void parseClaver232Format() throws ParseException, URISyntaxException {
        reportParser.collect(TestUtils.getResource(getClass(), "clover_2_3_2.xml"));
        verify(context).saveMeasure(new Directory("org/sonar/squid/sensors"), CoreMetrics.COVERAGE, 94.87);
    }

    @Test
    public void collectFileMeasures() throws Exception {
        reportParser.collect(xmlFile);

        final org.sonar.api.resources.File file = new org.sonar.api.resources.File("org/sonar/samples/", "ClassUnderTest.groovy");
        verify(context).saveMeasure(eq(file), argThat(new IsMeasure(CoreMetrics.LINES_TO_COVER, 5.0)));
        verify(context).saveMeasure(eq(file), argThat(new IsMeasure(CoreMetrics.UNCOVERED_LINES, 0.0)));
        verify(context).saveMeasure(eq(file), argThat(new IsMeasure(CoreMetrics.COVERAGE_LINE_HITS_DATA, "4=1;5=1;6=2;8=1;9=1")));
    }

    @Test
    public void collectFileHitsData() throws Exception {
        final org.sonar.api.resources.File file = new org.sonar.api.resources.File("org/sonar/samples/", "ClassUnderTest.groovy");

        reportParser.collect(xmlFile);
        verify(context).saveMeasure(eq(file), argThat(new IsMeasure(CoreMetrics.COVERAGE_LINE_HITS_DATA, "4=1;5=1;6=2;8=1;9=1")));
    }

    @Test
    public void clover1FileNameContainsPath() {
        XmlReportParser reportParser = new XmlReportParser(project, context);
        assertEquals("SampleClass.groovy", reportParser.extractClassName("C:\\src\\main\\java\\org\\sonar\\samples\\SampleClass.groovy"));
        assertEquals("SampleClass.groovy", reportParser.extractClassName("/src/main/java/org/sonar/samples/SampleClass.groovy"));
    }

    @Test
    public void clover2FileNameDoesNotContainPath() {
        XmlReportParser reportParser = new XmlReportParser(project, context);
        assertEquals("SampleClass.groovy", reportParser.extractClassName("SampleClass.groovy"));
    }

    @Test
    public void coverageShouldBeZeroWhenNoElements() throws URISyntaxException {
        File xmlFile = TestUtils.getResource(getClass(), "coverageShouldBeZeroWhenNoElements/clover.xml");
        context = mock(SensorContext.class);
        XmlReportParser reportParser = new XmlReportParser(project, context);
        reportParser.collect(xmlFile);
        verify(context, never()).saveMeasure((Resource) anyObject(), eq(CoreMetrics.COVERAGE), anyDouble());
        verify(context, never()).saveMeasure((Resource) anyObject(), eq(CoreMetrics.LINE_COVERAGE), anyDouble());
        verify(context, never()).saveMeasure((Resource) anyObject(), eq(CoreMetrics.BRANCH_COVERAGE), anyDouble());
    }
}
