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
import ru.slie.luna.maven.models.PluginResourceType;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static ru.slie.luna.maven.Constants.DESCRIPTOR_FILE;

@Mojo(name = "validate", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public class ValidatePluginDescriptorMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private String buildDirectory;
    private final I18nResolver i18n = new I18nResolver();

    private void validateI18NResource(PluginDescriptor.Resource resource) throws MojoExecutionException {
        Path path = Path.of(project.getBuild().getOutputDirectory(), resource.getPath() + ".properties");
        if (!Files.isRegularFile(path)) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.resource.not_exists", resource.getPath() + ".properties"));
        }
    }

    private void validateStaticResource(PluginDescriptor.Resource resource) throws MojoExecutionException {
        Path path = Path.of(project.getBuild().getOutputDirectory(), resource.getPath());
        if (!Files.isRegularFile(path)) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.resource.not_exists", resource.getPath()));
        }
    }

    private void validateResources(Collection<PluginDescriptor.Resource> resources, Set<String> definedResourcesKey) throws MojoExecutionException {
        if (resources == null) {
            return;
        }

        for (PluginDescriptor.Resource resource: resources) {
            if (resource.getKey() == null) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.resource.property_required", "key"));
            }

            if (definedResourcesKey.contains(resource.getKey())) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.resource.duplicate.error", resource.getKey()));
            }

            definedResourcesKey.add(resource.getKey());

            if (resource.getPath() == null) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.resource.property_required", "path"));
            }

            if (resource.getType() == null) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.resource.property_required", "type"));
            }

            if (Objects.requireNonNull(resource.getType()) == PluginResourceType.I18N) {
                validateI18NResource(resource);
            } else {
                validateStaticResource(resource);
            }
        }
    }

    private void validateRoutes(Collection<PluginDescriptor.WebRoute> routes, Set<String> existsRouteNames) throws MojoExecutionException {
        if (routes == null) {
            return;
        }

        for (PluginDescriptor.WebRoute route: routes) {
            if (route.getRouteName() == null) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.route.property_required", "routeName"));
            }

            if (existsRouteNames.contains(route.getRouteName())) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.route.duplicate_error", route.getRouteName()));
            }

            if (route.getRoutePath() == null){
                throw new MojoExecutionException(i18n.t("luna.descriptor.route.property_required", "routePath"));
            }

            if (route.getComponent() == null){
                throw new MojoExecutionException(i18n.t("luna.descriptor.route.property_required", "component"));
            }

            Path path = Path.of(project.getBuild().getOutputDirectory(), route.getComponent());
            if (!Files.isRegularFile(path)) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.route.component_not_exists", route.getComponent()));
            }

            existsRouteNames.add(route.getRouteName());
            validateRoutes(route.getChildren(), existsRouteNames);
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Path descriptorPath = Paths.get(project.getBuild().getOutputDirectory(), DESCRIPTOR_FILE);
        PluginDescriptor descriptor = new YAMLMapper().readValue(descriptorPath, PluginDescriptor.class);

        if (descriptor.getKey() == null || descriptor.getKey().trim().isEmpty()) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.key_required"));
        }

        if (descriptor.getName() == null || descriptor.getName().trim().isEmpty()) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.name_required"));
        }

        if (descriptor.getVersion() == null || descriptor.getVersion().trim().isEmpty()) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.version_required"));
        }

        if (descriptor.getVendor() == null) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.vendor_required"));
        } else {
            if (descriptor.getVendor().getName() == null || descriptor.getVendor().getName().trim().isEmpty()) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.vendor_name_required"));
            }

            if (descriptor.getVendor().getUrl() == null || descriptor.getVendor().getUrl().trim().isEmpty()) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.vendor_url_required"));
            }
        }

        Set<String> componentKeys = new HashSet<>();
        Set<String> resourceKeys = new HashSet<>();
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
                validateResources(component.getResources(), resourceKeys);
            }
        }
        validateRoutes(descriptor.getRoutes(), new HashSet<>());
        validateResources(descriptor.getResources(), resourceKeys);

        if (descriptor.getScanPackages() != null) {
            for (String pack: descriptor.getScanPackages()) {
                Path packagePath = Path.of(project.getBuild().getOutputDirectory(), pack.replace(".", "/"));
                if (!Files.isDirectory(packagePath)) {
                    throw new MojoExecutionException(i18n.t("luna.descriptor.scan.package_not_found", pack, packagePath));
                }
            }
        }
    }
}
