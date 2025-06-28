package com.testgenie.backend.dto;

import java.util.List;

public record FileNode(
        String name,
        String type, // "file" or "folder"
        String path,
        List<FileNode> children
) {}
