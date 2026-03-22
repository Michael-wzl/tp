package linuxlingo.cli;

import linuxlingo.exam.ExamSession;
import linuxlingo.shell.ShellSession;

/**
 * Parse and dispatch top-level commands in interactive mode.
 */
public class MainParser {
    private final Ui ui;
    private final ShellSession shellSession;
    private final ExamSession examSession;

    public MainParser(Ui ui, ShellSession shellSession, ExamSession examSession) {
        this.ui = ui;
        this.shellSession = shellSession;
        this.examSession = examSession;
    }

    /**
     * Start the interactive REPL. Blocks until user types "exit".
     */
    public void run() {
        ui.printWelcome();
        boolean running = true;
        while (running) {
            String input = ui.readLine("linuxlingo> ");
            if (input == null) {
                break;
            }
            input = input.trim();
            if (input.isEmpty()) {
                continue;
            }
            running = parseAndExecute(input);
        }
        ui.println("Goodbye!");
    }

    /**
     * @return false if the user wants to exit
     */
    private boolean parseAndExecute(String input) {
        String[] parts = input.split("\\s+");
        String command = parts[0];

        switch (command) {
        case "shell" -> {
            shellSession.startInteractive();
            return true;
        }
        case "exec" -> {
            handleExec(input);
            return true;
        }
        case "exam" -> {
            handleExam(parts);
            return true;
        }
        case "help" -> {
            printHelp();
            return true;
        }
        case "exit", "quit" -> {
            return false;
        }
        default -> {
            ui.println("Unknown command: " + command + ". Type 'help' for available commands.");
            return true;
        }
        }
    }

    private void handleExec(String input) {
        // Extract the command string after "exec"
        String rest = input.substring(4).trim();
        // Remove optional -e flag
        String envName = null;
        if (rest.startsWith("-e ")) {
            String[] execParts = rest.split("\\s+", 3);
            if (execParts.length >= 3) {
                envName = execParts[1];
                rest = execParts[2];
            }
        }
        // Remove surrounding quotes if present
        if ((rest.startsWith("\"") && rest.endsWith("\""))
                || (rest.startsWith("'") && rest.endsWith("'"))) {
            rest = rest.substring(1, rest.length() - 1);
        }

        if (rest.isEmpty()) {
            ui.println("exec: missing command");
            return;
        }

        if (envName != null) {
            try {
                var loaded = linuxlingo.storage.VfsSerializer.loadFromFile(envName);
                shellSession.replaceVfs(loaded.getVfs());
                shellSession.setWorkingDir(loaded.getWorkingDir());
            } catch (linuxlingo.storage.StorageException e) {
                ui.printError("exec: " + e.getMessage());
                return;
            }
        }

        var result = shellSession.executeOnce(rest);
        if (!result.getStdout().isEmpty()) {
            ui.println(result.getStdout());
        }
        if (!result.getStderr().isEmpty()) {
            ui.printError(result.getStderr());
        }
    }

    private void handleExam(String[] parts) {
        if (parts.length == 1) {
            examSession.startInteractive();
            return;
        }

        String topic = null;
        int count = -1;
        boolean random = false;
        boolean listTopics = false;

        for (int i = 1; i < parts.length; i++) {
            switch (parts[i]) {
            case "-t" -> {
                if (i + 1 < parts.length) {
                    topic = parts[++i];
                }
            }
            case "-n" -> {
                if (i + 1 < parts.length) {
                    try {
                        count = Integer.parseInt(parts[++i]);
                    } catch (NumberFormatException e) {
                        ui.println("Invalid count: " + parts[i]);
                        return;
                    }
                }
            }
            case "-random" -> random = true;
            case "-topics" -> listTopics = true;
            default -> { }
            }
        }

        if (listTopics) {
            examSession.listTopics();
        } else if (random && topic == null) {
            examSession.runOneRandom();
        } else if (topic != null) {
            examSession.startWithArgs(topic, count, random);
        } else {
            examSession.startInteractive();
        }
    }

    private void printHelp() {
        ui.println("Available commands:");
        ui.println("  shell                        Enter the Shell Simulator");
        ui.println("  exam                         Start an exam (interactive topic selection)");
        ui.println("  exam -t <topic> -n <count>   Start an exam on a specific topic");
        ui.println("  exam -t <topic> -random      Start with questions in random order");
        ui.println("  exam -topics                 List all available exam topics");
        ui.println("  exam -random                 One random question, then return");
        ui.println("  exec \"<shell command>\"        Execute a shell command and print output");
        ui.println("  exec -e <env> \"<command>\"    Execute in a saved environment");
        ui.println("  help                         Show this help message");
        ui.println("  exit                         Exit LinuxLingo");
    }
}
