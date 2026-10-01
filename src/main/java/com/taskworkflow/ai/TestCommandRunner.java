package com.taskworkflow.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Executa o comando de teste real do projeto no worktree; sucesso/falha vem do exit code, não da IA. */
@Slf4j
@Component
public class TestCommandRunner {

    private static final int MAX_OUTPUT_CHARS = 8000;

    public record Result(boolean succeeded, String output) {
    }

    public Result run(Path worktreePath, String command, int timeoutSeconds) {
        List<String> shellCommand = isWindows()
                ? List.of("cmd.exe", "/c", command)
                : List.of("/bin/sh", "-c", command);
        try {
            Process process = new ProcessBuilder(shellCommand)
                    .directory(worktreePath.toFile())
                    .redirectErrorStream(true)
                    .start();

            String output = readFully(process.getInputStream());

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Result(false, "Comando de teste excedeu o timeout de " + timeoutSeconds + "s.\n" + truncate(output));
            }
            boolean succeeded = process.exitValue() == 0;
            return new Result(succeeded, truncate(output));
        } catch (IOException e) {
            log.error("Falha ao executar comando de teste '{}' em {}", command, worktreePath, e);
            return new Result(false, "Falha ao executar o comando de teste: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, "Execução do comando de teste interrompida");
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private String readFully(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private String truncate(String text) {
        return text.length() > MAX_OUTPUT_CHARS ? text.substring(0, MAX_OUTPUT_CHARS) + "\n...(truncado)" : text;
    }
}
