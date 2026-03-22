package linuxlingo.exam.question;

import java.util.ArrayList;
import java.util.List;

import linuxlingo.exam.Checkpoint;
import linuxlingo.shell.vfs.FileNode;
import linuxlingo.shell.vfs.Permission;
import linuxlingo.shell.vfs.VirtualFileSystem;

/**
 * Practical question verified by checking VFS state after the user
 * completes tasks in the shell simulator.
 *
 * <p><b>Owner: D</b></p>
 *
 * <h3>Question bank format (parsed by {@code QuestionParser})</h3>
 * <pre>
 * PRAC | DIFFICULTY | questionText | path1:TYPE,path2:TYPE | setupItems | explanation
 * </pre>
 * Where TYPE is {@code DIR}, {@code FILE}, {@code NOT_EXISTS},
 * {@code CONTENT_EQUALS=content}, or {@code PERM=rwxr-x---},
 * and checkpoints are comma-separated.
 *
 * <h3>Setup items (v2.0)</h3>
 * <p>Optional field 5 may contain semicolon-separated setup instructions
 * that are applied to a temp VFS before user interaction:</p>
 * <pre>
 * MKDIR:/some/path;FILE:/some/file.txt=initial content;PERM:/some/file.txt=rwxrwx---
 * </pre>
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>{@code ExamSession} presents the question text.</li>
 *   <li>If setup items exist, they are applied to the temporary VFS.</li>
 *   <li>A temporary {@code ShellSession} is opened for the user to type commands.</li>
 *   <li>When the user types "done", the VFS is passed to {@link #checkVfs(VirtualFileSystem)}.</li>
 *   <li>Each {@link Checkpoint} is verified.</li>
 * </ol>
 */
public class PracQuestion extends Question {
    private final List<Checkpoint> checkpoints;
    private final List<SetupItem> setupItems;

    /**
     * A single VFS setup instruction applied before the user interacts.
     */
    public static class SetupItem {
        public enum SetupType {
            MKDIR, FILE, PERM
        }

        private final SetupType type;
        private final String path;
        private final String value; // content for FILE, permission string for PERM, null for MKDIR

        public SetupItem(SetupType type, String path, String value) {
            this.type = type;
            this.path = path;
            this.value = value;
        }

        public SetupType getType() {
            return type;
        }

        public String getPath() {
            return path;
        }

        public String getValue() {
            return value;
        }
    }

    /** Backward-compatible constructor (no setup items). */
    public PracQuestion(String questionText, String explanation,
                        Difficulty difficulty, List<Checkpoint> checkpoints) {
        this(questionText, explanation, difficulty, checkpoints, new ArrayList<>());
    }

    /** Full constructor with setup items. */
    public PracQuestion(String questionText, String explanation,
                        Difficulty difficulty, List<Checkpoint> checkpoints,
                        List<SetupItem> setupItems) {
        super(QuestionType.PRAC, difficulty, questionText, explanation);
        this.checkpoints = checkpoints;
        this.setupItems = setupItems;
    }

    @Override
    public String present() {
        return formatHeader() + " " + questionText + "\n";
    }

    @Override
    public boolean checkAnswer(String answer) {
        // Not used directly for PRAC; use checkVfs instead
        return false;
    }

    /**
     * Apply setup items to the given VFS to prepare the environment.
     *
     * @param vfs the virtual file system to initialize
     */
    public void applySetup(VirtualFileSystem vfs) {
        for (SetupItem item : setupItems) {
            try {
                switch (item.getType()) {
                case MKDIR:
                    vfs.createDirectory(item.getPath(), "/", true);
                    break;
                case FILE:
                    // Ensure parent directory exists
                    String parent = getParentPath(item.getPath());
                    if (!parent.equals("/")) {
                        vfs.createDirectory(parent, "/", true);
                    }
                    vfs.createFile(item.getPath(), "/");
                    if (item.getValue() != null && !item.getValue().isEmpty()) {
                        vfs.writeFile(item.getPath(), "/", item.getValue(), false);
                    }
                    break;
                case PERM:
                    if (item.getValue() != null) {
                        FileNode node = vfs.resolve(item.getPath(), "/");
                        node.setPermission(new Permission(item.getValue()));
                    }
                    break;
                default:
                    break;
                }
            } catch (Exception e) {
                // Setup failure is non-fatal; log but continue
                System.err.println("Setup warning: " + e.getMessage());
            }
        }
    }

    private String getParentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return path.substring(0, lastSlash);
    }

    /**
     * Verify that the VFS satisfies all checkpoints.
     *
     * @param vfs the virtual file system after the user's shell session
     * @return true if every checkpoint matches
     */
    public boolean checkVfs(VirtualFileSystem vfs) {
        for (Checkpoint checkpoint : checkpoints) {
            if (!checkpoint.matches(vfs)) {
                return false;
            }
        }
        return true;
    }

    public List<Checkpoint> getCheckpoints() {
        return checkpoints;
    }

    public List<SetupItem> getSetupItems() {
        return setupItems;
    }

    public boolean hasSetup() {
        return !setupItems.isEmpty();
    }
}
