package org.jetbrains.teamcity.runas;

import jetbrains.buildServer.RunBuildException;
import jetbrains.buildServer.agent.AgentExtension;
import jetbrains.buildServer.agent.runner.BuildCommandLineProcessor;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.runner.ProgramCommandLine;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RunAsCommandLineProcessor implements BuildCommandLineProcessor {
  private static final String BUILD_STARTUP_COMMAND_PARAM = "teamcity.build.runAs.command";
  private static final String START_BUILD_COMMAND_MACRO = "{start_build_command}";

  @NotNull
  public ProgramCommandLine process(@NotNull final AgentRunningBuild build, @NotNull final ProgramCommandLine origCommandLine)
    throws RunBuildException {
    final String execPath = getCustomExecutableCommand(build);
    if (execPath == null) return origCommandLine;

    final File script = createScriptFile(origCommandLine, build);

    return new ProgramCommandLine() {
      @NotNull
      public String getExecutablePath() throws RunBuildException {
        return execPath;
      }

      @NotNull
      public String getWorkingDirectory() throws RunBuildException {
        return origCommandLine.getWorkingDirectory();
      }

      @NotNull
      public List<String> getArguments() throws RunBuildException {
        List<String> args = new ArrayList<String>();
        args.addAll(getRunAsExecutableArgs(build, script.getAbsolutePath()));
        return args;
      }

      @NotNull
      public Map<String, String> getEnvironment() throws RunBuildException {
        return origCommandLine.getEnvironment();
      }
    };
  }

  private static File createScriptFile(@NotNull final ProgramCommandLine origCommandLine, @NotNull AgentRunningBuild build) throws RunBuildException {
    if (isWindows()) {
      return createScriptForWindows(origCommandLine, build);
    }

    return createScriptForUnix(origCommandLine, build);
  }

  private static boolean isWindows() throws RunBuildException {
    final String osname = System.getProperty("os.name");
    if (osname == null) throw new RunBuildException("System property os.name is not defined, unable to determine OS type");
    return osname.toLowerCase().indexOf("windows") != -1;
  }

  private static File createScriptForUnix(@NotNull final ProgramCommandLine origCommandLine, @NotNull final AgentRunningBuild build) throws RunBuildException {
    final File script;
    try {
      script = File.createTempFile("build", ".sh", build.getAgentTempDirectory());
      StringBuilder content = new StringBuilder();
      content.append("cd ").append(origCommandLine.getWorkingDirectory()).append("\n");
      content.append(createOriginalCommandLine(origCommandLine));
      FileUtil.writeFile(script, content.toString());
      FileUtil.setExectuableAttribute(script.getAbsolutePath(), true);
    } catch (IOException e) {
      throw new RunBuildException("Failed to create temp file, error: " + e.toString());
    }
    return script;
  }

  private static File createScriptForWindows(@NotNull final ProgramCommandLine origCommandLine, @NotNull final AgentRunningBuild build) throws RunBuildException {
    final File script;
    try {
      script = File.createTempFile("build", ".cmd", build.getAgentTempDirectory());
      StringBuilder content = new StringBuilder();
      content.append("cd ").append(origCommandLine.getWorkingDirectory()).append("\r\n");
      content.append(createOriginalCommandLine(origCommandLine));
      FileUtil.writeFile(script, content.toString());
    } catch (IOException e) {
      throw new RunBuildException("Failed to create temp file, error: " + e.toString());
    }
    return script;
  }

  private static String createOriginalCommandLine(@NotNull final ProgramCommandLine commandLine) throws RunBuildException {
    StringBuilder sb = new StringBuilder();
    sb.append(commandLine.getExecutablePath());
    for (String arg: commandLine.getArguments()) {
      sb.append(" ");
      final boolean hasSpaces = arg.indexOf(' ') != -1;
      if (hasSpaces) {
        sb.append("\"");
      }
      sb.append(arg.replace("\"", "\\\""));
      if (hasSpaces) {
        sb.append("\"");
      }
    }
    return sb.toString();
  }

  @Nullable
  private static String getCustomExecutableCommand(@NotNull AgentRunningBuild build) {
    String runAsCommand = build.getAgentConfiguration().getConfigurationParameters().get(BUILD_STARTUP_COMMAND_PARAM);
    if (runAsCommand == null) return null;
    final List<String> parts = StringUtil.splitCommandArgumentsAndUnquote(runAsCommand);
    return parts.size() > 0 ? parts.get(0) : null;
  }

  @NotNull
  public static List<String> getRunAsExecutableArgs(@NotNull AgentRunningBuild build, @NotNull String cline) {
    List<String> result = new ArrayList<String>();
    String command = build.getAgentConfiguration().getConfigurationParameters().get(BUILD_STARTUP_COMMAND_PARAM);
    if (command == null) return result;

    command = build.getSharedParametersResolver().resolve(command).getResult();
    command = replacePattern(command, START_BUILD_COMMAND_MACRO, cline);

    result.addAll(StringUtil.splitCommandArgumentsAndUnquote(command));
    result.remove(0);

    return result;
  }

  private static String replacePattern(final String command, final String pattern, String replacement) {
    if (replacement == null) return command;
    if (replacement.indexOf(" ") != -1) replacement = "\"" + replacement + "\"";
    return command.replace(pattern, replacement);
  }
}
