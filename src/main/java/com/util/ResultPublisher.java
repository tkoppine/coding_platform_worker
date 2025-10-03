package com.util;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
public class ResultPublisher {
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    public ResultPublisher(SqsClient sqsClient, ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
    }

    public void publishResult(String responseQueueUrl, String jobId, String result) {
        try {
            ResultMessage resultMessage = new ResultMessage(jobId, result);
            String body = objectMapper.writeValueAsString(resultMessage);

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(responseQueueUrl)
                    .messageBody(body)
                    .build());

            System.out.println("Published result for jobId=" + jobId);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            System.err.println("Failed to serialize result message: " + e.getMessage());
        } catch (software.amazon.awssdk.services.sqs.model.SqsException e) {
            System.err.println("Failed to send message to SQS: " + e.getMessage());
        }
    }

    static class ResultMessage {
        private final String jobId;
        private final String result;

        public ResultMessage(String jobId, String result) {
            this.jobId = jobId;
            this.result = result;
        }

        public String getJobId() {
            return jobId;
        }

        public String getResult() {
            return result;
        }
    }
}
