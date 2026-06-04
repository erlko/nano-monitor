package com.nano.monitor.analysis.rule;

import com.nano.monitor.model.AnalysisResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class RuleEngine {

    private final List<AnalysisRule> rules = new ArrayList<>();

    public RuleEngine() {
    }

    public void addRule(AnalysisRule rule) {
        rules.add(rule);
        log.info("注册分析规则: {}", rule.getName());
    }

    public void analyze(AnalysisResult result) {
        log.info("开始规则引擎分析: fingerprint={}, type={}", result.getFingerprint(),
                result.getExplainResult() != null ? result.getExplainResult().getType() : "null");

        for (AnalysisRule rule : rules) {
            try {
                rule.analyze(result);

                if (!result.getAdvices().isEmpty()) {
                    log.debug("规则触发: rule={}, totalAdvices={}", rule.getName(), result.getAdvices().size());
                }
            } catch (Exception e) {
                log.error("规则执行失败: rule={}", rule.getName(), e);
            }
        }

        result.setStatus(AnalysisResult.AnalysisStatus.RULE_ANALYZED);

        log.info("规则引擎分析完成: fingerprint={}, status={}, advices={}",
                result.getFingerprint(), result.getStatus(), result.getAdvices().size());
    }

    public boolean canHandle(String sql) {
        return true;
    }
}
