package com.smartdeploy.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AuditLogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public AuditLogPageResponse search(int page, int size, Collection<AuditAction> actions, String search) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        Specification<AuditLog> specification = (root, query, builder) -> builder.conjunction();

        if (actions != null && !actions.isEmpty()) {
            specification = specification.and((root, query, builder) -> root.get("action").in(actions));
        }

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.<String>get("actor")), pattern),
                    builder.like(builder.lower(root.<String>get("summary")), pattern),
                    builder.like(builder.lower(builder.coalesce(root.<String>get("applicationName"), "")), pattern),
                    builder.like(builder.lower(builder.coalesce(root.<String>get("environmentName"), "")), pattern)
            ));
        }

        Page<AuditLog> result = auditLogRepository.findAll(specification, pageRequest);

        return new AuditLogPageResponse(
                result.getContent().stream().map(AuditLogResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }
}
