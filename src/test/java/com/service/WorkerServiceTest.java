package com.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.model.JobMessage;
import com.util.DockerRunner;
import com.util.ResultPublisher;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@ExtendWith(MockitoExtension.class)
public class WorkerServiceTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private S3Client s3Client;

    @Mock
    private DockerRunner dockerRunner;

    @Mock
    private ResultPublisher resultPublisher;

    @Mock
    private ObjectMapper objectMapper;

    private WorkerService workerService;

    private static final String REQUEST_QUEUE_URL = "request-queue-url";
    private static final String RESPONSE_QUEUE_URL = "response-queue-url";
    private static final String BUCKET_NAME = "bucket-name";

    @BeforeEach
    public void setup() {
        workerService = new WorkerService(sqsClient, s3Client, dockerRunner, resultPublisher, objectMapper);
        ReflectionTestUtils.setField(workerService, "REQUEST_QUEUE_URL", REQUEST_QUEUE_URL);
        ReflectionTestUtils.setField(workerService, "RESPONSE_QUEUE_URL", RESPONSE_QUEUE_URL);
        ReflectionTestUtils.setField(workerService, "BUCKET_NAME", BUCKET_NAME);
    }

    @Test
    public void testStart_successfulJobExecution() throws Exception {
        Message mockMessage = mock(Message.class);
        when(mockMessage.body())
                .thenReturn("{\"jobId\":\"job-123\", \"language\":\"java\", \"s3Key\":\"path/file.java\"}");
        when(mockMessage.receiptHandle()).thenReturn("receipt-handle");

        ReceiveMessageResponse mockResponse = mock(ReceiveMessageResponse.class);
        when(mockResponse.messages()).thenReturn(List.of(mockMessage));

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockResponse)
                .thenReturn(mock(ReceiveMessageResponse.class));

        JobMessage mockJob = new JobMessage();
        mockJob.setJobId("job-123");
        mockJob.setLanguage("java");
        mockJob.setS3Key("path/file.java");
        when(objectMapper.readValue(anyString(), eq(JobMessage.class))).thenReturn(mockJob);

        Path mockPath = Files.createTempFile("test", ".java");
        doAnswer(invocation -> {
            return null;
        }).when(s3Client).getObject(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class), any(Path.class));

        DockerRunner.ExecutionResponse execResponse = new DockerRunner.ExecutionResponse("RESULT:{\"success\":true}", 100L);
        when(dockerRunner.runContainer(eq("java"), anyString())).thenReturn(execResponse);

        Thread workerThread = new Thread(() -> {
            try {
                workerService.start();
            } catch (Exception e) {
                // Expected when we mock empty responses to exit loop
            }
        });

        workerThread.start();
        Thread.sleep(100);
        workerThread.interrupt();

        verify(resultPublisher).publishResult(eq(RESPONSE_QUEUE_URL), eq("job-123"), 
                contains("\"success\":true"));
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));

        Files.deleteIfExists(mockPath);
    }

    @Test
    public void testStart_withErrorOutput() throws Exception {
        Message mockMessage = mock(Message.class);
        when(mockMessage.body())
                .thenReturn("{\"jobId\":\"job-123\", \"language\":\"java\", \"s3Key\":\"path/file.java\"}");
        when(mockMessage.receiptHandle()).thenReturn("receipt-handle");

        ReceiveMessageResponse mockResponse = mock(ReceiveMessageResponse.class);
        when(mockResponse.messages()).thenReturn(List.of(mockMessage));

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockResponse)
                .thenReturn(mock(ReceiveMessageResponse.class));

        JobMessage mockJob = new JobMessage();
        mockJob.setJobId("job-123");
        mockJob.setLanguage("java");
        mockJob.setS3Key("path/file.java");
        when(objectMapper.readValue(anyString(), eq(JobMessage.class))).thenReturn(mockJob);

        Path mockPath = Files.createTempFile("test", ".java");

        DockerRunner.ExecutionResponse execResponse = new DockerRunner.ExecutionResponse(
                "Error \"message\"\nStack trace", 100L);
        when(dockerRunner.runContainer(eq("java"), anyString())).thenReturn(execResponse);

        Thread workerThread = new Thread(() -> {
            try {
                workerService.start();
            } catch (Exception e) {
                // Expected
            }
        });

        workerThread.start();
        Thread.sleep(100);
        workerThread.interrupt();

        verify(resultPublisher).publishResult(eq(RESPONSE_QUEUE_URL), eq("job-123"), 
                contains("\"status\":\"error\""));
        verify(resultPublisher).publishResult(eq(RESPONSE_QUEUE_URL), eq("job-123"), 
                contains("\"message\":\"Error 'message'\""));

        Files.deleteIfExists(mockPath);
    }

    @Test
    public void testStart_handlesDockerExecutionError() throws Exception {
        Message mockMessage = mock(Message.class);
        when(mockMessage.body())
                .thenReturn("{\"jobId\":\"job-123\", \"language\":\"java\", \"s3Key\":\"path/file.java\"}");

        ReceiveMessageResponse mockResponse = mock(ReceiveMessageResponse.class);
        when(mockResponse.messages()).thenReturn(List.of(mockMessage));

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockResponse)
                .thenReturn(mock(ReceiveMessageResponse.class));

        JobMessage mockJob = new JobMessage();
        mockJob.setJobId("job-123");
        mockJob.setLanguage("java");
        mockJob.setS3Key("path/file.java");
        when(objectMapper.readValue(anyString(), eq(JobMessage.class))).thenReturn(mockJob);

        when(dockerRunner.runContainer(eq("java"), anyString())).thenThrow(new RuntimeException("Docker error"));

        Thread workerThread = new Thread(() -> {
            try {
                workerService.start();
            } catch (Exception e) {
                // Expected
            }
        });

        workerThread.start();
        Thread.sleep(100);
        workerThread.interrupt();

        verify(resultPublisher).publishResult(eq(RESPONSE_QUEUE_URL), eq("job-123"),
                contains("Container Execution Error"));
    }

    @Test
    public void testStart_handlesJsonProcessingException() throws Exception {
        Message mockMessage = mock(Message.class);
        when(mockMessage.body()).thenReturn("invalid-json");

        ReceiveMessageResponse mockResponse = mock(ReceiveMessageResponse.class);
        when(mockResponse.messages()).thenReturn(List.of(mockMessage));

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockResponse)
                .thenReturn(mock(ReceiveMessageResponse.class));

        when(objectMapper.readValue(anyString(), eq(JobMessage.class)))
                .thenThrow(new JsonProcessingException("Invalid JSON") {});

        Thread workerThread = new Thread(() -> {
            try {
                workerService.start();
            } catch (Exception e) {
                // Expected
            }
        });

        workerThread.start();
        Thread.sleep(100);
        workerThread.interrupt();

        verify(resultPublisher).publishResult(eq(RESPONSE_QUEUE_URL), eq("unknown"), 
                contains("JSON Processing Error"));
    }
}
