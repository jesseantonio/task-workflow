package com.taskworkflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskworkflow.config.TaskWorkflowProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Chama o Claude Code CLI ({@code claude -p}) em modo não-interativo, autenticado com a
 * sessão/plano do usuário. Sempre sem ferramentas ({@code --tools ""}): a IA só gera texto/JSON;
 * qualquer alteração em arquivos é aplicada por código Java determinístico (ver GitService).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeCodeClient {

    private final TaskWorkflowProperties properties;
    private final ObjectMapper objectMapper;

    public <T> T runJudge(String systemPrompt, String userPrompt, String jsonSchema, Class<T> responseType) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("task-workflow-ai-");
            List<String> command = baseCommand(systemPrompt, userPrompt, jsonSchema);
            command.add("--tools");
            command.add("");
            return execute(command, workDir, responseType);
        } catch (IOException e) {
            throw new AiWorkerException("Falha ao preparar diretório temporário para o Claude CLI", e);
        } finally {
            deleteQuietly(workDir);
        }
    }

    private List<String> baseCommand(String systemPrompt, String userPrompt, String jsonSchema) {
        TaskWorkflowProperties.Ai ai = properties.ai();
        List<String> command = new ArrayList<>(resolveClaudeExecutable());
        command.add("-p");
        command.add(userPrompt);
        command.add("--output-format");
        command.add("json");
        command.add("--system-prompt");
        command.add(systemPrompt);
        command.add("--json-schema");
        command.add(jsonSchema);
        command.add("--no-session-persistence");
        command.add("--strict-mcp-config");
        command.add("--setting-sources");
        command.add("");
        command.add("--max-budget-usd");
        command.add(String.valueOf(ai.maxBudgetUsd()));
        if (ai.model() != null && !ai.model().isBlank()) {
            command.add("--model");
            command.add(ai.model());
        }
        if (isWindows()) {
            // ProcessBuilder no Windows remove aspas duplas embutidas nos argumentos; \" sobrevive.
            command.replaceAll(arg -> arg.replace("\"", "\\\""));
        }
        return command;
    }

    /** No Windows, resolve o claude.exe nativo (via PATH) por trás do shim claude.cmd do npm. */
    private List<String> resolveClaudeExecutable() {
        if (!isWindows()) {
            return List.of("claude");
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                Path shim = Path.of(dir, "claude.cmd");
                if (Files.isRegularFile(shim) && shim.getParent() != null) {
                    Path exe = shim.getParent().resolve(Path.of("node_modules", "@anthropic-ai", "claude-code", "bin", "claude.exe"));
                    if (Files.isRegularFile(exe)) {
                        return List.of(exe.toString());
                    }
                    break;
                }
            }
        }
        log.warn("claude.exe não encontrado ao lado de um claude.cmd no PATH; usando claude.cmd via cmd.exe como fallback.");
        return List.of("claude.cmd");
    }

    private <T> T execute(List<String> command, Path workingDirectory, Class<T> responseType) {
        TaskWorkflowProperties.Ai ai = properties.ai();
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(false)
                    .start();

            // Lê stdout/stderr em paralelo pra evitar deadlock se um stream encher o pipe do SO.
            ExecutorService streamReaders = Executors.newFixedThreadPool(2);
            String stdout;
            String stderr;
            try {
                Future<String> stdoutFuture = streamReaders.submit(() -> readFully(process.getInputStream()));
                Future<String> stderrFuture = streamReaders.submit(() -> readFully(process.getErrorStream()));

                boolean finished = process.waitFor(ai.timeoutSeconds(), TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new AiWorkerException("Claude CLI excedeu o timeout de " + ai.timeoutSeconds() + "s");
                }
                stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
                stderr = stderrFuture.get(5, TimeUnit.SECONDS);
            } finally {
                streamReaders.shutdown();
            }

            if (process.exitValue() != 0) {
                throw new AiWorkerException("Claude CLI saiu com código " + process.exitValue() + ": " + stderr);
            }

            JsonNode root = objectMapper.readTree(stdout);
            if (root.path("is_error").asBoolean(false)) {
                throw new AiWorkerException("Claude CLI retornou erro: " + root.path("result").asText(stderr));
            }
            JsonNode structuredOutput = root.path("structured_output");
            if (structuredOutput.isMissingNode() || structuredOutput.isNull()) {
                throw new AiWorkerException("Claude CLI não retornou structured_output. result=" + root.path("result").asText());
            }
            return objectMapper.treeToValue(structuredOutput, responseType);
        } catch (IOException e) {
            throw new AiWorkerException("Falha ao invocar o Claude CLI", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiWorkerException("Chamada ao Claude CLI interrompida", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AiWorkerException("Falha ao ler saída do Claude CLI", e);
        }
    }

    private String readFully(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Falha ao limpar diretório temporário {}", path, e);
        }
    }
}
