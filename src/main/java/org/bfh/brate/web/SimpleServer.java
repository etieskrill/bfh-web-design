package org.bfh.brate.web;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Scanner;

public class SimpleServer {

    public static final int CHAR_BYTE_LEN = 1;
    public static final String CONTENT_ROOT = "./content";

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8080);

        while (true) {
            try (Socket socket = serverSocket.accept()) {
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                Scanner scanner = new Scanner(socket.getInputStream());

                if (scanner.hasNextLine()) {
                    String request = scanner.nextLine();
                    System.out.println("Processing request: \"" + request + "\"");
                    parseRequest(request, socket.getOutputStream(), writer);
                } else {
                    System.err.println("No request received");
                    return;
                }
            }
        }
    }

    public enum Method {
        GET,
        POST,
        PUT,
        DELETE
    }

    public enum ReturnCode {
        SUCCESS(200, "OK"),
        BAD_REQUEST(400, "Bad Request"),
        NOT_FOUND(404, "Not Found"),
        NOT_ALLOWED(405, "Method Not Allowed"),
        TEAPOT(418, "I'm a Teapot"),
        INTERNAL_ERROR(500, "Internal Server Error");

        private final int code;
        private final String message;

        ReturnCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }

    static void parseRequest(String request, OutputStream output, PrintWriter writer) {
        final String[] tokens = request.split(" ");
        if (tokens.length != 3) {
            System.out.println("Bad request: " + request);
            writeResponse(writer, ReturnCode.BAD_REQUEST);
            return;
        }

        Method method;
        try {
            method = Method.valueOf(tokens[0]);
        } catch (IllegalArgumentException e) {
            writeResponse(writer, ReturnCode.BAD_REQUEST);
            return;
        }

        if (method != Method.GET) {
            writeResponse(writer, ReturnCode.NOT_ALLOWED);
            return;
        }

        String fileName = (tokens[1].startsWith("/") ? "" : "/") + CONTENT_ROOT + tokens[1];
        String[] fileTokens = tokens[1].split("\\.");
        String fileEnding = fileTokens.length != 0 ? fileTokens[fileTokens.length - 1] : "";
        File file = new File(fileName);

        if (!file.exists()) {
            writeResponse(writer, ReturnCode.NOT_FOUND);
            return;
        }

        if (file.isFile()) {
            try {
                byte[] content = Files.readAllBytes(file.toPath());
                writeResponse(writer, output,
                        ReturnCode.SUCCESS,
                        new String[]{
                                "Content-Length: " + content.length,
                                "Content-Type: " + switch (fileEnding) {
                                    case "html" -> "text/html";
                                    case "css" -> "text/css";
                                    case "txt" -> "text/plain";
                                    case "jpg", "jpeg" -> "image/jpg";
                                    case "png" -> "image/png";
                                    default -> throw new UnsupportedOperationException(
                                            "Cannot parse file of type " + fileEnding);
                                }
                        },
                        content
                );
                return;
            } catch (IOException | UnsupportedOperationException e) {
                e.printStackTrace();
            }
        } else if (file.isDirectory()) {
            File[] files = file.listFiles((dir, name) -> name.equals("index.html"));
            if (files == null || files.length == 0) {
                writeResponse(writer, ReturnCode.SUCCESS);
                return;
            }
            try {
                byte[] content = Files.readAllBytes(files[0].toPath());
                writeResponse(writer, output,
                        ReturnCode.SUCCESS,
                        new String[]{
                                "Content-Length: " + CHAR_BYTE_LEN * content.length,
                                "Content-Type: text/html"
                        },
                        content
                );
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        writeResponse(writer, ReturnCode.SUCCESS);
    }

    static void writeResponse(PrintWriter writer, ReturnCode code) {
        writeResponse(writer, null, code, null, null);
    }
    
    static void writeResponse(PrintWriter writer, OutputStream output, ReturnCode code, String[] headers, byte[] body) {
        writer.println("HTTP/1.1 %d %s".formatted(code.getCode(), code.getMessage()));
        if (headers != null) {
            for (String header : headers)
                writer.println(header);
        }
        writer.println();
        
        if (body != null) {
            try {
                output.write(body);
            } catch (IOException e) {
                System.err.println("Failed to write to response: " + e.getMessage());
            }
        }
    }

}