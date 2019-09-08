/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.common.util;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Non-task variant of Gradle's JavaExec task.
 */
public class JavaExec {

    private static final String JAVA_EXECUTABLE;
    static {
        Path executable = Utils.isWindows() ? Jvm.findExecutable("javaw") : null;
        if (executable == null) {
            executable = Jvm.findExecutable("java");
        }
        requireNonNull(executable, "No java/javaw found in JAVA_HOME or PATH");
        JAVA_EXECUTABLE = executable.toAbsolutePath().toString();
    }

    private static final Executor PIPE_EXEC = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("java-exec-pipe-%d").build()
    );

    private final List<String> jvmArgs = new ArrayList<>();
    private final List<String> args = new ArrayList<>();
    private final ConfigurableFileCollection classpath;
    private File workingDir = new File(".");
    private String main;
    private OutputStream standardOutput;

    public JavaExec(Project project) {
        classpath = project.files();
    }

    public void exec() throws IOException {
        requireNonNull(main);
        Process process = new ProcessBuilder(buildCommand())
            .directory(workingDir)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
        process.getOutputStream().close();
        PIPE_EXEC.execute(() -> {
            try {
                IOUtils.copy(process.getInputStream(), standardOutput);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        if (exitCode != 0) {
            throw new IllegalStateException("Process exited with non-zero exit code: " + exitCode);
        }
    }

    private List<String> buildCommand() {
        return new ImmutableList.Builder<String>()
            .add(JAVA_EXECUTABLE)
            .addAll(jvmArgs)
            .add("-cp", classpath.getFiles().stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator)))
            .add(main)
            .addAll(args)
            .build();
    }

    public void setJvmArgs(Collection<String> jvmArgs) {
        this.jvmArgs.clear();
        this.jvmArgs.addAll(jvmArgs);
    }

    public List<String> getJvmArgs() {
        return jvmArgs;
    }

    public void setArgs(Collection<String> args) {
        this.args.clear();
        this.args.addAll(args);
    }

    public List<String> getArgs() {
        return args;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath.setFrom(classpath);
    }

    public FileCollection getClasspath() {
        return classpath;
    }

    public void setWorkingDir(File workingDir) {
        this.workingDir = workingDir;
    }

    public File getWorkingDir() {
        return workingDir;
    }

    public void setMain(String main) {
        this.main = main;
    }

    public String getMain() {
        return main;
    }

    public void setStandardOutput(OutputStream standardOutput) {
        this.standardOutput = standardOutput;
    }

    public OutputStream getStandardOutput() {
        return standardOutput;
    }
}
