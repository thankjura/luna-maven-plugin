package ru.slie.luna.maven.mojo;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.util.DefaultArchivedFileSet;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import ru.slie.luna.maven.I18nResolver;

import javax.inject.Inject;
import java.io.File;

@Mojo(name = "luna-package", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class PackagePluginMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Inject
    private ArchiverManager archiverManager;

    private final I18nResolver i18n = new I18nResolver();

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("current project: " + project.getArtifactId());
        getLog().info("current project: " + project.getId());

        getLog().info(i18n.t("luna.package.start_package"));

        File outputJar = new File(project.getBuild().getDirectory(), project.getBuild().getFinalName() + ".jar");

        try {
            JarArchiver jarArchiver = (JarArchiver) archiverManager.getArchiver("jar");

            jarArchiver.setDestFile(outputJar);
            jarArchiver.setRecompressAddedZips(true);
            jarArchiver.setDuplicateBehavior(Archiver.DUPLICATES_SKIP);

            DefaultFileSet classes = new DefaultFileSet();
            classes.setDirectory(new File(project.getBuild().getOutputDirectory()));
            classes.setIncludingEmptyDirectories(false);
            jarArchiver.addFileSet(classes);


            for (Artifact artifact: project.getArtifacts()) {
                if (Artifact.SCOPE_TEST.equals(artifact.getScope()) || Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) {
                    continue;
                }

                if ("pom".equals(artifact.getType()) || "pom".equals(artifact.getArtifactHandler().getExtension())) {
                    continue;
                }


                File file = artifact.getFile();
                if (file != null && file.isFile()) {
                    getLog().info(i18n.t("luna.package.add_dependency", artifact.getId()));
                    DefaultArchivedFileSet fileSet = new DefaultArchivedFileSet(file);
                    fileSet.setIncludingEmptyDirectories(false);
                    fileSet.setExcludes(new String[]{"META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA"});
                    jarArchiver.addArchivedFileSet(fileSet);
                }
            }

            jarArchiver.createArchive();
            project.getArtifact().setFile(outputJar);
            getLog().info(i18n.t("luna.package.success", outputJar.getAbsolutePath()));
        } catch (Exception e) {
            throw new MojoExecutionException(i18n.t("luna.package.error"), e);
        }
    }
}
