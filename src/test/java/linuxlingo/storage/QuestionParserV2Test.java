package linuxlingo.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import linuxlingo.exam.Checkpoint;
import linuxlingo.exam.question.PracQuestion;
import linuxlingo.exam.question.Question;

/**
 * Tests for v2.0 QuestionParser enhancements:
 * new checkpoint types (NOT_EXISTS, CONTENT_EQUALS, PERM) and SETUP items.
 */
public class QuestionParserV2Test {
    @TempDir
    Path tempDir;

    private Path createTempFile(String content) throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, content);
        return file;
    }

    // ─── PRAC with standard DIR/FILE checkpoints ────────────────

    @Test
    public void parsePrac_standardCheckpoints_parsesCorrectly() throws Exception {
        Path file = createTempFile(
                "PRAC | EASY | Create dirs | /home/user/a:DIR,/home/user/b.txt:FILE | | Use mkdir and touch"
        );
        List<Question> questions = QuestionParser.parseFile(file);
        assertEquals(1, questions.size());
        assertTrue(questions.get(0) instanceof PracQuestion);
        PracQuestion pq = (PracQuestion) questions.get(0);
        assertEquals(2, pq.getCheckpoints().size());
        assertEquals(Checkpoint.NodeType.DIR, pq.getCheckpoints().get(0).getExpectedType());
        assertEquals(Checkpoint.NodeType.FILE, pq.getCheckpoints().get(1).getExpectedType());
    }

    // ─── PRAC with NOT_EXISTS ───────────────────────────────────

    @Test
    public void parsePrac_notExists_parsesCorrectly() throws Exception {
        Path file = createTempFile(
                "PRAC | MEDIUM | Delete a file | /home/user/old.txt:NOT_EXISTS | | Use rm to delete"
        );
        List<Question> questions = QuestionParser.parseFile(file);
        assertEquals(1, questions.size());
        PracQuestion pq = (PracQuestion) questions.get(0);
        assertEquals(1, pq.getCheckpoints().size());
        assertEquals(Checkpoint.NodeType.NOT_EXISTS, pq.getCheckpoints().get(0).getExpectedType());
    }

    // ─── PRAC with CONTENT_EQUALS ───────────────────────────────

    @Test
    public void parsePrac_contentEquals_parsesCorrectly() throws Exception {
        Path file = createTempFile(
                "PRAC | HARD | Write content | /home/user/out.txt:CONTENT_EQUALS=hello world"
                        + " | | Use echo and redirect"
        );
        List<Question> questions = QuestionParser.parseFile(file);
        assertEquals(1, questions.size());
        PracQuestion pq = (PracQuestion) questions.get(0);
        assertEquals(1, pq.getCheckpoints().size());
        Checkpoint cp = pq.getCheckpoints().get(0);
        assertEquals(Checkpoint.NodeType.CONTENT_EQUALS, cp.getExpectedType());
        assertEquals("hello world", cp.getExpectedContent());
    }

    // ─── PRAC with PERM ────────────────────────────────────────

    @Test
    public void parsePrac_perm_parsesCorrectly() throws Exception {
        Path file = createTempFile(
                "PRAC | MEDIUM | Set permissions"
                        + " | /home/user/script.sh:PERM=rwxr-xr-x | | Use chmod"
        );
        List<Question> questions = QuestionParser.parseFile(file);
        assertEquals(1, questions.size());
        PracQuestion pq = (PracQuestion) questions.get(0);
        assertEquals(1, pq.getCheckpoints().size());
        Checkpoint cp = pq.getCheckpoints().get(0);
        assertEquals(Checkpoint.NodeType.PERM, cp.getExpectedType());
        assertEquals("rwxr-xr-x", cp.getExpectedPermission());
    }

    // ─── PRAC with mixed checkpoint types ───────────────────────

    @Test
    public void parsePrac_mixedCheckpoints_parsesAll() throws Exception {
        Path file = createTempFile(
                "PRAC | HARD | Complex task"
                        + " | /home/user/dir:DIR,/home/user/out.txt:CONTENT_EQUALS=data"
                        + ",/home/user/old:NOT_EXISTS | | Complex question"
        );
        List<Question> questions = QuestionParser.parseFile(file);
        assertEquals(1, questions.size());
        PracQuestion pq = (PracQuestion) questions.get(0);
        assertEquals(3, pq.getCheckpoints().size());
        assertEquals(Checkpoint.NodeType.DIR, pq.getCheckpoints().get(0).getExpectedType());
        assertEquals(Checkpoint.NodeType.CONTENT_EQUALS, pq.getCheckpoints().get(1).getExpectedType());
        assertEquals(Checkpoint.NodeType.NOT_EXISTS, pq.getCheckpoints().get(2).getExpectedType());
    }

    // ─── PRAC with SETUP items ──────────────────────────────────

    @Test
    public void parsePrac_withSetup_mkdir() throws Exception {
        Path file = createTempFile(
                "PRAC | EASY | Do a task | /home/user/out.txt:FILE | MKDIR:/home/user/project | Explanation"
        );
        List<Question> questions = QuestionParser.parseFile(file);
        assertEquals(1, questions.size());
        PracQuestion pq = (PracQuestion) questions.get(0);
        assertTrue(pq.hasSetup());
        assertEquals(1, pq.getSetupItems().size());
        assertEquals(PracQuestion.SetupItem.SetupType.MKDIR,
                pq.getSetupItems().get(0).getType());
        assertEquals("/home/user/project", pq.getSetupItems().get(0).getPath());
    }

    @Test
    public void parsePrac_withSetup_fileWithContent() throws Exception {
        Path file = createTempFile(
                "PRAC | MEDIUM | Process file | /home/user/result.txt:FILE"
                        + " | FILE:/home/user/data.txt=hello | Explanation"
        );
        List<Question> questions = QuestionParser.parseFile(file);
        PracQuestion pq = (PracQuestion) questions.get(0);
        assertTrue(pq.hasSetup());
        PracQuestion.SetupItem item = pq.getSetupItems().get(0);
        assertEquals(PracQuestion.SetupItem.SetupType.FILE, item.getType());
        assertEquals("/home/user/data.txt", item.getPath());
        assertEquals("hello", item.getValue());
    }

    @Test
    public void parsePrac_withSetup_perm() throws Exception {
        Path file = createTempFile(
                "PRAC | HARD | Fix perms | /home/user/script.sh:PERM=rwx------"
                        + " | PERM:/home/user/script.sh=rw-r--r-- | Explanation"
        );
        List<Question> questions = QuestionParser.parseFile(file);
        PracQuestion pq = (PracQuestion) questions.get(0);
        assertTrue(pq.hasSetup());
        PracQuestion.SetupItem item = pq.getSetupItems().get(0);
        assertEquals(PracQuestion.SetupItem.SetupType.PERM, item.getType());
        assertEquals("rw-r--r--", item.getValue());
    }

    @Test
    public void parsePrac_withMultipleSetups_parsesAll() throws Exception {
        Path file = createTempFile(
                "PRAC | HARD | Complex | /home/user/out.txt:FILE"
                        + " | MKDIR:/home/user/project;FILE:/home/user/project/data.txt=initial"
                        + ";PERM:/home/user/project/data.txt=rw-r--r-- | Explanation"
        );
        List<Question> questions = QuestionParser.parseFile(file);
        PracQuestion pq = (PracQuestion) questions.get(0);
        assertEquals(3, pq.getSetupItems().size());
        assertEquals(PracQuestion.SetupItem.SetupType.MKDIR, pq.getSetupItems().get(0).getType());
        assertEquals(PracQuestion.SetupItem.SetupType.FILE, pq.getSetupItems().get(1).getType());
        assertEquals(PracQuestion.SetupItem.SetupType.PERM, pq.getSetupItems().get(2).getType());
    }

    @Test
    public void parsePrac_noSetup_emptySetupItems() throws Exception {
        Path file = createTempFile(
                "PRAC | EASY | Simple task | /home/user/dir:DIR | | Explanation"
        );
        List<Question> questions = QuestionParser.parseFile(file);
        PracQuestion pq = (PracQuestion) questions.get(0);
        assertFalse(pq.hasSetup());
        assertEquals(0, pq.getSetupItems().size());
    }

    // ─── Backward compatibility ─────────────────────────────────

    @Test
    public void parsePrac_legacyFormat_stillWorks() throws Exception {
        Path file = createTempFile(
                "PRAC | EASY | Create a directory | /home/user/test:DIR | | Use mkdir"
        );
        List<Question> questions = QuestionParser.parseFile(file);
        assertEquals(1, questions.size());
        PracQuestion pq = (PracQuestion) questions.get(0);
        assertEquals(Checkpoint.NodeType.DIR, pq.getCheckpoints().get(0).getExpectedType());
        assertFalse(pq.hasSetup());
    }
}
