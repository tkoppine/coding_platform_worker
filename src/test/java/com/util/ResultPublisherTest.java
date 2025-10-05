package com.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

@ExtendWith(MockitoExtension.class)
public class ResultPublisherTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private ObjectMapper objectMapper;

    private ResultPublisher resultPublisher;

    private final String QUEUE_URL = "https://sqs.example.com/queue";
    private final String JOB_ID = "job-123";
    private final String RESULT = "test result";

    @BeforeEach
    public void setup() {
        resultPublisher = new ResultPublisher(sqsClient, objectMapper);
    }

    @Test
    public void testPublishResult_Success() throws JsonProcessingException {
        // Arrange
        String expectedJson = "{\"jobId\":\"job-123\",\"result\":\"test result\"}";
        when(objectMapper.writeValueAsString(any())).thenReturn(expectedJson);

        // Act
        resultPublisher.publishResult(QUEUE_URL, JOB_ID, RESULT);

        // Assert
        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(requestCaptor.capture());

        SendMessageRequest capturedRequest = requestCaptor.getValue();
        assertEquals(QUEUE_URL, capturedRequest.queueUrl());
        assertEquals(expectedJson, capturedRequest.messageBody());

        ArgumentCaptor<ResultPublisher.ResultMessage> messageCaptor = ArgumentCaptor
                .forClass(ResultPublisher.ResultMessage.class);
        verify(objectMapper).writeValueAsString(messageCaptor.capture());

        ResultPublisher.ResultMessage capturedMessage = messageCaptor.getValue();
        assertEquals(JOB_ID, capturedMessage.getJobId());
        assertEquals(RESULT, capturedMessage.getResult());
    }

    @Test
    public void testPublishResult_JsonProcessingException() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization error") {
        });

        // Act
        resultPublisher.publishResult(QUEUE_URL, JOB_ID, RESULT);

        // Assert
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    public void testPublishResult_SqsException() throws JsonProcessingException {
        // Arrange
        String expectedJson = "{\"jobId\":\"job-123\",\"result\":\"test result\"}";
        when(objectMapper.writeValueAsString(any())).thenReturn(expectedJson);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(SqsException.builder().message("SQS error").build());

        // Act
        resultPublisher.publishResult(QUEUE_URL, JOB_ID, RESULT);

        // Assert
        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    public void testResultMessage() {
        // Arrange & Act
        ResultPublisher.ResultMessage message = new ResultPublisher.ResultMessage(JOB_ID, RESULT);

        // Assert
        assertEquals(JOB_ID, message.getJobId());
        assertEquals(RESULT, message.getResult());
    }
}