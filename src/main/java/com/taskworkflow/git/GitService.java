package com.taskworkflow.git;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Isola cada tarefa num git worktree próprio, numa branch dedicada, dentro do repositório
 * informado em {@code POST /tasks}. Nunca dá push nem abre PR.
 */
@Slf4j
@Service
public class GitService {

    private static final int GIT_TIMEOUT_SECONDS = 30;

    public void validateRepository(Path repositoryPath) {
        if (!Files.isDirectory(repositoryPath)) {
            throw new GitOperationException("repositoryPath não existe ou não é um diretório: " + repositoryPath);
        }
        String output = run(repositoryPath, "rev-parse", "--is-inside-work-tree");
        if (!"true".equals(output.trim())) {
            throw new GitOperationException("repositoryPath não é um repositório git: " + repositoryPath);
        }
    }

    public Path createWorktree(Path repositoryPath, String branchName) {
        Path worktreePath;
        try {
            Path parent = Files.createTempDirectory("task-workflow-worktrees-");
            worktreePath = parent.resolve(branchName.replace('/', '-'));
        } catch (IOException e) {
            throw new GitOperationException("Falha ao criar diretório para o worktree", e);
        }
        run(repositoryPath, "worktree", "add", "-b", branchName, worktreePath.toString(), "HEAD");
        log.info("Worktree criado. repositoryPath={}, branch={}, worktree={}", repositoryPath, branchName, worktreePath);
        return worktreePath;
    }

    /** Lista os arquivos rastreados no worktree (contexto para a IA decidir o que ler/alterar). */
    public java.util.List<String> listFiles(Path worktreePath) {
        String output = run(worktreePath, "ls-files");
        return output.lines().filter(line -> !line.isBlank()).toList();
    }

    /**
     * Aplica um patch unificado gerado em texto pela IA. {@code --recount} porque LLMs costumam
     * errar a contagem de linhas no cabeçalho do hunk; o git recalcula a partir do conteúdo.
     */
    public void applyPatch(Path worktreePath, String patch) {
        Path patchFile;
        try {
            patchFile = Files.createTempFile("task-workflow-patch-", ".diff");
            Files.writeString(patchFile, patch);
        } catch (IOException e) {
            throw new GitOperationException("Falha ao escrever arquivo de patch temporário", e);
        }
        try {
            run(worktreePath, "apply", "--whitespace=fix", "--recount", patchFile.toString());
        } finally {
            try {
                Files.deleteIfExists(patchFile);
            } catch (IOException e) {
                log.warn("Falha ao limpar arquivo de patch temporário {}", patchFile, e);
            }
        }
    }

    /** Faz `git add -A` e devolve o diff resultante contra o HEAD original da branch. */
    public String stageAndDiff(Path worktreePath) {
        run(worktreePath, "add", "-A");
        return run(worktreePath, "diff", "--cached", "HEAD");
    }

    /** Faz `git add -A` e commita o estado atual como a implementação final. */
    public void commitAll(Path worktreePath, String message) {
        run(worktreePath, "add", "-A");
        try {
            run(worktreePath, "commit", "-m", message);
        } catch (GitOperationException e) {
            log.warn("Nada para commitar em {} (implementação pode não ter alterado arquivos): {}", worktreePath, e.getMessage());
        }
    }

    public void removeWorktree(Path repositoryPath, Path worktreePath) {
        try {
            run(repositoryPath, "worktree", "remove", "--force", worktreePath.toString());
        } catch (GitOperationException e) {
            log.warn("Falha ao remover worktree {}: {}", worktreePath, e.getMessage());
        }
    }

    public void deleteBranch(Path repositoryPath, String branchName) {
        try {
            run(repositoryPath, "branch", "-D", branchName);
        } catch (GitOperationException e) {
            log.warn("Falha ao remover branch {}: {}", branchName, e.getMessage());
        }
    }

    /** Lê um arquivo do worktree, rejeitando caminhos que escapem dele. */
    public String readFile(Path worktreePath, String relativePath) {
        Path resolved = worktreePath.resolve(relativePath).normalize();
        if (!resolved.startsWith(worktreePath.normalize())) {
            throw new GitOperationException("Caminho fora do worktree: " + relativePath);
        }
        if (!Files.isRegularFile(resolved)) {
            return null;
        }
        try {
            return Files.readString(resolved, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Falha ao ler arquivo {} do worktree {}", relativePath, worktreePath, e);
            return null;
        }
    }

    private String run(Path workingDirectory, String... args) {
        try {
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add("git");
            command.addAll(java.util.Arrays.asList(args));

            Process process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .start();

            String stdout = readFully(process.getInputStream());
            String stderr = readFully(process.getErrorStream());

            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GitOperationException("Comando git excedeu o timeout: git " + String.join(" ", args));
            }
            if (process.exitValue() != 0) {
                throw new GitOperationException("Comando 'git " + String.join(" ", args) + "' falhou: " + stderr);
            }
            return stdout;
        } catch (IOException e) {
            throw new GitOperationException("Falha ao executar git " + String.join(" ", args), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitOperationException("Execução do git interrompida", e);
        }
    }

    private String readFully(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
