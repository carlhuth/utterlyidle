package com.googlecode.utterlyidle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static com.googlecode.utterlyidle.HeaderParameters.headerParameters;

public class MemoryResponse implements Response {
    private Status status;
    private HeaderParameters headers = headerParameters();
    private ByteArrayOutputStream output = new ByteArrayOutputStream();
    private Object entity;

    public MemoryResponse() {
    }

    public MemoryResponse(Status status, HeaderParameters headers, Object entity) {
        this.status = status;
        this.headers = headers;
        this.entity = entity;
    }

    public Status status() {
        return status;
    }

    public Response status(Status value) {
        this.status = value;
        return this;
    }

    public String header(String name) {
        return headers.getValue(name);
    }

    public Iterable<String> headers(String name) {
        return headers.getValues(name);
    }

    public HeaderParameters headers() {
        return headers;
    }

    public Response header(String name, String value) {
        headers.add(name, value);
        return this;
    }

    public OutputStream output() {
        return output;
    }

    public byte[] bytes() {
        return output.toByteArray();
    }

    public Response bytes(byte[] value) {
        output = new ByteArrayOutputStream();
        try {
            output.write(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public Response output(OutputStream outputStream) {
        throw new UnsupportedOperationException();
    }

    public Object entity() {
        return entity;
    }

    public Response entity(Object value) {
        entity = value;
        return this;
    }

    public void close() throws IOException {
        output.close();
    }

    @Override
    public String toString() {
        return String.format("HTTP/1.1 %s\n%s\n", status, headers);
    }
}