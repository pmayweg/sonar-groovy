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
package org.sonar.plugins.groovy.jacoco;

import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.internal.analysis.SourceFileCoverageImpl;
import org.junit.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.sensor.coverage.CoverageType;
import org.sonar.api.config.Settings;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.plugins.groovy.GroovyPlugin;
import org.sonar.plugins.groovy.TestUtils;
import org.sonar.plugins.groovy.foundation.Groovy;
import org.sonar.plugins.groovy.foundation.GroovyFileSystem;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class JaCoCoAnalyzerTest {

    @Test
    public void testFindingFileInPackage() throws IOException {
        testFindingFile("example");
    }

    @Test
    public void testFindingFileDefaultPackage() throws IOException {
        testFindingFile("");
    }

    private void testFindingFile(String packageName) throws IOException {
        TestAnalyzer analyzer = prepareTestAnalyzer(packageName);
        ISourceFileCoverage fileCoverage = new SourceFileCoverageImpl("Hello.groovy", packageName);
        InputFile inputFile = analyzer.getInputFile(fileCoverage);
        assertNotNull(String.format("{%s; %s} input file is not found", "Hello.groovy", packageName), inputFile);
    }

    private TestAnalyzer prepareTestAnalyzer(String packageName) throws IOException {

        File outputDir = TestUtils.getResource("/org/sonar/plugins/groovy/jacoco/");
        File jacocoExecutionData = new File(outputDir, "jacoco-ut.exec");

        Settings settings = new Settings();
        settings.setProperty(GroovyPlugin.SONAR_GROOVY_BINARIES, ".");

        DefaultFileSystem fileSystem = new DefaultFileSystem(jacocoExecutionData.getParentFile());

        String filePath;
        if ("".equals(packageName)) {
            filePath = "Hello.groovy";
        } else {
            filePath = packageName + "/" + "Hello.groovy";
        }

        DefaultInputFile inputFile = new DefaultInputFile("", filePath)
                .setLanguage(Groovy.KEY)
                .setType(Type.MAIN);
        inputFile.setLines(50);
        fileSystem.add(inputFile);

        PathResolver pathResolver = mock(PathResolver.class);

        return new TestAnalyzer(new GroovyFileSystem(fileSystem), pathResolver, settings, jacocoExecutionData.getPath());

    }

    class TestAnalyzer extends AbstractAnalyzer {

        private final String reportPath;

        TestAnalyzer(GroovyFileSystem fileSystem, PathResolver pathResolver, Settings settings, String reportPath) {
            super(fileSystem, pathResolver, settings);
            this.reportPath = reportPath;
        }

        @Override
        protected String getReportPath() {
            return reportPath;
        }

        @Override
        protected CoverageType coverageType() {
            return CoverageType.UNIT;
        }

    }

}
