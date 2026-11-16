package ru.slie.luna.maven.mojo;

import jakarta.persistence.Entity;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import ru.slie.luna.maven.I18nResolver;
import ru.slie.luna.system.plugin.PluginDescriptor;
import ru.slie.luna.system.plugin.PluginResourceType;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static ru.slie.luna.maven.Constants.DESCRIPTOR_FILE;

@Mojo(name = "validate", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ValidatePluginDescriptorMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private String buildDirectory;
    private final I18nResolver i18n = new I18nResolver();

    private ClassLoader projectClassLoader;

    private ClassLoader getProjectClassLoader() throws MojoExecutionException {
        if (projectClassLoader == null) {
            try {
                List<String> classpathElements = project.getCompileClasspathElements();
                URL[] urls = new URL[classpathElements.size()];

                for (int i = 0; i < classpathElements.size(); i++) {
                    urls[i] = new File(classpathElements.get(i)).toURI().toURL();
                }

                projectClassLoader = new URLClassLoader(urls, getClass().getClassLoader());
            } catch (Exception e) {
                throw new MojoExecutionException("Не удалось инициализировать ClassLoader проекта", e);
            }
        }
        return projectClassLoader;
    }

    private void validateInterface(String componentKey, String className, Class<?> targetInterface) throws MojoExecutionException {
        try {
            ClassLoader loader = getProjectClassLoader();
            Class<?> componentClass = loader.loadClass(className);

            if (!targetInterface.isAssignableFrom(componentClass)) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.error.class_must_implemented", className, targetInterface.getCanonicalName()));
            }
        } catch (ClassNotFoundException e) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.component.class_not_found", className, componentKey), e);
        }
    }

    private void validateAnnotation(String componentKey, String className, Class<? extends Annotation> annotationClass) throws MojoExecutionException {
        try {
            ClassLoader loader = getProjectClassLoader();
            Class<?> componentClass = loader.loadClass(className);

            if (!componentClass.isAnnotationPresent(annotationClass)) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.error.annotation_missing",
                        className, annotationClass.getCanonicalName()));
            }
        } catch (ClassNotFoundException e) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.component.class_not_found", className, componentKey), e);
        }
    }

    private void validateI18NResource(PluginDescriptor.Resource resource) throws MojoExecutionException {
        Path path = Path.of(project.getBuild().getOutputDirectory(), resource.getPath() + ".properties");
        if (!Files.isRegularFile(path)) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.resource.not_exists", resource.getPath() + ".properties"));
        }
    }

    private void validateResourceExists(String resourcePath) throws MojoExecutionException {
        if (resourcePath != null) {
            Path path = Path.of(project.getBuild().getOutputDirectory(), resourcePath);
            if (!Files.isRegularFile(path)) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.file_not_exists", resourcePath));
            }
        }
    }

    private void validateStaticResource(PluginDescriptor.Resource resource) throws MojoExecutionException {
        Path path = Path.of(project.getBuild().getOutputDirectory(), resource.getPath());
        if (!Files.isRegularFile(path)) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.resource.not_exists", resource.getPath()));
        }
    }

    private void validateRest(PluginDescriptor.RestComp rest) throws MojoExecutionException {
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

    private void validateRoutes(Collection<PluginDescriptor.WebRouteComp> routes, Set<String> existsRouteNames) throws MojoExecutionException {
        if (routes == null) {
            return;
        }

        for (PluginDescriptor.WebRouteComp route: routes) {
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

    private void validateComponent(PluginDescriptor.Component component, Set<String> existsComponentKeys, Set<String> resourceKeys, Class<?> requiredImpl) throws MojoExecutionException {
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

        if (requiredImpl != null) {
            validateInterface(component.getKey(), component.getClassName(), requiredImpl);
        } else if (!classFile.exists()) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.component.class_not_found", component.getClassName(), component.getKey()));
        }
        existsComponentKeys.add(component.getKey());
        validateResources(component.getResources(), resourceKeys);
    }

    private static final Pattern WEB_COMPONENT_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");

    /**
     * Validates a web component reference: exactly one of {@code path} (an ES module built into the plugin)
     * or {@code name} (a component registered on the host, e.g. FieldContextOptions) must be set.
     *
     * @param property descriptor property the reference belongs to, used in error messages
     */
    private void validateWebComponent(PluginDescriptor.WebComponent webComponent, String owner, String property) throws MojoExecutionException {
        if (webComponent == null) {
            return;
        }

        String location = owner + "." + property;
        boolean hasPath = webComponent.getPath() != null && !webComponent.getPath().isBlank();
        boolean hasName = webComponent.getName() != null && !webComponent.getName().isBlank();

        if (!hasPath && !hasName) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.web_component.path_or_name_required", location));
        }
        if (hasPath && hasName) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.web_component.path_and_name", location));
        }

        if (hasPath) {
            String relativePath = webComponent.getPath().trim();
            if (!relativePath.endsWith(".js") && !relativePath.endsWith(".mjs")) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.web_component.not_js_module", relativePath, location));
            }
            Path path = Path.of(project.getBuild().getOutputDirectory(), relativePath.replaceFirst("^/", ""));
            if (!Files.isRegularFile(path)) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.route.file_not_exists", relativePath));
            }
        } else if (!WEB_COMPONENT_NAME.matcher(webComponent.getName().trim()).matches()) {
            throw new MojoExecutionException(i18n.t("luna.descriptor.web_component.invalid_name", webComponent.getName(), location));
        }
    }

    private void validateWebComponents(List<PluginDescriptor.WebComponent> webComponents, String owner, String property) throws MojoExecutionException {
        if (webComponents == null) {
            return;
        }
        for (PluginDescriptor.WebComponent webComponent: webComponents) {
            if (webComponent == null) {
                throw new MojoExecutionException(i18n.t("luna.descriptor.web_component.path_or_name_required", owner + "." + property));
            }
            validateWebComponent(webComponent, owner, property);
        }
    }

    private void validateComponents(Collection<? extends PluginDescriptor.Component> components, Set<String> existsComponentKeys, Set<String> resourceKeys, Class<?> requiredImpl) throws MojoExecutionException {
        if (components != null) {
            for (PluginDescriptor.Component component: components) {
                validateComponent(component, existsComponentKeys, resourceKeys, requiredImpl);
            }
        }
    }

    private void validateWorkflowFunctions(List<PluginDescriptor.WorkflowFunctionComp> functions, Set<String> existsComponentKeys, Set<String> resourceKeys, Class<?> requiredImpl) throws MojoExecutionException {
        if (functions == null || functions.isEmpty()) {
            return;
        }

        for (PluginDescriptor.WorkflowFunctionComp workflowFunction: functions) {
            validateComponent(workflowFunction, existsComponentKeys, resourceKeys, requiredImpl);

            validateWebComponent(workflowFunction.getViewComponent(), workflowFunction.getKey(), "viewComponent");
            validateWebComponent(workflowFunction.getFormComponent(), workflowFunction.getKey(), "formComponent");
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
        validateComponents(descriptor.getComponents(), componentKeys, resourceKeys, null);
        validateComponents(descriptor.getFilters(), componentKeys, resourceKeys, null);
        validateComponents(descriptor.getQueryFunctions(), componentKeys, resourceKeys, ru.slie.luna.issue.query.func.QueryFunction.class);
        validateComponents(descriptor.getClauseValueProviders(), componentKeys, resourceKeys, ru.slie.luna.issue.query.value.ClauseValueProvider.class);
        validateComponents(descriptor.getWebSections(), componentKeys, resourceKeys, ru.slie.luna.web.WebSection.class);
        validateComponents(descriptor.getWebSectionProviders(), componentKeys, resourceKeys, ru.slie.luna.web.WebSectionProvider.class);
        validateComponents(descriptor.getWebItems(), componentKeys, resourceKeys, ru.slie.luna.web.WebItem.class);
        validateComponents(descriptor.getWebItemProviders(), componentKeys, resourceKeys, ru.slie.luna.web.WebItemProvider.class);

        validateWorkflowFunctions(descriptor.getWorkflowConditions(), componentKeys, resourceKeys, ru.slie.luna.issue.workflow.condition.WorkflowCondition.class);
        validateWorkflowFunctions(descriptor.getWorkflowValidators(), componentKeys, resourceKeys, ru.slie.luna.issue.workflow.validator.WorkflowValidator.class);
        validateWorkflowFunctions(descriptor.getWorkflowPostfunctions(), componentKeys, resourceKeys, ru.slie.luna.issue.workflow.postfunction.WorkflowPostfunction.class);

        if (descriptor.getFieldTypes() != null) {
            for (PluginDescriptor.FieldTypeComp customFieldType: descriptor.getFieldTypes()) {
                validateComponent(customFieldType, componentKeys, resourceKeys, ru.slie.luna.issue.field.type.FieldType.class);

                String owner = customFieldType.getKey();
                validateWebComponents(customFieldType.getViewComponents(), owner, "viewComponents");
                validateWebComponents(customFieldType.getFormComponents(), owner, "formComponents");
                validateWebComponents(customFieldType.getNavigatorComponents(), owner, "navigatorComponents");
                validateWebComponent(customFieldType.getOptionsComponent(), owner, "optionsComponent");
                validateWebComponent(customFieldType.getPreviewComponent(), owner, "previewComponent");
            }
        }

        if (descriptor.getWebPanels() != null) {
            for (PluginDescriptor.WebPanelComp component : descriptor.getWebPanels()) {
                validateComponent(component, componentKeys, resourceKeys, ru.slie.luna.web.WebPanel.class);
                validateWebComponent(component.getWebComponent(), component.getKey(), "webComponent");
            }
        }

        if (descriptor.getFieldSearchers() != null) {
            for (PluginDescriptor.FieldSearcherComp customFieldSearcher: descriptor.getFieldSearchers()) {
                validateComponent(customFieldSearcher, componentKeys, resourceKeys, ru.slie.luna.issue.field.searcher.FieldSearcher.class);
                validateWebComponent(customFieldSearcher.getFormComponent(), customFieldSearcher.getKey(), "formComponent");
            }
        }

        if (descriptor.getIssuesExportModules() != null) {
            for (PluginDescriptor.IssuesExportModuleComp exportModule: descriptor.getIssuesExportModules()) {
                validateComponent(exportModule, componentKeys, resourceKeys, ru.slie.luna.issue.export.IssuesExportModule.class);
                validateWebComponent(exportModule.getWebComponent(), exportModule.getKey(), "webComponent");
            }
        }

        if (descriptor.getReportModules() != null) {
            for (PluginDescriptor.ReportModuleComp reportModule: descriptor.getReportModules()) {
                validateComponent(reportModule, componentKeys, resourceKeys, ru.slie.luna.report.ReportModule.class);
                validateWebComponent(reportModule.getWebComponent(), reportModule.getKey(), "webComponent");
                validateResourceExists(reportModule.getThumbnailPath());
            }
        }

        if (descriptor.getDirectoryTypes() != null) {
            for (PluginDescriptor.DirectoryTypeComp directoryType: descriptor.getDirectoryTypes()) {
                validateComponent(directoryType, componentKeys, resourceKeys, ru.slie.luna.directory.DirectoryType.class);
                validateWebComponent(directoryType.getWebComponent(), directoryType.getKey(), "webComponent");
            }
        }

        if (descriptor.getBulkActions() != null) {
            for (PluginDescriptor.BulkActionComp bulkAction: descriptor.getBulkActions()) {
                validateComponent(bulkAction, componentKeys, resourceKeys, ru.slie.luna.issue.bulk.BulkAction.class);
                validateWebComponent(bulkAction.getParamsComponent(), bulkAction.getKey(), "paramsComponent");
                validateWebComponent(bulkAction.getConfirmComponent(), bulkAction.getKey(), "confirmComponent");
            }
        }

        validateComponents(descriptor.getProjectImportParticipants(), componentKeys, resourceKeys, ru.slie.luna.system.restore.project.ProjectImportParticipant.class);

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

                validateInterface("activeDocs", activeDocClass, ru.slie.luna.regolith.ActiveDocEntity.class);
                validateAnnotation("activeDocs", activeDocClass, Entity.class);
            }
        }

        if (descriptor.getRestPackages() != null) {
            for (PluginDescriptor.RestComp rest: descriptor.getRestPackages()) {
                validateRest(rest);
            }
        }
    }
}
