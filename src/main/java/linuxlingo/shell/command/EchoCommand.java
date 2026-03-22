package linuxlingo.shell.command;

import java.util.ArrayList;
import java.util.List;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;

public class EchoCommand implements Command {
    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        boolean noNewline = false;
        List<String> textArgs = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-n") && textArgs.isEmpty()) {
                noNewline = true;
            } else {
                textArgs.add(arg);
            }
        }

        String output = String.join(" ", textArgs);
        // The -n flag is noted but since CommandResult stdout is always
        // printed with println by the shell, we add a marker that the
        // shell can recognize. For simplicity, we just return the text.
        // In a real shell, -n suppresses the trailing newline.
        return CommandResult.success(output);
    }

    @Override
    public String getUsage() {
        return "echo [-n] <text>";
    }

    @Override
    public String getDescription() {
        return "Print text";
    }
}
