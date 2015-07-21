package com.pikle6.orion.core;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Created by pikle6 on 7/21/2015.
 * the primary transfer object
 */
public class Chunk implements Serializable{
    private long sequenceNumber;
    /* private long startingIndex; */ // TODO : implement this
    private long documentId;
    private String md5Checksum;
    private long size;
    private byte[] data;

    public Chunk(long sequenceNumber, long documentId, long size) {
        this.sequenceNumber = sequenceNumber;
        this.documentId = documentId;
        this.size = size;
        data = new byte[(int)this.size];
    }

    public String getMd5Checksum() {
        return md5Checksum;
    }

    public void setMd5Checksum(String md5Checksum) {
        this.md5Checksum = md5Checksum;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public long getSequenceNumber() {

        return sequenceNumber;
    }

    public long getDocumentId() {
        return documentId;
    }

    public long getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "sequenceNumber=" + sequenceNumber +
                ", documentId=" + documentId +
                ", md5Checksum='" + md5Checksum + '\'' +
                ", SIZE=" + size +
                '}';
    }
}
