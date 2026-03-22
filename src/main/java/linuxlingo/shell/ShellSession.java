package linuxlingo.shell;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import linuxlingo.cli.Ui;
import linuxlingo.shell.command.Command;
import linuxlingo.shell.vfs.FileNode;
import linuxlingo.shell.vfs.VirtualFileSystem;

/**
 * Manages the lifecycle of a shell session (interactive REPL + one-shot execution).
 *
 * <p><b>Owner: A — implement start(), executePlan(), and executePlanSilent().</b></p>
 *
 * <p>The getters / setters and the constructor are provided. You need to implement
 * the REPL loop and the plan execution engine that chains pipes, redirections,
 * {@code &&}, and {@code ;} operators.</p>
 *
 * <p><b>Dependency:</b> Falls back to {@link Ui#readLine(String)} for reading input.
 * (Tab-completion via ShellCompleter is deferred beyond v1.0.)</p>
 */
public class ShellSession {

    private static final Logger LOGGER = Logger.getLogger(ShellSession.class.getName());

    private VirtualFileSystem vfs;
    private String workingDir;
    private String previousDir;
    private int lastExitCode;
    private final CommandRegistry registry;
    private final Ui ui;
    private boolean running;
    private final Map<String, String> aliases;
    private ShellLineReader lineReader;
    private final List<String> commandHistory;

    public ShellSession(VirtualFileSystem vfs, Ui ui) {
        if (vfs == null) {
            throw new IllegalArgumentException("ShellSession: vfs must not be null");
        }

        this.vfs = vfs;
        this.ui = ui;
        this.workingDir = "/";
        this.previousDir = null;
        this.lastExitCode = 0;
        this.registry = new CommandRegistry();
        this.running = false;
        this.aliases = new LinkedHashMap<>();
        this.lineReader = null;
        this.commandHistory = new ArrayList<>();

        LOGGER.fine("ShellSession initialised with workingDir='/'");
    }

    /**
     * Enter interactive shell REPL. Steps:
     * <ol>
     *   <li>Set {@code running = true}; print welcome message.</li>
     *   <li>Read input using {@link Ui#readLine(String)} with the shell prompt.</li>
     *   <li>Loop: read line → handle special words ("back", "exit", "done") → call
     *       {@link #executePlan(String)}.</li>
     * </ol>     *
     * <p>If a {@link ShellLineReader} has been set via {@link #setLineReader},
     * input will be read from JLine (with tab-completion and history).
     * Otherwise, falls back to {@link Ui#readLine(String)}.</p>     */
    public void start() {
        assert !running : "start() called while session is already running";

        running = true;
        LOGGER.info("Shell session started");
        ui.println("Welcome to LinuxLingo Shell! Type 'exit' to quit.");

        while (running) {
            String input;
            if (lineReader != null) {
                input = lineReader.readLine(getPrompt());
            } else {
                input = ui.readLine(getPrompt());
            }

            // null signals end of piped test input or Ctrl-D
            if (input == null) {
                running = false;
                break;
            }

            // Skip blank lines and redisplay the prompt
            if (input.trim().isEmpty()) {
                continue;
            }

            // Exit keyword stops the REPL
            String trimmed = input.trim();
            if (trimmed.equalsIgnoreCase("exit")) {
                LOGGER.info("Exit keyword received... stopping REPL");
                running = false;
                break;
            }

            // Track command in history
            commandHistory.add(trimmed);

            executePlan(input);
        }

        LOGGER.info("Shell session ended with lastExitCode=" + lastExitCode);
    }

    /**
     * Start an interactive shell with JLine tab-completion and command history.
     * Creates a {@link ShellLineReader} with a system terminal, then delegates
     * to {@link #start()}.
     *
     * <p>If JLine cannot initialise (e.g. no TTY), falls back to plain Ui input.</p>
     */
    public void startInteractive() {
        try {
            this.lineReader = ShellLineReader.create(this);
        } catch (Exception e) {
            LOGGER.warning("JLine init failed, falling back to Ui: "
                    + e.getMessage());
        }
        try {
            start();
        } finally {
            if (lineReader != null) {
                lineReader.close();
                lineReader = null;
            }
        }
    }

    /**
     * Execute a single command string silently (for programmatic / exam use).
     * Returns the final {@link CommandResult} without printing.
     *
     * @param input raw command string
     * @return the result of the last segment
     */
    public CommandResult executeOnce(String input) {
        LOGGER.fine(() -> "executeOnce called with: " + input);
        return executePlanSilent(input);
    }

    /**
     * Execute a parsed plan and print output to the UI.
     *
     * <p>Algorithm outline:</p>
     * <ol>
     *   <li>Parse input with {@link ShellParser#parse(String)}.</li>
     *   <li>Iterate segments. For each:
     *     <ul>
     *       <li>Check preceding operator: if {@code &&} and lastExitCode ≠ 0, break.</li>
     *       <li>Look up command from registry. If not found → print error, set exitCode=127.</li>
     *       <li>If previous operator was {@code |}, pass previous stdout as stdin.</li>
     *       <li>Call {@code command.execute(this, args, stdin)}.</li>
     *       <li>Handle redirect: write stdout to file via
     *           {@link VirtualFileSystem#writeFile(String, String, String, boolean)}.</li>
     *       <li>Print stderr immediately.</li>
     *     </ul>
     *   </li>
     *   <li>After loop, print final stdout.</li>
     * </ol>
     */
    private void executePlan(String input) {
        CommandResult result = runPlan(input);
        // Print the final stdout produced by the last segment
        if (result != null && !result.getStdout().isEmpty()) {
            ui.println(result.getStdout());
        }
    }

    /**
     * Silent variant of {@link #executePlan(String)} — returns result instead of printing.
     */
    private CommandResult executePlanSilent(String input) {
        return runPlan(input);
    }

    /**
     * Core plan execution engine shared by both {@link #executePlan} and
     * {@link #executePlanSilent}.
     *
     * <p>Operator semantics:</p>
     * <ul>
     *   <li>{@code PIPE}      — stdout of segment N becomes stdin of segment N+1</li>
     *   <li>{@code AND}       — segment N+1 is skipped if lastExitCode != 0</li>
     *   <li>{@code SEMICOLON} — segment N+1 always runs regardless of exit code</li>
     * </ul>
     *
     * @param input raw command string
     * @return the {@link CommandResult} of the final executed segment, or a
     *         zero-exit success result if the input was blank / produced no segments
     */
    private CommandResult runPlan(String input) {
        ShellParser.ParsedPlan plan = new ShellParser().parse(input);

        // Checking whether structure is invariant form the parser
        assert plan.operators.size() == Math.max(0, plan.segments.size() - 1)
                : "ParsedPlan invariant violated: operators=" + plan.operators.size()
                + " segments=" + plan.segments.size();

        // When nothing to execute
        if (plan.segments.isEmpty()) {
            LOGGER.fine("runPlan: no segments to execute");
            return CommandResult.success("");
        }

        CommandResult lastResult = CommandResult.success("");
        String pipedStdin = null; // stdout carried forward through a pipe

        for (int i = 0; i < plan.segments.size(); i++) {
            ShellParser.Segment segment = plan.segments.get(i);

            assert segment != null : "Null segment at index " + i;
            assert segment.commandName != null && !segment.commandName.isBlank()
                    : "Segment at index " + i + " has blank commandName";

            // Check the operator that precedes this segment
            // operators.get(i-1) sits between segment[i-1] and segment[i]
            if (i > 0) {
                ShellParser.TokenType precedingOp = plan.operators.get(i - 1);

                if (precedingOp == ShellParser.TokenType.AND && lastExitCode != 0) {
                    // && requires previous success → skip remaining
                    break;
                }

                if (precedingOp == ShellParser.TokenType.OR && lastExitCode == 0) {
                    // || requires previous failure → skip if succeeded
                    break;
                }

                if (precedingOp != ShellParser.TokenType.PIPE) {
                    // SEMICOLON, AND (passed), or OR (passed): clear piped stdin
                    pipedStdin = null;
                }
                // PIPE: pipedStdin was already set at the end of the previous iteration
            }

            // pipedStdin is non-null only when the preceding operator was PIPE
            String stdin = pipedStdin;
            pipedStdin = null;

            // Handle input redirect: read file content as stdin
            if (segment.inputRedirect != null && !segment.inputRedirect.isEmpty()) {
                try {
                    stdin = vfs.readFile(segment.inputRedirect, workingDir);
                } catch (Exception e) {
                    String errorMsg = segment.inputRedirect + ": No such file or directory";
                    ui.println(errorMsg);
                    setLastExitCode(1);
                    lastResult = CommandResult.error(errorMsg);
                    continue;
                }
            }

            // Resolve alias: replace command name if it's an alias
            String resolvedName = segment.commandName;
            if (aliases.containsKey(resolvedName)) {
                resolvedName = aliases.get(resolvedName);
            }

            // Expand glob patterns in arguments
            String[] expandedArgs = expandGlobs(segment.args);

            //  Look up command in registry
            Command command = registry.get(resolvedName);
            if (command == null) {
                String errorMsg = resolvedName + ": command not found";
                LOGGER.warning("Command not found: '" + resolvedName + "'");
                String suggestion = suggestCommand(resolvedName);
                if (suggestion != null) {
                    errorMsg += "\n" + suggestion;
                }
                ui.println(errorMsg);
                setLastExitCode(127);
                lastResult = CommandResult.error(errorMsg);
                continue; // no piped output from a missing command
            }

            // Execute the command
            CommandResult result = command.execute(this, expandedArgs, stdin);

            //  Print stderr immediately (user is not redirected)
            if (!result.getStderr().isEmpty()) {
                ui.println(result.getStderr());
            }

            // Handle output redirect (> or >>)
            if (segment.redirect != null) {
                // Flush stdout to the target file; suppress it from terminal / pipe
                vfs.writeFile(
                        segment.redirect.target,
                        workingDir,
                        result.getStdout(),
                        segment.redirect.isAppend()
                );
                // stdout consumed by redirect, replaced with an empty success so
                // nothing gets printed or forwarded downstream
                result = CommandResult.success("");
            }

            // Carry stdout forward if the next operator is PIPE
            boolean nextIsPipe = (i < plan.operators.size())
                    && plan.operators.get(i) == ShellParser.TokenType.PIPE;
            if (nextIsPipe) {
                pipedStdin = result.getStdout();
            }

            // Update session state
            setLastExitCode(result.getExitCode());
            lastResult = result;

            if (result.shouldExit()) {
                running = false;
                break;
            }
        }

        return lastResult;
    }

    // Getters / Setters

    public VirtualFileSystem getVfs() {
        return vfs;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public void setWorkingDir(String path) {
        this.workingDir = path;
    }

    public String getPreviousDir() {
        return previousDir;
    }

    public void setPreviousDir(String dir) {
        this.previousDir = dir;
    }

    public int getLastExitCode() {
        return lastExitCode;
    }

    public void setLastExitCode(int code) {
        this.lastExitCode = code;
    }

    public CommandRegistry getRegistry() {
        return registry;
    }

    public Ui getUi() {
        return ui;
    }

    public String getPrompt() {
        return "user@linuxlingo:" + workingDir + "$ ";
    }

    public void replaceVfs(VirtualFileSystem newVfs) {
        this.vfs = newVfs;
    }

    public boolean isRunning() {
        return running;
    }

    public Map<String, String> getAliases() {
        return aliases;
    }

    public ShellLineReader getLineReader() {
        return lineReader;
    }

    public void setLineReader(ShellLineReader reader) {
        this.lineReader = reader;
    }

    /**
     * Get the in-memory command history list.
     * This is always available, even without JLine.
     *
     * @return mutable list of command history entries
     */
    public List<String> getCommandHistory() {
        return commandHistory;
    }

    // ─── "Did you mean?" suggestion ─────────────────────────────

    /**
     * Suggest a similar command name using edit distance.
     *
     * @param input the unrecognized command name
     * @return a suggestion string like "Did you mean 'ls'?" or null
     */
    public String suggestCommand(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        Set<String> names = registry.getAllNames();
        String best = null;
        int bestDist = Integer.MAX_VALUE;

        for (String name : names) {
            int dist = editDistance(input, name);
            if (dist < bestDist) {
                bestDist = dist;
                best = name;
            }
        }

        // Only suggest if edit distance is <= 2
        if (best != null && bestDist <= 2 && bestDist > 0) {
            return "Did you mean '" + best + "'?";
        }
        return null;
    }

    /**
     * Compute Levenshtein edit distance between two strings.
     */
    static int editDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                        dp[i - 1][j] + 1,       // delete
                        dp[i][j - 1] + 1),       // insert
                        dp[i - 1][j - 1] + cost); // replace
            }
        }
        return dp[a.length()][b.length()];
    }

    // ─── Glob expansion ─────────────────────────────────────────

    /**
     * Expand glob patterns in arguments.
     * Only expands if the argument contains a path separator with wildcards
     * (e.g., /home/user/*.txt, ./docs/??.md).
     * Simple patterns without '/' (like *.txt) are NOT expanded
     * to avoid breaking commands that take patterns as arguments (e.g., find -name *.txt).
     *
     * @param args the original arguments
     * @return expanded arguments
     */
    public String[] expandGlobs(String[] args) {
        List<String> expanded = new ArrayList<>();
        for (String arg : args) {
            // Only expand globs that contain a directory path
            if ((arg.contains("*") || arg.contains("?")) && arg.contains("/")) {
                List<String> matches = expandSingleGlob(arg);
                if (!matches.isEmpty()) {
                    expanded.addAll(matches);
                } else {
                    expanded.add(arg); // no match → keep literal
                }
            } else {
                expanded.add(arg);
            }
        }
        return expanded.toArray(new String[0]);
    }

    private List<String> expandSingleGlob(String pattern) {
        List<String> results = new ArrayList<>();

        // Determine base directory and file pattern
        String dir;
        String filePattern;
        int lastSlash = pattern.lastIndexOf('/');
        if (lastSlash >= 0) {
            dir = pattern.substring(0, lastSlash);
            if (dir.isEmpty()) {
                dir = "/";
            }
            filePattern = pattern.substring(lastSlash + 1);
        } else {
            dir = workingDir;
            filePattern = pattern;
        }

        try {
            List<FileNode> matches = vfs.findByName(dir, workingDir, filePattern);
            for (FileNode match : matches) {
                results.add(match.getAbsolutePath());
            }
        } catch (Exception e) {
            // If expansion fails, return empty list
        }

        return results;
    }
}
