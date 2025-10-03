package com.model;

public class JobMessage {
    private String jobId;
    private String s3Key;
    private String language;

    public JobMessage() {
    }

    public JobMessage(String jobId, String s3Key, String language) {
        this.jobId = jobId;
        this.s3Key = s3Key;
        this.language = language;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @Override
    public String toString() {
        return "JobMessage{" +
                "jobId='" + jobId + '\'' +
                ", s3Key='" + s3Key + '\'' +
                ", language='" + language + '\'' +
                '}';
    }
}
