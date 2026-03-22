package linuxlingo.shell.command;

import java.util.ArrayList;
import java.util.List;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.FileNode;
import linuxlingo.shell.vfs.RegularFile;
import linuxlingo.shell.vfs.VfsException;

/**
 * Finds files by name pattern, type, or size under a given path.
 * Syntax: find &lt;path&gt; [-name &lt;pattern&gt;] [-type f|d] [-size +N|-N|N]
 *
 * <p><b>Owner: C</b></p>
 */
public class FindCommand implements Command {
    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        if (args.length < 1) {
            return CommandResult.error("find: " + getUsage());
        }

        String path = args[0];
        String namePattern = null;
        String typeFilter = null;  // "f" or "d"
        String sizeFilter = null;  // "+N", "-N", or "N"

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
            case "-name":
                if (i + 1 >= args.length) {
                    return CommandResult.error("find: missing argument to -name");
                }
                namePattern = args[++i];
                break;
            case "-type":
                if (i + 1 >= args.length) {
                    return CommandResult.error("find: missing argument to -type");
                }
                typeFilter = args[++i];
                if (!typeFilter.equals("f") && !typeFilter.equals("d")) {
                    return CommandResult.error("find: unknown type: " + typeFilter);
                }
                break;
            case "-size":
                if (i + 1 >= args.length) {
                    return CommandResult.error("find: missing argument to -size");
                }
                sizeFilter = args[++i];
                break;
            default:
                return CommandResult.error("find: unknown option: " + args[i]);
            }
        }

        try {
            // Use findByName if namePattern is set, otherwise find everything
            String searchPattern = (namePattern != null) ? namePattern : "*";
            List<FileNode> matches = session.getVfs().findByName(
                    path, session.getWorkingDir(), searchPattern);

            List<String> results = new ArrayList<>();
            for (FileNode node : matches) {
                // Apply type filter
                if (typeFilter != null) {
                    if (typeFilter.equals("f") && node.isDirectory()) {
                        continue;
                    }
                    if (typeFilter.equals("d") && !node.isDirectory()) {
                        continue;
                    }
                }

                // Apply size filter
                if (sizeFilter != null && !node.isDirectory()) {
                    int fileSize = ((RegularFile) node).getSize();
                    if (!matchesSize(sizeFilter, fileSize)) {
                        continue;
                    }
                } else if (sizeFilter != null && node.isDirectory()) {
                    continue; // directories have no meaningful size
                }

                results.add(node.getAbsolutePath());
            }
            return CommandResult.success(String.join("\n", results));
        } catch (VfsException e) {
            return CommandResult.error("find: " + e.getMessage());
        }
    }

    private boolean matchesSize(String sizeFilter, int fileSize) {
        try {
            if (sizeFilter.startsWith("+")) {
                int threshold = Integer.parseInt(sizeFilter.substring(1));
                return fileSize > threshold;
            } else if (sizeFilter.startsWith("-")) {
                int threshold = Integer.parseInt(sizeFilter.substring(1));
                return fileSize < threshold;
            } else {
                int threshold = Integer.parseInt(sizeFilter);
                return fileSize == threshold;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String getUsage() {
        return "find <path> [-name <pattern>] [-type f|d] [-size +N|-N|N]";
    }

    @Override
    public String getDescription() {
        return "Find files by name, type, or size";
    }
}
