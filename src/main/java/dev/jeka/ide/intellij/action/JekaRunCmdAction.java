package dev.jeka.ide.intellij.action;

import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.ModuleBasedConfigurationOptions;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import dev.jeka.ide.intellij.common.ModuleHelper;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;


public class JekaRunCmdAction extends AnAction {

    public static final JekaRunCmdAction RUN_JEKA_INSTANCE = new JekaRunCmdAction(false);

    public static final JekaRunCmdAction DEBUG_JEKA_INSTANCE = new JekaRunCmdAction(true);

    private final boolean debug;

    private JekaRunCmdAction(boolean debug) {
        super((debug ? "Debug" : "Run") +   " Command",
                (debug ? "Debug" : "Run") +   " Command",
                debug ? AllIcons.Actions.StartDebugger : AllIcons.RunConfigurations.TestState.Run);
        this.debug = debug;
    }

    private static String configurationName(Module module, String cmdName) {
        return module.getName() + " [jeka $" + cmdName + "]";
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        DataContext dataContext = event.getDataContext();
        CmdInfo data = CmdInfo.KEY.getData(dataContext);
        String name = configurationName(data.module,  data.cmdName);
        ApplicationConfiguration configuration = new ApplicationConfiguration(name, project);
        configuration.setWorkingDirectory("$MODULE_WORKING_DIR$");
        configuration.setMainClassName("dev.jeka.core.tool.Main");
        configuration.setModule(data.module);
        configuration.setProgramParameters("$" + data.cmdName);
        configuration.setBeforeRunTasks(Collections.emptyList());

        RunnerAndConfigurationSettings runnerAndConfigurationSettings =
                RunManager.getInstance(project).createConfiguration(configuration, configuration.getFactory());
        ApplicationConfiguration applicationRunConfiguration =
                (ApplicationConfiguration) runnerAndConfigurationSettings.getConfiguration();

        applicationRunConfiguration.setBeforeRunTasks(Collections.emptyList());
        applyClasspathModification(applicationRunConfiguration, data.module);

        Executor executor = debug ?  DefaultDebugExecutor.getDebugExecutorInstance() :
                DefaultRunExecutor.getRunExecutorInstance();
        RunManager.getInstance(project).addConfiguration(runnerAndConfigurationSettings);
        RunManager.getInstance(project).setSelectedConfiguration(runnerAndConfigurationSettings);
        ProgramRunnerUtil.executeConfiguration(runnerAndConfigurationSettings, executor);
    }

    private static void applyClasspathModification(ApplicationConfiguration applicationConfiguration, Module module) {
        LinkedHashSet<ModuleBasedConfigurationOptions.ClasspathModification> excludes = new LinkedHashSet<>();
        excludes.addAll(findExclusion(module));
        ModuleManager moduleManager = ModuleManager.getInstance(module.getProject());
        List<Module> depModules = ModuleHelper.getModuleDependencies(moduleManager, module);
        depModules.forEach(mod -> excludes.addAll(findExclusion(mod)));
        applicationConfiguration.setClasspathModifications(new LinkedList<>(excludes));
    }

    private static List<ModuleBasedConfigurationOptions.ClasspathModification> findExclusion(Module module) {
        VirtualFile[] roots = ModuleRootManager.getInstance(module).orderEntries().classes().getRoots();
        return Arrays.stream(roots)
                .filter(virtualFile -> "file".equals(virtualFile.getFileSystem().getProtocol()))
                .peek(virtualFile -> System.out.println(virtualFile.getPath()))
                .map(VirtualFile::toNioPath)
                .map(path ->
                        new ModuleBasedConfigurationOptions.ClasspathModification(path.toString(), true))
                .collect(Collectors.toList());
    }

    @Value
    public static class CmdInfo {

        public static final DataKey<CmdInfo> KEY = DataKey.create(CmdInfo.class.getName());

        String cmdName;

        Module module;

    }
}
