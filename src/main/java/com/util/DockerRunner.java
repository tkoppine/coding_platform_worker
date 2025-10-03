package com.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

@Service
public class DockerRunner {
    public String runContainer(String language, String localFilePath) throws Exception {
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
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        process.waitFor();

        return output.toString();
    }
}
