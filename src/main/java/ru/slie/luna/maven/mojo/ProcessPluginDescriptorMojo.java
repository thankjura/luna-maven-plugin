package ru.slie.luna.maven.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.interpolation.*;
import ru.slie.luna.maven.I18nResolver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static ru.slie.luna.maven.Constants.DESCRIPTOR_FILE;

@Mojo(name = "process", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = true)
public class ProcessPluginDescriptorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    private final I18nResolver i18n = new I18nResolver();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Path sourcePath = Paths.get(project.getBasedir().getPath(), "src", "main", "resources", DESCRIPTOR_FILE);
        Path targetPath = Paths.get(project.getBuild().getOutputDirectory(), DESCRIPTOR_FILE);

        if (!Files.exists(sourcePath)) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.file_not_found", sourcePath.toAbsolutePath()));
        }
        getLog().info(i18n.t("luna.descriptor.start_processing", DESCRIPTOR_FILE));

        StringSearchInterpolator interpolator = new StringSearchInterpolator();
        interpolator.addValueSource(new PrefixedValueSourceWrapper(
                new ObjectBasedValueSource(project),
                List.of("project", "pom"),
                true));

        interpolator.addValueSource(new PropertiesBasedValueSource(project.getProperties()));
        try {
            interpolator.addValueSource(new EnvarBasedValueSource());
        } catch (Exception ignored) {}
        interpolator.addValueSource(new PropertiesBasedValueSource(System.getProperties()));

        interpolator.addValueSource(new AbstractValueSource(false) {
            @Override
            public Object getValue(String expression) {
                getLog().warn(i18n.t("luna.descriptor.property_not_found", expression));
                return expression;
            }
        });

        try {
            String originalContent = Files.readString(sourcePath, StandardCharsets.UTF_8);
            String processedContent = interpolator.interpolate(originalContent);
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, processedContent, StandardCharsets.UTF_8);
            getLog().info(i18n.t("luna.descriptor.process_successfully", targetPath));
        } catch (Exception e) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.process_error", DESCRIPTOR_FILE), e);
        }
    }
}
