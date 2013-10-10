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

package org.sonar.plugins.groovy.foundation;

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;

import com.google.common.base.CharMatcher;
import com.google.common.io.Files;

import org.sonar.api.batch.AbstractSourceImporter;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.resources.ProjectFileSystem;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.resources.Resource;
import org.sonar.api.utils.SonarException;
import org.sonar.plugins.groovy.jacoco.GroovyFile;

public class GroovySourceImporter extends AbstractSourceImporter {

    public GroovySourceImporter(Groovy groovy) {
        super(groovy);
    }

    protected void analyse(ProjectFileSystem fileSystem, SensorContext context) {
        parseDirs(context, fileSystem.getSourceFiles(Groovy.INSTANCE), fileSystem.getSourceDirs(), false,
                fileSystem.getSourceCharset());
        parseDirs(context, fileSystem.getTestFiles(Groovy.INSTANCE), fileSystem.getTestDirs(), true,
                fileSystem.getSourceCharset());
    }

    protected void parseDirs(SensorContext context, List<File> files, List<File> sourceDirs, boolean unitTest,
            Charset sourcesEncoding) {
        for (File file : files) {
            Resource resource = createResource(file, sourceDirs, unitTest);
            if (resource != null) {
                try {
                    context.index(resource);
                    String source = Files.toString(file, Charset.forName(sourcesEncoding.name()));
                    // SONAR-3860 Remove BOM character from source
                    source = CharMatcher.anyOf("\uFEFF").removeFrom(source);
                    context.saveSource(resource, source);
                } catch (Exception e) {
                    throw new SonarException("Unable to read and import the source file : '" + file.getAbsolutePath()
                            + "' with the charset : '" + sourcesEncoding.name() + "'.", e);
                }
            }
        }
    }

    protected Resource createResource(File file, List<File> sourceDirs, boolean unitTest) {
        return GroovyFile.fromIOFile(file, sourceDirs, unitTest);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
