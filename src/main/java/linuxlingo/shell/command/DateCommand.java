package linuxlingo.shell.command;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;

/**
 * Prints the current date and time.
 * Syntax: date
 */
public class DateCommand implements Command {

    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy");
        return CommandResult.success(now.format(formatter));
    }

    @Override
    public String getUsage() {
        return "date";
    }

    @Override
    public String getDescription() {
        return "Print the current date and time";
    }
}
