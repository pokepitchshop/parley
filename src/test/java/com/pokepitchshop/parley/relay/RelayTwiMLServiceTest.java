package com.pokepitchshop.parley.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pokepitchshop.parley.caller.CallerContext;
import com.pokepitchshop.parley.caller.CallerService;
import com.pokepitchshop.parley.twilio.TwilioSignatureValidator;
import com.twilio.twiml.TwiMLException;

@ExtendWith(MockitoExtension.class)
class RelayTwiMLServiceTest {

	@Mock
	private CallerService callerService;

	@Mock
	private TwilioSignatureValidator signatureValidator;

	@InjectMocks
	private RelayTwiMLService relayTwiMLService;

	@Test
	void conversationRelayConnectReturnsConnectConversationRelayTwiml() throws TwiMLException {
		given(callerService.contextFor("+15551234567")).willReturn(CallerContext.anonymous());
		given(signatureValidator.relayWebSocketUrl()).willReturn("wss://parley.example.com/relay");

		String twiml = relayTwiMLService.conversationRelayConnect("+15551234567");

		assertThat(twiml).contains("<Connect>");
		assertThat(twiml).contains("<ConversationRelay");
		assertThat(twiml).contains("url=\"wss://parley.example.com/relay\"");
		assertThat(twiml).contains("welcomeGreeting=\"Hi, you're through to Poke Pitch Shop. What can I help you with?\"");
	}
}
