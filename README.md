Sonar Groovy
============

A fork of the Sonar Groovy Plugin v0.6 with the following changes:

 + now being built using gradle
 + adds 'Plugin-ChildFirstClassLoader: true' to the Manifest - solves an issue seen when trying to analyse groovy code from gradle
 + added clover support

To build the plugin:

    ./gradlew jar

After you've built the plugin - you can find original plugin documentation and installation instructions from http://docs.codehaus.org/display/SONAR/Groovy+Plugin

(hint: copy the jar into $SONAR_HOME/extensions/plugins)
