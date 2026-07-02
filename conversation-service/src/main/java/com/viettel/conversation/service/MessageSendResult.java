package com.viettel.conversation.service;

import com.viettel.conversation.api.MessageResponse;

public record MessageSendResult(MessageResponse response, boolean replayed) {
}
