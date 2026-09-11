package com.wangyunfei.cockpitdiag.model;

import java.util.List;

public record TestSuggestion(
        String name,
        String precondition,
        List<String> steps,
        String expectedResult) {
}
