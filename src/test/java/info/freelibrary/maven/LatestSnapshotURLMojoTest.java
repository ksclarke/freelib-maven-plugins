
package info.freelibrary.maven;

import static info.freelibrary.maven.LatestSnapshotURLMojo.SNAPSHOT_ARTIFACT;
import static info.freelibrary.maven.LatestSnapshotURLMojo.SNAPSHOT_GROUP;
import static info.freelibrary.maven.LatestSnapshotURLMojo.SNAPSHOT_VERSION;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.Test;

/**
 * A test of the {@link LatestSnapshotURLMojo}.
 */
public class LatestSnapshotURLMojoTest extends BetterAbstractMojoTestCase {

    /**
     * The POM file being used by the tests.
     */
    private static final File POM = new File("src/test/resources/test-pom.xml");

    /**
     * The output we expect to see in our logging.
     */
    private static final String EXPECTED_VALUE = "Setting System property (snapshot.url = https://s01.oss.sonatype";

    /**
     * Tests running the {@link LatestSnapshotURLMojo}.
     *
     * @throws Exception If there is trouble running the test
     */
    @Test
    public void testMojoGoal() throws Exception {
        final Properties props = getProperties(SNAPSHOT_ARTIFACT, "cantaloupe-auth-delegate", SNAPSHOT_GROUP,
                "edu.ucla.library", SNAPSHOT_VERSION, "0.0.1-SNAPSHOT");
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        final PrintStream standardErrorStream = System.err;

        try (PrintStream logStream = new PrintStream(byteStream)) {
            System.setErr(logStream); // Kidnap our System.err so we can check it for our expected property

            lookupConfiguredMojo(POM, props, "set-snapshot-url").execute();
            assertTrue(byteStream.toString(StandardCharsets.UTF_8).contains(EXPECTED_VALUE));
        } finally {
            System.setErr(standardErrorStream);
        }
    }

}
