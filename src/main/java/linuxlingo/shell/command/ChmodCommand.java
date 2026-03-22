package linuxlingo.shell.command;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.Directory;
import linuxlingo.shell.vfs.FileNode;
import linuxlingo.shell.vfs.Permission;
import linuxlingo.shell.vfs.VfsException;

/**
 * Changes file permissions.
 * Supports both octal (e.g., 755) and symbolic (e.g., u+x) notation.
 * Syntax: chmod [-R] &lt;mode&gt; &lt;file&gt;
 *
 * <p><b>Owner: C</b></p>
 */
public class ChmodCommand implements Command {
    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        boolean recursive = false;
        String mode = null;
        String file = null;

        for (String arg : args) {
            if (arg.equals("-R")) {
                recursive = true;
            } else if (mode == null) {
                mode = arg;
            } else if (file == null) {
                file = arg;
            }
        }

        if (mode == null || file == null) {
            return CommandResult.error("chmod: " + getUsage());
        }

        boolean isOctal = mode.matches("[0-7]{3}");
        boolean isSymbolic = mode.matches("[ugoa]+[+-=][rwx]+");
        if (!isOctal && !isSymbolic) {
            return CommandResult.error("chmod: invalid mode: " + mode);
        }

        try {
            FileNode node = session.getVfs().resolve(file, session.getWorkingDir());

            if (recursive && node.isDirectory()) {
                applyPermissionRecursive(node, mode, isOctal);
            } else {
                applyPermission(node, mode, isOctal);
            }

            return CommandResult.success("");
        } catch (VfsException e) {
            return CommandResult.error("chmod: " + e.getMessage());
        }
    }

    private void applyPermission(FileNode node, String mode, boolean isOctal) {
        Permission newPerm;
        if (isOctal) {
            newPerm = Permission.fromOctal(mode);
        } else {
            newPerm = Permission.fromSymbolic(mode, node.getPermission());
        }
        node.setPermission(newPerm);
    }

    private void applyPermissionRecursive(FileNode node, String mode, boolean isOctal) {
        applyPermission(node, mode, isOctal);
        if (node.isDirectory()) {
            for (FileNode child : ((Directory) node).getChildren()) {
                applyPermissionRecursive(child, mode, isOctal);
            }
        }
    }

    @Override
    public String getUsage() {
        return "chmod [-R] <mode> <file>";
    }

    @Override
    public String getDescription() {
        return "Change file permissions";
    }
}
