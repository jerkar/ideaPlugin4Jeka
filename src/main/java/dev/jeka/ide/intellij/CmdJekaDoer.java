/*
 * Copyright 2018-2019 original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.jeka.ide.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * @author Jerome Angibaud
 */
public class CmdJekaDoer {

    static final CmdJekaDoer INSTANCE = new CmdJekaDoer();

    private static final String JEKA_JAR_NAME = "dev.jeka.jeka-core.jar";

    private ConsoleView view = null;

    private ToolWindow window = null;

    private String jekaScriptPath;

    public void generateIml(Project project, VirtualFile moduleDir, String qualifiedClassName, boolean clearConsole,
                            @Nullable  Module existingModule, Runnable onFinish) {
        Path modulePath = Paths.get(moduleDir.getPath());
        GeneralCommandLine cmd = new GeneralCommandLine(jekaCmd(modulePath, false));
        cmd.addParameters("intellij#iml", "-LH");
        cmd.setWorkDirectory(modulePath.toFile());
        if (qualifiedClassName != null) {
            cmd.addParameter("-CC=" + qualifiedClassName);
        }
        Runnable onSuccess = () -> refreshAfterIml(project, existingModule, moduleDir, onFinish);
        Runnable onError = () -> generaImlWithJkCommnds(project, moduleDir, existingModule, onFinish);
        start(cmd, project, clearConsole, onSuccess,  onError );
    }

    public void scaffoldModule(Project project, VirtualFile moduleDir, boolean createStrucure, boolean createWrapper,
                               Path wrapDelegate, Module existingModule, ScaffoldNature nature) {
        initView(project);
        Path modulePath = Paths.get(moduleDir.getPath());
        GeneralCommandLine cmd = new GeneralCommandLine(jekaCmd(modulePath, true));
        cmd.addParameters("-CC=dev.jeka.core.tool.JkCommandSet", "-LH");
        if (createStrucure) {
            cmd.addParameters("scaffold#run");
            if (nature != ScaffoldNature.SIMPLE) {
                cmd.addParameters(natureParam(nature));
            }
        }
        if (createWrapper) {
            cmd.addParameter("scaffold#wrap");
        }
        if (wrapDelegate != null) {
            cmd.addParameters("-scaffold#wrapDelegatePath=" + wrapDelegate.toString());
        }
        cmd.setWorkDirectory(new File(moduleDir.getPath()));
        Runnable onSuccess = () -> {
            generateIml(project, moduleDir, null, false, existingModule, null);
            if (wrapDelegate != null) {
                Utils.deleteDir(modulePath.resolve("jeka/wrapper"));
            }
        };
        start(cmd, project, true, onSuccess, null);
    }

    private static String[] natureParam(ScaffoldNature nature) {
        if (nature == ScaffoldNature.JAVA) {
            return new String[] {"java#"};
        }
        return new String[] {"@dev.jeka:springboot-plugin:2.2.3.RELEASE", "springboot#"};
    }

    private void generaImlWithJkCommnds(Project project, VirtualFile moduleDir, Module existingModule,
                                        Runnable onFinish) {
        Path modulePath = Paths.get(moduleDir.getPath());
        GeneralCommandLine cmd = new GeneralCommandLine(jekaCmd(modulePath, false));
        cmd.addParameters("intellij#iml", "-LH", "-CC=JkCommandSet");
        cmd.setWorkDirectory(modulePath.toFile());
        start(cmd, project, false, () -> refreshAfterIml(project, existingModule, moduleDir, onFinish), null);
    }

    private static void refreshAfterIml(Project project, Module existingModule, VirtualFile moduleDir, Runnable onFinish) {
        if (existingModule == null) {
            addModule(project, moduleDir);
        } else {
            VirtualFile imlFile = existingModule.getModuleFile();
            VfsUtil.markDirtyAndRefresh(false, false, false, imlFile);
        }
        if (onFinish != null) {
            onFinish.run();
        }
    }

    private static void addModule(Project project, VirtualFile moduleDir) {
        Path iml = findImlFile(Paths.get(moduleDir.getPath()));
        Path projectDir = Paths.get(project.getBasePath());
        Path modulesXml = projectDir.resolve(".idea/modules.xml");
        Utils.addModule(projectDir, modulesXml, iml);
        VfsUtil.markDirtyAndRefresh(false, false, false,
                VfsUtil.findFile(modulesXml, true));
    }

    private static Path findImlFile(Path moduleDir) {
        String name = moduleDir.getFileName().toString() + ".iml";
        Path candidate = moduleDir.resolve(name);
        if (Files.exists(candidate)) {
            return candidate;
        }
        candidate = moduleDir.resolve(".idea").resolve(name);
        if (Files.exists(candidate)) {
            return candidate;
        }
        return null;
    }

    private void start(GeneralCommandLine cmd, Project project, boolean clear, Runnable onSuccess, Runnable onFailure) {
        OSProcessHandler handler = null;
        try {
            handler = new OSProcessHandler(cmd);
            handler.addProcessListener(new ProcessAdapter() {

                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
                    if (event.getExitCode() != 0 && onFailure != null) {
                        view.print("\nSync has failed !!! Let's try to sync with standard class JkCommandSet\n",
                                ConsoleViewContentType.ERROR_OUTPUT);
                        onFailure.run();
                    } else if (event.getExitCode() == 0 && onSuccess != null) {
                        onSuccess.run();
                    }
                }

            });
        } catch (ExecutionException e) {
            this.jekaScriptPath = null;
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            this.jekaScriptPath = null;
            throw e;
        }
        attachView(project, handler, clear);
        if (ApplicationManager.getApplication().isDispatchThread()) {
            window.show(() -> {
            });
        }
    }

    private void initView(Project project) {
        if (view == null) {
            TextConsoleBuilderFactory factory = TextConsoleBuilderFactory.getInstance();
            TextConsoleBuilder builder = factory.createBuilder(project);
            view = builder.getConsole();
        }
    }

    private void attachView(Project project, OSProcessHandler handler, boolean clear) {
        initView(project);
        view.attachToProcess(handler);
        if (clear) {
            view.clear();
        }
        handler.startNotify();
        if (window == null) {
            ToolWindowManager manager = ToolWindowManager.getInstance(project);
            window = manager.registerToolWindow("Jeka console", true, ToolWindowAnchor.BOTTOM);
            window.setIcon(JkIcons.JEKA_GREY_NAKED_13);
            final ContentManager contentManager = window.getContentManager();
            Content content = contentManager
                    .getFactory()
                    .createContent(view.getComponent(), "", false);
            contentManager.addContent(content);
        }
    }

    private String jekaCmd(Path moduleDir, boolean forceJeka) {
        if (isWindows()) {
            if (forceJeka) {
                return jekaScriptPath(true);
            }
            return Files.exists(moduleDir.resolve("jekaw.bat")) ?
                    moduleDir.toAbsolutePath().resolve("jekaw.bat").toString()
                    : jekaScriptPath(true);
        }
        if (forceJeka) {
            return jekaScriptPath(false);
        }
        return Files.exists(moduleDir.resolve("jekaw")) ? "./jekaw" : jekaScriptPath(false);
    }

    private static boolean isWindows() {
        final String osName = System.getProperty("os.name");
        if (osName == null) {
            return false;
        }
        return osName.startsWith("Windows");
    }

    private String jekaScriptPath(boolean isWindows) {
        if (jekaScriptPath != null) {
            return jekaScriptPath;
        }
        String scriptName = isWindows ? "jeka.bat" : "jeka";;
        jekaScriptPath = createDistribIfNeeed().resolve(scriptName).toAbsolutePath().toString();
        return jekaScriptPath;
    }

    private Path createDistribIfNeeed() {
        Path parent = embeddedDir();
        Path file = parent.resolve(JEKA_JAR_NAME);
        if (!Files.exists(file)) {
            view.print("Creating local Jeka distrib\n", ConsoleViewContentType.LOG_INFO_OUTPUT);
            try {
                Files.createDirectories(parent);
                InputStream is = CmdJekaDoer.class.getClassLoader().getResourceAsStream("dev.jeka.jeka-core-distrib.zip");
                Utils.unzip(is, parent);
                addExecPerm(parent.resolve("jeka"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return parent;
    }

    private static void addExecPerm(Path file) {
        Set<PosixFilePermission> perms;
        try {
            perms = Files.getPosixFilePermissions(file);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException e) {
            // Windows system
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path embeddedDir() {
        IdeaPluginDescriptor[] plugins  = PluginManager.getPlugins();
        String version = null;
        for (IdeaPluginDescriptor plugin : plugins) {
            if (plugin.getPluginId().equals(PluginId.getId("dev.jeka.ide.intellij"))) {
                version = plugin.getVersion();
            }
        }
        String jekaUserHomeEnv = System.getenv("JEKA_USER_HOME");
        Path userhome = jekaUserHomeEnv != null ? Paths.get(jekaUserHomeEnv) :
                Paths.get(System.getProperty("user.home")).resolve(".jeka");
        return userhome.resolve("intellij-plugin").resolve(version).resolve("distrib");
    }


}
