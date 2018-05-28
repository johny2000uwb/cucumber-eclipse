package cucumber.eclipse.editor.steps.jdt;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import cucumber.eclipse.editor.preferences.CucumberUserSettingsPage;
import cucumber.eclipse.steps.integration.IStepDefinitions;
import cucumber.eclipse.steps.integration.IStepListener;
import cucumber.eclipse.steps.integration.Step;
import cucumber.eclipse.steps.integration.StepsChangedEvent;
import cucumber.eclipse.steps.jdt.StepDefinitions;

/**
 * @author girija.panda@nokia.com
 * 
 *         Purpose: Inhering 'cucumber.eclipse.steps.jdt.StepDefinitions' class
 *         is to avoid plugin-dependency conflicts due to
 *         'CucumberUserSettingsPage' class. Supports reusing of
 *         Step-Definitions from external JAR(.class) exists in class-path.
 *         Reads the package name of external JAR from 'User Settings' of
 *         Cucumber-Preference page and Populate the step proposals from
 *         external JAR
 * 
 *         Also Modified For Issue #211 : Duplicate Step definitions
 * 
 */
public class JDTStepDefinitions extends StepDefinitions implements IStepDefinitions {

    // To Collect all Steps as Set for ContentAssistance
    public static Set<Step> steps = null;

    private final CucumberUserSettingsPage userSettingsPage = new CucumberUserSettingsPage();

    // 1. To get Steps as Set from both Java-Source and JAR file
    @Override
    public Set<Step> getSteps(final IFile featurefile, final IProgressMonitor progressMonitor) {

        // Commented By Girija to use LinkedHashSet Instead of HashSet
        // Set<Step> steps = new HashSet<Step>();

        // Used LinkedHashSet : Import all steps from step-definition File
        steps = new LinkedHashSet<Step>();
        final IProject project = featurefile.getProject();

        // Get Package name/s from 'User-Settings' preference
        final String externalPackageName = this.userSettingsPage.getPackageName();
        // System.out.println("Package Names = " + externalPackageName);
        final String[] extPackages = externalPackageName.trim().split(this.COMMA);

        // #239:Only match step implementation in same package as feature file
        final boolean onlyPackages = this.userSettingsPage.getOnlyPackages();
        final String[] onlySpeficicPackagesValue = this.userSettingsPage.getOnlySpecificPackage().trim().split(";"); // TODO
        final boolean onlySpeficicPackages = onlySpeficicPackagesValue.length == 0 ? false : true;
        final String featurefilePackage = featurefile.getParent().getFullPath().toString();

        try {

            if (project.isNatureEnabled(this.JAVA_PROJECT)) {

                final IJavaProject javaProject = JavaCore.create(project);
                final IPackageFragment[] packages = javaProject.getPackageFragments();

                for (final IPackageFragment javaPackage : packages) {
                    // Get Packages from source folder of current project
                    // #239:Only match step implementation in same package as feature file
                    if (javaPackage.getKind() == this.JAVA_SOURCE) {// TODO
                        if ((!onlyPackages || featurefilePackage.startsWith(javaPackage.getPath().toString()))) {
                            if (!onlySpeficicPackages) {
                                // System.out.println("Package Name-1
                                // :"+javaPackage.getElementName());
                                // Collect All Steps From Source
                                this.collectCukeStepsFromSource(javaProject, javaPackage, steps, progressMonitor);
                            }
                            else {
                                for (int i = 0; i < onlySpeficicPackagesValue.length; i++) {
                                    if (javaPackage.getElementName().startsWith(onlySpeficicPackagesValue[i])) {
                                        this.collectCukeStepsFromSource(javaProject, javaPackage, steps,
                                                progressMonitor);
                                    }
                                }
                            }
                        }
                    }

                    // Get Packages from JAR exists in class-path
                    if ((javaPackage.getKind() == this.JAVA_JAR_BINARY) && !externalPackageName.equals("")) {
                        // Iterate all external packages
                        for (final String extPackageName : extPackages) {
                            // Check package from external JAR/class file
                            if (javaPackage.getElementName().equals(extPackageName.trim())
                                    || javaPackage.getElementName().startsWith(extPackageName.trim())) {
                                // Collect All Steps From JAR
                                this.collectCukeStepsFromJar(javaPackage, steps);
                            }
                        }
                    }
                }
            }
        }
        catch (final CoreException e) {
            e.printStackTrace();
        }
        return steps;
    }

    /**
     * Collect all cuke-steps from java-source Files
     * 
     * @param javaProject
     * @param javaPackage
     * @param steps
     * @param progressMonitor
     * @throws JavaModelException
     * @throws CoreException
     */
    public void collectCukeStepsFromSource(final IJavaProject javaProject, final IPackageFragment javaPackage,
            final Set<Step> steps, final IProgressMonitor progressMonitor) throws JavaModelException, CoreException {

        for (final ICompilationUnit iCompUnit : javaPackage.getCompilationUnits()) {
            // Collect and add Steps
            steps.addAll(this.getCukeSteps(javaProject, iCompUnit, progressMonitor));
        }
    }

    /**
     * Collect all cuke-steps from .class file of Jar
     * 
     * @param javaPackage
     * @param steps
     * @throws JavaModelException
     * @throws CoreException
     */
    public void collectCukeStepsFromJar(final IPackageFragment javaPackage, final Set<Step> steps)
            throws JavaModelException, CoreException {

        @SuppressWarnings("deprecation")
        final IClassFile[] classFiles = javaPackage.getClassFiles();
        for (final IClassFile classFile : classFiles) {
            // System.out.println("----classFile: "
            // +classFile.getElementName());
            steps.addAll(this.getCukeSteps(javaPackage, classFile));
        }
    }

    @Override
    public void addStepListener(final IStepListener listener) {
        // this.listeners.add(listener);
        // #240:For Changes in step implementation is reflected in feature file
        StepDefinitions.listeners.add(listener);
    }

    @Override
    public void removeStepListener(final IStepListener listener) {
        // this.listeners.remove(listener);
        // #240:For Changes in step implementation is reflected in feature file
        StepDefinitions.listeners.remove(listener);
    }

    @Override
    public void notifyListeners(final StepsChangedEvent event) {
        for (final IStepListener listener : listeners) {
            listener.onStepsChanged(event);
        }
    }
}
