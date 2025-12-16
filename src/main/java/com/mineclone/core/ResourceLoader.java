package com.mineclone.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ResourceLoader {
    public static String loadResource(String fileName) throws Exception {
        String result;
        try (InputStream in = ResourceLoader.class.getResourceAsStream(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            result = reader.lines().reduce("", (a, b) -> a + "\n" + b);
        } catch (IOException e) {
            throw new Exception("Error loading resource: " + fileName, e);
        }
        return result;
    }
} 