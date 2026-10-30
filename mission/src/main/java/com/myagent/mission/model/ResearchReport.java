package com.myagent.mission.model;

import java.util.List;

public record ResearchReport(
        String title,
        String executiveSummary,
        List<String> keyFindings,
        List<String> sources
) {
}
