package com.jyk.feedbackme.service;

import com.jyk.feedbackme.analysis.AnalysisStep;

/** 분석 호출 전 확장 지점입니다. 운영 환경에서는 구현체가 없어 아무 동작도 하지 않습니다. */
public interface FailureInjector {
    String beforeCall(AnalysisStep step) throws Exception;
}
