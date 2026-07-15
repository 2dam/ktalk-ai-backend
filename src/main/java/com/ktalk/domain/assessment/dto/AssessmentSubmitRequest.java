package com.ktalk.domain.assessment.dto;

import java.util.List;

public record AssessmentSubmitRequest(List<AssessmentAnswer> answers) {
}
