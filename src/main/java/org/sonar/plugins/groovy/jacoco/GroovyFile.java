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
package  org.sonar.plugins.groovy.jacoco;

import org.sonar.api.resources.Qualifiers;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.Language;

import org.apache.commons.lang.StringUtils;
import org.sonar.api.resources.Scopes;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.api.utils.WildcardPattern;
import org.sonar.plugins.groovy.foundation.Groovy;

import java.io.File;
import java.util.List;

/**
 * A class that represents a Groovy class. This class can either be a Test class or source class
 *
 */
public class GroovyFile extends Resource<GroovyPackage> {

  private String filename;
  private String longName;
  private String packageKey;
  private boolean unitTest;
  private GroovyPackage parent;

  /**
   * Creates a GroovyFile that is not a class of test based on package and file names
   */
  public GroovyFile(String packageName, String className) {
    this(packageName, className, false);
  }

  /**
   * Creates a GroovyFile that can be of any type based on package and file names
   *
   * @param unitTest whether it is a unit test file or a source file
   */
  public GroovyFile(String packageKey, String className, boolean unitTest) {
    if (className == null) {
        throw new IllegalArgumentException("Groovy filename can not be null");
    }
    this.filename = StringUtils.trim(className);
    String key;
    if (StringUtils.isBlank(packageKey)) {
        this.packageKey = GroovyPackage.DEFAULT_PACKAGE_NAME;
        this.longName = this.filename;
        key = new StringBuilder().append(this.packageKey).append(".").append(this.filename).toString();
    } else {
        this.packageKey = packageKey.trim();
        key = new StringBuilder().append(this.packageKey).append(".").append(this.filename).toString();
        this.longName = key;
    }
    setKey(key);
    this.unitTest = unitTest;
  }

    /**
     * Creates a source file from its key
     */
    public GroovyFile(String key) {
        this(key, false);
    }

    /**
     * Creates any GroovyFile from its key
     *
     * @param unitTest whether it is a unit test file or a source file
     */
    public GroovyFile(String key, boolean unitTest) {
        if (key == null) {
            throw new IllegalArgumentException("Groovy filename can not be null");
        }
        String realKey = StringUtils.trim(key);
        this.unitTest = unitTest;

        if (realKey.contains(".")) {
            this.filename = StringUtils.substringAfterLast(realKey, ".");
            this.packageKey = StringUtils.substringBeforeLast(realKey, ".");
            this.longName = realKey;

        } else {
            this.filename = realKey;
            this.longName = realKey;
            this.packageKey = GroovyPackage.DEFAULT_PACKAGE_NAME;
            realKey = new StringBuilder().append(GroovyPackage.DEFAULT_PACKAGE_NAME).append(".").append(realKey).toString();
        }
        setKey(realKey);
    }

  /**
   * {@inheritDoc}
   */
  @Override
  public GroovyPackage getParent() {
    if (parent == null) {
      parent = new GroovyPackage(packageKey);
    }
    return parent;

  }

  /**
   * @return null
   */
  @Override
  public String getDescription() {
    return null;
  }

  /**
   * @return Groovy
   */
  @Override
  public Language getLanguage() {
    return Groovy.INSTANCE;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getName() {
    return filename;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getLongName() {
    return longName;
  }

  /**
   * @return SCOPE_ENTITY
   */
  @Override
  public String getScope() {
    return Scopes.FILE;
  }

  /**
   * @return QUALIFIER_UNIT_TEST_CLASS or QUALIFIER_CLASS depending whether it is a unit test class
   */
  @Override
  public String getQualifier() {
    return unitTest ? Qualifiers.UNIT_TEST_FILE : Qualifiers.CLASS;
  }

  /**
   * @return whether the GroovyFile is a unit test class or not
   */
  public boolean isUnitTest() {
    return unitTest;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean matchFilePattern(String antPattern) {
    String fileKey = getKey();
    if (!fileKey.endsWith(".groovy")) {
      fileKey += ".groovy";
    }
    if (StringUtils.substringAfterLast(antPattern, "/").indexOf(".") < 0) {
      antPattern += ".*";
    }
    WildcardPattern matcher = WildcardPattern.create(antPattern, ".");
    return matcher.match(fileKey);
  }

  public static GroovyFile fromRelativePath(String relativePath, boolean unitTest) {
    if (relativePath != null) {
      String pacname = null;
      String classname = relativePath;

      if (relativePath.indexOf('/') >= 0) {
        pacname = StringUtils.substringBeforeLast(relativePath, "/");
        pacname = StringUtils.replace(pacname, "/", ".");
        classname = StringUtils.substringAfterLast(relativePath, "/");
      }
      classname = StringUtils.substringBeforeLast(classname, ".");
      return new GroovyFile(pacname, classname, unitTest);
    }
    return null;
  }

  /**
   *  Creates a GroovyFile from a file in the source directories
   *
   * @return the GroovyFile created if exists, null otherwise
   */
  public static GroovyFile fromIOFile(File file, List<File> sourceDirs, boolean unitTest) {
    if (file == null || !StringUtils.endsWithIgnoreCase(file.getName(), ".groovy")) {
      return null;
    }
    PathResolver.RelativePath relativePath = new PathResolver().relativePath(sourceDirs, file);
    if (relativePath != null) {
      return fromRelativePath(relativePath.path(), unitTest);
    }
    return null;
  }

  /**
   * Shortcut to fromIOFile with an abolute path
   */
  public static GroovyFile fromAbsolutePath(String path, List<File> sourceDirs, boolean unitTest) {
    if (path == null) {
      return null;
    }
    return fromIOFile(new File(path), sourceDirs, unitTest);
  }

  @Override
  public String toString() {
    return getKey();
  }

}