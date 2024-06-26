
package info.freelibrary.maven;

import java.io.File;
import java.util.Iterator;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

/**
 * A Maven mojo that can insert properties into the build as a result of checking the existence of a file.
 */
@Mojo(name = MojoNames.CHECK_FILE_SET_PROPERTY, defaultPhase = LifecyclePhase.INITIALIZE)
public class IfFileThenPropertiesMojo extends AbstractMojo {

    /**
     * The logger for IfFileThenPropertiesMojo.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(UUIDGeneratingMojo.class, MessageCodes.BUNDLE);

    /**
     * A path to a file to test for presence.
     */
    @Parameter(alias = Config.EXISTS)
    protected File myExistsFile;

    /**
     * A path to a file to test for absence.
     */
    @Parameter(alias = Config.MISSING)
    protected File myMissingFile;

    /**
     * The Maven project directory.
     */
    @Parameter(defaultValue = "${project}")
    protected MavenProject myProject;

    /**
     * Properties to set if the file exists.
     */
    @Parameter(alias = Config.PROPERTIES)
    protected Properties myProperties;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (myExistsFile != null && myMissingFile != null) {
            if (myExistsFile.exists() && !myMissingFile.exists()) {
                addProperties();
            }
        } else if (myExistsFile != null && myExistsFile.exists() || myMissingFile != null && !myMissingFile.exists()) {
            addProperties();
        }
    }

    /**
     * Adds properties to the Maven build.
     */
    private void addProperties() {
        final Properties properties = myProject.getProperties();

        if (myProperties != null) {
            final Iterator<?> keyIterator = myProperties.keySet().iterator();

            while (keyIterator.hasNext()) {
                final String key = keyIterator.next().toString();
                final String value = myProperties.getProperty(key);

                LOGGER.debug(MessageCodes.MVN_014, key, value);

                properties.put(key, value);
            }
        } else {
            LOGGER.warn(MessageCodes.MVN_015, myExistsFile);
        }
    }

    /**
     * The Mojo's configuration options.
     */
    private static final class Config {

        /**
         * The exists configuration option.
         */
        private static final String EXISTS = "exists";

        /**
         * The missing configuration option.
         */
        private static final String MISSING = "missing";

        /**
         * The property configuration option.
         */
        private static final String PROPERTIES = "properties";
    }
}
