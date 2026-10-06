
package info.freelibrary.maven;

import java.io.File;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;

import info.freelibrary.util.I18nRuntimeException;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

/**
 * An improved AbstractMojoTestCase for Maven plugin tests (cf. https://stackoverflow.com/a/33704746/171452); it has
 * more of the Maven defaults set (so, for instance, the <code>${project}</code> that a Mojo references isn't null).
 */
public abstract class BetterAbstractMojoTestCase extends AbstractMojoTestCase {

    /**
     * Logger for the tests.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(BetterAbstractMojoTestCase.class, MessageCodes.BUNDLE);

    /**
     * Builds a Properties object from the supplied strings.
     *
     * @param aKvArray An array of keys and values from which to build a properties object
     * @return A properties object
     * @throws IllegalArgumentException If the supplied properties are not supplied in pairs
     */
    protected Properties getProperties(final String... aKvArray) {
        final Properties properties = new Properties();

        if (aKvArray.length % 2 == 1) {
            throw new IllegalArgumentException(LOGGER.getMessage(MessageCodes.MVN_117, aKvArray.length, aKvArray));
        }

        for (int index = 0; index < aKvArray.length; index += 2) {
            properties.put(aKvArray[index], aKvArray[index + 1]);
        }

        return properties;
    }

    /**
     * Provides a wrapper around the parent class' {@link #lookupConfiguredMojo(MavenProject, String)}; it wraps the
     * super method and improves on it by simplifying the arguments needed.
     *
     * @param aPOM A POM file
     * @param aProperties A set of properties to use for the test
     * @param aGoal The goal for the Maven mojo we want to retrieve
     * @throws Exception If an exception is thrown while looking up the configured mojo
     * @return A Maven mojo
     */
    protected Mojo lookupConfiguredMojo(final File aPOM, final Properties aProperties, final String aGoal)
            throws Exception {
        assertNotNull(LOGGER.getMessage(MessageCodes.MVN_136), aPOM);
        assertTrue(LOGGER.getMessage(MessageCodes.MVN_135), aPOM.exists());

        final ProjectBuildingRequest buildingRequest = newMavenSession().getProjectBuildingRequest();
        final MavenProject project = lookup(ProjectBuilder.class).build(aPOM, buildingRequest).getProject();
        final Mojo mojo;

        // Give option to override the system properties at time of individual test
        if (aProperties != null) {
            final Properties properties = project.getProperties();

            aProperties.forEach((key, value) -> {
                properties.put(key, value);
            });
        }

        mojo = lookupConfiguredMojo(project, aGoal);
        assertNotNull(mojo);

        return mojo;
    }

    /**
     * Provides a wrapper around the parent class' {@link #lookupConfiguredMojo(MavenProject, String)}; it wraps the
     * super method and improves on it by simplifying the arguments needed.
     *
     * @param aPOM A POM file
     * @param aGoal The goal for the Maven mojo we want to retrieve
     * @throws Exception If an exception is thrown while looking up the configured mojo
     * @return A Maven mojo
     */
    protected Mojo lookupConfiguredMojo(final File aPOM, final String aGoal) throws Exception {
        return lookupConfiguredMojo(aPOM, null, aGoal);
    }

    /**
     * Creates a new Maven session with system properties and a local repository set.
     *
     * @return A pre-configured Maven session
     * @throws I18nRuntimeException If there is an exception thrown while creating a new session
     */
    @SuppressWarnings("deprecation")
    protected MavenSession newMavenSession() {
        try {
            final MavenExecutionRequest request = new DefaultMavenExecutionRequest();
            final MavenExecutionResult result = new DefaultMavenExecutionResult();
            final MavenExecutionRequestPopulator reqPop = getContainer().lookup(MavenExecutionRequestPopulator.class);
            final DefaultMaven maven = (DefaultMaven) getContainer().lookup(Maven.class);
            final DefaultRepositorySystemSession session;

            reqPop.populateDefaults(request);
            request.setSystemProperties(System.getProperties());
            session = new DefaultRepositorySystemSession(maven.newRepositorySession(request));
            session.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory().newInstance(session,
                    new LocalRepository(request.getLocalRepository().getBasedir())));

            return new MavenSession(getContainer(), session, request, result);
        } catch (final Exception details) {
            throw new I18nRuntimeException(details);
        }
    }

    /**
     * Extends the super class to use the new {@link #newMavenSession()} introduced here; this sets the defaults one
     * expects from to be able to access from a Maven mojo.
     *
     * @param aProject A Maven project
     * @return A pre-configured Maven session
     */
    @Override
    protected MavenSession newMavenSession(final MavenProject aProject) {
        final MavenSession session = newMavenSession();

        session.setCurrentProject(aProject);
        session.setProjects(Arrays.asList(aProject));

        return session;
    }
}
