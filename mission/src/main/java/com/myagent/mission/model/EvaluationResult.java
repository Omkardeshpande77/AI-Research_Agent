package com.myagent.mission.model;

import java.util.List;

public record EvaluationResult(
        Boolean isFactuallyAccurate,
        List<String> identifiedHallucinations,
        String explanation
) {
}
