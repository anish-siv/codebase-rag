package com.anish.codebase_rag.model;

import java.util.List;

public record QueryResponse(String answer, List<String> sources) {}
