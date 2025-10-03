package com.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

@Service
public class DockerRunner {
    public static class ExecutionResponse {
        private final String output;
        private final long executionTimeMs;

        public ExecutionResponse(String output, long executionTimeMs) {
            this.output = output;
            this.executionTimeMs = executionTimeMs;
        }

        public String getOutput() {
            return output;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs;
        }
    }

    public ExecutionResponse runContainer(String language, String localFilePath) throws Exception {
        Path filePath = Path.of(localFilePath);
        Path parentDir = filePath.getParent();

        String image;
        String command;

        if (language.equalsIgnoreCase("java")) {
            image = "tkoppine/java-runner";
            command = "javac /app/" + filePath.getFileName() + " && java -cp /app " +
                    filePath.getFileName().toString().replace(".java", "");
        } else if (language.equalsIgnoreCase("python")) {
            image = "tkoppine/python-runner";
            command = "python /app/" + filePath.getFileName();
        } else {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", parentDir.toAbsolutePath() + ":/app",
                image,
                "sh", "-c", command);

        pb.redirectErrorStream(true);

        Long startTime = System.currentTimeMillis();
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        StringBuilder output = new StringBuilder();
        Thread readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (java.io.IOException | RuntimeException e) {
                System.err.println("Error reading process output: " + e.getMessage());
            }
        });
        readerThread.start();
        boolean finished = process.waitFor(90, TimeUnit.SECONDS);

        Long endTime = System.currentTimeMillis();

        Long durationMs = endTime - startTime;

        if (!finished) {
            process.destroyForcibly();
            process.waitFor(15, TimeUnit.SECONDS);
            return new ExecutionResponse("Time limit exceeded", durationMs);
        }

        readerThread.join();

        return new ExecutionResponse(output.toString(), durationMs);
    }
}
