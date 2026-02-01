package ru.slie.luna.maven.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.compiler.AbstractCompilerMojo;
import org.apache.maven.plugin.compiler.CompilationFailureException;
import org.apache.maven.plugin.compiler.CompilerMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.lang.reflect.Field;

@Mojo(name = "compile", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true, requiresDependencyResolution= ResolutionScope.COMPILE)
public class CompilePluginMojo extends CompilerMojo {
    @Override
    public void execute() throws MojoExecutionException, CompilationFailureException {
        try {
            Field field = AbstractCompilerMojo.class.getDeclaredField("parameters");
            field.setAccessible(true);
            field.set(this, true);
        } catch (Exception ignored) {

        }

        super.execute();
    }
}
