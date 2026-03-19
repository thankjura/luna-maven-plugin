package ru.slie.luna.maven.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import ru.slie.luna.maven.I18nResolver;
import ru.slie.luna.system.plugin.PluginDescriptor;
import ru.slie.luna.system.plugin.PluginResourceType;
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

    private void validateRest(PluginDescriptor.Rest rest) throws MojoExecutionException {
        if (rest.getName() == null || rest.getName().trim().isEmpty()) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.rest.name_is_undefined"));
        }


        if (rest.getPackages() == null || rest.getPackages().isEmpty()) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.rest.package_is_undefined", rest.getName()));
        }

        for (String packName: rest.getPackages()) {
            Path packagePath = Path.of(project.getBuild().getOutputDirectory(), packName.replace(".", "/"));
            if (!Files.isDirectory(packagePath)) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.rest.package_not_found", packName, packagePath));
            }
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

    private void validateComponent(PluginDescriptor.Component component, Set<String> existsComponentKeys, Set<String> resourceKeys) throws MojoExecutionException {
        if (component.getKey() == null) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.component.property_required", "key"));
        }
        if (component.getClassName() == null) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.component.property_required", "className"));
        }

        if (existsComponentKeys.contains(component.getKey())) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.component.duplicate.error", component.getKey()));
        }

        String classPath = component.getClassName().replace('.', File.separatorChar) + ".class";
        getLog().info(project.getBuild().getOutputDirectory());
        File classFile = new File(project.getBuild().getOutputDirectory(), classPath);
        if (!classFile.exists()) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.component.class_not_found", component.getClassName(), component.getKey()));
        }
        existsComponentKeys.add(component.getKey());
        validateResources(component.getResources(), resourceKeys);
    }

    private void validateWebComponent(PluginDescriptor.WebComponent webComponent) throws MojoExecutionException {
        if (webComponent.getPath() != null && !webComponent.getPath().isBlank()) {
            Path path = Path.of(project.getBuild().getOutputDirectory(), webComponent.getPath());
            if (!Files.isRegularFile(path)) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.route.file_not_exists", webComponent.getPath()));
            }
        }
    }

    @Override
    public void execute() throws MojoExecutionException {
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
                validateComponent(component, componentKeys, resourceKeys);
            }
        }

        if (descriptor.getFieldTypes() != null) {
            for (PluginDescriptor.FieldType customFieldType: descriptor.getFieldTypes()) {
                validateComponent(customFieldType, componentKeys, resourceKeys);

                if (customFieldType.getViewComponents() != null) {
                    for (PluginDescriptor.WebComponent webComponent: customFieldType.getViewComponents()) {
                        validateWebComponent(webComponent);
                    }
                }

                if (customFieldType.getEditComponents() != null) {
                    for (PluginDescriptor.WebComponent webComponent: customFieldType.getEditComponents()) {
                        validateWebComponent(webComponent);
                    }
                }

                if (customFieldType.getNavigatorComponents() != null) {
                    for (PluginDescriptor.WebComponent webComponent: customFieldType.getNavigatorComponents()) {
                        validateWebComponent(webComponent);
                    }
                }

                if (customFieldType.getOptionsComponent() != null) {
                    validateWebComponent(customFieldType.getOptionsComponent());
                }

                if (customFieldType.getIconPath() != null) {
                    Path path = Path.of(project.getBuild().getOutputDirectory(), customFieldType.getIconPath());
                    if (!Files.isRegularFile(path)) {
                        throw new MojoExecutionException(i18n.t("luna.descriptor.file_not_exists", customFieldType.getIconPath()));
                    }
                }
            }
        }

        if (descriptor.getFieldSearchers() != null) {
            for (PluginDescriptor.FieldType customFieldType: descriptor.getFieldTypes()) {
                validateComponent(customFieldType, componentKeys, resourceKeys);
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

        if (descriptor.getActiveDocs() != null) {
            for (String activeDocClass: descriptor.getActiveDocs()) {
                String classPath = activeDocClass.replace('.', File.separatorChar) + ".class";
                File classFile = new File(project.getBuild().getOutputDirectory(), classPath);
                if (!classFile.exists()) {
                    throw new MojoExecutionException(i18n.t("luna.descriptor.active_docs.class_not_found", activeDocClass));
                }
            }
        }

        if (descriptor.getRestPackages() != null) {
            for (PluginDescriptor.Rest rest: descriptor.getRestPackages()) {
                validateRest(rest);
            }
        }
    }
}
