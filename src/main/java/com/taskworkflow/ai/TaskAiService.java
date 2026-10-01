package com.taskworkflow.ai;

import com.taskworkflow.git.GitService;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Camada de domínio sobre o {@link ClaudeCodeClient}: um prompt/schema por etapa do fluxo,
 * usada pelos listeners quando task-workflow.ai.enabled=true.
 */
@Service
@RequiredArgsConstructor
public class TaskAiService {

    private static final int MAX_INPUT_CHARS = 4000;
    private static final int MAX_FILE_CONTENT_CHARS = 3000;
    private static final int MAX_FILES_TO_READ = 5;

    private static final String DEV_SCHEMA = """
            {"type":"object","properties":{\
            "implementation":{"type":"string"},\
            "summary":{"type":"string"}},\
            "required":["implementation","summary"],"additionalProperties":false}""";

    private static final String FILES_TO_READ_SCHEMA = """
            {"type":"object","properties":{\
            "filesToRead":{"type":"array","items":{"type":"string"},"maxItems":5}},\
            "required":["filesToRead"],"additionalProperties":false}""";

    private static final String PATCH_SCHEMA = """
            {"type":"object","properties":{\
            "patch":{"type":"string"},\
            "summary":{"type":"string"}},\
            "required":["patch","summary"],"additionalProperties":false}""";

    private static final String REVIEW_SCHEMA = """
            {"type":"object","properties":{\
            "approved":{"type":"boolean"},\
            "feedback":{"type":"string"}},\
            "required":["approved","feedback"],"additionalProperties":false}""";

    private static final String TEST_SCHEMA = """
            {"type":"object","properties":{\
            "succeeded":{"type":"boolean"},\
            "report":{"type":"string"}},\
            "required":["succeeded","report"],"additionalProperties":false}""";

    private final ClaudeCodeClient claudeCodeClient;
    private final GitService gitService;

    public record DevelopmentResult(String implementation, String summary) {
    }

    private record FilesToRead(List<String> filesToRead) {
    }

    public record PatchResult(String patch, String summary) {
    }

    public record ReviewResult(boolean approved, String feedback) {
    }

    public record TestResult(boolean succeeded, String report) {
    }

    public DevelopmentResult implement(String taskName, String previousFeedback) {
        String system = "Você é um desenvolvedor backend sênior. Implemente exatamente o que a "
                + "tarefa pede, de forma concisa (pseudocódigo ou trecho de código real). "
                + "Se houver feedback de uma tentativa anterior rejeitada, corrija o problema apontado.";
        String user = userPrompt(taskName, previousFeedback);
        return claudeCodeClient.runJudge(system, user, DEV_SCHEMA, DevelopmentResult.class);
    }

    /** Pergunta quais arquivos ler, lê-os via Java e pede o patch final — a IA nunca acessa arquivos. */
    public PatchResult implementAsPatch(Path worktreePath, String taskName, String previousFeedback) {
        List<String> projectFiles = gitService.listFiles(worktreePath);
        String fileList = truncate(String.join("\n", projectFiles));

        String step1System = "Você é um desenvolvedor backend sênior. Dada a lista de arquivos de um projeto e "
                + "uma tarefa, responda quais arquivos existentes você precisa ler para implementá-la "
                + "(no máximo 5). Não liste arquivos que ainda não existem.";
        String step1User = userPrompt(taskName, previousFeedback) + "\n\nArquivos do projeto:\n" + fileList;
        FilesToRead filesToRead = claudeCodeClient.runJudge(step1System, step1User, FILES_TO_READ_SCHEMA, FilesToRead.class);

        StringBuilder fileContents = new StringBuilder();
        for (String relativePath : filesToRead.filesToRead().stream().limit(MAX_FILES_TO_READ).toList()) {
            String content = gitService.readFile(worktreePath, relativePath);
            if (content != null) {
                fileContents.append("--- ").append(relativePath).append(" ---\n")
                        .append(truncateFileContent(content)).append("\n\n");
            }
        }

        String step2System = "Você é um desenvolvedor backend sênior. Gere um patch unificado (formato "
                + "`git diff`, aplicável com `git apply`) que implemente exatamente a tarefa pedida. "
                + "Use os caminhos de arquivo exatamente como fornecidos. Para um arquivo novo, use o "
                + "formato padrão de diff com '--- /dev/null'. Se houver feedback de uma tentativa anterior "
                + "rejeitada, corrija exatamente o que foi apontado.";
        String step2User = userPrompt(taskName, previousFeedback)
                + "\n\nArquivos do projeto:\n" + fileList
                + "\n\nConteúdo dos arquivos relevantes:\n" + fileContents;
        return claudeCodeClient.runJudge(step2System, step2User, PATCH_SCHEMA, PatchResult.class);
    }

    public ReviewResult review(String taskName, String implementation) {
        String system = "Você é um revisor de código criterioso, mas justo. Avalie se a implementação "
                + "atende à tarefa pedida. Aprove implementações razoáveis; rejeite apenas problemas reais, "
                + "explicando exatamente o que precisa mudar.";
        String user = "Tarefa: " + truncate(taskName) + "\n\nImplementação:\n" + truncate(implementation);
        return claudeCodeClient.runJudge(system, user, REVIEW_SCHEMA, ReviewResult.class);
    }

    public TestResult test(String taskName, String implementation) {
        String system = "Você é um engenheiro de QA. Analise estaticamente (sem executar) se a implementação "
                + "abaixo cumpre os requisitos funcionais da tarefa e não contém bugs óbvios. "
                + "Decida se os testes passariam.";
        String user = "Tarefa: " + truncate(taskName) + "\n\nImplementação:\n" + truncate(implementation);
        return claudeCodeClient.runJudge(system, user, TEST_SCHEMA, TestResult.class);
    }

    private String userPrompt(String taskName, String previousFeedback) {
        StringBuilder user = new StringBuilder("Tarefa: ").append(truncate(taskName));
        if (previousFeedback != null && !previousFeedback.isBlank()) {
            user.append("\n\nFeedback da tentativa anterior (corrija isto): ").append(truncate(previousFeedback));
        }
        return user.toString();
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_INPUT_CHARS ? text.substring(0, MAX_INPUT_CHARS) : text;
    }

    private String truncateFileContent(String text) {
        return text.length() > MAX_FILE_CONTENT_CHARS ? text.substring(0, MAX_FILE_CONTENT_CHARS) + "\n...(truncado)" : text;
    }
}
