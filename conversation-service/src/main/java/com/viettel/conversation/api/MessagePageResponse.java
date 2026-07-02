package com.viettel.conversation.api;

import java.util.List;

public record MessagePageResponse(
        List<MessageResponse> messages,
        Long nextAfterSeq,
        Long nextBeforeSeq,
        boolean hasMore
) {
}
