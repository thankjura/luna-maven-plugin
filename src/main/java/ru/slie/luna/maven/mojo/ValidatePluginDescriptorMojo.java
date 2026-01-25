package ru.slie.luna.maven.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import ru.slie.luna.maven.I18nResolver;
import ru.slie.luna.maven.models.PluginDescriptor;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static ru.slie.luna.maven.Constants.DESCRIPTOR_FILE;

@Mojo(name = "validate", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public class ValidatePluginDescriptorMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private String buildDirectory;
    private final I18nResolver i18n = new I18nResolver();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Path descriptorPath = Paths.get(project.getBuild().getOutputDirectory(), DESCRIPTOR_FILE);
        PluginDescriptor descriptor = new YAMLMapper().readValue(descriptorPath, PluginDescriptor.class);
        Set<String> componentKeys = new HashSet<>();
        if (descriptor.getComponents() != null) {
            for (PluginDescriptor.Component component: descriptor.getComponents()) {
                if (component.getKey() == null) {
                    throw new MojoExecutionException(i18n.t("luna.descriptor.component.property_required", "key"));
                }
                if (component.getClassName() == null) {
                    throw new MojoExecutionException(i18n.t("luna.descriptor.component.property_required", "className"));
                }

                if (componentKeys.contains(component.getKey())) {
                    throw new MojoExecutionException(i18n.t("luna.descriptor.component.duplicate.error", component.getKey()));
                }

                String classPath = component.getClassName().replace('.', File.separatorChar) + ".class";
                getLog().info(project.getBuild().getOutputDirectory());
                File classFile = new File(project.getBuild().getOutputDirectory(), classPath);
                if (!classFile.exists()) {
                    throw new MojoExecutionException(i18n.t("luna.descriptor.component.class_not_found", component.getClassName(), component.getKey()));
                }
                componentKeys.add(component.getKey());
            }
        }

        getLog().info(i18n.t("luna.descriptor.validate.error", descriptorPath.toString()));

    }
}
