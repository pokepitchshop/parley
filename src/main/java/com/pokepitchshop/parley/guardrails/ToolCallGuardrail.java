package com.pokepitchshop.parley.guardrails;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ToolCallGuardrail {

	private final CallLimitService callLimitService;

	public ToolCallGuardrail(CallLimitService callLimitService) {
		this.callLimitService = callLimitService;
	}

	public boolean allowToolCall(String callSid) {
		return callLimitService.canInvokeTool(callSid);
	}

	public void recordToolCall(String callSid) {
		callLimitService.recordToolCall(callSid);
	}

	public String blockedToolMessage() {
		return """
				I've reached the limit for account actions on this call. \
				I can still answer general shop questions, or you can call back if you need more help.
				""".trim();
	}

	public boolean isBlocked(String callSid) {
		return StringUtils.hasText(callSid) && !allowToolCall(callSid);
	}

}
