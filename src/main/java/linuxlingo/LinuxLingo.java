package linuxlingo;

import java.nio.file.Path;

import linuxlingo.cli.MainParser;
import linuxlingo.cli.Ui;
import linuxlingo.exam.ExamSession;
import linuxlingo.exam.QuestionBank;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.VirtualFileSystem;
import linuxlingo.storage.ResourceExtractor;
import linuxlingo.storage.Storage;
import linuxlingo.storage.StorageException;

/**
 * Main entry point for LinuxLingo.
 */
public class LinuxLingo {

    public static void main(String[] args) {
        Ui ui = new Ui();

        // Extract bundled resources on first run
        try {
            ResourceExtractor.extractIfNeeded(Storage.getDataDir());
        } catch (StorageException e) {
            // Non-fatal: continue without question banks from resources
            System.err.println("Warning: " + e.getMessage());
        }

        // Load question bank
        QuestionBank questionBank = new QuestionBank();
        Path questionsDir = Storage.getDataDir().resolve("questions");
        if (Storage.exists(questionsDir)) {
            questionBank.load(questionsDir);
        }

        // Create shared VFS and sessions
        VirtualFileSystem vfs = new VirtualFileSystem();
        ShellSession shellSession = new ShellSession(vfs, ui);
        ExamSession examSession = new ExamSession(questionBank, ui, VirtualFileSystem::new);

        if (args.length == 0) {
            // Interactive mode
            MainParser parser = new MainParser(ui, shellSession, examSession);
            parser.run();
        } else {
            // One-shot mode
            handleOneShot(args, ui, shellSession, examSession);
        }
    }

    private static void handleOneShot(String[] args, Ui ui,
                                      ShellSession shellSession, ExamSession examSession) {
        switch (args[0]) {
        case "shell" -> shellSession.startInteractive();
        case "exec" -> handleExec(args, ui, shellSession);
        case "exam" -> handleExam(args, examSession);
        default -> {
            ui.println("Unknown command: " + args[0]);
            ui.println("Usage: java -jar LinuxLingo.jar [shell|exec|exam]");
        }
        }
    }

    private static void handleExec(String[] args, Ui ui, ShellSession shellSession) {
        if (args.length < 2) {
            ui.println("exec: missing command");
            return;
        }

        int cmdStart = 1;
        if (args[1].equals("-e") && args.length >= 4) {
            String envName = args[2];
            try {
                var loaded = linuxlingo.storage.VfsSerializer.loadFromFile(envName);
                shellSession.replaceVfs(loaded.getVfs());
                shellSession.setWorkingDir(loaded.getWorkingDir());
            } catch (StorageException e) {
                ui.printError("exec: " + e.getMessage());
                return;
            }
            cmdStart = 3;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = cmdStart; i < args.length; i++) {
            if (i > cmdStart) {
                sb.append(" ");
            }
            sb.append(args[i]);
        }

        var result = shellSession.executeOnce(sb.toString());
        if (!result.getStdout().isEmpty()) {
            ui.println(result.getStdout());
        }
        if (!result.getStderr().isEmpty()) {
            ui.printError(result.getStderr());
        }
    }

    private static void handleExam(String[] args, ExamSession examSession) {
        String topic = null;
        int count = -1;
        boolean random = false;
        boolean listTopics = false;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
            case "-t" -> {
                if (i + 1 < args.length) {
                    topic = args[++i];
                }
            }
            case "-n" -> {
                if (i + 1 < args.length) {
                    try {
                        count = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        // ignore
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
}
