package com.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.model.JobMessage;
import com.util.DockerRunner;
import com.util.ResultPublisher;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@Service
public class WorkerService {
    private static final String REQUEST_QUEUE_URL = "https://sqs.us-east-2.amazonaws.com/012560051368/code-submission-request";
    private static final String RESPONSE_QUEUE_URL = "https://sqs.us-east-2.amazonaws.com/012560051368/code-results-response";
    private static final String BUCKET_NAME = "candidate-code-submission";

    private final SqsClient sqsClient;
    private final S3Client s3Client;
    private final DockerRunner dockerRunner;
    private final ResultPublisher resultPublisher;
    private final ObjectMapper objectMapper;

    public WorkerService(SqsClient sqsClient, S3Client s3Client, DockerRunner dockerRunner,
            ResultPublisher resultPublisher, ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.s3Client = s3Client;
        this.dockerRunner = dockerRunner;
        this.resultPublisher = resultPublisher;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void startListening() {
        System.out.println("Starting Worker Service...");
        new Thread(this::start).start();
    }

    public void start() {
        System.out.println("Worker Service started, listening for messages...");

        while (true) {
            ReceiveMessageResponse sqsResponse = sqsClient.receiveMessage(
                    ReceiveMessageRequest.builder()
                            .queueUrl(REQUEST_QUEUE_URL)
                            .maxNumberOfMessages(1)
                            .waitTimeSeconds(10)
                            .build());

            if (sqsResponse.messages().isEmpty()) {
                continue;
            }

            for (Message sqsMessage : sqsResponse.messages()) {
                Path localFilePath = null;
                try {
                    JobMessage job = objectMapper.readValue(sqsMessage.body(), JobMessage.class);
                    System.out.println("Received Job: " + job);

                    localFilePath = downloadCode(job.getJobId(), job.getS3Key());

                    String finalResult;
                    try {
                        DockerRunner.ExecutionResponse execResponse = dockerRunner.runContainer(job.getLanguage(),
                                localFilePath.toString());

                        String parsedResult = parseResult(execResponse.getOutput());

                        finalResult = "{"
                                + "\"jobId\":\"" + job.getJobId() + "\","
                                + "\"executionTimeMs\":" + execResponse.getExecutionTimeMs() + ","
                                + "\"result\":" + parsedResult
                                + "}";
                    } catch (Exception e) {
                        System.err.println(e);
                        resultPublisher.publishResult(RESPONSE_QUEUE_URL, job.getJobId(),
                                "Container Execution Error: " + e.getMessage());
                        continue;
                    }

                    resultPublisher.publishResult(RESPONSE_QUEUE_URL, job.getJobId(), finalResult);

                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                            .queueUrl(REQUEST_QUEUE_URL)
                            .receiptHandle(sqsMessage.receiptHandle())
                            .build());

                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    System.err.println(e);
                    resultPublisher.publishResult(RESPONSE_QUEUE_URL, "unknown",
                            "JSON Processing Error: " + e.getMessage());
                } catch (java.io.IOException e) {
                    System.err.println(e);
                    resultPublisher.publishResult(RESPONSE_QUEUE_URL, "unknown", "IO Error: " + e.getMessage());
                } catch (RuntimeException e) {
                    System.err.println(e);
                    resultPublisher.publishResult(RESPONSE_QUEUE_URL, "unknown", "Runtime Error: " + e.getMessage());
                } finally {
                    if (localFilePath != null) {
                        try {
                            Files.deleteIfExists(localFilePath);
                            System.out.println("Deleted file " + localFilePath);
                        } catch (IOException e) {
                            System.err.println("Failed to delete file: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private String parseResult(String output) {
        for (String line : output.split("\n")) {
            if (line.startsWith("RESULT:")) {
                return line.substring(7);
            }
        }
        return "{"
                + "\"status\":\"error\","
                + "\"message\":\"" + summarizeError(output) + "\""
                + "}";
    }

    private String summarizeError(String output) {
        String[] lines = output.split("\n");
        if (lines.length > 0) {
            return lines[0].replace("\"", "'");
        }
        return "Unknown error";
    }

    private Path downloadCode(String jobId, String s3Key) throws IOException {

        Path tempDir = Files.createTempDirectory("submission-" + jobId + "-");

        String fileName = Path.of(s3Key).getFileName().toString();

        Path localFile = tempDir.resolve(fileName);

        s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(s3Key)
                        .build(),
                localFile);

        return localFile;
    }

}
