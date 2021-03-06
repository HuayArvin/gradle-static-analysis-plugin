package com.novoda.staticanalysis.internal.detekt

import com.novoda.staticanalysis.StaticAnalysisExtension
import com.novoda.staticanalysis.Violations
import com.novoda.staticanalysis.internal.Configurator
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task

class DetektConfigurator implements Configurator {

    private static final String DETEKT_PLUGIN = 'io.gitlab.arturbosch.detekt'
    private static final String LAST_COMPATIBLE_DETEKT_VERSION = '1.0.0.RC8'
    private static final String DETEKT_NOT_APPLIED = 'The Detekt plugin is configured but not applied. Please apply the plugin in your build script.\nFor more information see https://github.com/arturbosch/detekt.'
    private static final String OUTPUT_NOT_DEFINED = 'Output not defined! To analyze the results, `output` needs to be defined in Detekt profile.'
    private static final String DETEKT_CONFIGURATION_ERROR = "A problem occurred while configuring Detekt. Please make sure to use a compatible version (All versions up to $LAST_COMPATIBLE_DETEKT_VERSION)"

    private final Project project
    private final Violations violations
    private final Task evaluateViolations

    static DetektConfigurator create(Project project,
                                     NamedDomainObjectContainer<Violations> violationsContainer,
                                     Task evaluateViolations) {
        Violations violations = violationsContainer.maybeCreate('Detekt')
        return new DetektConfigurator(project, violations, evaluateViolations)
    }

    private DetektConfigurator(Project project, Violations violations, Task evaluateViolations) {
        this.project = project
        this.violations = violations
        this.evaluateViolations = evaluateViolations
    }

    @Override
    void execute() {
        project.extensions.findByType(StaticAnalysisExtension).ext.detekt = { Closure config ->
            if (!isKotlinProject(project)) {
                return
            }

            if (!project.plugins.hasPlugin(DETEKT_PLUGIN)) {
                throw new GradleException(DETEKT_NOT_APPLIED)
            }

            def detekt = project.extensions.findByName('detekt')
            config.delegate = detekt
            config()
            configureToolTask(detekt)
        }
    }

    private void configureToolTask(detekt) {
        def detektTask = project.tasks['detektCheck']
        detektTask.group = 'verification'

        // evaluate violations after detekt
        def output = resolveOutput(detekt)
        if (!output) {
            throw new IllegalArgumentException(OUTPUT_NOT_DEFINED)
        }
        def collectViolations = createCollectViolationsTask(violations, project.file(output))
        evaluateViolations.dependsOn collectViolations
        collectViolations.dependsOn detektTask
    }

    private static resolveOutput(detekt) {
        if (detekt.hasProperty('profileStorage')) {
            detekt.profileStorage.systemOrDefault.output
        } else if (detekt.respondsTo('systemOrDefaultProfile')) {
            detekt.systemOrDefaultProfile().output
        } else {
            throw new IllegalStateException(DETEKT_CONFIGURATION_ERROR)
        }
    }

    private CollectDetektViolationsTask createCollectViolationsTask(Violations violations, File outputFolder) {
        project.tasks.create('collectDetektViolations', CollectDetektViolationsTask) { task ->
            task.xmlReportFile = new File(outputFolder, 'detekt-checkstyle.xml')
            task.htmlReportFile = new File(outputFolder, 'detekt-report.html')
            task.violations = violations
        }
    }

    private static boolean isKotlinProject(final Project project) {
        final boolean isKotlin = project.plugins.hasPlugin('kotlin')
        final boolean isKotlinAndroid = project.plugins.hasPlugin('kotlin-android')
        final boolean isKotlinPlatformCommon = project.plugins.hasPlugin('kotlin-platform-common')
        final boolean isKotlinPlatformJvm = project.plugins.hasPlugin('kotlin-platform-jvm')
        final boolean isKotlinPlatformJs = project.plugins.hasPlugin('kotlin-platform-js')
        return isKotlin || isKotlinAndroid || isKotlinPlatformCommon || isKotlinPlatformJvm || isKotlinPlatformJs
    }
}
