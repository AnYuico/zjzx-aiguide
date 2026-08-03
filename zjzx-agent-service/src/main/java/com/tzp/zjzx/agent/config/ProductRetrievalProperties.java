package com.tzp.zjzx.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "zjzx.agent.retrieval")
public class ProductRetrievalProperties {

    private boolean vectorEnabled;
    private Duration hybridTimeout = Duration.ofSeconds(8);
    private double similarityThreshold = 0.55D;
    private int vectorCandidateMultiplier = 3;
    private int indexPageSize = 100;
    private int maxIndexDocuments = 10000;

    public boolean isVectorEnabled() {
        return vectorEnabled;
    }

    public void setVectorEnabled(boolean vectorEnabled) {
        this.vectorEnabled = vectorEnabled;
    }

    public Duration getHybridTimeout() {
        return hybridTimeout;
    }

    public void setHybridTimeout(Duration hybridTimeout) {
        this.hybridTimeout = hybridTimeout;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getVectorCandidateMultiplier() {
        return vectorCandidateMultiplier;
    }

    public void setVectorCandidateMultiplier(int vectorCandidateMultiplier) {
        this.vectorCandidateMultiplier = vectorCandidateMultiplier;
    }

    public int getIndexPageSize() {
        return indexPageSize;
    }

    public void setIndexPageSize(int indexPageSize) {
        this.indexPageSize = indexPageSize;
    }

    public int getMaxIndexDocuments() {
        return maxIndexDocuments;
    }

    public void setMaxIndexDocuments(int maxIndexDocuments) {
        this.maxIndexDocuments = maxIndexDocuments;
    }
}
