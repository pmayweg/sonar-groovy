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

package org.sonar.plugins.groovy.clover;

import org.apache.commons.lang.StringUtils;
import org.codehaus.staxmate.in.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.CoverageMeasuresBuilder;
import org.sonar.api.measures.Measure;
import org.sonar.api.resources.Directory;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.utils.ParsingUtils;
import org.sonar.api.utils.StaxParser;
import org.sonar.api.utils.XmlParserException;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.text.ParseException;

import static org.sonar.api.utils.ParsingUtils.scaleValue;

public class XmlReportParser {

    private static final Logger LOG = LoggerFactory.getLogger(XmlReportParser.class);
    private Project project;
    private SensorContext context;
    final CoverageMeasuresBuilder fileMeasuresBuilder = CoverageMeasuresBuilder.create();

    public XmlReportParser(Project project, SensorContext context) {
        this.project = project;
        this.context = context;
    }

    private boolean reportExists(File report) {
        return report != null && report.exists() && report.isFile();
    }

    protected void collect(File xmlFile) {
        try {
            if (reportExists(xmlFile)) {
                LOG.info("Parsing " + xmlFile.getCanonicalPath());
                StaxParser parser = new StaxParser(new StaxParser.XmlStreamHandler() {
                    public void stream(SMHierarchicCursor rootCursor) throws XMLStreamException {
                        try {
                            collectProjectMeasures(rootCursor.advance());
                        } catch (ParseException e) {
                            throw new XMLStreamException(e);
                        }
                    }
                });
                parser.parse(xmlFile);
            }
        } catch (Exception e) {
            throw new XmlParserException(e);
        }
    }

    private void collectProjectMeasures(SMInputCursor rootCursor) throws ParseException, XMLStreamException {
        SMInputCursor projectCursor = rootCursor.descendantElementCursor("project");
        SMInputCursor projectChildrenCursor = projectCursor.advance().childElementCursor();
        projectChildrenCursor.setFilter(new SimpleFilter(SMEvent.START_ELEMENT));

        SMInputCursor metricsCursor = projectChildrenCursor.advance();
        analyseMetricsNode(null, metricsCursor);
        collectPackageMeasures(projectChildrenCursor);
    }

    private void analyseMetricsNode(Resource resource, SMInputCursor metricsCursor) throws ParseException, XMLStreamException {
        int elements = (int) ParsingUtils.parseNumber(metricsCursor.getAttrValue("elements"));
        if (elements == 0) {
            return;
        }

        int statements = (int) ParsingUtils.parseNumber(metricsCursor.getAttrValue("statements"));
        int methods = (int) ParsingUtils.parseNumber(metricsCursor.getAttrValue("methods"));
        int conditionals = (int) ParsingUtils.parseNumber(metricsCursor.getAttrValue("conditionals"));
        int coveredElements = (int) ParsingUtils.parseNumber(metricsCursor.getAttrValue("coveredelements"));
        int coveredStatements = (int) ParsingUtils.parseNumber(metricsCursor.getAttrValue("coveredstatements"));
        int coveredMethods = (int) ParsingUtils.parseNumber(metricsCursor.getAttrValue("coveredmethods"));
        int coveredConditionals = (int) ParsingUtils.parseNumber(metricsCursor.getAttrValue("coveredconditionals"));

        context.saveMeasure(resource, CoreMetrics.COVERAGE, calculateCoverage(coveredElements, elements));

        context.saveMeasure(resource, CoreMetrics.LINE_COVERAGE, calculateCoverage(coveredMethods + coveredStatements, methods + statements));
        context.saveMeasure(resource, CoreMetrics.LINES_TO_COVER, (double) (statements + methods));
        context.saveMeasure(resource, CoreMetrics.UNCOVERED_LINES, (double) (statements + methods - coveredStatements - coveredMethods));

        if (conditionals > 0) {
            context.saveMeasure(resource, CoreMetrics.BRANCH_COVERAGE, calculateCoverage(coveredConditionals, conditionals));
            context.saveMeasure(resource, CoreMetrics.CONDITIONS_TO_COVER, (double) (conditionals));
            context.saveMeasure(resource, CoreMetrics.UNCOVERED_CONDITIONS, (double) (conditionals - coveredConditionals));
        }
    }

    private double calculateCoverage(int coveredElements, int elements) {
        if (elements > 0) {
            return scaleValue(100.0 * ((double) coveredElements / (double) elements));
        }
        return 0.0;
    }

    private void collectPackageMeasures(SMInputCursor packCursor) throws ParseException, XMLStreamException {
        while (packCursor.getNext() != null) {
            String packageName = packCursor.getAttrValue("name");
            Directory pack = new Directory(packageName.replace(".", "/"));
            SMInputCursor packChildrenCursor = packCursor.descendantElementCursor();
            packChildrenCursor.setFilter(new SimpleFilter(SMEvent.START_ELEMENT));
            SMInputCursor metricsCursor = packChildrenCursor.advance();
            analyseMetricsNode(pack, metricsCursor);
            collectFileMeasures(packChildrenCursor, pack);
        }
    }

    private void collectFileMeasures(SMInputCursor fileCursor, Directory pack) throws ParseException, XMLStreamException {
        fileCursor.setFilter(SMFilterFactory.getElementOnlyFilter("file"));
        while (fileCursor.getNext() != null) {
            if (fileCursor.asEvent().isStartElement()) {
                String classKey = extractClassName(fileCursor.getAttrValue("name"));
                if (classKey != null) {
                    SMInputCursor fileChildrenCursor = fileCursor.childCursor(new SimpleFilter(SMEvent.START_ELEMENT));
                    // cursor should be on the metrics element
                    if (canBeIncludedInFileMetrics(fileChildrenCursor)) {
                        // cursor should be now on the line cursor
                        org.sonar.api.resources.File resource = new org.sonar.api.resources.File(pack.getKey(), classKey);
                        saveHitsData(resource, fileChildrenCursor);
                    }
                }
            }
        }
    }

    private void saveHitsData(Resource resource, SMInputCursor lineCursor) throws ParseException, XMLStreamException {
        fileMeasuresBuilder.reset();

        while (lineCursor.getNext() != null) {
            // skip class elements on format 2_3_2
            if (lineCursor.getLocalName().equals("class")) {
                continue;
            }
            final int lineId = Integer.parseInt(lineCursor.getAttrValue("num"));
            String count = lineCursor.getAttrValue("count");
            if (StringUtils.isNotBlank(count)) {
                fileMeasuresBuilder.setHits(lineId, Integer.parseInt(count));

            } else {
                int trueCount = (int) ParsingUtils.parseNumber(lineCursor.getAttrValue("truecount"));
                int falseCount = (int) ParsingUtils.parseNumber(lineCursor.getAttrValue("falsecount"));
                int coveredConditions = 0;
                if (trueCount > 0) {
                    coveredConditions++;
                }
                if (falseCount > 0) {
                    coveredConditions++;
                }
                fileMeasuresBuilder.setConditions(lineId, 2, coveredConditions);
            }
        }
        for (Measure measure : fileMeasuresBuilder.createMeasures()) {
            context.saveMeasure(resource, measure);
        }
    }

    private boolean canBeIncludedInFileMetrics(SMInputCursor metricsCursor) throws ParseException, XMLStreamException {
        while (metricsCursor.getNext() != null && metricsCursor.getLocalName().equals("class")) {
            // skip class elements on 1.x xml format
        }
        return ParsingUtils.parseNumber(metricsCursor.getAttrValue("elements")) > 0;
    }

    protected String extractClassName(String filename) {
        if (filename != null) {
            filename = StringUtils.replaceChars(filename, '\\', '/');
            if (filename.indexOf('/') >= 0) {
                filename = StringUtils.substringAfterLast(filename, "/");
            }
        }
        return filename;
    }
}