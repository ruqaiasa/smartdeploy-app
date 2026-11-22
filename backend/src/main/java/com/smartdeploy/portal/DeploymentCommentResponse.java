package com.smartdeploy.portal;

import java.time.LocalDateTime;

/** Un message, tel qu'affiché dans la discussion. */
public record DeploymentCommentResponse(Long id, String authorName, String authorEmail, String authorRole,
                                        String message, LocalDateTime createdAt, boolean mine) {
}
