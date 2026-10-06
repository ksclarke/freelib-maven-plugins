package info.freelibrary.maven;

import static info.freelibrary.util.Constants.DASH;
import static java.nio.charset.StandardCharsets.UTF_8;

import info.freelibrary.util.FileUtils;
import info.freelibrary.util.I18nRuntimeException;
import info.freelibrary.util.JarUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.ThrowingConsumer;
import info.freelibrary.util.warnings.PMD;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * I18nCodesMojo is a Maven mojo that can generate a <code>MessageCodes</code> class from which I18N message codes can
 * be referenced. The codes are then used to retrieve textual messages from resource bundles. The benefit of this is the
 * code can be generic, but the actual text from the pre-configured message file will be displayed in the IDE.
 */
@Mojo(name = MojoNames.GENERATE_CODES, defaultPhase = LifecyclePhase.PROCESS_SOURCES,
  requiresDependencyResolution = ResolutionScope.COMPILE)
@SuppressWarnings({PMD.EXCESSIVE_IMPORTS})
public class I18nCodesMojo extends AbstractMojo {

    /**
     * A delimiter to use in the bundle name.
     */
    private static final String BUNDLE_DELIM = "_";

    /**
     * A regular expression pattern to find the expected message file.
     */
    private static final RegexFileFilter DEFAULT_MESSAGE_FILTER = new RegexFileFilter(".*_messages.xml");

    /**
     * The logger for I18nCodesMojo.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(I18nCodesMojo.class, MessageCodes.BUNDLE);

    /**
     * The name of the message class.
     */
    private static final String MESSAGE_CLASS_NAME = "message-class-name";

    /**
     * The resources directory where the message file should be found.
     */
    private static final File RESOURCES_DIR = new File("src/main/resources");

    /**
     * A configuration option to ignore if the messages file is missing.
     */
    @Parameter(alias = Config.IGNORE_MISSING_MESSAGE_FILES, property = Config.IGNORE_MISSING_MESSAGE_FILES,
      defaultValue = "false")
    protected boolean isIgnoringMissingFiles;

    /**
     * A configuration option for generating a standard properties file in addition to the codes class.
     */
    @Parameter(alias = Config.IS_TRANSCODING_NEEDED, property = Config.IS_TRANSCODING_NEEDED, defaultValue = "false")
    protected boolean isTranscodingNeeded;

    /**
     * A configuration option for the generated sources directory.
     */
    @Parameter(alias = Config.GEN_SRC_DIR, property = Config.GEN_SRC_DIR,
      defaultValue = "${project.basedir}/src/main/generated")
    protected File myGeneratedSrcDir;

    /**
     * The Maven project directory.
     */
    @Parameter(defaultValue = "${project}")
    protected MavenProject myProject;

    /**
     * A configuration option for the message file(s).
     */
    @Parameter(alias = Config.MESSAGE_FILES, property = Config.MESSAGE_FILES)
    protected List<String> myPropertyFiles;

    @Override
    @SuppressWarnings({PMD.PRESERVE_STACK_TRACE, PMD.CYCLOMATIC_COMPLEXITY})
    public void execute() throws MojoExecutionException, MojoFailureException {
        LOGGER.info(MessageCodes.MVN_127);

        try {
            if (myPropertyFiles != null && !myPropertyFiles.isEmpty()) {
                generateMessageCodes(myPropertyFiles);

                if (isTranscodingNeeded) {
                    LOGGER.info(MessageCodes.MVN_128);
                    writePropertiesFiles(getPropertyFiles());
                }
            } else {
                final List<String> fileList = Arrays.stream(FileUtils.listFiles(RESOURCES_DIR, DEFAULT_MESSAGE_FILTER))
                  .map(File::getAbsolutePath).collect(Collectors.toList());

                generateMessageCodes(fileList);

                if (isTranscodingNeeded) {
                    LOGGER.info(MessageCodes.MVN_129);
                    writePropertiesFiles(fileList);
                }
            }
        } catch (final FileNotFoundException | NoSuchFileException details) {
            if (!isIgnoringMissingFiles) {
                LOGGER.warn(MessageCodes.MVN_001);
            }
        } catch (final IOException details) {
            throw new MojoExecutionException(details);
        } catch (final I18nRuntimeException details) {
            throw new MojoExecutionException(details.getCause());
        }
    }

    /**
     * Generates the message codes class.
     *
     * @param aFilesList A list of message files
     */
    @SuppressWarnings({PMD.AVOID_FILE_STREAM, PMD.COGNITIVE_COMPLEXITY})
    private void generateMessageCodes(final List<String> aFilesList) {
        final Properties properties = new Properties();

        aFilesList.stream().map(File::new).filter(File::exists).forEach((ThrowingConsumer<File>) file -> {
            LOGGER.debug("Generating message codes for: {}", file);

            try (FileInputStream inStream = new FileInputStream(file)) {
                properties.loadFromXML(inStream);

                final String fullClassName = properties.getProperty(MESSAGE_CLASS_NAME);
                final String srcFolder = myGeneratedSrcDir == null ? myProject.getBuild().getSourceDirectory()
                  : myGeneratedSrcDir.getAbsolutePath();

                if (fullClassName != null) {
                    final String[] nameParts = fullClassName.split("\\.");
                    final int classNameIndex = nameParts.length - 1;
                    final String className = nameParts[classNameIndex];
                    final String[] packageParts = Arrays.copyOfRange(nameParts, 0, classNameIndex);
                    final String pkgName = StringUtils.join(packageParts, ".");
                    final JavaClassSource source = Roaster.create(JavaClassSource.class);
                    final File pkgDir = Path.of(srcFolder, pkgName.replace('.', File.separatorChar)).toFile();

                    source.setFinal(true).setPublic();

                    // Make sure the package directory already exists
                    if (!pkgDir.exists() && !pkgDir.mkdirs()) {
                        throw new MojoExecutionException(LOGGER.getMessage(MessageCodes.MVN_003, pkgDir, className));
                    }

                    // Cycle through all the entries in the supplied messages file, creating fields
                    for (final String key : properties.stringPropertyNames()) {
                        // Create a field that contains the name of the bundle file
                        if (MESSAGE_CLASS_NAME.equals(key)) {
                            final FieldSource<JavaClassSource> field = source.addField();
                            final String bundleName = FileUtils.stripExt(file.getName());

                            field.setName("BUNDLE").setStringInitializer(bundleName);
                            field.setType(String.class.getSimpleName()).setPublic().setStatic(true).setFinal(true);
                            field.getJavaDoc().setFullText("Message bundle name.");
                        }

                        // Create a field in our new message codes class for the message
                        if (!MESSAGE_CLASS_NAME.equals(key)) {
                            final String normalizedKey = key.replaceAll("[\\.-]", BUNDLE_DELIM);
                            final String value = properties.getProperty(key);
                            final FieldSource<JavaClassSource> field = source.addField();

                            field.setName(normalizedKey).setStringInitializer(key);
                            field.setType(String.class.getSimpleName()).setPublic().setStatic(true).setFinal(true);
                            field.getJavaDoc().setFullText("Message: " + value);
                        }
                    }

                    // Add private constructor
                    source.addMethod().setPrivate().setConstructor(true).setBody("super();");

                    // Create our new message codes class in the requested package directory
                    try (FileWriter javaWriter = new FileWriter(new File(pkgDir, className + ".java"), UTF_8)) {
                        // Let's tell Checkstyle to ignore the generated code (if it's so configured)
                        source.getJavaDoc().setFullText(LOGGER.getMessage(MessageCodes.MVN_008));

                        // Name our Java file and add a constructor
                        source.setPackage(pkgName).setName(className);

                        // Lastly, write our generated Java class out to the file system
                        javaWriter.write(source.toString());
                    }
                } else {
                    LOGGER.warn(MessageCodes.MVN_002, MESSAGE_CLASS_NAME);
                }
            } catch (final IOException details) {
                LOGGER.error(details.getMessage(), details);
            }
        });
    }

    /**
     * Load the user-supplied property files from a combination of file and Jar sources.
     *
     * @return An array of accessible property files
     * @throws IOException If there is trouble reading the property files
     */
    private List<String> getPropertyFiles() throws IOException {
        final List<String> files = new ArrayList<>();

        myPropertyFiles.stream().forEach((ThrowingConsumer<String>) file -> {
            if (new File(file).exists()) {
                files.add(file);
            } else {
                final Stream<String> classpathStream = myProject.getCompileClasspathElements().stream();
                final Predicate<String> isJar = element -> element.endsWith(".jar");

                LOGGER.debug(MessageCodes.MVN_131, file);

                classpathStream.filter(isJar).forEach((ThrowingConsumer<String>) jar -> {
                    final JarFile jarFile = new JarFile(jar);

                    if (JarUtils.contains(jarFile, file)) {
                        final File tmpDir = Files.createTempDirectory(UUID.randomUUID().toString() + DASH).toFile();

                        LOGGER.debug(MessageCodes.MVN_130, jar);

                        JarUtils.extract(jarFile, file, tmpDir);
                        files.add(Path.of(tmpDir.toString(), file).toString());
                    }
                });
            }
        });

        return files;
    }

    /**
     * Writes corresponding properties files from the supplied XML files.
     *
     * @param aFilesList A list of XML resource files
     */
    private void writePropertiesFiles(final List<String> aFilesList) {
        aFilesList.stream().forEach((ThrowingConsumer<String>) xmlFilePath -> {
            final Path fileName = Path.of(xmlFilePath.replace(".xml", ".properties")).getFileName();
            final String projectDir = myProject.getBasedir().getAbsolutePath();
            final Path filePath = Path.of(projectDir, "target/classes", fileName.toString());
            final Path sourceFilePath = Path.of(xmlFilePath);
            final Properties properties = new Properties();

            LOGGER.debug(MessageCodes.MVN_125, xmlFilePath, filePath);

            // Make sure out output directory exists before trying to write to it
            Files.createDirectories(filePath.getParent());

            try (InputStream xmlFileStream = Files.newInputStream(sourceFilePath);
                 BufferedWriter fileWriter = Files.newBufferedWriter(filePath, UTF_8)) {
                properties.loadFromXML(xmlFileStream);
                properties.store(fileWriter, LOGGER.getMessage(MessageCodes.MVN_126));
            }
        });
    }

    /**
     * The Mojo's configuration options.
     */
    final class Config {

        /**
         * Constant for the generated sources directory property.
         */
        static final String GEN_SRC_DIR = "generatedSourcesDirectory";

        /**
         * Constant for whether to ignore possibly missing message files.
         */
        static final String IGNORE_MISSING_MESSAGE_FILES = "ignoreMissing";

        /**
         * Constant for the transcoding needed property.
         */
        static final String IS_TRANSCODING_NEEDED = "createPropertiesFile";

        /**
         * Constant for the message files property.
         */
        static final String MESSAGE_FILES = "messageFiles";

        /**
         * A private constructor for a constants class.
         */
        private Config() {
            // This is intentionally left empty.
        }
    }
}
